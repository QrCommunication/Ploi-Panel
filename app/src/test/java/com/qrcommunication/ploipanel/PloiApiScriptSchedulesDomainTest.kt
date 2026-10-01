package com.qrcommunication.ploipanel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Parsing, validation and wire-level coverage for every documented route of the
 * `script-schedules` domain (/api/scripts/{script}/schedules, 6 routes).
 * Every route requires the Pro plan or higher (documented on each page).
 */
class PloiApiScriptSchedulesDomainTest {
    private val token = "test-token"
    private val recorded = CopyOnWriteArrayList<HttpRequest>()
    private var scriptedBody = "{}"

    private fun installFakeClient() {
        val transport = HttpTransport { request ->
            recorded.add(request)
            HttpResponse(200, scriptedBody, emptyMap())
        }
        PloiApi.httpClient = PloiHttpClient(transport = transport)
    }

    @After fun restoreClient() {
        PloiApi.httpClient = PloiHttpClient()
    }

    /** Documented schedule entry (developers.ploi.io/script-schedules/list-schedules). */
    private fun scheduleEntry(): JSONObject = JSONObject()
        .put("id", 1)
        .put("script_id", 42)
        .put("cron_expression", "*/5 * * * *")
        .put("servers", JSONArray().put(12).put(18))
        .put("is_paused", false)
        .put("next_run_at", "2026-05-13T08:55:00+00:00")
        .put("last_run_at", "2026-05-13T08:50:00+00:00")
        .put("created_at", "2026-05-12 10:11:22")
        .put("updated_at", "2026-05-13 08:50:01")

    /** Documented list shape. */
    private fun scheduleListJson(): String = JSONObject()
        .put("data", JSONArray().put(scheduleEntry()))
        .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
        .toString()

    /** Documented single-schedule shape (get + create + update + toggle responses share it). */
    private fun scheduleJson(): String = JSONObject().put("data", scheduleEntry()).toString()

    /** Documented toggle response shape: paused schedule with null next_run_at. */
    private fun pausedScheduleJson(): String = JSONObject()
        .put(
            "data",
            scheduleEntry()
                .put("is_paused", true)
                .put("next_run_at", JSONObject.NULL)
        )
        .toString()

    // ---- GET /api/scripts/{script}/schedules ----

    @Test fun parsesScheduleListWithDocumentedShape() {
        val page = PloiApi.parseScriptSchedules(scheduleListJson())
        assertEquals(1, page.currentPage)
        assertFalse(page.hasNext)
        val schedule = page.schedules.single()
        assertEquals(1L, schedule.id)
        assertEquals(42L, schedule.scriptId)
        assertEquals("*/5 * * * *", schedule.cronExpression)
        assertEquals(listOf(12L, 18L), schedule.servers)
        assertFalse(schedule.isPaused)
        assertEquals("2026-05-13T08:55:00+00:00", schedule.nextRunAt)
        assertEquals("2026-05-13T08:50:00+00:00", schedule.lastRunAt)
        assertEquals("2026-05-12 10:11:22", schedule.createdAt)
        assertEquals("2026-05-13 08:50:01", schedule.updatedAt)
    }

    @Test fun scriptSchedulesHitsDocumentedRouteWithPagination() {
        installFakeClient()
        scriptedBody = scheduleListJson()
        val page = PloiApi.scriptSchedules(token, scriptId = 42, page = 2, perPage = 50)
        assertEquals(1, page.schedules.size)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/scripts/42/schedules?page=2&per_page=50", request.url)
        recorded.clear()
        PloiApi.scriptSchedules(token, scriptId = 42)
        assertEquals("https://ploi.io/api/scripts/42/schedules?page=1&per_page=15", recorded.single().url)
        assertThrowsIAE { PloiApi.scriptSchedules(token, scriptId = 0) }
        assertThrowsIAE { PloiApi.scriptSchedules(token, scriptId = -1) }
        assertThrowsIAE { PloiApi.scriptSchedules(token, scriptId = 42, page = 0) }
        assertThrowsIAE { PloiApi.scriptSchedules(token, scriptId = 42, perPage = 51) }
    }

    @Test fun schedulePaginationMetaDrivesHasNext() {
        val twoPages = JSONObject(scheduleListJson())
            .put("meta", JSONObject().put("current_page", 1).put("last_page", 2))
            .toString()
        assertTrue(PloiApi.parseScriptSchedules(twoPages).hasNext)
    }

    // ---- GET /api/scripts/{script}/schedules/{schedule} ----

    @Test fun scriptScheduleReadsDocumentedRoute() {
        installFakeClient()
        scriptedBody = scheduleJson()
        val schedule = PloiApi.scriptSchedule(token, scriptId = 42, scheduleId = 1)
        assertEquals(1L, schedule.id)
        assertEquals(42L, schedule.scriptId)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/scripts/42/schedules/1", request.url)
        assertThrowsIAE { PloiApi.scriptSchedule(token, scriptId = 0, scheduleId = 1) }
        assertThrowsIAE { PloiApi.scriptSchedule(token, scriptId = 42, scheduleId = 0) }
        assertThrowsIAE { PloiApi.scriptSchedule(token, scriptId = 42, scheduleId = -3) }
    }

    // ---- POST /api/scripts/{script}/schedules ----

    @Test fun createScriptSchedulePostsDocumentedRequiredFields() {
        installFakeClient()
        scriptedBody = scheduleJson()
        val created = PloiApi.createScriptSchedule(
            token, scriptId = 42, CreateScriptScheduleRequest("*/5 * * * *", listOf(12, 18))
        )
        assertEquals(1L, created.id)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/scripts/42/schedules", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("*/5 * * * *", body.getString("cron_expression"))
        val servers = body.getJSONArray("servers")
        assertEquals(2, servers.length())
        assertEquals(12L, servers.getLong(0))
        assertEquals(18L, servers.getLong(1))
    }

