package com.qrcommunication.ploipanel.ssh

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.UserInfo
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Outcome of confronting a host key presented during an SSH handshake with the pinned trust
 * store. The UI turns [FirstContact] into an explicit fingerprint confirmation ([SshHostTrustStore.trust])
 * and [KeyMismatch] into a hard block lifted only by an operator re-pin ([SshHostTrustStore.repin]).
 */
internal sealed interface SshProbeAssessment {
    /** Presented key matches the pin; connection may proceed. */
    data class Trusted(val presented: SshHostKey) : SshProbeAssessment

    /** No pin yet: first contact, explicit operator confirmation required before [SshHostTrustStore.trust]. */
    data class FirstContact(val presented: SshHostKey) : SshProbeAssessment

    /** Presented key differs from the pin: hard block, only an explicit re-pin can lift it. */
    data class KeyMismatch(val pinned: SshHostKey, val presented: SshHostKey) : SshProbeAssessment
}

/**
 * Compares one presented host key with the trust store. Rejects unsupported types and blobs
 * whose embedded algorithm does not match the announced type, exactly like pinning does.
 *
 * @throws IllegalArgumentException on an unsupported or malformed presented key.
 */
internal fun assessPresentedKey(
    store: SshHostTrustStore,
    host: String,
    port: Int,
    keyType: String,
    keyBlob: ByteArray
): SshProbeAssessment {
    require(keyType in SSH_HOST_KEY_TYPES) { "Unsupported key type" }
    require(keyBlob.isNotEmpty() && keyBlob.size <= SshHostTrustStore.MAX_KEY_BLOB_BYTES) {
        "Key blob size out of bounds"
    }
    require(SshHostTrustStore.keyBlobType(keyBlob) == keyType) { "Key blob does not match key type" }
    val presented = SshHostKey(
        SshHostTrustStore.validateHost(host),
        SshHostTrustStore.validatePort(port),
        keyType,
        keyBlob.copyOf()
    )
    return when (store.evaluate(presented.host, presented.port, presented.keyType, presented.keyBlob)) {
        SshTrustDecision.TRUSTED -> SshProbeAssessment.Trusted(presented)
        SshTrustDecision.UNKNOWN -> SshProbeAssessment.FirstContact(presented)
        SshTrustDecision.MISMATCH -> SshProbeAssessment.KeyMismatch(
            pinned = store.entries().first {
                it.host == presented.host && it.port == presented.port && it.keyType == presented.keyType
            },
            presented = presented
        )
    }
}

/**
 * Blocking transport that performs an SSH handshake only — no authentication, no channel, no
 * command — and returns the host key presented by the server. Implementations must bound their
 * network waits and never accept or pin anything by themselves.
 */
internal interface SshHostKeyTransport {
    /** @return the announced key type and raw SSH blob of the presented host key. */
    @Throws(IOException::class)
    fun fetchHostKey(host: String, port: Int): Pair<String, ByteArray>
}

/**
 * JSch-backed probe. The host key is recorded from a repository whose [HostKeyRepository.check]
 * always answers [HostKeyRepository.NOT_INCLUDED] with strict host key checking enabled and no
 * `UserInfo`: JSch then aborts the connection before any authentication attempt, which is
 * exactly what a probe wants. Nothing is ever trusted here; the decision belongs to
 * [assessPresentedKey] and the operator.
 */
internal class JSchHostKeyTransport(
    private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS
) : SshHostKeyTransport {
    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000

        /** Placeholder: the handshake aborts before authentication, no credential is ever sent. */
        private const val PROBE_USERNAME = "ploi-panel-probe"
    }

    override fun fetchHostKey(host: String, port: Int): Pair<String, ByteArray> {
        val normalized = SshHostTrustStore.validateHost(host)
        val checkedPort = SshHostTrustStore.validatePort(port)
        val recorded = AtomicReference<ByteArray?>()
        val recorder = object : HostKeyRepository {
            override fun check(host: String?, key: ByteArray?): Int {
                if (key != null && key.isNotEmpty()) recorded.compareAndSet(null, key.copyOf())
                // Never accepted inside the transport: the trust store decides, the operator confirms.
                return HostKeyRepository.NOT_INCLUDED
            }

            override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
            override fun remove(host: String?, type: String?) = Unit
            override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
            override fun getKnownHostsRepositoryID(): String = "ploi-panel-probe"
            override fun getHostKey(): Array<HostKey> = emptyArray()
            override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
        }
        val session = JSch().getSession(PROBE_USERNAME, normalized, checkedPort)
        try {
            session.setHostKeyRepository(recorder)
            session.setDaemonThread(true)
            session.setConfig("PreferredAuthentications", "none")
            // No UserInfo is installed: with strict checking the unknown key aborts the
            // handshake right after check() records it, before any authentication attempt.
            session.setConfig("StrictHostKeyChecking", "yes")
            session.timeout = timeoutMillis
            session.connect(timeoutMillis)
        } catch (failure: JSchException) {
            if (recorded.get() == null) {
                throw IOException(failure.message ?: "SSH handshake failed", failure)
            }
            // Expected path: the key was captured and the connection aborted before authentication.
        } finally {
            try {
                session.disconnect()
            } catch (ignored: Exception) {
                // Best-effort teardown of a probe connection.
            }
        }
        val key = recorded.get() ?: throw IOException("SSH handshake presented no host key")
        val keyType = SshHostTrustStore.keyBlobType(key)
            ?: throw IOException("SSH host key is malformed")
        return keyType to key
    }
}
