package com.qrcommunication.ploipanel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Parsing and wire-level coverage for every documented route of the
 * `insights` domain (/api/servers/{server}/insights, 6 routes).
 */
class PloiApiInsightsDomainTest {
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

    private fun insightEntry(): JSONObject = JSONObject()
        .put("id", 1)
        .put("type", "supervisor-needs-running")
        .put("status", "open")
        .put("description", "Your **supervisor** service is running, but you do not seem to use it.")
        .put("log_file", "/var/log/supervisor/supervisor.log")
        .put("priority", "medium")
        .put("meta", JSONObject.NULL)
        .put("is_fixable", true)
        .put("processed_at", JSONObject.NULL)
        .put("created_at", "2021-11-09 12:50:40")

    private fun insightListJson(): String = JSONObject()
        .put("data", JSONArray().put(insightEntry()))
        .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
        .toString()

    private fun insightJson(): String = JSONObject().put("data", insightEntry()).toString()

    // ---- GET /api/servers/{server}/insights ----

    @Test fun parsesInsightListWithDocumentedShape() {
        val page = PloiApi.parseInsights(insightListJson())
        assertFalse(page.hasNext)
        val insight = page.insights.single()
        assertEquals(1L, insight.id)
        assertEquals("supervisor-needs-running", insight.type)
        assertEquals("open", insight.status)
        assertEquals("/var/log/supervisor/supervisor.log", insight.logFile)
        assertEquals("medium", insight.priority)
        assertEquals("", insight.meta)
        assertTrue(insight.fixable)
        assertEquals("", insight.processedAt)
        assertEquals("2021-11-09 12:50:40", insight.createdAt)
    }

    @Test fun insightsHitsDocumentedRouteWithPagination() {
        installFakeClient()
        scriptedBody = insightListJson()
        PloiApi.insights(token, 1, page = 2, perPage = 50)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/insights?page=2&per_page=50", request.url)
    }

    // ---- GET /api/servers/{server}/insights/{id} ----

    @Test fun insightHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = insightJson()
        val insight = PloiApi.insight(token, 1, 1)
        assertEquals(1L, insight.id)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/insights/1", request.url)
    }

    // ---- GET /api/servers/{server}/insights/{id}/detail ----

    @Test fun parsesInsightDetail() {
        val detail = PloiApi.parseInsightDetail(
            JSONObject().put(
                "data",
                JSONObject()
                    .put("id", 1)
                    .put("description", "Your **supervisor** service is running.")
                    .put("description_html", "Your <strong>supervisor</strong> service is running.")
                    .put("html", "<h2>Explanation</h2>")
            ).toString()
        )
        assertEquals(1L, detail.id)
        assertEquals("Your **supervisor** service is running.", detail.description)
        assertEquals("Your <strong>supervisor</strong> service is running.", detail.descriptionHtml)
        assertEquals("<h2>Explanation</h2>", detail.html)
    }

    @Test fun insightDetailHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject()
            .put("data", JSONObject().put("id", 1).put("description", "d").put("description_html", "h").put("html", "x"))
            .toString()
        PloiApi.insightDetail(token, 1, 1)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/insights/1/detail", request.url)
    }

    // ---- POST /api/servers/{server}/insights/{id}/automatically-fix ----

    @Test fun automaticallyFixHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject().put("message", "Insight is being fixed.").toString()
        val message = PloiApi.automaticallyFixInsight(token, 1, 1)
        assertEquals("Insight is being fixed.", message)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/insights/1/automatically-fix", request.url)
    }

    // ---- POST /api/servers/{server}/insights/{id}/ignore ----

    @Test fun ignoreInsightHitsDocumentedRouteAndParsesInsight() {
        installFakeClient()
        scriptedBody = JSONObject()
            .put("data", insightEntry().put("status", "ignored").put("processed_at", "2021-11-09 13:06:45"))
            .toString()
        val insight = PloiApi.ignoreInsight(token, 1, 1)
        assertEquals("ignored", insight.status)
        assertEquals("2021-11-09 13:06:45", insight.processedAt)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/insights/1/ignore", request.url)
    }

    // ---- DELETE /api/servers/{server}/insights/{id} ----

    @Test fun deleteInsightHitsDocumentedRouteWithoutParsingBody() {
        installFakeClient()
        scriptedBody = JSONObject()
            .put("data", JSONArray())
            .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
            .toString()
        PloiApi.deleteInsight(token, 1, 1)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/insights/1", request.url)
    }
}
