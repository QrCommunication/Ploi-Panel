package com.qrcommunication.ploipanel

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Wire-level coverage for every documented route of the `fastcgi-cache` domain
 * (/api/servers/{server}/sites/{site}/fastcgi-cache…, 3 routes).
 */
class PloiApiFastcgiCacheDomainTest {
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

    /** Documented site payload shared by the three FastCGI routes. */
    private fun siteJson(): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("id", 1)
                .put("status", "active")
                .put("server_id", 1)
                .put("domain", "awesome.com")
                .put("deploy_script", false)
                .put("web_directory", "/public")
                .put("project_type", JSONObject.NULL)
                .put("project_root", "/")
                .put("last_deploy_at", JSONObject.NULL)
                .put("system_user", "ploi")
                .put("php_version", 7.4)
                .put("health_url", JSONObject.NULL)
                .put("has_repository", false)
                .put("zero_downtime_deployment", false)
                .put("created_at", "2020-08-03 11:23:48")
        )
        .toString()

    // ---- POST /api/servers/{server}/sites/{site}/fastcgi-cache/enable ----

    @Test fun enableFastcgiCacheHitsDocumentedRouteAndParsesSite() {
        installFakeClient()
        scriptedBody = siteJson()
        val site = PloiApi.enableFastcgiCache(token, 1, 1)
        assertEquals(1L, site.id)
        assertEquals("awesome.com", site.domain)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/fastcgi-cache/enable", request.url)
    }

    // ---- DELETE /api/servers/{server}/sites/{site}/fastcgi-cache/disable ----

    @Test fun disableFastcgiCacheHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = siteJson()
        PloiApi.disableFastcgiCache(token, 1, 1)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/fastcgi-cache/disable", request.url)
    }

    // ---- POST /api/servers/{server}/sites/{site}/fastcgi-cache/flush ----

    @Test fun flushFastcgiCacheHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = siteJson()
        PloiApi.flushFastcgiCache(token, 1, 1)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/fastcgi-cache/flush", request.url)
    }
}
