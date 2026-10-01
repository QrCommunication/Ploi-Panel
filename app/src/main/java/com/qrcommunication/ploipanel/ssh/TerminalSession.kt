package com.qrcommunication.ploipanel.ssh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Lifecycle of one terminal tab. */
internal sealed interface TerminalState {
    data object Connected : TerminalState

    /** [byUser] distinguishes a deliberate disconnect from a dropped link. */
    data class Closed(val byUser: Boolean, val error: String? = null) : TerminalState
}

/**
 * Binds one [SshShellSession] to one [TerminalEmulator]: a daemon reader thread feeds host output
 * into the emulator and publishes immutable [TerminalSnapshot]s; writes go through a single
 * background executor so the UI thread never blocks on the network.
 *
 * Pure JVM (no Android types) so the whole pipe is tested with a fake session.
 */
internal class TerminalSession(
    val label: String,
    val profileId: String,
    private val shell: SshShellSession,
    columns: Int,
    rows: Int,
    scrollback: Int = TerminalEmulator.DEFAULT_SCROLLBACK,
) : AutoCloseable {
    val id: String = UUID.randomUUID().toString()
    private val lock = Any()
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ssh-writer").apply { isDaemon = true }
    }
    private val closing = AtomicBoolean(false)
    private val emulator = TerminalEmulator(columns, rows, scrollback) { reply -> send(reply) }

    private val _snapshot = MutableStateFlow(emulator.snapshot())
    val snapshot: StateFlow<TerminalSnapshot> = _snapshot.asStateFlow()

    private val _state = MutableStateFlow<TerminalState>(TerminalState.Connected)
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    val hostKey: SshHostKey get() = shell.presentedHostKey

    private val reader = Thread({ readLoop() }, "ssh-reader").apply { isDaemon = true }

    init {
        reader.start()
    }

    /** Whether cursor keys must be sent in application mode (vim, less, htop). */
    fun applicationCursorKeys(): Boolean = synchronized(lock) { emulator.applicationCursorKeys }

    fun bracketedPaste(): Boolean = synchronized(lock) { emulator.bracketedPaste }

    fun send(bytes: ByteArray) {
        if (bytes.isEmpty() || closing.get()) return
        val copy = bytes.copyOf()
        try {
            writer.execute {
                try {
                    shell.output.write(copy)
                    shell.output.flush()
                } catch (failure: IOException) {
                    finish(byUser = false, error = failure.message)
                } finally {
                    copy.fill(0)
                }
            }
        } catch (rejected: java.util.concurrent.RejectedExecutionException) {
            copy.fill(0)
        }
    }

    fun resize(columns: Int, rows: Int) {
        val changed = synchronized(lock) {
            val before = emulator.columns to emulator.rows
            emulator.resize(columns, rows)
            val after = emulator.columns to emulator.rows
            if (before != after) _snapshot.value = emulator.snapshot()
            if (before != after) after else null
        } ?: return
        if (closing.get()) return
        try {
            writer.execute {
                try { shell.resize(changed.first, changed.second) } catch (ignored: Exception) { /* next resize retries */ }
            }
        } catch (rejected: java.util.concurrent.RejectedExecutionException) {
            // closed concurrently
        }
    }

    /** Plain text of the screen and history, for copy/share. Never logged. */
    fun transcript(): String = synchronized(lock) {
        val snap = emulator.snapshot()
        (snap.scrollback + snap.rows).joinToString("\n") { row -> row.joinToString("") { it.ch.toString() }.trimEnd() }
            .trimEnd()
    }

    override fun close() = finish(byUser = true, error = null)

    private fun readLoop() {
        val buffer = ByteArray(8 * 1024)
        try {
            while (!closing.get()) {
                val count = shell.input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                synchronized(lock) {
                    emulator.feed(buffer, 0, count)
                    _snapshot.value = emulator.snapshot()
                }
            }
            finish(byUser = false, error = null)
        } catch (failure: IOException) {
            finish(byUser = false, error = failure.message)
        } catch (failure: RuntimeException) {
            finish(byUser = false, error = failure.message)
        } finally {
            buffer.fill(0)
        }
    }

    private fun finish(byUser: Boolean, error: String?) {
        if (!closing.compareAndSet(false, true)) return
        _state.value = TerminalState.Closed(byUser, error?.take(200))
        writer.shutdown()
        try { shell.close() } catch (ignored: Exception) { /* best-effort */ }
        try { writer.awaitTermination(1, TimeUnit.SECONDS) } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}

/**
 * Process-wide registry of open terminal tabs. Sessions deliberately outlive a single screen so
 * switching to another app (to fetch a password) does not drop the connection; they die with the
 * process, on explicit disconnect, or when their profile is removed or locked out.
 */
internal object TerminalSessions {
    const val MAX_SESSIONS = 8
    private val sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val all: StateFlow<List<TerminalSession>> = sessions.asStateFlow()

    @Synchronized
    fun add(session: TerminalSession) {
        prune()
        require(sessions.value.size < MAX_SESSIONS) { "Too many sessions" }
        sessions.value = sessions.value + session
    }

    @Synchronized
    fun remove(id: String) {
        val target = sessions.value.firstOrNull { it.id == id } ?: return
        target.close()
        sessions.value = sessions.value.filterNot { it.id == id }
    }

    @Synchronized
    fun closeProfile(profileId: String) {
        sessions.value.filter { it.profileId == profileId }.forEach(TerminalSession::close)
        sessions.value = sessions.value.filterNot { it.profileId == profileId }
    }

    @Synchronized
    fun closeAll() {
        sessions.value.forEach(TerminalSession::close)
        sessions.value = emptyList()
    }

    fun forProfile(profileId: String): List<TerminalSession> = sessions.value.filter { it.profileId == profileId }

    @Synchronized
    private fun prune() {
        sessions.value = sessions.value.filter { it.state.value is TerminalState.Connected }
    }
}
