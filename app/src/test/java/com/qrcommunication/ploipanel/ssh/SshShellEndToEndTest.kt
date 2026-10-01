package com.qrcommunication.ploipanel.ssh

import com.qrcommunication.ploipanel.ProfilePrefs
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.shell.ShellFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private class E2EPrefs : ProfilePrefs {
    val map = mutableMapOf<String, String>()
    override fun read(key: String): String? = map[key]
    override fun write(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

/**
 * Echo shell: announces the PTY size then echoes every byte back upper-cased, so the test can
 * prove the full path server -> JSch channel -> TerminalSession -> TerminalEmulator and back.
 */
private class EchoShell(private val sizes: MutableList<String>) : Command {
    private lateinit var input: InputStream
    private lateinit var output: OutputStream
    private var exit: ExitCallback? = null
    private var thread: Thread? = null

    override fun setInputStream(`in`: InputStream) { input = `in` }
    override fun setOutputStream(out: OutputStream) { output = out }
    override fun setErrorStream(err: OutputStream) = Unit
    override fun setExitCallback(callback: ExitCallback) { exit = callback }

    override fun start(channel: ChannelSession, env: Environment) {
        val cols = env.env["COLUMNS"]
        val lines = env.env["LINES"]
        sizes.add("$cols x $lines ${env.env["TERM"]}")
        env.addSignalListener({ _, _ -> sizes.add("${env.env["COLUMNS"]} x ${env.env["LINES"]}") })
        thread = Thread {
            try {
                output.write("ready $cols x $lines\r\n".toByteArray())
                output.flush()
                val buffer = ByteArray(256)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    val text = String(buffer, 0, n)
                    if (text.contains("exit")) break
                    output.write(text.uppercase().toByteArray())
                    output.flush()
                }
            } catch (ignored: Exception) {
            } finally {
                exit?.onExit(0)
            }
        }.apply { isDaemon = true; start() }
    }

    override fun destroy(channel: ChannelSession) { thread?.interrupt() }
}

class SshShellEndToEndTest {
    private lateinit var server: SshServer
    private val hostKeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private val authorizedKeys = CopyOnWriteArrayList<java.security.PublicKey>()
    private val passwordAttempts = AtomicInteger()
    private val sizes = CopyOnWriteArrayList<String>()
    private lateinit var prefs: E2EPrefs
    private lateinit var trust: SshHostTrustStore

    @Before fun startServer() {
        server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = KeyPairProvider.wrap(hostKeyPair)
            setPasswordAuthenticator { user, password, _ ->
                passwordAttempts.incrementAndGet()
                user == "deploy" && password == "s3cret-pass"
            }
            setPublickeyAuthenticator { user, key, _ ->
                user == "deploy" && authorizedKeys.any { KeyUtils.compareKeys(it, key) }
            }
            shellFactory = ShellFactory { EchoShell(sizes) }
        }
        server.start()
        prefs = E2EPrefs()
        trust = SshHostTrustStore(prefs, "p1")
    }

    @After fun stopServer() {
        TerminalSessions.closeAll()
        server.stop(true)
    }

    private fun hostBlob(): ByteArray {
        val line = PublicKeyEntry.toString(hostKeyPair.public)
        return Base64.getDecoder().decode(line.split(" ")[1])
    }

    private fun pinHost() {
        trust.trust("127.0.0.1", server.port, "ecdsa-sha2-nistp256", hostBlob())
    }

    private fun request() = SshConnectRequest("127.0.0.1", server.port, "deploy", 80, 24)

    private fun connector(keys: Map<String, String> = emptyMap()) =
        JSchShellConnector(trust, { id -> keys[id] }, connectTimeoutMillis = 10_000, keepAliveMillis = 0)

    private fun awaitScreen(session: TerminalSession, predicate: (TerminalSnapshot) -> Boolean): TerminalSnapshot {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val snap = session.snapshot.value
            if (predicate(snap)) return snap
            Thread.sleep(20)
        }
        throw AssertionError("Screen never matched: " + session.transcript())
    }

    private fun text(snap: TerminalSnapshot) = snap.rows.joinToString("\n") { row -> row.joinToString("") { it.ch.toString() } }

    @Test fun `unknown host is refused before any authentication`() {
        val failure = assertThrows(SshConnectException.UnknownHost::class.java) {
            connector().open(request(), SshCredential.Password("s3cret-pass".toCharArray()))
        }
        assertNotNull(failure.presented)
        assertEquals("ecdsa-sha2-nistp256", failure.presented!!.keyType)
        assertEquals(0, passwordAttempts.get())
        // Nothing was pinned by the connection itself.
        assertTrue(trust.entries().isEmpty())
    }

    @Test fun `changed host key is a hard block and credentials are never sent`() {
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val otherBlob = Base64.getDecoder().decode(PublicKeyEntry.toString(other.public).split(" ")[1])
        trust.trust("127.0.0.1", server.port, "ecdsa-sha2-nistp256", otherBlob)
        assertThrows(SshConnectException.HostKeyMismatch::class.java) {
            connector().open(request(), SshCredential.Password("s3cret-pass".toCharArray()))
        }
        assertEquals(0, passwordAttempts.get())
    }

    @Test fun `wrong password is reported as authentication failure`() {
        pinHost()
        assertThrows(SshConnectException.AuthenticationFailed::class.java) {
            connector().open(request(), SshCredential.Password("nope".toCharArray()))
        }
    }

    @Test fun `password shell round trip through emulator, resize and close`() {
        pinHost()
        val shell = connector().open(request(), SshCredential.Password("s3cret-pass".toCharArray()))
        assertEquals("ecdsa-sha2-nistp256", shell.presentedHostKey.keyType)
        val session = TerminalSession("web-1", "p1", shell, 80, 24)
        TerminalSessions.add(session)
        awaitScreen(session) { text(it).contains("ready 80 x 24") }
        assertTrue(sizes.first().endsWith("xterm-256color"))
        session.send(TerminalInput.text("hello"))
        awaitScreen(session) { text(it).contains("HELLO") }
        session.resize(100, 30)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (sizes.none { it == "100 x 30" } && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue("server saw window change: $sizes", sizes.contains("100 x 30"))
        TerminalSessions.remove(session.id)
        val state = session.state.value
        assertTrue(state is TerminalState.Closed && state.byUser)
        assertFalse(shell.isConnected())
    }

    @Test fun `remote exit closes the session without user action`() {
        pinHost()
        val shell = connector().open(request(), SshCredential.Password("s3cret-pass".toCharArray()))
        val session = TerminalSession("web-1", "p1", shell, 80, 24)
        awaitScreen(session) { text(it).contains("ready") }
        session.send(TerminalInput.text("exit\n"))
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (session.state.value is TerminalState.Connected && System.nanoTime() < deadline) Thread.sleep(20)
        val state = session.state.value
        assertTrue(state is TerminalState.Closed && !state.byUser)
    }

    @Test fun `generated ed25519 vault key authenticates`() {
        pinHost()
        val generated = SshKeyMaterial.generateEd25519()
        val line = SshKeyMaterial.authorizedKeyLine(generated.keyType, generated.publicKeyBase64, "ploi-panel")
        authorizedKeys.add(PublicKeyEntry.parsePublicKeyEntry(line).resolvePublicKey(null, emptyMap(), null))
        val shell = connector(mapOf("k1" to generated.privatePem)).open(request(), SshCredential.PrivateKey("k1", null))
        val session = TerminalSession("web-1", "p1", shell, 80, 24)
        awaitScreen(session) { text(it).contains("ready") }
        session.close()
        assertEquals(0, passwordAttempts.get())
    }

    @Test fun `key not authorized is an authentication failure`() {
        pinHost()
        val generated = SshKeyMaterial.generateEd25519()
        assertThrows(SshConnectException.AuthenticationFailed::class.java) {
            connector(mapOf("k1" to generated.privatePem)).open(request(), SshCredential.PrivateKey("k1", null))
        }
    }

    @Test fun `unreachable port is reported as unreachable`() {
        val closed = java.net.ServerSocket(0).use { it.localPort }
        trust.trust("127.0.0.1", closed, "ecdsa-sha2-nistp256", hostBlob())
        assertThrows(SshConnectException.Unreachable::class.java) {
            connector().open(
                SshConnectRequest("127.0.0.1", closed, "deploy", 80, 24),
                SshCredential.Password("x".toCharArray())
            )
        }
    }

    @Test fun `probe and shell agree on the presented key`() {
        val (type, blob) = JSchHostKeyTransport().fetchHostKey("127.0.0.1", server.port)
        assertEquals("ecdsa-sha2-nistp256", type)
        assertTrue(blob.contentEquals(hostBlob()))
    }
}
