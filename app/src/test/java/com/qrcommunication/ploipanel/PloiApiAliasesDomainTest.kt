package com.qrcommunication.ploipanel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Parsing, validation and wire-level coverage for every documented route of the
 * `aliases` domain (/api/servers/{server}/sites/{site}/aliases, 3 routes).
 */
class PloiApiAliasesDomainTest {
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

    private fun aliasesJson(vararg aliases: String): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("aliases", JSONArray().apply { aliases.forEach { put(it) } })
                .put("count", aliases.size)
                .put("main", "alias.com")
        )
        .toString()

    // ---- GET /api/servers/{server}/sites/{site}/aliases ----

    @Test fun parsesAliasesWithDocumentedShape() {
        val result = PloiApi.parseAliases(aliasesJson("first-alias.com", "second-alias.com"))
        assertEquals(listOf("first-alias.com", "second-alias.com"), result.aliases)
        assertEquals(2, result.count)
        assertEquals("alias.com", result.main)
    }

    @Test fun aliasesHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = aliasesJson("first-alias.com")
        PloiApi.aliases(token, 1, 1)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/aliases", request.url)
    }

    // ---- POST /api/servers/{server}/sites/{site}/aliases ----

    @Test fun createAliasesSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = aliasesJson("first-alias.com", "second-alias.com")
        val result = PloiApi.createAliases(token, 1, 1, listOf("first-alias.com", "second-alias.com"))
        assertEquals(2, result.count)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/aliases", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("first-alias.com", body.getJSONArray("aliases").getString(0))
        assertEquals("second-alias.com", body.getJSONArray("aliases").getString(1))
    }

    @Test fun createAliasesRejectsInvalidDomains() {
        installFakeClient()
        listOf(
            emptyList(),
            listOf("bad domain"),
            listOf("ok.com", "")
        ).forEach { invalid ->
            try {
                PloiApi.createAliases(token, 1, 1, invalid)
                fail("Expected rejection for $invalid")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // ---- DELETE /api/servers/{server}/sites/{site}/aliases/{alias} ----

    @Test fun deleteAliasHitsDocumentedRouteAndParsesRemainingAliases() {
        installFakeClient()
        scriptedBody = aliasesJson("first-alias.com")
        val result = PloiApi.deleteAlias(token, 1, 1, "second-alias.com")
        assertEquals(listOf("first-alias.com"), result.aliases)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/aliases/second-alias.com", request.url)
    }
}
