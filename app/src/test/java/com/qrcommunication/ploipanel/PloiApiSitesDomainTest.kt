package com.qrcommunication.ploipanel

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Parsing, validation and wire-level coverage for every documented route of the `sites` domain. */
class PloiApiSitesDomainTest {
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

    private fun siteJson(overrides: Map<String, Any?> = emptyMap()): String {
        val data = JSONObject()
            .put("id", 1).put("status", "active").put("server_id", 52)
            .put("domain", "wordpress.examplehosting.com").put("test_domain", JSONObject.NULL)
            .put("deploy_script", false).put("web_directory", "/public")
            .put("project_type", "wordpress").put("project_root", "/")
            .put("last_deploy_at", JSONObject.NULL).put("system_user", "ploi")
            .put("php_version", 8.2).put("health_url", JSONObject.NULL)
            .put("disable_robots", false).put("has_repository", true)
            .put("quick_deploy", false).put("zero_downtime_deployment", false)
            .put("has_staging", false).put("fastcgi_cache", false)
            .put("created_at", "2022-11-08 13:43:23")
        overrides.forEach { (key, value) -> data.put(key, value ?: JSONObject.NULL) }
        return JSONObject().put("data", data).toString()
    }

    // ---- Site model parsing ----

    @Test fun parsesFullSiteWithNumericPhpVersion() {
        val site = PloiApi.parseSite(siteJson())
        assertEquals(1L, site.id)
        assertEquals(52L, site.serverId)
        assertEquals("wordpress.examplehosting.com", site.domain)
        assertEquals("active", site.status)
        assertEquals("8.2", site.phpVersion)
        assertEquals("/public", site.webDirectory)
        assertEquals("wordpress", site.projectType)
        assertEquals("ploi", site.systemUser)
        assertEquals("", site.testDomain)
        assertEquals("", site.lastDeployAt)
        assertTrue(site.hasRepository)
        assertFalse(site.quickDeploy)
        assertFalse(site.zeroDowntimeDeployment)
        assertFalse(site.disableRobots)
        assertFalse(site.fastcgiCache)
        assertEquals("2022-11-08 13:43:23", site.createdAt)
    }

    @Test fun parsesNullableSiteFields() {
        val site = PloiApi.parseSite(siteJson(mapOf("project_type" to null, "system_user" to null)))
        assertEquals("", site.projectType)
        assertEquals("", site.systemUser)
    }

    // ---- POST /api/servers/{server}/sites ----

    private fun validSiteRequest() = CreateSiteRequest(rootDomain = "domain.com", webDirectory = "/public")

    @Test fun createSitePostsRequiredFieldsOnly() {
        installFakeClient()
        scriptedBody = siteJson()
        PloiApi.createSite(token, 7, validSiteRequest())
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/sites", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("domain.com", body.getString("root_domain"))
        assertEquals("/public", body.getString("web_directory"))
        assertFalse(body.has("project_root"))
        assertFalse(body.has("project_type"))
        assertFalse(body.has("system_user"))
        assertFalse(body.has("webserver_template"))
        assertFalse(body.has("webhook_url"))
    }

    @Test fun createSiteSerializesOptionalFields() {
        val request = validSiteRequest().copy(
            projectRoot = "/app", projectType = "laravel", systemUser = "ploi",
            webserverTemplate = 3, webhookUrl = "https://example.com/hook"
        )
        val body = JSONObject(request.toJson())
        assertEquals("/app", body.getString("project_root"))
        assertEquals("laravel", body.getString("project_type"))
        assertEquals("ploi", body.getString("system_user"))
        assertEquals(3L, body.getLong("webserver_template"))
        assertEquals("https://example.com/hook", body.getString("webhook_url"))
    }

