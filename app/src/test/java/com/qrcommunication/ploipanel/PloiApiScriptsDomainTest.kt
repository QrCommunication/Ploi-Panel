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
 * `scripts` domain (/api/scripts + /api/servers/{server}/scripts/run, 8 routes).
 */
class PloiApiScriptsDomainTest {
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

    /** Documented list shape (developers.ploi.io/scripts/list-scripts). */
    private fun scriptListJson(): String = JSONObject()
        .put(
            "data",
            JSONArray().put(
                JSONObject()
                    .put("id", 1)
                    .put("user", "root")
                    .put("label", "Install Elastic Search")
                    .put("content", "#!/bin/bash\n\napt-get install elasticsearch")
                    .put("created_at", "2018-06-18 14:05:00")
            )
        )
        .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
        .toString()

    /** Documented single-script shape (get + create + update responses share it). */
    private fun scriptJson(): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("id", 1)
                .put("user", "ploi")
                .put("label", "Echo Script")
                .put("content", "echo 123")
                .put("created_at", "2019-08-07 11:21:02")
        )
        .toString()

    /** Documented pending execution shape (run one-off script response). */
    private fun pendingExecutionJson(): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("id", "3f4c9e9a-8b4e-4f0e-9d3b-2f6f2c1a7d42")
                .put("server_id", 1)
                .put("user", "deployer")
                .put("content", "npm install -g pm2")
                .put("status", "pending")
                .put("exit_code", JSONObject.NULL)
                .put("output", JSONObject.NULL)
                .put("created_at", "2026-08-14T15:03:12+00:00")
                .put("started_at", JSONObject.NULL)
                .put("finished_at", JSONObject.NULL)
        )
        .toString()

    /** Documented finished execution shape (get script execution response). */
    private fun finishedExecutionJson(): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("id", "3f4c9e9a-8b4e-4f0e-9d3b-2f6f2c1a7d42")
                .put("server_id", 1)
                .put("user", "deployer")
                .put("content", "npm install -g pm2")
                .put("status", "finished")
                .put("exit_code", 0)
                .put("output", "added 1 package in 4s")
                .put("created_at", "2026-08-14T15:03:12+00:00")
                .put("started_at", "2026-08-14T15:03:14+00:00")
                .put("finished_at", "2026-08-14T15:03:19+00:00")
        )
        .toString()

    // ---- GET /api/scripts ----

    @Test fun parsesScriptListWithDocumentedShape() {
        val page = PloiApi.parseScripts(scriptListJson())
        assertEquals(1, page.currentPage)
        assertFalse(page.hasNext)
        val script = page.scripts.single()
        assertEquals(1L, script.id)
        assertEquals("root", script.user)
        assertEquals("Install Elastic Search", script.label)
        assertEquals("#!/bin/bash\n\napt-get install elasticsearch", script.content)
        assertEquals("2018-06-18 14:05:00", script.createdAt)
    }

    @Test fun scriptsHitsDocumentedRouteWithPagination() {
        installFakeClient()
        scriptedBody = scriptListJson()
        val page = PloiApi.scripts(token, page = 2, perPage = 50)
        assertEquals(1, page.scripts.size)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/scripts?page=2&per_page=50", request.url)
        recorded.clear()
        PloiApi.scripts(token)
        assertEquals("https://ploi.io/api/scripts?page=1&per_page=15", recorded.single().url)
        assertThrowsIAE { PloiApi.scripts(token, page = 0) }
        assertThrowsIAE { PloiApi.scripts(token, perPage = 51) }
    }

    // ---- GET /api/scripts/{script} ----

    @Test fun scriptReadsDocumentedRoute() {
        installFakeClient()
        scriptedBody = scriptJson()
        val script = PloiApi.script(token, scriptId = 1)
        assertEquals("Echo Script", script.label)
        assertEquals("ploi", script.user)
        assertEquals("echo 123", script.content)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/scripts/1", request.url)
        assertThrowsIAE { PloiApi.script(token, scriptId = 0) }
        assertThrowsIAE { PloiApi.script(token, scriptId = -1) }
    }

    // ---- POST /api/scripts ----

    @Test fun createScriptPostsDocumentedRequiredFields() {
        installFakeClient()
        scriptedBody = scriptJson()
        val created = PloiApi.createScript(token, CreateScriptRequest("Echo Script", "ploi", "echo 123"))
        assertEquals(1L, created.id)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/scripts", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("Echo Script", body.getString("label"))
        assertEquals("ploi", body.getString("user"))
        assertEquals("echo 123", body.getString("content"))
    }

    @Test fun createScriptValidatesDocumentedRequiredFields() {
        // documented: label, user and content are all required
        assertThrowsIAE { CreateScriptRequest("", "ploi", "echo 123") }
        assertThrowsIAE { CreateScriptRequest("   ", "ploi", "echo 123") }
        assertThrowsIAE { CreateScriptRequest("Echo Script", "", "echo 123") }
        assertThrowsIAE { CreateScriptRequest("Echo Script", "ploi", "") }
        assertThrowsIAE { CreateScriptRequest("Echo Script", "ploi", "  ") }
        CreateScriptRequest("Install Elastic Search", "root", "#!/bin/bash")
    }

    // ---- PATCH /api/scripts/{script} ----

    @Test fun updateScriptSendsOnlyProvidedAttributes() {
        installFakeClient()
        scriptedBody = scriptJson()
        val updated = PloiApi.updateScript(token, 1, UpdateScriptRequest(label = "Echo Script"))
        assertEquals("Echo Script", updated.label)
        val request = recorded.single()
        assertEquals("PATCH", request.method)
        assertEquals("https://ploi.io/api/scripts/1", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("Echo Script", body.getString("label"))
        assertFalse(body.has("user"))
        assertFalse(body.has("content"))
    }

    @Test fun updateScriptRequiresAtLeastOneAttribute() {
        // documented: every attribute is optional, but an empty PATCH is meaningless
        assertThrowsIAE { UpdateScriptRequest() }
        assertThrowsIAE { UpdateScriptRequest("", "", "") }
        UpdateScriptRequest(user = "root")
        UpdateScriptRequest(content = "echo 1")
        assertThrowsIAE { PloiApi.updateScript(token, 0, UpdateScriptRequest(label = "x")) }
    }

    // ---- DELETE /api/scripts/{script} ----

    @Test fun deleteScriptHitsDocumentedRouteAndParsesMessage() {
        installFakeClient()
        // documented: {"message": "Script has been deleted."}
        scriptedBody = JSONObject().put("message", "Script has been deleted.").toString()
        val message = PloiApi.deleteScript(token, scriptId = 1)
        assertEquals("Script has been deleted.", message)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/scripts/1", request.url)
        assertNull(request.body)
        assertThrowsIAE { PloiApi.deleteScript(token, scriptId = 0) }
    }

    // ---- POST /api/scripts/{script}/run ----

    @Test fun runScriptPostsDocumentedServersArray() {
        installFakeClient()
        // documented: {"data": {"running_on_servers": [{"name": "awesome-server", "ip": "127.0.0.1", "id": 1}]}}
        scriptedBody = JSONObject()
            .put(
                "data",
                JSONObject().put(
                    "running_on_servers",
                    JSONArray().put(
                        JSONObject().put("name", "awesome-server").put("ip", "127.0.0.1").put("id", 1)
                    )
                )
            )
            .toString()
        val servers = PloiApi.runScript(token, scriptId = 1, servers = listOf(1))
        val server = servers.single()
        assertEquals(1L, server.id)
        assertEquals("awesome-server", server.name)
        assertEquals("127.0.0.1", server.ip)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/scripts/1/run", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals(1L, body.getJSONArray("servers").getLong(0))
    }

    @Test fun runScriptValidatesDocumentedServersArray() {
        // documented: servers is a required array of server IDs
        assertThrowsIAE { PloiApi.runScript(token, 1, emptyList()) }
        assertThrowsIAE { PloiApi.runScript(token, 1, listOf(0)) }
        assertThrowsIAE { PloiApi.runScript(token, 1, listOf(1, -2)) }
        assertThrowsIAE { PloiApi.runScript(token, 0, listOf(1)) }
    }

    // ---- POST /api/servers/{server}/scripts/run ----

    @Test fun runOneOffScriptPostsDocumentedFieldsAndParsesPendingExecution() {
        installFakeClient()
        scriptedBody = pendingExecutionJson()
        val execution = PloiApi.runOneOffScript(token, serverId = 1, content = "npm install -g pm2", user = "deployer")
        assertEquals("3f4c9e9a-8b4e-4f0e-9d3b-2f6f2c1a7d42", execution.id)
        assertEquals(1L, execution.serverId)
        assertEquals("deployer", execution.user)
        assertEquals("pending", execution.status)
        assertNull(execution.exitCode)
        assertEquals("", execution.output)
        assertEquals("", execution.startedAt)
        assertEquals("", execution.finishedAt)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/scripts/run", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("npm install -g pm2", body.getString("content"))
        assertEquals("deployer", body.getString("user"))
    }

    @Test fun runOneOffScriptDefaultsToDocumentedPloiUser() {
        installFakeClient()
        scriptedBody = pendingExecutionJson()
        PloiApi.runOneOffScript(token, serverId = 1, content = "echo 1")
        val body = JSONObject(recorded.single().body.orEmpty())
        assertEquals("ploi", body.getString("user")) // documented default
    }

    @Test fun runOneOffScriptValidatesDocumentedConstraints() {
        // documented: content required, max 7500 characters
        assertThrowsIAE { PloiApi.runOneOffScript(token, 1, "") }
        assertThrowsIAE { PloiApi.runOneOffScript(token, 1, "  ") }
        assertThrowsIAE { PloiApi.runOneOffScript(token, 1, "x".repeat(ONE_OFF_SCRIPT_MAX_LENGTH + 1)) }
        assertThrowsIAE { PloiApi.runOneOffScript(token, 1, "echo 1", user = "") }
        assertThrowsIAE { PloiApi.runOneOffScript(token, 0, "echo 1") }
        assertEquals(7_500, ONE_OFF_SCRIPT_MAX_LENGTH)
    }

    // ---- GET /api/servers/{server}/scripts/run/{execution} ----

    @Test fun scriptExecutionReadsDocumentedRouteAndFinishedShape() {
        installFakeClient()
        scriptedBody = finishedExecutionJson()
        val execution = PloiApi.scriptExecution(token, 1, "3f4c9e9a-8b4e-4f0e-9d3b-2f6f2c1a7d42")
        assertEquals("finished", execution.status)
        assertEquals(0, execution.exitCode)
        assertEquals("added 1 package in 4s", execution.output)
        assertEquals("2026-08-14T15:03:14+00:00", execution.startedAt)
        assertEquals("2026-08-14T15:03:19+00:00", execution.finishedAt)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals(
            "https://ploi.io/api/servers/1/scripts/run/3f4c9e9a-8b4e-4f0e-9d3b-2f6f2c1a7d42",
            request.url
        )
        assertThrowsIAE { PloiApi.scriptExecution(token, 0, "abc") }
        assertThrowsIAE { PloiApi.scriptExecution(token, 1, "") }
        assertThrowsIAE { PloiApi.scriptExecution(token, 1, "  ") }
    }

    // ---- Error handling ----

    @Test fun malformedScriptPayloadSurfacesTypedError() {
        installFakeClient()
        scriptedBody = """{"unexpected":true}"""
        try {
            PloiApi.script(token, 1)
            fail("Expected PloiMalformedPayloadException")
        } catch (expected: PloiMalformedPayloadException) {
            // typed error, not a raw JSONException
        }
        try {
            PloiApi.parseScripts("""{"data":[{"user":"root"}]}""") // missing documented id/label/content
            fail("Expected JSONException on missing documented fields")
        } catch (expected: org.json.JSONException) {
            // required fields are enforced by the parser
        }
        try {
            PloiApi.parseScriptRunResponse("""{"data":{"running_on_servers":{}}}""") // array expected
            fail("Expected JSONException on unexpected run payload")
        } catch (expected: org.json.JSONException) {
            // running_on_servers must be an array
        }
        try {
            PloiApi.parseScriptExecution("""{"data":{"id":"abc"}}""") // missing documented fields
            fail("Expected JSONException on missing documented fields")
        } catch (expected: org.json.JSONException) {
            // required fields are enforced by the parser
        }
    }

    @Test fun paginationMetaDrivesHasNext() {
        val twoPages = JSONObject(scriptListJson())
            .put("meta", JSONObject().put("current_page", 1).put("last_page", 2))
            .toString()
        assertTrue(PloiApi.parseScripts(twoPages).hasNext)
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
