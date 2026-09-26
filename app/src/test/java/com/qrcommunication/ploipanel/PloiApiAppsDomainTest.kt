package com.qrcommunication.ploipanel

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Parsing and wire-level coverage for every documented route of the
 * `apps` domain (WordPress / Nextcloud / Statamic installers, 6 routes).
 */
class PloiApiAppsDomainTest {
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

    /** Documented WordPress/Nextcloud shape: no server_id, no status. */
    private fun appSiteJson(wordpress: Boolean): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("id", 90)
                .put("domain", "domain.com")
                .put("web_directory", "/public")
                .put("wordpress", wordpress)
                .put("laravel", false)
                .put("project_root", "/")
                .put("last_deploy_at", "2018-09-14 20:43:11")
                .put("created_at", "2018-09-14 11:40:01")
        )
        .toString()

    /** Documented Statamic shape: full site payload with server_id and status. */
    private fun statamicSiteJson(status: String): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("id", 1)
                .put("status", status)
                .put("server_id", 1)
                .put("domain", "example.com")
                .put("test_domain", JSONObject.NULL)
                .put("deploy_script", false)
                .put("web_directory", "/public")
                .put("project_type", "statamic")
                .put("project_root", "/")
                .put("last_deploy_at", JSONObject.NULL)
                .put("system_user", "ploi")
                .put("php_version", 7.4)
                .put("health_url", JSONObject.NULL)
                .put("has_repository", false)
                .put("zero_downtime_deployment", false)
                .put("fastcgi_cache", false)
                .put("created_at", "2021-01-01 00:00:00")
        )
        .put("message", "Statamic is being installed")
        .toString()

    // ---- POST /api/servers/{server}/sites/{id}/wordpress ----

    @Test fun installWordpressHitsDocumentedRouteAndParsesSite() {
        installFakeClient()
        scriptedBody = appSiteJson(wordpress = true)
        val site = PloiApi.installWordpress(token, 1, 90, createDatabase = true)
        assertEquals(90L, site.id)
        assertEquals("domain.com", site.domain)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/90/wordpress", request.url)
        assertTrue(JSONObject(request.body.orEmpty()).getBoolean("create_database"))
    }

    @Test fun installWordpressWithoutDatabaseSendsNoBody() {
        installFakeClient()
        scriptedBody = appSiteJson(wordpress = true)
        PloiApi.installWordpress(token, 1, 90)
        assertEquals(null, recorded.single().body)
    }

    // ---- DELETE /api/servers/{server}/sites/{id}/wordpress ----

    @Test fun uninstallWordpressHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = appSiteJson(wordpress = false)
        PloiApi.uninstallWordpress(token, 1, 90)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/90/wordpress", request.url)
    }

    // ---- POST /api/servers/{server}/sites/{id}/nextcloud ----

    @Test fun installNextcloudHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = appSiteJson(wordpress = false)
        PloiApi.installNextcloud(token, 1, 90)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/90/nextcloud", request.url)
    }

    // ---- DELETE /api/servers/{server}/sites/{id}/nextcloud ----

    @Test fun uninstallNextcloudHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = appSiteJson(wordpress = false)
        PloiApi.uninstallNextcloud(token, 1, 90)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/90/nextcloud", request.url)
    }

    // ---- POST /api/servers/{server}/sites/{id}/statamic ----

    @Test fun installStatamicHitsDocumentedRouteAndParsesSite() {
        installFakeClient()
        scriptedBody = statamicSiteJson("statamic-installing")
        val site = PloiApi.installStatamic(token, 1, 1)
        assertEquals("statamic-installing", site.status)
        assertEquals("statamic", site.projectType)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/statamic", request.url)
    }

    // ---- DELETE /api/servers/{server}/sites/{id}/statamic ----

    @Test fun uninstallStatamicHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = statamicSiteJson("statamic-uninstalling")
        val site = PloiApi.uninstallStatamic(token, 1, 1)
        assertEquals("statamic-uninstalling", site.status)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/statamic", request.url)
    }
}
