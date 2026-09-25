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
 * `script-actions` domain (/api/scripts/{script}/actions, 7 routes).
 * Every route requires the Unlimited plan (documented on each page).
 */
class PloiApiScriptActionsDomainTest {
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

    /** Documented per-server pivot entry (developers.ploi.io/script-actions/list-actions). */
    private fun serverPivot(status: String = "installed"): JSONObject = JSONObject()
        .put("server_id", 12)
        .put("status", status)
        .put("installed_at", if (status == "installed") "2026-05-12T10:11:30+00:00" else JSONObject.NULL)
        .put("last_error", if (status == "failed") "unit install failed" else JSONObject.NULL)

    /** Documented action entry. */
    private fun actionEntry(): JSONObject = JSONObject()
        .put("id", 1)
        .put("script_id", 42)
        .put("trigger", "server.booted")
        .put("trigger_label", "Server has booted")
        .put("delay_seconds", 30)
        .put("is_paused", false)
        .put("last_triggered_at", "2026-05-13T08:42:11+00:00")
        .put("servers", JSONArray().put(serverPivot()))
        .put("created_at", "2026-05-12 10:11:22")
        .put("updated_at", "2026-05-13 08:42:11")

    /** Documented list shape: a bare data array, no pagination meta. */
    private fun actionListJson(): String = JSONObject()
        .put("data", JSONArray().put(actionEntry()))
        .toString()

    /** Documented single-action shape (get + create + update + toggle + rotate share it). */
    private fun actionJson(): String = JSONObject().put("data", actionEntry()).toString()

    /** Documented create response: last_triggered_at null, servers pending. */
    private fun createdActionJson(): String = JSONObject()
        .put(
            "data",
            actionEntry()
                .put("last_triggered_at", JSONObject.NULL)
                .put("servers", JSONArray().put(serverPivot("pending")).put(serverPivot("pending")))
        )
        .toString()

    // ---- GET /api/scripts/{script}/actions ----

    @Test fun parsesActionListWithDocumentedShape() {
        val actions = PloiApi.parseScriptActions(actionListJson())
        val action = actions.single()
        assertEquals(1L, action.id)
        assertEquals(42L, action.scriptId)
        assertEquals("server.booted", action.trigger)
        assertEquals("Server has booted", action.triggerLabel)
        assertEquals(30, action.delaySeconds)
        assertFalse(action.isPaused)
        assertEquals("2026-05-13T08:42:11+00:00", action.lastTriggeredAt)
        assertEquals("2026-05-12 10:11:22", action.createdAt)
        assertEquals("2026-05-13 08:42:11", action.updatedAt)
        val server = action.servers.single()
        assertEquals(12L, server.serverId)
        assertEquals("installed", server.status)
        assertEquals("2026-05-12T10:11:30+00:00", server.installedAt)
        assertEquals("", server.lastError)
    }

