package com.qrcommunication.ploipanel

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Parsing, validation and wire-level coverage for every documented route of the
 * `services` domain (restart/reload + WordPress CLI, 5 routes).
 */
class PloiApiServicesDomainTest {
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

    // ---- POST /api/servers/{server}/services/{service}/restart ----

    @Test fun parsesStatusMessageAck() {
        val ack = PloiApi.parseOperationAck(
            JSONObject().put("status", "ok").put("message", "Service is being restarted.").toString()
        )
        assertEquals("ok", ack.status)
        assertEquals("Service is being restarted.", ack.message)
        assertFalse(ack.fallback)
    }

    @Test fun restartServiceHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject().put("status", "ok").put("message", "Service is being restarted.").toString()
        val ack = PloiApi.restartService(token, 1, "nginx")
        assertEquals("ok", ack.status)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/services/nginx/restart", request.url)
    }

    // ---- POST /api/servers/{server}/services/{service}/reload ----

    @Test fun parsesReloadAckWithFallback() {
        val ack = PloiApi.parseOperationAck(
            JSONObject().put("message", "Service database reloaded (fallback to restart)").put("fallback", true).toString()
        )
        assertEquals("", ack.status)
        assertEquals("Service database reloaded (fallback to restart)", ack.message)
        assertTrue(ack.fallback)
    }

    @Test fun reloadServiceHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject().put("message", "Service database reloaded (fallback to restart)").toString()
        PloiApi.reloadService(token, 1, "mysql")
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/services/mysql/reload", request.url)
    }

    @Test fun serviceNameIsValidated() {
        installFakeClient()
        listOf("", "bad name", "bad/name", "x".repeat(65)).forEach { invalid ->
            try {
                PloiApi.restartService(token, 1, invalid)
                fail("Expected rejection for '$invalid'")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertTrue(recorded.isEmpty())
    }

    // ---- POST /api/servers/{server}/install/wp-cli ----

    @Test fun installWpCliHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject().put("status", "ok").put("message", "WordPress CLI is being installed.").toString()
        val ack = PloiApi.installWpCli(token, 1)
        assertEquals("WordPress CLI is being installed.", ack.message)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/install/wp-cli", request.url)
    }

    // ---- POST /api/servers/{server}/wp-cli/run ----

    @Test fun runServerWpCliSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = JSONObject().put("status", "ok").put("message", "Success: The cache was flushed.\n").toString()
        val ack = PloiApi.runServerWpCli(token, 1, "cache flush")
        assertEquals("Success: The cache was flushed.\n", ack.message)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/wp-cli/run", request.url)
        assertEquals("cache flush", JSONObject(request.body.orEmpty()).getString("command"))
    }

    @Test fun runServerWpCliRejectsBlankCommand() {
        installFakeClient()
        try {
            PloiApi.runServerWpCli(token, 1, " ")
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {
        }
        assertTrue(recorded.isEmpty())
    }

    // ---- DELETE /api/servers/{server}/uninstall/wp-cli ----

    @Test fun uninstallWpCliHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject().put("status", "ok").put("message", "WordPress CLI is being uninstalled.").toString()
        val ack = PloiApi.uninstallWpCli(token, 1)
        assertEquals("WordPress CLI is being uninstalled.", ack.message)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/uninstall/wp-cli", request.url)
    }
}
