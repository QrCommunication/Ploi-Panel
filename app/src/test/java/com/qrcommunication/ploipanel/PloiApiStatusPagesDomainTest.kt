package com.qrcommunication.ploipanel

import org.json.JSONArray
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
 * `status-pages` domain (/api/status-pages, 5 routes). Response shapes mirror the
 * documented examples on developers.ploi.io/status-pages pages.
 */
class PloiApiStatusPagesDomainTest {
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

    /** Documented theme object (list example: empty-string logo, get example: null logo). */
    private fun themeJson(logoNull: Boolean = false): JSONObject = JSONObject()
        .put("primary", "#1853DB")
        .put("secondary", "#1853DB")
        .put("borders", JSONObject().put("header", false))
        .put("lightMode", "light")
        .put("logo", if (logoNull) JSONObject.NULL else "")
        .put("branding", true)

    /** Documented status page entry. */
    private fun statusPageEntry(): JSONObject = JSONObject()
        .put("id", 1)
        .put("name", "My awesome status page")
        .put("slug", "my-awesome-status-page")
        .put("description", JSONObject.NULL)
        .put("theme", themeJson())

    private fun linksJson(path: String): JSONObject = JSONObject()
        .put("first", "https://ploi.io/api/$path?page=1")
        .put("last", "https://ploi.io/api/$path?page=1")
        .put("prev", JSONObject.NULL)
        .put("next", JSONObject.NULL)

    private fun metaJson(path: String, to: Int = 1, total: Int = 1): JSONObject = JSONObject()
        .put("current_page", 1)
        .put("from", 1)
        .put("last_page", 1)
        .put("path", "https://ploi.io/api/$path")
        .put("per_page", 15)
        .put("to", to)
        .put("total", total)

    /** Documented list shape: data array plus links/meta pagination. */
    private fun statusPageListJson(): String = JSONObject()
        .put("data", JSONArray().put(statusPageEntry()))
        .put("links", linksJson("status-pages"))
        .put("meta", metaJson("status-pages"))
        .toString()

    /** Documented single status page shape. */
    private fun statusPageJson(): String = JSONObject().put("data", statusPageEntry()).toString()

    /** Documented incident entries (latest first). */
    private fun incidentListJson(): String = JSONObject()
        .put(
            "data",
            JSONArray()
                .put(
                    JSONObject()
                        .put("id", 2)
                        .put("title", "Server connection snowy-crater recovered")
                        .put("description", "Connection to server snowy-crater has been recovered")
                        .put("severity", "resolved")
                )
                .put(
                    JSONObject()
                        .put("id", 1)
                        .put("title", "Server snowy-crater is not reachable")
                        .put("description", "Unable to establish connection to server snowy-crater")
                        .put("severity", "high")
                )
        )
        .put("links", linksJson("status-pages/1/incidents"))
        .put("meta", metaJson("status-pages/1/incidents", to = 3, total = 3))
        .toString()

