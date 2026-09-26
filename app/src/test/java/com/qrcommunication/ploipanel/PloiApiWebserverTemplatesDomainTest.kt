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
 * `webserver-templates` domain (/api/webserver-templates, 2 routes). Response
 * shapes mirror the documented examples on
 * developers.ploi.io/webserver-templates pages. The domain is read-only in the
 * documented inventory: no create/update/delete route is extrapolated.
 */
class PloiApiWebserverTemplatesDomainTest {
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

    /** Documented webserver template entry. */
    private fun templateEntry(): JSONObject = JSONObject()
        .put("id", 1)
        .put("label", "Magento webserver")
        .put("content", ".....")
        .put("created_at", "2020-10-26 10:03:51")

    private fun linksJson(): JSONObject = JSONObject()
        .put("first", "https://ploi.io/api/webserver-templates?page=1")
        .put("last", "https://ploi.io/api/webserver-templates?page=1")
        .put("prev", JSONObject.NULL)
        .put("next", JSONObject.NULL)

    private fun metaJson(): JSONObject = JSONObject()
        .put("current_page", 1)
        .put("from", 1)
        .put("last_page", 1)
        .put("path", "https://ploi.io/api/webserver-templates")
        .put("per_page", 15)
        .put("to", 1)
        .put("total", 1)

    /** Documented list shape: data array plus links/meta pagination. */
    private fun templateListJson(): String = JSONObject()
        .put("data", JSONArray().put(templateEntry()))
        .put("links", linksJson())
        .put("meta", metaJson())
        .toString()

    /** Documented single template shape. */
    private fun templateJson(): String = JSONObject().put("data", templateEntry().put("content", "...")).toString()

    // ---- GET /api/webserver-templates ----

    @Test fun parsesTemplateListWithDocumentedShape() {
        val page = PloiApi.parseWebserverTemplates(templateListJson())
        assertEquals(1, page.currentPage)
        assertEquals(1, page.lastPage)
        assertFalse(page.hasNext)
        val template = page.templates.single()
        assertEquals(1L, template.id)
        assertEquals("Magento webserver", template.label)
        assertEquals(".....", template.content)
        assertEquals("2020-10-26 10:03:51", template.createdAt)
    }

    @Test fun webserverTemplatesHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = templateListJson()
        val page = PloiApi.webserverTemplates(token, page = 2, perPage = 50)
        assertEquals(1, page.templates.size)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/webserver-templates?page=2&per_page=50", request.url)
        assertThrowsIAE { PloiApi.webserverTemplates(token, page = 0) }
        assertThrowsIAE { PloiApi.webserverTemplates(token, perPage = 51) }
    }

    // ---- GET /api/webserver-templates/{id} ----

    @Test fun parsesTemplateDetailWithDocumentedShape() {
        val template = PloiApi.parseWebserverTemplate(templateJson())
        assertEquals(1L, template.id)
        assertEquals("Magento webserver", template.label)
        assertEquals("...", template.content)
        assertEquals("2020-10-26 10:03:51", template.createdAt)
    }

    @Test fun webserverTemplateReadsDocumentedRoute() {
        installFakeClient()
        scriptedBody = templateJson()
        val template = PloiApi.webserverTemplate(token, templateId = 1)
        assertEquals(1L, template.id)
        assertEquals("Magento webserver", template.label)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/webserver-templates/1", request.url)
        assertThrowsIAE { PloiApi.webserverTemplate(token, templateId = 0) }
        assertThrowsIAE { PloiApi.webserverTemplate(token, templateId = -1) }
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
