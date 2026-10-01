package com.qrcommunication.ploipanel.ssh

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference

/** How the user proves identity to the server. Secrets live only for the connection attempt. */
internal sealed interface SshCredential {
    /** Stored vault key; [passphrase] only for a passphrase-protected PEM, never persisted. */
    class PrivateKey(val keyId: String, val passphrase: CharArray?) : SshCredential

    /** Password (also answers a keyboard-interactive "Password:" prompt). */
    class Password(val password: CharArray) : SshCredential
}

/** Parameters of an interactive shell session. */
internal data class SshConnectRequest(
    val host: String,
    val port: Int,
    val username: String,
    val columns: Int,
    val rows: Int,
) {
    init {
        SshHostTrustStore.validateHost(host)
        SshHostTrustStore.validatePort(port)
        require(isValidUsername(username)) { "Invalid username" }
        require(columns in TerminalEmulator.MIN_SIZE..TerminalEmulator.MAX_COLUMNS) { "Invalid columns" }
        require(rows in TerminalEmulator.MIN_SIZE..TerminalEmulator.MAX_ROWS) { "Invalid rows" }
    }

    companion object {
        private val USERNAME = Regex("[A-Za-z_][A-Za-z0-9_.-]{0,31}")
        fun isValidUsername(username: String) = USERNAME.matches(username)
    }
}

/** An open interactive shell. Implementations must be safe to [close] from any thread, repeatedly. */
internal interface SshShellSession : AutoCloseable {
    val input: InputStream
    val output: OutputStream
    val presentedHostKey: SshHostKey
    fun resize(columns: Int, rows: Int)
    fun isConnected(): Boolean
    override fun close()
}

/** Why a connection could not be established; each maps to one operator-facing message. */
internal sealed class SshConnectException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    /** Host has no pin yet: run the connection-time probe and confirm the fingerprint first. */
    class UnknownHost(val presented: SshHostKey?) : SshConnectException("Host key not pinned")

    /** Presented key differs from the pin: hard block, never bypassed here. */
    class HostKeyMismatch(val presented: SshHostKey?) : SshConnectException("Host key mismatch")
    class AuthenticationFailed(cause: Throwable?) : SshConnectException("Authentication failed", cause)
    class WrongPassphrase : SshConnectException("Wrong key passphrase")
    class Unreachable(cause: Throwable?) : SshConnectException("Host unreachable", cause)
    class Protocol(cause: Throwable?) : SshConnectException("SSH negotiation failed", cause)
}

internal fun interface SshShellConnector {
    /** Blocking; call from a background dispatcher. Throws [SshConnectException]. */
    fun open(request: SshConnectRequest, credential: SshCredential): SshShellSession
}

/**
 * JSch-backed interactive shell with strict host key checking against [trustStore].
 *
 * The host key repository is read-only from JSch's point of view: `check()` answers from the
 * TOFU store and `add()` is a no-op, so a connection can never pin anything by itself. Unknown
 * and mismatching keys abort the handshake *before* authentication, so no password or key
 * signature is ever sent to an unverified host.
 */