    /** Documented create response. */
    private fun createdIncidentJson(): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("id", 3)
                .put("title", "New incident")
                .put("description", "An error has occured!")
                .put("severity", "normal")
        )
        .toString()

    // ---- GET /api/status-pages ----

    @Test fun parsesStatusPageListWithDocumentedShape() {
        val page = PloiApi.parseStatusPages(statusPageListJson())
        assertEquals(1, page.currentPage)
        assertEquals(1, page.lastPage)
        assertFalse(page.hasNext)
        val statusPage = page.statusPages.single()
        assertEquals(1L, statusPage.id)
        assertEquals("My awesome status page", statusPage.name)
        assertEquals("my-awesome-status-page", statusPage.slug)
        assertEquals("", statusPage.description) // documented null
        val theme = statusPage.theme
        assertEquals("#1853DB", theme.primary)
        assertEquals("#1853DB", theme.secondary)
        assertFalse(theme.headerBorder)
        assertEquals("light", theme.lightMode)
        assertEquals("", theme.logo) // documented empty string in the list example
        assertTrue(theme.branding)
    }

    @Test fun statusPagesHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = statusPageListJson()
        val page = PloiApi.statusPages(token, page = 2, perPage = 50)
        assertEquals(1, page.statusPages.size)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/status-pages?page=2&per_page=50", request.url)
        assertThrowsIAE { PloiApi.statusPages(token, page = 0) }
        assertThrowsIAE { PloiApi.statusPages(token, perPage = 51) }
    }

    // ---- GET /api/status-pages/{statusPage} ----

    @Test fun parsesDocumentedNullLogoOfDetail() {
        val entry = statusPageEntry().put("theme", themeJson(logoNull = true))
        val statusPage = PloiApi.parseStatusPage(JSONObject().put("data", entry).toString())
        assertEquals("", statusPage.theme.logo) // documented null in the get example
        assertTrue(statusPage.theme.branding)
    }

    @Test fun statusPageReadsDocumentedRoute() {
        installFakeClient()
        scriptedBody = statusPageJson()
        val statusPage = PloiApi.statusPage(token, statusPageId = 1)
        assertEquals(1L, statusPage.id)
        assertEquals("my-awesome-status-page", statusPage.slug)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/status-pages/1", request.url)
        assertThrowsIAE { PloiApi.statusPage(token, statusPageId = 0) }
        assertThrowsIAE { PloiApi.statusPage(token, statusPageId = -1) }
    }

    // ---- GET /api/status-pages/{statusPage}/incidents ----

    @Test fun parsesIncidentsWithDocumentedShape() {
        val page = PloiApi.parseStatusPageIncidents(incidentListJson())
        assertEquals(1, page.currentPage)
        assertEquals(2, page.incidents.size)
        val latest = page.incidents[0] // documented ordering: latest entry first
        assertEquals(2L, latest.id)
        assertEquals("Server connection snowy-crater recovered", latest.title)
        assertEquals("Connection to server snowy-crater has been recovered", latest.description)
        assertEquals("resolved", latest.severity)
        assertEquals("high", page.incidents[1].severity)
    }

    @Test fun statusPageIncidentsHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = incidentListJson()
        val page = PloiApi.statusPageIncidents(token, statusPageId = 1, page = 3, perPage = 20)
        assertEquals(2, page.incidents.size)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/status-pages/1/incidents?page=3&per_page=20", request.url)
        assertThrowsIAE { PloiApi.statusPageIncidents(token, statusPageId = 0) }
        assertThrowsIAE { PloiApi.statusPageIncidents(token, statusPageId = 1, page = 0) }
        assertThrowsIAE { PloiApi.statusPageIncidents(token, statusPageId = 1, perPage = 0) }
    }

    // ---- POST /api/status-pages/{statusPage}/incidents ----

    @Test fun createStatusPageIncidentPostsDocumentedFields() {
        installFakeClient()
        scriptedBody = createdIncidentJson()
        val created = PloiApi.createStatusPageIncident(
            token, statusPageId = 1,
            CreateStatusPageIncidentRequest(title = "New incident", description = "An error has occured!", severity = "high")
        )
        assertEquals(3L, created.id)
        assertEquals("normal", created.severity)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/status-pages/1/incidents", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("New incident", body.getString("title"))
        assertEquals("An error has occured!", body.getString("description"))
        assertEquals("high", body.getString("severity"))
    }

    @Test fun createOmitsDocumentedOptionalFieldsWhenUnset() {
        val body = JSONObject(CreateStatusPageIncidentRequest(title = "  Only title  ").toJson())
        assertEquals("Only title", body.getString("title")) // trimmed
        assertFalse(body.has("description"))
        assertFalse(body.has("severity")) // documented default (normal) applies server-side
    }

    @Test fun createValidatesDocumentedSeveritySet() {
        STATUS_PAGE_INCIDENT_SEVERITIES.forEach { severity ->
            assertEquals(severity, CreateStatusPageIncidentRequest(title = "t", severity = severity).severity)
        }
        assertEquals(setOf("normal", "high", "maintenance", "resolved"), STATUS_PAGE_INCIDENT_SEVERITIES)
        assertThrowsIAE { CreateStatusPageIncidentRequest(title = "t", severity = "critical") }
        assertThrowsIAE { CreateStatusPageIncidentRequest(title = "") }
        assertThrowsIAE { CreateStatusPageIncidentRequest(title = "   ") }
    }

    @Test fun createValidatesStatusPageId() {
        assertThrowsIAE {
            PloiApi.createStatusPageIncident(token, statusPageId = 0, CreateStatusPageIncidentRequest(title = "t"))
        }
    }

    // ---- DELETE /api/status-pages/{statusPage}/incident/{incident} ----

    @Test fun deleteStatusPageIncidentHitsSingularDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject().put("message", "Incident deleted successfully").toString()
        val message = PloiApi.deleteStatusPageIncident(token, statusPageId = 1, incidentId = 2)
        assertEquals("Incident deleted successfully", message)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        // Documented route uses the singular /incident/ segment.
        assertEquals("https://ploi.io/api/status-pages/1/incident/2", request.url)
        assertThrowsIAE { PloiApi.deleteStatusPageIncident(token, statusPageId = 0, incidentId = 2) }
        assertThrowsIAE { PloiApi.deleteStatusPageIncident(token, statusPageId = 1, incidentId = 0) }
        assertThrowsIAE { PloiApi.deleteStatusPageIncident(token, statusPageId = 1, incidentId = -4) }
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
