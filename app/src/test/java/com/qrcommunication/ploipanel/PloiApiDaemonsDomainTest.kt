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
 * `daemons` domain (/api/servers/{server}/daemons, 6 routes).
 */
class PloiApiDaemonsDomainTest {
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

    /** Documented list shape (developers.ploi.io/daemons/list-daemons). */
    private fun daemonListJson(): String = JSONObject()
        .put(
            "data",
            JSONArray().put(
                JSONObject()
                    .put("id", 1)
                    .put("command", "echo 123")
                    .put("processes", 1)
                    .put("system_user", "ploi")
                    .put("directory", "/home/ploi")
                    .put("status", "active")
            )
        )
        .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
        .toString()

    /** Documented single-daemon shape (get + create responses share it; directory may be null). */
    private fun daemonJson(): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("id", 1)
                .put("command", "echo 123")
                .put("processes", 1)
                .put("system_user", "ploi")
                .put("directory", JSONObject.NULL)
                .put("status", "created")
        )
        .toString()

    // ---- GET /api/servers/{server}/daemons ----

    @Test fun parsesDaemonListWithDocumentedShape() {
        val page = PloiApi.parseDaemons(daemonListJson())
        assertEquals(1, page.currentPage)
        assertFalse(page.hasNext)
        val daemon = page.daemons.single()
        assertEquals(1L, daemon.id)
        assertEquals("echo 123", daemon.command)
        assertEquals(1, daemon.processes)
        assertEquals("ploi", daemon.systemUser)
        assertEquals("/home/ploi", daemon.directory)
        assertEquals("active", daemon.status)
    }

    @Test fun daemonsHitsDocumentedRouteWithPagination() {
        installFakeClient()
        scriptedBody = daemonListJson()
        val page = PloiApi.daemons(token, serverId = 7, page = 2, perPage = 50)
        assertEquals(1, page.daemons.size)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/7/daemons?page=2&per_page=50", request.url)
        recorded.clear()
        PloiApi.daemons(token, serverId = 7)
        assertEquals("https://ploi.io/api/servers/7/daemons?page=1&per_page=15", recorded.single().url)
        assertThrowsIAE { PloiApi.daemons(token, serverId = 0) }
        assertThrowsIAE { PloiApi.daemons(token, serverId = -1) }
        assertThrowsIAE { PloiApi.daemons(token, serverId = 7, page = 0) }
        assertThrowsIAE { PloiApi.daemons(token, serverId = 7, perPage = 51) }
    }

    // ---- GET /api/servers/{server}/daemons/{daemon} ----

    @Test fun daemonReadsDocumentedRouteAndNullDirectory() {
        installFakeClient()
        scriptedBody = daemonJson()
        val daemon = PloiApi.daemon(token, serverId = 7, daemonId = 1)
        assertEquals("echo 123", daemon.command)
        assertEquals("created", daemon.status)
        assertEquals("", daemon.directory) // documented null directory tolerated
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/7/daemons/1", request.url)
        assertThrowsIAE { PloiApi.daemon(token, serverId = 0, daemonId = 1) }
        assertThrowsIAE { PloiApi.daemon(token, serverId = 7, daemonId = 0) }
    }

    // ---- POST /api/servers/{server}/daemons ----

    @Test fun createDaemonPostsDocumentedRequiredFields() {
        installFakeClient()
        scriptedBody = daemonJson()
        val daemon = PloiApi.createDaemon(
            token, 7,
            CreateDaemonRequest(command = "echo 123", systemUser = "ploi", processes = 1)
        )
        assertEquals(1L, daemon.id)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/daemons", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("echo 123", body.getString("command"))
        assertEquals("ploi", body.getString("system_user"))
        assertEquals(1, body.getInt("processes"))
        assertEquals(3, body.length()) // directory is optional and omitted when blank
    }

    @Test fun createDaemonSendsOptionalDirectoryWhenProvided() {
        installFakeClient()
        scriptedBody = daemonJson()
        PloiApi.createDaemon(
            token, 7,
            CreateDaemonRequest(command = "echo 123", systemUser = "ploi", processes = 2, directory = "/home/ploi")
        )
        val body = JSONObject(recorded.single().body.orEmpty())
        assertEquals("/home/ploi", body.getString("directory"))
        assertEquals(2, body.getInt("processes"))
    }

    @Test fun createDaemonValidatesDocumentedConstraints() {
        assertThrowsIAE { CreateDaemonRequest("", "ploi", 1) }
        assertThrowsIAE { CreateDaemonRequest("   ", "ploi", 1) }
        // documented: command max 150 characters
        assertThrowsIAE { CreateDaemonRequest("c".repeat(151), "ploi", 1) }
        CreateDaemonRequest("c".repeat(150), "ploi", 1)
        assertThrowsIAE { CreateDaemonRequest("echo 123", "", 1) }
        assertThrowsIAE { CreateDaemonRequest("echo 123", "  ", 1) }
        assertThrowsIAE { CreateDaemonRequest("echo 123", "ploi", 0) }
        assertThrowsIAE { CreateDaemonRequest("echo 123", "ploi", -1) }
        assertThrowsIAE { CreateDaemonRequest("echo 123", "ploi", 1, "   ") }
        assertThrowsIAE {
            PloiApi.createDaemon(token, 0, CreateDaemonRequest("echo 123", "ploi", 1))
        }
    }

    // ---- POST /api/servers/{server}/daemons/{daemon}/restart ----

    @Test fun restartDaemonPostsDocumentedRouteAndReadsMessage() {
        installFakeClient()
        scriptedBody = JSONObject().put("message", "Daemon has been restarted").toString()
        val message = PloiApi.restartDaemon(token, serverId = 7, daemonId = 1)
        assertEquals("Daemon has been restarted", message)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/daemons/1/restart", request.url)
        assertThrowsIAE { PloiApi.restartDaemon(token, serverId = 0, daemonId = 1) }
        assertThrowsIAE { PloiApi.restartDaemon(token, serverId = 7, daemonId = -1) }
    }

    // ---- POST /api/servers/{server}/daemons/{daemon}/toggle-pause ----

    @Test fun togglePauseDaemonParsesDocumentedReducedShape() {
        installFakeClient()
        // Documented toggle-pause response carries only id/command/processes/status.
        scriptedBody = JSONObject()
            .put(
                "data",
                JSONObject()
                    .put("id", 1)
                    .put("command", "echo 123")
                    .put("processes", 1)
                    .put("status", "active")
            )
            .toString()
        val daemon = PloiApi.togglePauseDaemon(token, serverId = 7, daemonId = 1)
        assertEquals(1L, daemon.id)
        assertEquals("active", daemon.status)
        assertEquals("", daemon.systemUser) // absent from the documented toggle-pause shape
        assertEquals("", daemon.directory)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/daemons/1/toggle-pause", request.url)
        assertThrowsIAE { PloiApi.togglePauseDaemon(token, serverId = 0, daemonId = 1) }
        assertThrowsIAE { PloiApi.togglePauseDaemon(token, serverId = 7, daemonId = 0) }
    }

    // ---- DELETE /api/servers/{server}/daemons/{daemon} ----

    @Test fun deleteDaemonHitsDocumentedRouteAndToleratesEmptyBody() {
        installFakeClient()
        scriptedBody = "" // documented: empty response body
        PloiApi.deleteDaemon(token, serverId = 7, daemonId = 1)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/7/daemons/1", request.url)
        assertNull(request.body)
        assertThrowsIAE { PloiApi.deleteDaemon(token, serverId = 0, daemonId = 1) }
        assertThrowsIAE { PloiApi.deleteDaemon(token, serverId = 7, daemonId = -1) }
    }

    // ---- Error handling ----

    @Test fun malformedDaemonPayloadSurfacesTypedError() {
        installFakeClient()
        scriptedBody = """{"unexpected":true}"""
        try {
            PloiApi.daemon(token, 7, 1)
            fail("Expected PloiMalformedPayloadException")
        } catch (expected: PloiMalformedPayloadException) {
            // typed error, not a raw JSONException
        }
        try {
            PloiApi.parseDaemons("""{"data":[{"system_user":"ploi"}]}""") // missing documented id/command
            fail("Expected JSONException on missing documented fields")
        } catch (expected: org.json.JSONException) {
            // required fields are enforced by the parser
        }
    }

    @Test fun paginationMetaDrivesHasNext() {
        val twoPages = JSONObject(daemonListJson())
            .put("meta", JSONObject().put("current_page", 1).put("last_page", 2))
            .toString()
        assertTrue(PloiApi.parseDaemons(twoPages).hasNext)
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
