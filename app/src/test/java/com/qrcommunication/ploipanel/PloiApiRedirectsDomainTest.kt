package com.qrcommunication.ploipanel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Parsing, validation and wire-level coverage for every documented route of the
 * `redirects` domain (/api/servers/{server}/sites/{site}/redirects, 4 routes).
 */
class PloiApiRedirectsDomainTest {
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

    private fun redirectEntry(): JSONObject = JSONObject()
        .put("id", 1)
        .put("status", "active")
        .put("redirect_from", "/")
        .put("redirect_to", "/home")
        .put("type", "permanent")

    private fun redirectListJson(): String = JSONObject()
        .put("data", JSONArray().put(redirectEntry()))
        .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
        .toString()

    // ---- GET /api/servers/{server}/sites/{site}/redirects ----

    @Test fun parsesRedirectListWithDocumentedShape() {
        val page = PloiApi.parseRedirects(redirectListJson())
        assertFalse(page.hasNext)
        val redirect = page.redirects.single()
        assertEquals(1L, redirect.id)
        assertEquals("active", redirect.status)
        assertEquals("/", redirect.redirectFrom)
        assertEquals("/home", redirect.redirectTo)
        assertEquals("permanent", redirect.type)
    }

    @Test fun redirectsHitsDocumentedRouteWithPagination() {
        installFakeClient()
        scriptedBody = redirectListJson()
        PloiApi.redirects(token, 1, 1, page = 2, perPage = 50)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/redirects?page=2&per_page=50", request.url)
    }

    // ---- GET /api/servers/{server}/sites/{site}/redirects/{redirect} ----

    @Test fun redirectHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject().put("data", redirectEntry()).toString()
        val redirect = PloiApi.redirect(token, 1, 1, 1)
        assertEquals(1L, redirect.id)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/redirects/1", request.url)
    }

    // ---- POST /api/servers/{server}/sites/{site}/redirects ----

    @Test fun createRedirectSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = JSONObject().put("data", redirectEntry().put("status", "created")).toString()
        val redirect = PloiApi.createRedirect(token, 1, 1, CreateRedirectRequest("/", "/home", "permanent"))
        assertEquals("created", redirect.status)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/redirects", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("/", body.getString("redirect_from"))
        assertEquals("/home", body.getString("redirect_to"))
        assertEquals("permanent", body.getString("type"))
    }

    @Test fun createRedirectRequestRejectsInvalidInput() {
        listOf(
            { CreateRedirectRequest("no-slash", "/home", "permanent") },
            { CreateRedirectRequest("/", "", "permanent") },
            { CreateRedirectRequest("/", "/home", "forever") }
        ).forEach { invalid ->
            try {
                invalid()
                fail("Expected rejection")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // ---- DELETE /api/servers/{server}/sites/{site}/redirects/{redirect} ----

    @Test fun deleteRedirectHitsDocumentedRouteAndParsesMessage() {
        installFakeClient()
        scriptedBody = JSONObject().put("message", "Redirect has been deleted").toString()
        val message = PloiApi.deleteRedirect(token, 1, 1, 1)
        assertEquals("Redirect has been deleted", message)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/redirects/1", request.url)
    }
}
