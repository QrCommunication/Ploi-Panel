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
 * `tenants` domain (/api/servers/{server}/sites/{site}/tenants, 7 routes).
 */
class PloiApiTenantsDomainTest {
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

    private fun tenantsJson(vararg tenants: String): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("tenants", JSONArray().apply { tenants.forEach { put(it) } })
                .put("count", tenants.size)
                .put("main", "domain.com")
        )
        .toString()

    // ---- GET /api/servers/{server}/sites/{site}/tenants ----

    @Test fun parsesTenantsWithDocumentedShape() {
        val result = PloiApi.parseTenants(tenantsJson("anotherdomain.com"))
        assertEquals(listOf("anotherdomain.com"), result.tenants)
        assertEquals(1, result.count)
        assertEquals("domain.com", result.main)
    }

    @Test fun tenantsHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = tenantsJson("anotherdomain.com")
        PloiApi.tenants(token, 1, 1)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/tenants", request.url)
    }

    // ---- POST /api/servers/{server}/sites/{site}/tenants ----

    @Test fun createTenantsSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = tenantsJson("anotherdomain.com", "example.com")
        val result = PloiApi.createTenants(token, 1, 1, listOf("anotherdomain.com", "example.com"))
        assertEquals(2, result.count)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/tenants", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("anotherdomain.com", body.getJSONArray("tenants").getString(0))
    }

    @Test fun createTenantsRejectsInvalidDomains() {
        installFakeClient()
        try {
            PloiApi.createTenants(token, 1, 1, listOf("bad domain"))
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    // ---- DELETE /api/servers/{server}/sites/{site}/tenants/{tenant} ----

    @Test fun deleteTenantHitsDocumentedRouteWithoutParsingBody() {
        installFakeClient()
        scriptedBody = ""
        PloiApi.deleteTenant(token, 1, 1, "anotherdomain.com")
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/tenants/anotherdomain.com", request.url)
    }

    // ---- POST /api/servers/{server}/sites/{site}/tenants/{tenant}/request-certificate ----

    @Test fun requestTenantCertificateHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = ""
        PloiApi.requestTenantCertificate(token, 1, 1, "anotherdomain.com", webhook = "https://hooks.example.com/x", force = true)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals(
            "https://ploi.io/api/servers/1/sites/1/tenants/anotherdomain.com/request-certificate",
            request.url
        )
        val body = JSONObject(request.body.orEmpty())
        assertEquals("https://hooks.example.com/x", body.getString("webhook"))
        assertEquals(true, body.getBoolean("force"))
    }

    @Test fun requestTenantCertificateOmitsEmptyWebhook() {
        installFakeClient()
        scriptedBody = ""
        PloiApi.requestTenantCertificate(token, 1, 1, "anotherdomain.com")
        val body = JSONObject(recorded.single().body.orEmpty())
        assertFalse(body.has("webhook"))
        assertFalse(body.has("force"))
    }

    @Test fun webhookMustUseHttps() {
        installFakeClient()
        try {
            PloiApi.requestTenantCertificate(token, 1, 1, "anotherdomain.com", webhook = "http://insecure.example.com")
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    // ---- POST /api/servers/{server}/sites/{site}/tenants/{tenant}/revoke-certificate ----

    @Test fun revokeTenantCertificateHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = ""
        PloiApi.revokeTenantCertificate(token, 1, 1, "anotherdomain.com")
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals(
            "https://ploi.io/api/servers/1/sites/1/tenants/anotherdomain.com/revoke-certificate",
            request.url
        )
    }

    // ---- GET /api/servers/{server}/sites/{site}/tenants/{tenant}/nginx-configuration ----

    @Test fun tenantNginxConfigurationHitsDocumentedRouteAndParsesContent() {
        installFakeClient()
        scriptedBody = JSONObject().put("content", "..Contents of webserver configuration file..").toString()
        val content = PloiApi.tenantNginxConfiguration(token, 1, 1, "anotherdomain.com")
        assertEquals("..Contents of webserver configuration file..", content)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals(
            "https://ploi.io/api/servers/1/sites/1/tenants/anotherdomain.com/nginx-configuration",
            request.url
        )
    }

    // ---- PATCH /api/servers/{server}/sites/{site}/tenants/{tenant}/nginx-configuration ----

    @Test fun updateTenantNginxConfigurationSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = JSONObject()
            .put("status", "ok")
            .put("message", "Don't forget to reload or restart NGINX after this call.")
            .toString()
        val ack = PloiApi.updateTenantNginxConfiguration(token, 1, 1, "anotherdomain.com", "server { }")
        assertEquals("Don't forget to reload or restart NGINX after this call.", ack.message)
        val request = recorded.single()
        assertEquals("PATCH", request.method)
        assertEquals(
            "https://ploi.io/api/servers/1/sites/1/tenants/anotherdomain.com/nginx-configuration",
            request.url
        )
        assertEquals("server { }", JSONObject(request.body.orEmpty()).getString("content"))
    }
}