    @Test fun createScriptScheduleValidatesDocumentedRequiredFields() {
        // documented: cron_expression (standard 5 fields) and servers (non-empty IDs) are required
        assertThrowsIAE { CreateScriptScheduleRequest("", listOf(12)) }
        assertThrowsIAE { CreateScriptScheduleRequest("   ", listOf(12)) }
        assertThrowsIAE { CreateScriptScheduleRequest("*/5 * * *", listOf(12)) } // 4 fields
        assertThrowsIAE { CreateScriptScheduleRequest("*/5 * * * * *", listOf(12)) } // 6 fields
        assertThrowsIAE { CreateScriptScheduleRequest("*/5 * * * *; rm -rf /", listOf(12)) } // illegal chars
        assertThrowsIAE { CreateScriptScheduleRequest("*/5 * * * *", emptyList()) }
        assertThrowsIAE { CreateScriptScheduleRequest("*/5 * * * *", listOf(0)) }
        assertThrowsIAE { CreateScriptScheduleRequest("*/5 * * * *", listOf(12, -1)) }
        CreateScriptScheduleRequest("0 3 * * *", listOf(12))
        CreateScriptScheduleRequest("0 3 * JAN MON-FRI", listOf(12)) // named fields accepted
        assertThrowsIAE { PloiApi.createScriptSchedule(token, 0, CreateScriptScheduleRequest("0 3 * * *", listOf(1))) }
    }

    // ---- PATCH /api/scripts/{script}/schedules/{schedule} ----

    @Test fun updateScriptScheduleReplacesBothDocumentedFields() {
        installFakeClient()
        scriptedBody = scheduleJson()
        val updated = PloiApi.updateScriptSchedule(
            token, scriptId = 42, scheduleId = 1, UpdateScriptScheduleRequest("0 3 * * *", listOf(12))
        )
        assertEquals("*/5 * * * *", updated.cronExpression) // parser returns the server response
        val request = recorded.single()
        assertEquals("PATCH", request.method)
        assertEquals("https://ploi.io/api/scripts/42/schedules/1", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("0 3 * * *", body.getString("cron_expression"))
        assertEquals(1, body.getJSONArray("servers").length())
    }

    @Test fun updateScriptScheduleRequiresBothDocumentedFields() {
        // documented: both cron_expression and servers are required (replace, not merge)
        assertThrowsIAE { UpdateScriptScheduleRequest("", listOf(12)) }
        assertThrowsIAE { UpdateScriptScheduleRequest("0 3 * * *", emptyList()) }
        assertThrowsIAE { UpdateScriptScheduleRequest("0 3 * *", listOf(12)) }
        assertThrowsIAE {
            PloiApi.updateScriptSchedule(token, 42, 0, UpdateScriptScheduleRequest("0 3 * * *", listOf(12)))
        }
    }

    // ---- POST /api/scripts/{script}/schedules/{schedule}/toggle ----

    @Test fun toggleScriptSchedulePostsDocumentedRouteAndParsesPausedState() {
        installFakeClient()
        scriptedBody = pausedScheduleJson()
        val toggled = PloiApi.toggleScriptSchedule(token, scriptId = 42, scheduleId = 1)
        assertTrue(toggled.isPaused)
        assertEquals("", toggled.nextRunAt) // documented null while paused
        assertEquals("2026-05-13T08:50:00+00:00", toggled.lastRunAt)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/scripts/42/schedules/1/toggle", request.url)
        assertThrowsIAE { PloiApi.toggleScriptSchedule(token, scriptId = 0, scheduleId = 1) }
        assertThrowsIAE { PloiApi.toggleScriptSchedule(token, scriptId = 42, scheduleId = 0) }
    }

    // ---- DELETE /api/scripts/{script}/schedules/{schedule} ----

    @Test fun deleteScriptScheduleHitsDocumentedRouteAndToleratesNullMessage() {
        installFakeClient()
        // documented: {"status": "ok", "message": null}
        scriptedBody = JSONObject().put("status", "ok").put("message", JSONObject.NULL).toString()
        val message = PloiApi.deleteScriptSchedule(token, scriptId = 42, scheduleId = 1)
        assertEquals("", message)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/scripts/42/schedules/1", request.url)
        assertNull(request.body)
        assertThrowsIAE { PloiApi.deleteScriptSchedule(token, scriptId = 0, scheduleId = 1) }
        assertThrowsIAE { PloiApi.deleteScriptSchedule(token, scriptId = 42, scheduleId = 0) }
    }

    // ---- Error handling ----

    @Test fun malformedSchedulePayloadSurfacesTypedError() {
        installFakeClient()
        scriptedBody = """{"unexpected":true}"""
        try {
            PloiApi.scriptSchedule(token, 42, 1)
            fail("Expected PloiMalformedPayloadException")
        } catch (expected: PloiMalformedPayloadException) {
            // typed error, not a raw JSONException
        }
        try {
            PloiApi.parseScriptSchedules("""{"data":[{"id":1}]}""") // missing documented fields
            fail("Expected JSONException on missing documented fields")
        } catch (expected: org.json.JSONException) {
            // required fields are enforced by the parser
        }
        try {
            PloiApi.parseScriptSchedule("""{"data":{"id":1,"script_id":42,"cron_expression":"* * * * *","servers":{}}}""")
            fail("Expected JSONException on non-array servers")
        } catch (expected: org.json.JSONException) {
            // servers must be an array of server IDs
        }
    }

    private fun assertThrowsIAE(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // validation worked
        }
    }
}
