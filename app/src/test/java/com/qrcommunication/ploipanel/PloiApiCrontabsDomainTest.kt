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
 * `crontabs` domain (/api/servers/{server}/crontabs, 4 routes).
 */
class PloiApiCrontabsDomainTest {
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

    /** Documented list shape (developers.ploi.io/crontabs/list-crontabs). */
    private fun crontabListJson(): String = JSONObject()
        .put(
            "data",
            JSONArray().put(
                JSONObject()
                    .put("id", 1)
                    .put("command", "/home/ploi/domain.com/php artisan schedule:run")
                    .put("user", "ploi")
                    .put("frequency", "* * * * *")
                    .put("created_at", "2019-07-16 13:05:58")
            )
        )
        .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
        .toString()

    /** Documented single-crontab shape (get + create responses share it). */
    private fun crontabJson(): String = JSONObject()
        .put("data", JSONObject(crontabListJson()).getJSONArray("data").getJSONObject(0))
        .toString()

    // ---- GET /api/servers/{server}/crontabs ----

    @Test fun parsesCrontabListWithDocumentedShape() {
        val page = PloiApi.parseCrontabs(crontabListJson())
        assertEquals(1, page.currentPage)
        assertFalse(page.hasNext)
        val crontab = page.crontabs.single()
        assertEquals(1L, crontab.id)
        assertEquals("/home/ploi/domain.com/php artisan schedule:run", crontab.command)
        assertEquals("ploi", crontab.user)
        assertEquals("* * * * *", crontab.frequency)
        assertEquals("2019-07-16 13:05:58", crontab.createdAt)
    }

    @Test fun crontabsHitsDocumentedRouteWithPagination() {
        installFakeClient()
        scriptedBody = crontabListJson()
        val page = PloiApi.crontabs(token, serverId = 7, page = 2, perPage = 50)
        assertEquals(1, page.crontabs.size)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/7/crontabs?page=2&per_page=50", request.url)
        recorded.clear()
        PloiApi.crontabs(token, serverId = 7)
        assertEquals("https://ploi.io/api/servers/7/crontabs?page=1&per_page=15", recorded.single().url)
        assertThrowsIAE { PloiApi.crontabs(token, serverId = 0) }
        assertThrowsIAE { PloiApi.crontabs(token, serverId = -1) }
        assertThrowsIAE { PloiApi.crontabs(token, serverId = 7, page = 0) }
        assertThrowsIAE { PloiApi.crontabs(token, serverId = 7, perPage = 51) }
    }

    // ---- GET /api/servers/{server}/crontabs/{id} ----

    @Test fun crontabReadsDocumentedRoute() {
        installFakeClient()
        scriptedBody = crontabJson()
        val crontab = PloiApi.crontab(token, serverId = 7, crontabId = 1)
        assertEquals("ploi", crontab.user)
        assertEquals("* * * * *", crontab.frequency)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/7/crontabs/1", request.url)
        assertThrowsIAE { PloiApi.crontab(token, serverId = 0, crontabId = 1) }
        assertThrowsIAE { PloiApi.crontab(token, serverId = 7, crontabId = 0) }
    }

    // ---- POST /api/servers/{server}/crontabs ----

    @Test fun createCrontabPostsDocumentedRequiredFields() {
        installFakeClient()
        scriptedBody = crontabJson()
        val crontab = PloiApi.createCrontab(
            token, 7,
            CreateCrontabRequest(
                user = "ploi",
                command = "/home/ploi/domain.com/php artisan schedule:run",
                frequency = "* * * * *"
            )
        )
        assertEquals(1L, crontab.id)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/crontabs", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("ploi", body.getString("user"))
        assertEquals("/home/ploi/domain.com/php artisan schedule:run", body.getString("command"))
        assertEquals("* * * * *", body.getString("frequency"))
        assertEquals(3, body.length()) // only the three documented fields
    }

    @Test fun createCrontabValidatesDocumentedMaxLengths() {
        assertThrowsIAE { CreateCrontabRequest("", "cmd", "* * * * *") }
        assertThrowsIAE { CreateCrontabRequest("   ", "cmd", "* * * * *") }
        assertThrowsIAE { CreateCrontabRequest("u".repeat(256), "cmd", "* * * * *") }
        assertThrowsIAE { CreateCrontabRequest("ploi", "", "* * * * *") }
        assertThrowsIAE { CreateCrontabRequest("ploi", "  ", "* * * * *") }
        assertThrowsIAE { CreateCrontabRequest("ploi", "c".repeat(256), "* * * * *") }
        assertThrowsIAE { CreateCrontabRequest("ploi", "cmd", "") }
        assertThrowsIAE { CreateCrontabRequest("ploi", "cmd", " ".repeat(51)) }
        // documented maxima pass: user/command 255, frequency 50
        CreateCrontabRequest("u".repeat(255), "c".repeat(255), "f".repeat(50))
        CreateCrontabRequest("ploi", "php artisan schedule:run", "0 3 * * 1-5")
        assertThrowsIAE { PloiApi.createCrontab(token, 0, CreateCrontabRequest("ploi", "cmd", "* * * * *")) }
    }

    // ---- DELETE /api/servers/{server}/crontabs/{id} ----

    @Test fun deleteCrontabHitsDocumentedRouteAndToleratesEmptyBody() {
        installFakeClient()
        scriptedBody = "" // documented: empty response body
        PloiApi.deleteCrontab(token, serverId = 7, crontabId = 1)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/7/crontabs/1", request.url)
        assertNull(request.body)
        assertThrowsIAE { PloiApi.deleteCrontab(token, serverId = 0, crontabId = 1) }
        assertThrowsIAE { PloiApi.deleteCrontab(token, serverId = 7, crontabId = -1) }
    }

    // ---- Error handling ----

    @Test fun malformedCrontabPayloadSurfacesTypedError() {
        installFakeClient()
        scriptedBody = """{"unexpected":true}"""
        try {
            PloiApi.crontab(token, 7, 1)
            fail("Expected PloiMalformedPayloadException")
        } catch (expected: PloiMalformedPayloadException) {
            // typed error, not a raw JSONException
        }
        try {
            PloiApi.parseCrontabs("""{"data":[{"user":"ploi"}]}""") // missing documented id/command
            fail("Expected JSONException on missing documented fields")
        } catch (expected: org.json.JSONException) {
            // required fields are enforced by the parser
        }
    }

    @Test fun paginationMetaDrivesHasNext() {
        val twoPages = JSONObject(crontabListJson())
            .put("meta", JSONObject().put("current_page", 1).put("last_page", 2))
            .toString()
        assertTrue(PloiApi.parseCrontabs(twoPages).hasNext)
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