    @Test fun scriptActionsHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = actionListJson()
        val actions = PloiApi.scriptActions(token, scriptId = 42)
        assertEquals(1, actions.size)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/scripts/42/actions", request.url)
        assertThrowsIAE { PloiApi.scriptActions(token, scriptId = 0) }
        assertThrowsIAE { PloiApi.scriptActions(token, scriptId = -1) }
    }

    @Test fun parsesDocumentedNullablesOfFreshAction() {
        val action = PloiApi.parseScriptAction(createdActionJson())
        assertEquals("", action.lastTriggeredAt) // documented null before the first trigger
        assertEquals(2, action.servers.size)
        action.servers.forEach { server ->
            assertEquals("pending", server.status)
            assertEquals("", server.installedAt) // documented null while pending
        }
    }

    // ---- GET /api/scripts/{script}/actions/{action} ----

    @Test fun scriptActionReadsDocumentedRoute() {
        installFakeClient()
        scriptedBody = actionJson()
        val action = PloiApi.scriptAction(token, scriptId = 42, actionId = 1)
        assertEquals(1L, action.id)
        assertEquals(42L, action.scriptId)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/scripts/42/actions/1", request.url)
        assertThrowsIAE { PloiApi.scriptAction(token, scriptId = 0, actionId = 1) }
        assertThrowsIAE { PloiApi.scriptAction(token, scriptId = 42, actionId = 0) }
        assertThrowsIAE { PloiApi.scriptAction(token, scriptId = 42, actionId = -3) }
    }

    // ---- POST /api/scripts/{script}/actions ----

    @Test fun createScriptActionPostsDocumentedFields() {
        installFakeClient()
        scriptedBody = createdActionJson()
        val created = PloiApi.createScriptAction(
            token, scriptId = 42, CreateScriptActionRequest("server.booted", listOf(12, 18), delaySeconds = 30)
        )
        assertEquals(1L, created.id)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/scripts/42/actions", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("server.booted", body.getString("trigger"))
        assertEquals(30, body.getInt("delay_seconds"))
        val servers = body.getJSONArray("servers")
        assertEquals(2, servers.length())
        assertEquals(12L, servers.getLong(0))
        assertEquals(18L, servers.getLong(1))
    }

    @Test fun createScriptActionOmitsOptionalDelayWhenUnset() {
        installFakeClient()
        scriptedBody = createdActionJson()
        PloiApi.createScriptAction(token, scriptId = 42, CreateScriptActionRequest("server.shutdown", listOf(12)))
        val body = JSONObject(recorded.single().body.orEmpty())
        assertFalse(body.has("delay_seconds")) // documented default (0) applies server-side
        assertEquals("server.shutdown", body.getString("trigger"))
    }

    @Test fun createScriptActionValidatesDocumentedConstraints() {
        // documented: trigger in {server.booted, server.shutdown}, servers non-empty IDs, delay 0..7200
        assertThrowsIAE { CreateScriptActionRequest("", listOf(12)) }
        assertThrowsIAE { CreateScriptActionRequest("server.rebooted", listOf(12)) }
        assertThrowsIAE { CreateScriptActionRequest("*", listOf(12)) }
        assertThrowsIAE { CreateScriptActionRequest("server.booted", emptyList()) }
        assertThrowsIAE { CreateScriptActionRequest("server.booted", listOf(0)) }
        assertThrowsIAE { CreateScriptActionRequest("server.booted", listOf(12, -1)) }
        assertThrowsIAE { CreateScriptActionRequest("server.booted", listOf(12), delaySeconds = -1) }
        assertThrowsIAE { CreateScriptActionRequest("server.booted", listOf(12), delaySeconds = 7201) }
        CreateScriptActionRequest("server.booted", listOf(12), delaySeconds = 0)
        CreateScriptActionRequest("server.shutdown", listOf(12), delaySeconds = 7200)
        assertThrowsIAE { PloiApi.createScriptAction(token, 0, CreateScriptActionRequest("server.booted", listOf(1))) }
    }

    // ---- PATCH /api/scripts/{script}/actions/{action} ----

    @Test fun updateScriptActionReplacesDocumentedFields() {
        installFakeClient()
        scriptedBody = actionJson()
        val updated = PloiApi.updateScriptAction(
            token, scriptId = 42, actionId = 1, UpdateScriptActionRequest("server.booted", listOf(12), delaySeconds = 60)
        )
        assertEquals(30, updated.delaySeconds) // parser returns the server response
        val request = recorded.single()
        assertEquals("PATCH", request.method)
        assertEquals("https://ploi.io/api/scripts/42/actions/1", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("server.booted", body.getString("trigger"))
        assertEquals(60, body.getInt("delay_seconds"))
        assertEquals(1, body.getJSONArray("servers").length())
    }

    @Test fun updateScriptActionRequiresTriggerAndServers() {
        // documented: trigger and servers are required (the list replaces, servers left out are uninstalled)
        assertThrowsIAE { UpdateScriptActionRequest("", listOf(12)) }
        assertThrowsIAE { UpdateScriptActionRequest("server.booted", emptyList()) }
        assertThrowsIAE { UpdateScriptActionRequest("server.booted", listOf(12), delaySeconds = 7201) }
        assertThrowsIAE {
            PloiApi.updateScriptAction(token, 42, 0, UpdateScriptActionRequest("server.booted", listOf(12)))
        }
    }

    // ---- POST /api/scripts/{script}/actions/{action}/toggle ----

    @Test fun toggleScriptActionPostsDocumentedRouteAndParsesPausedState() {
        installFakeClient()
        scriptedBody = JSONObject().put("data", actionEntry().put("is_paused", true)).toString()
        val toggled = PloiApi.toggleScriptAction(token, scriptId = 42, actionId = 1)
        assertTrue(toggled.isPaused)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/scripts/42/actions/1/toggle", request.url)
        assertThrowsIAE { PloiApi.toggleScriptAction(token, scriptId = 0, actionId = 1) }
        assertThrowsIAE { PloiApi.toggleScriptAction(token, scriptId = 42, actionId = 0) }
    }

    // ---- POST /api/scripts/{script}/actions/{action}/rotate-secret ----

    @Test fun rotateScriptActionSecretPostsDocumentedRouteAndParsesPendingReinstall() {
        installFakeClient()
        // documented: after rotation every attached server walks back to pending
        scriptedBody = JSONObject()
            .put("data", actionEntry().put("servers", JSONArray().put(serverPivot("pending"))))
            .toString()
        val rotated = PloiApi.rotateScriptActionSecret(token, scriptId = 42, actionId = 1)
        assertEquals("pending", rotated.servers.single().status)
        assertEquals("", rotated.servers.single().installedAt)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/scripts/42/actions/1/rotate-secret", request.url)
        assertThrowsIAE { PloiApi.rotateScriptActionSecret(token, scriptId = 0, actionId = 1) }
        assertThrowsIAE { PloiApi.rotateScriptActionSecret(token, scriptId = 42, actionId = 0) }
    }

    // ---- DELETE /api/scripts/{script}/actions/{action} ----

    @Test fun deleteScriptActionHitsDocumentedRouteAndToleratesNullMessage() {
        installFakeClient()
        // documented: {"status": "ok", "message": null}
        scriptedBody = JSONObject().put("status", "ok").put("message", JSONObject.NULL).toString()
        val message = PloiApi.deleteScriptAction(token, scriptId = 42, actionId = 1)
        assertEquals("", message)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/scripts/42/actions/1", request.url)
        assertNull(request.body)
        assertThrowsIAE { PloiApi.deleteScriptAction(token, scriptId = 0, actionId = 1) }
        assertThrowsIAE { PloiApi.deleteScriptAction(token, scriptId = 42, actionId = 0) }
    }

    // ---- Error handling ----

    @Test fun malformedActionPayloadSurfacesTypedError() {
        installFakeClient()
        scriptedBody = """{"unexpected":true}"""
        try {
            PloiApi.scriptAction(token, 42, 1)
            fail("Expected PloiMalformedPayloadException")
        } catch (expected: PloiMalformedPayloadException) {
            // typed error, not a raw JSONException
        }
        try {
            PloiApi.parseScriptActions("""{"data":[{"id":1}]}""") // missing documented fields
            fail("Expected JSONException on missing documented fields")
        } catch (expected: org.json.JSONException) {
            // required fields are enforced by the parser
        }
        try {
            PloiApi.parseScriptAction(
                """{"data":{"id":1,"script_id":42,"trigger":"server.booted","delay_seconds":0,"is_paused":false,"servers":{}}}"""
            )
            fail("Expected JSONException on non-array servers")
        } catch (expected: org.json.JSONException) {
            // servers must be an array of per-server pivots
        }
    }

    @Test fun failedServerPivotKeepsDocumentedError() {
        val entry = actionEntry().put("servers", JSONArray().put(serverPivot("failed")))
        val server = PloiApi.parseScriptAction(JSONObject().put("data", entry).toString()).servers.single()
        assertEquals("failed", server.status)
        assertEquals("unit install failed", server.lastError)
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