    @Test fun createSiteRejectsInvalidValues() {
        assertThrowsIAE { validSiteRequest().copy(rootDomain = " ") }
        assertThrowsIAE { validSiteRequest().copy(rootDomain = "a".repeat(101)) }
        assertThrowsIAE { validSiteRequest().copy(rootDomain = "bad domain.com") }
        assertThrowsIAE { validSiteRequest().copy(webDirectory = "/pub lic") }
        assertThrowsIAE { validSiteRequest().copy(webDirectory = "/" + "a".repeat(60)) }
        assertThrowsIAE { validSiteRequest().copy(projectRoot = "a".repeat(51)) }
        assertThrowsIAE { validSiteRequest().copy(projectType = "drupal") }
        assertThrowsIAE { validSiteRequest().copy(webserverTemplate = 0) }
        assertThrowsIAE { validSiteRequest().copy(webhookUrl = "http://example.com") }
    }

    // ---- PATCH /api/servers/{server}/sites/{site} (update-site + robot-access) ----

    @Test fun updateSiteSendsOnlyPresentFields() {
        installFakeClient()
        scriptedBody = siteJson()
        PloiApi.updateSite(token, 7, 1, rootDomain = "new.com")
        val request = recorded.single()
        assertEquals("PATCH", request.method)
        assertEquals("https://ploi.io/api/servers/7/sites/1", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("new.com", body.getString("root_domain"))
        assertFalse(body.has("zero_downtime_deployment"))
        assertFalse(body.has("disable_robots"))
    }

    @Test fun updateSiteSerializesFlags() {
        installFakeClient()
        scriptedBody = siteJson(mapOf("zero_downtime_deployment" to true, "disable_robots" to true))
        val site = PloiApi.updateSite(token, 7, 1, zeroDowntimeDeployment = true, disableRobots = true)
        val body = JSONObject(recorded.single().body.orEmpty())
        assertTrue(body.getBoolean("zero_downtime_deployment"))
        assertTrue(body.getBoolean("disable_robots"))
        assertFalse(body.has("root_domain"))
        assertTrue(site.zeroDowntimeDeployment)
        assertTrue(site.disableRobots)
    }

    @Test fun updateSiteRequiresAtLeastOneChangeAndValidDomain() {
        assertThrowsIAE { PloiApi.updateSite(token, 7, 1) }
        assertThrowsIAE { PloiApi.updateSite(token, 7, 1, rootDomain = "bad domain") }
        assertThrowsIAE { PloiApi.updateSite(token, 7, 1, rootDomain = "a".repeat(101)) }
    }

    @Test fun updateRobotAccessSendsDisableRobotsOnly() {
        installFakeClient()
        scriptedBody = siteJson(mapOf("disable_robots" to true))
        val site = PloiApi.updateRobotAccess(token, 7, 1, disableRobots = true)
        val request = recorded.single()
        assertEquals("PATCH", request.method)
        assertEquals("https://ploi.io/api/servers/7/sites/1", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertTrue(body.getBoolean("disable_robots"))
        assertEquals(1, body.length())
        assertTrue(site.disableRobots)
    }

    // ---- DELETE /api/servers/{server}/sites/{id} ----

    @Test fun deleteSiteParsesConfirmationMessage() {
        installFakeClient()
        scriptedBody = """{"message":"Site has been deleted"}"""
        assertEquals("Site has been deleted", PloiApi.deleteSite(token, 7, 1))
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/7/sites/1", request.url)
        assertNull(request.body)
    }

    // ---- GET /api/servers/{server}/sites/{id}/log[/{log}] ----

    @Test fun parsesPaginatedSiteLogs() {
        val page = PloiApi.parseSiteLogs("""{"data":[
            {"id":2,"description":"Revoke LetsEncrypt SSL certificate","content":"SSL file exists","type":null,
             "created_at":"2020-09-02 12:59:27","created_at_human":"1 day ago"},
            {"id":1,"description":"Pull git changes","content":"git output","type":"deploy",
             "created_at":"2020-09-02 11:51:55","created_at_human":"1 day ago"}],
            "meta":{"current_page":1,"last_page":3}}""")
        assertTrue(page.hasNext)
        assertEquals(2, page.logs.size)
        assertEquals("", page.logs[0].type)
        assertEquals("deploy", page.logs[1].type)
        assertEquals("1 day ago", page.logs[0].createdAtHuman)
    }

    @Test fun siteLogsRequestsPaginatedPath() {
        installFakeClient()
        scriptedBody = """{"data":[],"meta":{"current_page":2,"last_page":2}}"""
        PloiApi.siteLogs(token, 7, 1, page = 2, perPage = 50)
        assertEquals("https://ploi.io/api/servers/7/sites/1/log?page=2&per_page=50", recorded.single().url)
        assertThrowsIAE { PloiApi.siteLogs(token, 7, 1, perPage = 51) }
        assertThrowsIAE { PloiApi.siteLogs(token, 7, 1, page = 0) }
    }

    @Test fun siteLogRequestsSingleEntry() {
        installFakeClient()
        scriptedBody = """{"data":{"id":5,"description":"Install virtual host ploi.io","content":"Full log content..",
            "type":null,"created_at":"2020-11-23 12:29:28","created_at_human":"21 hours ago"}}"""
        val entry = PloiApi.siteLog(token, 7, 1, 5)
        assertEquals("https://ploi.io/api/servers/7/sites/1/log/5", recorded.single().url)
        assertEquals(5L, entry.id)
        assertEquals("Full log content..", entry.content)
    }

    // ---- Test domain (get / enable / disable) ----

    private fun testDomainJson() = """{"data":{"id":1,"domain":"wordpress.com",
        "test_domain":"mellow-winds-034wtvtzb8.ploi.link",
        "full_test_domain":"https://mellow-winds-034wtvtzb8.ploi.link"}}"""

    @Test fun testDomainEndpointsHitDocumentedRoute() {
        installFakeClient()
        scriptedBody = testDomainJson()
        val fetched = PloiApi.testDomain(token, 7, 1)
        assertEquals("GET", recorded[0].method)
        assertEquals("https://ploi.io/api/servers/7/sites/1/test-domain", recorded[0].url)
        PloiApi.enableTestDomain(token, 7, 1)
        assertEquals("POST", recorded[1].method)
        PloiApi.disableTestDomain(token, 7, 1)
        assertEquals("DELETE", recorded[2].method)
        assertEquals("mellow-winds-034wtvtzb8.ploi.link", fetched.testDomain)
        assertEquals("https://mellow-winds-034wtvtzb8.ploi.link", fetched.fullTestDomain)
    }

    // ---- Suspend / resume ----

    @Test fun suspendSiteSendsOptionalReason() {
        installFakeClient()
        scriptedBody = siteJson(mapOf("status" to "suspended"))
        val site = PloiApi.suspendSite(token, 7, 1, reason = "Outstanding invoices")
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/sites/1/suspend", request.url)
        assertEquals("Outstanding invoices", JSONObject(request.body.orEmpty()).getString("reason"))
        assertEquals("suspended", site.status)
    }

    @Test fun suspendSiteOmitsBlankReasonAndResumePostsEmpty() {
        installFakeClient()
        scriptedBody = siteJson()
        PloiApi.suspendSite(token, 7, 1)
        assertFalse(JSONObject(recorded[0].body.orEmpty()).has("reason"))
        PloiApi.resumeSite(token, 7, 1)
        assertEquals("https://ploi.io/api/servers/7/sites/1/resume", recorded[1].url)
        assertEquals("POST", recorded[1].method)
        assertNull(recorded[1].body)
    }

    // ---- GET /api/servers/{server}/sites/laravel/horizon/{type} ----

    @Test fun parsesHorizonStats() {
        val stats = PloiApi.parseHorizonStatistics("stats", """{"data":{"failedJobs":1,"jobsPerMinute":0,
            "pausedMasters":0,"periods":{"failedJobs":10080,"recentJobs":60},"processes":1,
            "queueWithMaxRuntime":null,"queueWithMaxThroughput":null,"recentJobs":0,
            "status":"running","wait":{"redis:default":0}}}""") as HorizonStatistics.Stats
        assertEquals(1L, stats.failedJobs)
        assertEquals(1L, stats.processes)
        assertEquals("running", stats.status)
        assertEquals(mapOf("redis:default" to 0L), stats.wait)
    }

    @Test fun parsesHorizonWorkload() {
        val workload = PloiApi.parseHorizonStatistics("workload",
            """{"data":[{"name":"default","length":0,"wait":0,"processes":1}]}""") as HorizonStatistics.Workload
        assertEquals(listOf(HorizonQueue("default", 0, 0, 1)), workload.queues)
    }

    @Test fun parsesHorizonMasters() {
        val masters = PloiApi.parseHorizonStatistics("masters", """{"data":{"from-macbook-pro-Ry0f":{
            "name":"from-macbook-pro-Ry0f","pid":"104064","status":"running",
            "supervisors":[{"name":"sup-1"}]}}}""") as HorizonStatistics.Masters
        val master = masters.masters.single()
        assertEquals("from-macbook-pro-Ry0f", master.name)
        assertEquals("104064", master.pid)
        assertEquals(1, master.supervisors)
    }

    @Test fun parsesHorizonFailedJobs() {
        val failed = PloiApi.parseHorizonStatistics("failed", """{"data":{"jobs":[{"id":"abc",
            "connection":"redis","queue":"default","name":"App\\Jobs\\Fails","status":"failed",
            "exception":"ErrorException"}],"total":1}}""") as HorizonStatistics.Failed
        assertEquals(1L, failed.total)
        assertEquals("App\\Jobs\\Fails", failed.jobs.single().name)
        assertEquals("ErrorException", failed.jobs.single().exception)
    }

    @Test fun horizonStatisticsRequestsTypedPathAndValidatesType() {
        installFakeClient()
        scriptedBody = """{"data":{"status":"running","wait":{}}}"""
        PloiApi.horizonStatistics(token, 7, "stats")
        assertEquals("https://ploi.io/api/servers/7/sites/laravel/horizon/stats", recorded.single().url)
        HORIZON_TYPES.forEach { PloiApi.parseHorizonStatistics(it, horizonFixture(it)) }
        assertThrowsIAE { PloiApi.horizonStatistics(token, 7, "bogus") }
        assertThrowsIAE { PloiApi.parseHorizonStatistics("bogus", "{}") }
    }

    private fun horizonFixture(type: String): String = when (type) {
        "workload" -> """{"data":[]}"""
        "masters" -> """{"data":{}}"""
        "failed" -> """{"data":{"jobs":[],"total":0}}"""
        else -> """{"data":{"wait":{}}}"""
    }

    // ---- NGINX configuration ----

    @Test fun nginxConfigurationReadsAndUpdatesContent() {
        installFakeClient()
        scriptedBody = """{"content":"server { listen 80; }"}"""
        assertEquals("server { listen 80; }", PloiApi.nginxConfiguration(token, 7, 1))
        assertEquals("GET", recorded[0].method)
        assertEquals("https://ploi.io/api/servers/7/sites/1/nginx-configuration", recorded[0].url)
        scriptedBody = """{"status":"ok","message":"Don't forget to reload or restart NGINX after this call."}"""
        val message = PloiApi.updateNginxConfiguration(token, 7, 1, "server { listen 443; }")
        assertEquals("Don't forget to reload or restart NGINX after this call.", message)
        assertEquals("PATCH", recorded[1].method)
        assertEquals("server { listen 443; }", JSONObject(recorded[1].body.orEmpty()).getString("content"))
        assertThrowsIAE { PloiApi.updateNginxConfiguration(token, 7, 1, " ") }
    }

    // ---- Clone ----

    @Test fun cloneSitePostsTargetServerAndOptionalDomain() {
        installFakeClient()
        scriptedBody = """{"status":"ok","message":"Site test.com is being cloned"}"""
        assertEquals("Site test.com is being cloned", PloiApi.cloneSite(token, 7, 1, targetServerId = 2, domain = "test.com"))
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/sites/1/clone", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals(2L, body.getLong("clone_to_server"))
        assertEquals("test.com", body.getString("domain"))
    }

    @Test fun cloneSiteValidatesTargetAndDomain() {
        assertThrowsIAE { PloiApi.cloneSite(token, 7, 1, targetServerId = 0) }
        assertThrowsIAE { PloiApi.cloneSite(token, 7, 1, targetServerId = 2, domain = "bad domain") }
        installFakeClient()
        scriptedBody = """{"status":"ok","message":"Site is being cloned"}"""
        PloiApi.cloneSite(token, 7, 1, targetServerId = 2)
        assertFalse(JSONObject(recorded.single().body.orEmpty()).has("domain"))
    }

    // ---- PHP version ----

    @Test fun changeSitePhpVersionPostsValidatedVersion() {
        installFakeClient()
        scriptedBody = siteJson(mapOf("php_version" to "8.1"))
        val site = PloiApi.changeSitePhpVersion(token, 7, 1, "8.1")
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/sites/1/php-version", request.url)
        assertEquals("8.1", JSONObject(request.body.orEmpty()).getString("php_version"))
        assertEquals("8.1", site.phpVersion)
        assertThrowsIAE { PloiApi.changeSitePhpVersion(token, 7, 1, "none") }
        assertThrowsIAE { PloiApi.changeSitePhpVersion(token, 7, 1, "8.6") }
    }

    // ---- Permission reset ----

    @Test fun resetSitePermissionsPostsAndParsesSite() {
        installFakeClient()
        scriptedBody = siteJson()
        val site = PloiApi.resetSitePermissions(token, 7, 1)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/sites/1/permission-reset", request.url)
        assertNull(request.body)
        assertEquals(1L, site.id)
    }

    // ---- Error handling ----

    @Test fun malformedSitePayloadSurfacesTypedError() {
        installFakeClient()
        scriptedBody = """{"unexpected":true}"""
        try {
            PloiApi.createSite(token, 7, validSiteRequest())
            fail("Expected PloiMalformedPayloadException")
        } catch (expected: PloiMalformedPayloadException) {
            // typed error, not a raw JSONException
        }
    }

    @Test fun mutationsRejectInvalidResourceIds() {
        assertThrowsIAE { PloiApi.createSite(token, 0, validSiteRequest()) }
        assertThrowsIAE { PloiApi.updateSite(token, 7, 0, rootDomain = "a.com") }
        assertThrowsIAE { PloiApi.deleteSite(token, 7, -1) }
        assertThrowsIAE { PloiApi.siteLogs(token, 0, 1) }
        assertThrowsIAE { PloiApi.siteLog(token, 7, 1, 0) }
        assertThrowsIAE { PloiApi.testDomain(token, 7, 0) }
        assertThrowsIAE { PloiApi.enableTestDomain(token, 0, 1) }
        assertThrowsIAE { PloiApi.disableTestDomain(token, 7, -1) }
        assertThrowsIAE { PloiApi.suspendSite(token, 0, 1) }
        assertThrowsIAE { PloiApi.resumeSite(token, 7, 0) }
        assertThrowsIAE { PloiApi.horizonStatistics(token, 0, "stats") }
        assertThrowsIAE { PloiApi.nginxConfiguration(token, 0, 1) }
        assertThrowsIAE { PloiApi.updateNginxConfiguration(token, 7, 0, "x") }
        assertThrowsIAE { PloiApi.cloneSite(token, 0, 1, targetServerId = 2) }
        assertThrowsIAE { PloiApi.changeSitePhpVersion(token, 7, 0, "8.1") }
        assertThrowsIAE { PloiApi.resetSitePermissions(token, 7, 0) }
        assertThrowsIAE { PloiApi.updateRobotAccess(token, 0, 1, true) }
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
