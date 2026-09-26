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
 * `php` domain (OPcache + version management, 6 routes).
 */
class PloiApiPhpDomainTest {
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

    /** Documented OPcache toggle shape (enable/disable share the full server payload). */
    private fun serverJson(): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("id", 1)
                .put("status", "active")
                .put("type", "server")
                .put("name", "silent-willow")
                .put("ip_address", "127.0.0.1")
                .put("internal_ip", JSONObject.NULL)
                .put("ssh_port", 22)
                .put("reboot_required", false)
                .put("php_version", 7.4)
                .put("mysql_version", 5.7)
                .put("sites_count", 1)
                .put("monitoring", false)
                .put("opcache", false)
                .put("installed_php_versions", JSONArray().put("7.4"))
                .put("status_id", 2)
                .put("created_at", "2020-07-27 13:37:19")
        )
        .toString()

    // ---- POST /api/servers/{server}/refresh-opcache ----

    @Test fun refreshOpcacheHitsDocumentedRouteAndParsesServer() {
        installFakeClient()
        scriptedBody = serverJson()
        val server = PloiApi.refreshOpcache(token, 1)
        assertEquals(1L, server.id)
        assertEquals("silent-willow", server.name)
        assertEquals("7.4", server.phpVersion)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/refresh-opcache", request.url)
    }

    // ---- POST /api/servers/{server}/enable-opcache ----

    @Test fun enableOpcacheHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = serverJson()
        PloiApi.enableOpcache(token, 1)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/enable-opcache", request.url)
    }

    // ---- DELETE /api/servers/{server}/disable-opcache ----

    @Test fun disableOpcacheHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = serverJson()
        val server = PloiApi.disableOpcache(token, 1)
        assertFalse(server.opcache)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/disable-opcache", request.url)
    }

    // ---- GET /api/servers/{server}/php/versions ----

    @Test fun parsesInstalledPhpVersions() {
        val versions = PloiApi.parsePhpVersions(
            JSONObject().put("data", JSONObject().put("versions", JSONArray().put("7.4").put("8.1"))).toString()
        )
        assertEquals(listOf("7.4", "8.1"), versions)
    }

    @Test fun phpVersionsHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject().put("data", JSONObject().put("versions", JSONArray().put("7.4"))).toString()
        val versions = PloiApi.phpVersions(token, 1)
        assertEquals(listOf("7.4"), versions)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/php/versions", request.url)
    }

    // ---- POST /api/servers/{server}/php/install ----

    @Test fun installPhpVersionSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = JSONObject().put("status", "ok").put("message", "PHP version 8.1 is being installed.").toString()
        val ack = PloiApi.installPhpVersion(token, 1, "8.1")
        assertEquals("PHP version 8.1 is being installed.", ack.message)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/php/install", request.url)
        assertEquals("8.1", JSONObject(request.body.orEmpty()).getString("version"))
    }

    // ---- POST /api/servers/{server}/php/cli-version ----

    @Test fun switchPhpCliVersionSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = JSONObject().put("status", "ok").put("message", "PHP CLI version switched.").toString()
        PloiApi.switchPhpCliVersion(token, 1, "8.2")
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/php/cli-version", request.url)
        assertEquals("8.2", JSONObject(request.body.orEmpty()).getString("version"))
    }

    @Test fun phpVersionStringIsValidated() {
        installFakeClient()
        listOf("", "8", "eight.one", "8.1; rm -rf /").forEach { invalid ->
            try {
                PloiApi.installPhpVersion(token, 1, invalid)
                fail("Expected rejection for '$invalid'")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertTrue(recorded.isEmpty())
    }
}
