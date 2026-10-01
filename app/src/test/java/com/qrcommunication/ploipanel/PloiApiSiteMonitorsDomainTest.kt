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
 * Parsing and wire-level coverage for every documented route of the site
 * `monitoring` domain (/api/servers/{server}/sites/{site}/monitors, 4 routes).
 */
class PloiApiSiteMonitorsDomainTest {
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

    private fun monitorEntry(): JSONObject = JSONObject()
        .put("id", 1)
        .put("label", "My Monitor")
        .put("location", "singapore")
        .put("average_uptime", "6.23")
        .put("active", true)
        .put("created_at", "2019-08-13 14:06:21")

    // ---- GET /api/servers/{server}/sites/{site}/monitors ----

    @Test fun parsesMonitorListWithDocumentedShape() {
        val page = PloiApi.parseSiteMonitors(
            JSONObject()
                .put("data", JSONArray().put(monitorEntry()))
                .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
                .toString()
        )
        assertFalse(page.hasNext)
        val monitor = page.monitors.single()
        assertEquals(1L, monitor.id)
        assertEquals("My Monitor", monitor.label)
        assertEquals("singapore", monitor.location)
        assertEquals("6.23", monitor.averageUptime)
        assertTrue(monitor.active)
        assertEquals("2019-08-13 14:06:21", monitor.createdAt)
    }

    @Test fun siteMonitorsHitsDocumentedRouteWithPagination() {
        installFakeClient()
        scriptedBody = JSONObject()
            .put("data", JSONArray().put(monitorEntry()))
            .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
            .toString()
        PloiApi.siteMonitors(token, 1, 1, page = 2, perPage = 50)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/monitors?page=2&per_page=50", request.url)
    }

    // ---- GET /api/servers/{server}/sites/{site}/monitors/{monitor} ----

    @Test fun siteMonitorHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject().put("data", monitorEntry()).toString()
        val monitor = PloiApi.siteMonitor(token, 1, 1, 1)
        assertEquals(1L, monitor.id)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/monitors/1", request.url)
    }

    // ---- GET /api/servers/{server}/sites/{site}/monitors/{monitor}/uptime-responses ----

    @Test fun parsesUptimeResponses() {
        val responses = PloiApi.parseUptimeResponses(
            JSONObject().put(
                "data",
                JSONArray()
                    .put(JSONObject().put("response_time", 0.556179).put("created_at", "2023-02-27 07:32:49"))
                    .put(JSONObject().put("response_time", 0.754359).put("created_at", "2023-02-27 07:27:08"))
            ).toString()
        )
        assertEquals(2, responses.size)
        assertEquals(0.556179, responses[0].responseTime, 0.000001)
        assertEquals("2023-02-27 07:32:49", responses[0].createdAt)
    }

    @Test fun uptimeResponsesHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject()
            .put("data", JSONArray().put(JSONObject().put("response_time", 0.5).put("created_at", "2023-02-27 07:32:49")))
            .toString()
        PloiApi.uptimeResponses(token, 1, 1, 1)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/monitors/1/uptime-responses", request.url)
    }

    // ---- DELETE /api/servers/{server}/sites/{site}/monitors/{monitor} ----

    @Test fun deleteSiteMonitorHitsDocumentedRouteAndToleratesNullMessage() {
        installFakeClient()
        scriptedBody = JSONObject().put("status", "ok").put("message", JSONObject.NULL).toString()
        val message = PloiApi.deleteSiteMonitor(token, 1, 1, 1)
        assertEquals("", message)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/monitors/1", request.url)
    }
}
