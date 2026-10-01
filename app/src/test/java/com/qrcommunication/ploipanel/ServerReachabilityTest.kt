package com.qrcommunication.ploipanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket

class ServerReachabilityTest {
    private fun detail(status: String, ip: String = "10.0.0.9", port: Int = 2222) = ServerDetail(
        id = 7, status = status, statusId = 0, type = "server", databaseType = "", name = "web", ipAddress = ip,
        internalIp = "", sshPort = port, rebootRequired = false, phpVersion = "", phpCliVersion = "",
        mysqlVersion = "", sitesCount = 0, monitoring = false, opcache = false, installedPhpVersions = emptyList(),
        updatesPackages = 0, updatesSecurity = 0, description = "", providerName = "", createdAt = "",
        createdHuman = "", uptimeHuman = ""
    )

    @Test fun onlyUnreachableLikeStatusesTriggerAnAutomaticRecheck() {
        listOf("unreachable", "Server unreachable", "offline", "down", "Connection timed out").forEach {
            assertTrue(it, needsRecheck(it))
        }
        listOf("active", "Server active", "installing", "").forEach { assertFalse(it, needsRecheck(it)) }
    }

    @Test fun recheckUsesPloiFreshAddressAndSshPortThenProbesFromDevice() {
        var probed: Pair<String, Int>? = null
        val result = recheckServer(
            "token", Server(7, "web", "unreachable", "10.0.0.1"),
            prober = { host, port, _ -> probed = host to port; DeviceProbe.Reachable(port, 12) },
            fetch = { _, _ -> detail("active") },
            now = { 1_000L }
        )
        assertEquals("active", result.ploiStatus)
        assertNull(result.ploiError)
        assertEquals("10.0.0.9" to 2222, probed)
        assertEquals(DeviceProbe.Reachable(2222, 12), result.probe)
        assertEquals(1_000L, result.checkedAt)
    }

    @Test fun ploiFailureIsKeptAndDeviceProbeStillRunsOnKnownAddress() {
        val failure = PloiOfflineException(IOException("no route"))
        val result = recheckServer(
            "token", Server(7, "web", "unreachable", "10.0.0.1"),
            prober = { _, port, _ -> DeviceProbe.TimedOut(port) },
            fetch = { _, _ -> throw failure }
        )
        assertNull("no invented status", result.ploiStatus)
        assertEquals(failure, result.ploiError)
        assertEquals(DeviceProbe.TimedOut(SSH_DEFAULT_PORT), result.probe)
    }

    @Test fun noAddressMeansNoProbeRatherThanAFakeResult() {
        val result = recheckServer(
            "token", Server(7, "web", "unreachable", ""),
            prober = { _, _, _ -> error("must not probe") },
            fetch = { _, _ -> detail("unreachable", ip = "") }
        )
        assertNull(result.probe)
    }

    @Test fun socketProberReportsReachableAndRefusedOnLoopback() {
        ServerSocket(0).use { open ->
            val ok = SocketTcpProber.probe("127.0.0.1", open.localPort, 2_000)
            assertTrue(ok is DeviceProbe.Reachable)
        }
        val closedPort = ServerSocket(0).use { it.localPort }
        val refused = SocketTcpProber.probe("127.0.0.1", closedPort, 2_000)
        assertTrue("expected refused, got $refused", refused is DeviceProbe.Refused)
    }
}