internal class JSchShellConnector(
    private val trustStore: SshHostTrustStore,
    private val privateKeyPem: (keyId: String) -> String?,
    private val connectTimeoutMillis: Int = 15_000,
    private val keepAliveMillis: Int = 30_000,
) : SshShellConnector {

    override fun open(request: SshConnectRequest, credential: SshCredential): SshShellSession {
        val jsch = JSchSupport.newJSch()
        val presented = AtomicReference<SshHostKey?>()
        val decision = AtomicReference<SshTrustDecision?>()
        val host = SshHostTrustStore.validateHost(request.host)
        val port = SshHostTrustStore.validatePort(request.port)

        val repository = object : HostKeyRepository {
            override fun check(checkedHost: String?, key: ByteArray?): Int {
                if (key == null || key.isEmpty()) return HostKeyRepository.NOT_INCLUDED
                val type = SshHostTrustStore.keyBlobType(key) ?: return HostKeyRepository.NOT_INCLUDED
                presented.set(SshHostKey(host, port, type, key.copyOf()))
                val verdict = try {
                    trustStore.evaluate(host, port, type, key)
                } catch (unreadable: Exception) {
                    // Corrupted trust store: fail closed like OpenSSH with an unreadable known_hosts.
                    SshTrustDecision.MISMATCH
                }
                decision.set(verdict)
                return when (verdict) {
                    SshTrustDecision.TRUSTED -> HostKeyRepository.OK
                    SshTrustDecision.MISMATCH -> HostKeyRepository.CHANGED
                    SshTrustDecision.UNKNOWN -> HostKeyRepository.NOT_INCLUDED
                }
            }

            override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
            override fun remove(host: String?, type: String?) = Unit
            override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
            override fun getKnownHostsRepositoryID(): String = "ploi-panel-trust-store"
            override fun getHostKey(): Array<HostKey> = emptyArray()
            override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
        }

        var passwordCopy: CharArray? = null
        var session: Session? = null
        try {
            when (credential) {
                is SshCredential.PrivateKey -> {
                    val pem = privateKeyPem(credential.keyId)
                        ?: throw SshConnectException.AuthenticationFailed(null)
                    val passphrase = credential.passphrase?.let { String(it).toByteArray(Charsets.UTF_8) }
                    try {
                        if (!SshKeyMaterial.unlocks(pem, passphrase)) throw SshConnectException.WrongPassphrase()
                        jsch.addIdentity("vault-${credential.keyId}", pem.toByteArray(Charsets.US_ASCII), null, passphrase)
                    } catch (invalid: JSchException) {
                        throw SshConnectException.AuthenticationFailed(invalid)
                    } finally {
                        passphrase?.fill(0)
                    }
                }
                is SshCredential.Password -> passwordCopy = credential.password.copyOf()
            }
            val s = jsch.getSession(request.username, host, port)
            session = s
            s.setHostKeyRepository(repository)
            s.setConfig("StrictHostKeyChecking", "yes")
            s.setConfig("HashKnownHosts", "no")
            s.setConfig(
                "server_host_key",
                JSchSupport.hostKeyAlgorithms(
                    pinnedKeyTypes(host, port),
                    s.getConfig("server_host_key") ?: ""
                )
            )
            s.setConfig(
                "PreferredAuthentications",
                if (credential is SshCredential.Password) "password,keyboard-interactive" else "publickey"
            )
            // Never forward the phone's agent or X11 display to a server.
            s.setConfig("ForwardAgent", "no")
            s.setDaemonThread(true)
            s.setServerAliveInterval(keepAliveMillis)
            s.setServerAliveCountMax(3)
            passwordCopy?.let { password ->
                s.setPassword(String(password).toByteArray(Charsets.UTF_8))
                s.setUserInfo(PasswordPrompts(password))
            }
            s.timeout = connectTimeoutMillis
            s.connect(connectTimeoutMillis)
            s.timeout = 0 // interactive: reads block until data or disconnect

            val channel = s.openChannel("shell") as ChannelShell
            channel.setPtyType("xterm-256color", request.columns, request.rows, 0, 0)
            channel.setPty(true)
            channel.setAgentForwarding(false)
            channel.setEnv("LANG", "en_US.UTF-8")
            channel.setEnv("COLORTERM", "truecolor")
            val input = channel.inputStream
            val output = channel.outputStream
            channel.connect(connectTimeoutMillis)
            val key = presented.get() ?: throw SshConnectException.Protocol(null)
            return JSchShell(s, channel, input, output, key)
        } catch (known: SshConnectException) {
            session?.disconnect()
            throw known
        } catch (failure: JSchException) {
            session?.disconnect()
            throw classify(failure, decision.get(), presented.get())
        } catch (failure: IOException) {
            session?.disconnect()
            throw SshConnectException.Unreachable(failure)
        } finally {
            passwordCopy?.fill('\u0000')
        }
    }

    private fun pinnedKeyTypes(host: String, port: Int): List<String> = try {
        trustStore.entries().filter { it.host == host && it.port == port }.map { it.keyType }
    } catch (unreadable: Exception) {
        emptyList()
    }

    private fun classify(failure: JSchException, decision: SshTrustDecision?, presented: SshHostKey?): SshConnectException {
        when (decision) {
            SshTrustDecision.UNKNOWN -> return SshConnectException.UnknownHost(presented)
            SshTrustDecision.MISMATCH -> return SshConnectException.HostKeyMismatch(presented)
            else -> Unit
        }
        val message = failure.message.orEmpty()
        val cause = failure.cause
        return when {
            message.contains("Auth fail", ignoreCase = true) ||
                message.contains("Auth cancel", ignoreCase = true) -> SshConnectException.AuthenticationFailed(failure)
            cause is IOException || message.contains("timeout", ignoreCase = true) ||
                message.contains("UnknownHost", ignoreCase = true) ||
                message.contains("Connection refused", ignoreCase = true) -> SshConnectException.Unreachable(failure)
            else -> SshConnectException.Protocol(failure)
        }
    }

    /** Answers keyboard-interactive password prompts only; any other challenge is refused. */
    private class PasswordPrompts(private val password: CharArray) : UserInfo, UIKeyboardInteractive {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String = String(password)
        override fun promptPassword(message: String?): Boolean = true
        override fun promptPassphrase(message: String?): Boolean = false
        override fun promptYesNo(message: String?): Boolean = false // never accept host keys interactively
        override fun showMessage(message: String?) = Unit
        override fun promptKeyboardInteractive(
            destination: String?, name: String?, instruction: String?, prompt: Array<out String>?, echo: BooleanArray?
        ): Array<String>? {
            val prompts = prompt ?: return null
            if (prompts.size != 1 || !prompts[0].contains("password", ignoreCase = true)) return null
            return arrayOf(String(password))
        }
    }

    private class JSchShell(
        private val session: Session,
        private val channel: ChannelShell,
        override val input: InputStream,
        override val output: OutputStream,
        override val presentedHostKey: SshHostKey,
    ) : SshShellSession {
        override fun resize(columns: Int, rows: Int) {
            if (channel.isConnected) channel.setPtySize(columns, rows, 0, 0)
        }

        override fun isConnected(): Boolean = session.isConnected && channel.isConnected && !channel.isClosed

        override fun close() {
            try { channel.disconnect() } catch (ignored: Exception) { /* best-effort teardown */ }
            try { session.disconnect() } catch (ignored: Exception) { /* best-effort teardown */ }
        }
    }
}
