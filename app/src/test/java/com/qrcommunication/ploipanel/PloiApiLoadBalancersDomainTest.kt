package com.qrcommunication.ploipanel

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Wire-level coverage for every documented route of the `load-balancers` domain
 * (/api/servers/{server}/load-balancer…, 4 routes).
 */
class PloiApiLoadBalancersDomainTest {
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

    // ---- PATCH /api/servers/{server}/load-balancer/attach ----

    @Test fun attachServerSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = "{}"
        PloiApi.attachLoadBalancerServer(token, 1, 2)
        val request = recorded.single()
        assertEquals("PATCH", request.method)
        assertEquals("https://ploi.io/api/servers/1/load-balancer/attach", request.url)
        assertEquals(2L, JSONObject(request.body.orEmpty()).getLong("server_id"))
    }

    // ---- PATCH /api/servers/{server}/load-balancer/detach ----

    @Test fun detachServerSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = "{}"
        PloiApi.detachLoadBalancerServer(token, 1, 2)
        val request = recorded.single()
        assertEquals("PATCH", request.method)
        assertEquals("https://ploi.io/api/servers/1/load-balancer/detach", request.url)
        assertEquals(2L, JSONObject(request.body.orEmpty()).getLong("server_id"))
    }

    @Test fun attachRejectsInvalidTarget() {
        installFakeClient()
        try {
            PloiApi.attachLoadBalancerServer(token, 1, 0)
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    // ---- POST /api/servers/{server}/load-balancer/{domain}/request-certificate ----

    @Test fun requestCertificateHitsDocumentedRouteAndParsesMessage() {
        installFakeClient()
        scriptedBody = JSONObject()
            .put("message", "Certificate request for dennis.examplehosting.com is in progress for this load balancer")
            .toString()
        val message = PloiApi.requestLoadBalancerCertificate(token, 1, "dennis.examplehosting.com")
        assertEquals(
            "Certificate request for dennis.examplehosting.com is in progress for this load balancer",
            message
        )
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals(
            "https://ploi.io/api/servers/1/load-balancer/dennis.examplehosting.com/request-certificate",
            request.url
        )
    }

    // ---- DELETE /api/servers/{server}/load-balancer/{domain}/revoke-certificate ----

    @Test fun revokeCertificateHitsDocumentedRouteAndParsesMessage() {
        installFakeClient()
        scriptedBody = JSONObject()
            .put("message", "Certificate revoke request for dennis.examplehosting.com is in progress for this load balancer")
            .toString()
        val message = PloiApi.revokeLoadBalancerCertificate(token, 1, "dennis.examplehosting.com")
        assertEquals(
            "Certificate revoke request for dennis.examplehosting.com is in progress for this load balancer",
            message
        )
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals(
            "https://ploi.io/api/servers/1/load-balancer/dennis.examplehosting.com/revoke-certificate",
            request.url
        )
    }

    @Test fun certificateDomainIsValidated() {
        installFakeClient()
        try {
            PloiApi.requestLoadBalancerCertificate(token, 1, "bad domain")
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {
        }
    }
}
