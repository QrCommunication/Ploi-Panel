package com.qrcommunication.ploipanel

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Re-test for servers Ploi reports as unreachable. Ploi documents no endpoint that asks it to
 * re-probe a server, so a recheck does two honest things and labels them separately:
 *  1. re-reads the server from Ploi (GET /servers/{id}) to show its current status;
 *  2. opens a TCP connection from this phone to the server's SSH port.
 * The second result says only "this device could / could not open port N"; it never claims the
 * server is healthy, and it is never presented as Ploi's own verdict.
 */
internal sealed interface DeviceProbe {
    data class Reachable(val port: Int, val latencyMs: Long) : DeviceProbe
    data class Refused(val port: Int) : DeviceProbe
    data class TimedOut(val port: Int) : DeviceProbe
    data class Failed(val port: Int) : DeviceProbe
}

internal fun interface TcpProber {
    fun probe(host: String, port: Int, timeoutMs: Int): DeviceProbe
}

/** Plain TCP connect with a bounded timeout; nothing is sent over the socket. */
internal object SocketTcpProber : TcpProber {
    override fun probe(host: String, port: Int, timeoutMs: Int): DeviceProbe {
        val started = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            DeviceProbe.Reachable(port, ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0))
        } catch (_: SocketTimeoutException) {
            DeviceProbe.TimedOut(port)
        } catch (_: java.net.ConnectException) {
            DeviceProbe.Refused(port)
        } catch (_: IOException) {
            DeviceProbe.Failed(port)
        } catch (_: IllegalArgumentException) {
            DeviceProbe.Failed(port)
        } catch (_: SecurityException) {
            DeviceProbe.Failed(port)
        }
    }
}

internal const val DEVICE_PROBE_TIMEOUT_MS = 4_000

/** At most this many servers per page are re-read automatically, to stay far below rate limits. */
internal const val MAX_AUTO_RECHECKS_PER_PAGE = 10

/** Statuses that ask for an automatic recheck when the list or the server is opened. */
internal fun needsRecheck(status: String): Boolean {
    val value = status.trim().lowercase()
    return value.contains("unreach") || value.contains("offline") || value.contains("down") ||
        value.contains("timeout") || value.contains("timed out") || value.contains("injoignable")
}

/** Outcome of one recheck: the fresh Ploi status (if re-read) and the device probe (if run). */
internal data class Recheck(
    val serverId: Long,
    val ploiStatus: String?,
    val ploiError: Throwable?,
    val probe: DeviceProbe?,
    val checkedAt: Long
)

/**
 * Re-reads the server from Ploi, then probes the SSH port reported by Ploi (22 when unknown).
 * Either half may fail independently; each failure is kept, never replaced by a guess.
 */
internal fun recheckServer(
    token: String, server: Server,
    prober: TcpProber = SocketTcpProber,
    fetch: (String, Long) -> ServerDetail = PloiApi::server,
    now: () -> Long = System::currentTimeMillis
): Recheck {
    var status: String? = null
    var error: Throwable? = null
    var host = server.ipAddress
    var port = SSH_DEFAULT_PORT
    try {
        val detail = fetch(token, server.id)
        status = detail.status
        if (detail.ipAddress.isNotBlank()) host = detail.ipAddress
        if (detail.sshPort in 1..65535) port = detail.sshPort
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        error = failure
    }
    val probe = if (host.isNotBlank()) prober.probe(host, port, DEVICE_PROBE_TIMEOUT_MS) else null
    return Recheck(server.id, status, error, probe, now())
}
