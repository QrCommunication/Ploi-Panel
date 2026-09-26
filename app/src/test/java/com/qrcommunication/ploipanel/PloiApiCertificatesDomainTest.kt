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
 * `certificates` domain (/api/servers/{server}/sites/{site}/certificates, 6 routes).
 */
class PloiApiCertificatesDomainTest {
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

    private fun certificateEntry(): JSONObject = JSONObject()
        .put("id", 1)
        .put("status", "active")
        .put("domain", "domain1.com")
        .put("type", "letsencrypt")
        .put("active", true)
        .put("site_id", 1)
        .put("server_id", 1)
        .put("expires_at", "2019-10-29 05:25:05")
        .put("created_at", "2019-07-31 08:24:54")

    private fun certificateListJson(): String = JSONObject()
        .put("data", JSONArray().put(certificateEntry()))
        .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
        .toString()

    // ---- GET /api/servers/{server}/sites/{site}/certificates ----

    @Test fun parsesCertificateListWithDocumentedShape() {
        val page = PloiApi.parseCertificates(certificateListJson())
        assertFalse(page.hasNext)
        val certificate = page.certificates.single()
        assertEquals(1L, certificate.id)
        assertEquals("active", certificate.status)
        assertEquals("domain1.com", certificate.domain)
        assertEquals("letsencrypt", certificate.type)
        assertTrue(certificate.active)
        assertFalse(certificate.tenant)
        assertEquals(1L, certificate.siteId)
        assertEquals(1L, certificate.serverId)
        assertEquals("2019-10-29 05:25:05", certificate.expiresAt)
        assertEquals("2019-07-31 08:24:54", certificate.createdAt)
    }

    @Test fun certificatesHitsDocumentedRouteWithPagination() {
        installFakeClient()
        scriptedBody = certificateListJson()
        PloiApi.certificates(token, 1, 1, page = 2, perPage = 50)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/certificates?page=2&per_page=50", request.url)
    }

    // ---- GET /api/servers/{server}/sites/{site}/certificates/{certificate} ----

    @Test fun certificateHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject().put("data", certificateEntry()).toString()
        val certificate = PloiApi.certificate(token, 1, 1, 1)
        assertEquals(1L, certificate.id)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/certificates/1", request.url)
    }

    // ---- POST /api/servers/{server}/sites/{site}/certificates ----

    @Test fun createLetsencryptCertificateSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = JSONObject().put(
            "data",
            certificateEntry().put("status", "created").put("expires_at", JSONObject.NULL)
        ).toString()
        val certificate = PloiApi.createCertificate(
            token, 1, 1, CreateCertificateRequest(type = "letsencrypt", certificate = "domain.com")
        )
        assertEquals("created", certificate.status)
        assertEquals("", certificate.expiresAt)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/certificates", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("letsencrypt", body.getString("type"))
        assertEquals("domain.com", body.getString("certificate"))
        assertFalse(body.has("private"))
        assertFalse(body.has("force"))
    }

    @Test fun createWildcardCertificateSendsAdditionalObject() {
        val additional = JSONObject().put("provider", "cloudflare").put("key", "KEY").put("secret", "SECRET")
        val body = JSONObject(
            CreateCertificateRequest("letsencrypt", "domain.com,*.domain.com", force = true, additional = additional).toJson()
        )
        assertTrue(body.getBoolean("force"))
        assertEquals("cloudflare", body.getJSONObject("additional").getString("provider"))
    }

    @Test fun createCertificateRequestRejectsInvalidInput() {
        listOf(
            { CreateCertificateRequest("selfsigned", "domain.com") },
            { CreateCertificateRequest("letsencrypt", "") },
            { CreateCertificateRequest("custom", "CERT") }
        ).forEach { invalid ->
            try {
                invalid()
                fail("Expected rejection")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // ---- GET /api/servers/{server}/sites/{site}/certificates/{certificate}/download ----

    @Test fun parsesCertificateDownload() {
        val download = PloiApi.parseCertificateDownload(
            JSONObject()
                .put("certificate", "-----BEGIN CERTIFICATE-----\nMIIF...\n-----END CERTIFICATE-----\n")
                .put("certificate_path", "/etc/letsencrypt/live/domain.com-1/fullchain.pem")
                .put("expires_at", "2026-10-29 05:34:30")
                .toString()
        )
        assertTrue(download.certificate.startsWith("-----BEGIN CERTIFICATE-----"))
        assertEquals("/etc/letsencrypt/live/domain.com-1/fullchain.pem", download.certificatePath)
        assertEquals("2026-10-29 05:34:30", download.expiresAt)
    }

    @Test fun downloadCertificateHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject()
            .put("certificate", "-----BEGIN CERTIFICATE-----")
            .put("certificate_path", "/etc/letsencrypt/live/domain.com/fullchain.pem")
            .put("expires_at", "2026-10-29 05:34:30")
            .toString()
        PloiApi.downloadCertificate(token, 1, 1, 1)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/certificates/1/download", request.url)
    }

    // ---- POST /api/servers/{server}/sites/{site}/certificates/{certificate}/activate ----

    @Test fun activateCertificateHitsDocumentedRouteAndParsesCertificate() {
        installFakeClient()
        scriptedBody = JSONObject().put("data", certificateEntry().put("id", 2).put("active", false)).toString()
        val certificate = PloiApi.activateCertificate(token, 1, 1, 2)
        assertEquals(2L, certificate.id)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/certificates/2/activate", request.url)
    }

    // ---- DELETE /api/servers/{server}/sites/{site}/certificates/{certificate} ----

    @Test fun deleteCertificateHitsDocumentedRouteAndParsesMessage() {
        installFakeClient()
        scriptedBody = JSONObject().put("message", "Certificate has been deleted").toString()
        val message = PloiApi.deleteCertificate(token, 1, 1, 1)
        assertEquals("Certificate has been deleted", message)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/certificates/1", request.url)
    }
}
