package com.qrcommunication.ploipanel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptExecutionPollingTest {
    private fun execution(status: String, finishedAt: String = "") = ScriptExecution(
        id = "75b7a596-0ce5-4d35-9979-580e316c8371", serverId = 8,
        user = "ploi", content = "date", status = status, exitCode = null,
        output = "", createdAt = "", startedAt = "", finishedAt = finishedAt
    )

    @Test fun pollsOnlyPendingExecutions() {
        assertTrue(shouldPollExecution(execution("queued")))
        assertTrue(shouldPollExecution(execution("running")))
        assertFalse(shouldPollExecution(execution("completed")))
        assertFalse(shouldPollExecution(execution("FAILED")))
        assertFalse(shouldPollExecution(execution("canceled")))
        assertFalse(shouldPollExecution(execution("running", "2026-01-01T00:00:00Z")))
        assertFalse(shouldPollExecution(null))
    }
}
