package com.qrcommunication.ploipanel

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeployScriptBatchTest {
    private val first = DeployTarget(11, 101, "app.example.com", "Server A")
    private val second = DeployTarget(22, 202, "stage.example.com", "Server B")
    private class FakeGateway : DeployScriptGateway {
        val scripts = mutableMapOf(11L to "original-a", 22L to "original-b")
        val writes = mutableListOf<Pair<Long, String>>()
        var failRead: Long? = null
        var failWrite: Long? = null
        var wrongReadback: Long? = null
        override suspend fun read(serverId: Long, siteId: Long): String {
            if (siteId == failRead) error("preflight failure")
            if (siteId == wrongReadback && writes.any { it.first == siteId }) return "not the requested script"
            return scripts.getValue(serverId)
        }
        override suspend fun write(serverId: Long, siteId: Long, script: String) {
            if (siteId == failWrite) throw PloiHttpException(429)
            scripts[serverId] = script
            writes += siteId to script
        }
    }

    @Test fun preflightsEverySelectionBeforeAnyMutation() = runBlocking {
        val gateway = FakeGateway().apply { failRead = second.siteId }
        val result = DeployScriptBatch(gateway).apply("echo ready", listOf(first, second))
        assertTrue(result.abortedBeforeWrite)
        assertTrue(gateway.writes.isEmpty())
        assertEquals(1, result.targets.count { it.status == DeployApplyStatus.PREFLIGHT_FAILED })
    }

    @Test fun writesOnlyExplicitSelectionsAndVerifiesReadback() = runBlocking {
        val gateway = FakeGateway()
        val outcome = DeployScriptBatch(gateway).apply("echo {{domain}} {{server_id}}", listOf(second))
        assertEquals(listOf(202L to "echo 'stage.example.com' 22"), gateway.writes)
        assertEquals(listOf(DeployApplyStatus.VERIFIED), outcome.targets.map { it.status })
        assertEquals("original-a", gateway.scripts[11L])
        assertFalse(outcome.abortedBeforeWrite)
    }

    @Test fun rateLimitStopsRemainingSitesWithoutRetryingWrites() = runBlocking {
        val gateway = FakeGateway().apply { failWrite = first.siteId }
        val result = DeployScriptBatch(gateway).apply("echo safe", listOf(first, second))
        assertEquals(emptyList<Pair<Long, String>>(), gateway.writes)
        assertEquals(listOf(DeployApplyStatus.FAILED, DeployApplyStatus.SKIPPED), result.targets.map { it.status })
    }

    @Test fun readbackMismatchIsNotReportedAsSuccess() = runBlocking {
        val gateway = FakeGateway().apply { wrongReadback = first.siteId }
        val result = DeployScriptBatch(gateway).apply("echo changed", listOf(first, second))
        assertEquals(listOf(DeployApplyStatus.UNVERIFIED, DeployApplyStatus.VERIFIED), result.targets.map { it.status })
        assertEquals(2, gateway.writes.size)
    }

    @Test fun invalidEmptyDuplicateOrUnknownPlaceholderBlocksBeforeNetwork() {
        val gateway = FakeGateway()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { DeployScriptBatch(gateway).apply("echo hi", emptyList()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { DeployScriptBatch(gateway).apply("echo hi", listOf(first, first)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { DeployScriptBatch(gateway).apply("echo {{unknown}}", listOf(first)) }
        }
        assertTrue(gateway.writes.isEmpty())
    }
}
