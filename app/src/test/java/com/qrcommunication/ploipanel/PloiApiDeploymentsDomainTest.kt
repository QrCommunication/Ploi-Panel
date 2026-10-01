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

/**
 * Parsing, validation and wire-level coverage for every documented route of the
 * `deployments`, `repositories` and `environment` domains.
 */
class PloiApiDeploymentsDomainTest {
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

    private fun repositoryJson(
        repository: JSONObject? = JSONObject()
            .put("branch", "main").put("user", "ploi-deploy").put("name", "roadmap").put("provider", "github"),
        quickDeploy: Boolean = true
    ): String {
        val data = JSONObject()
            .put("id", 1).put("domain", "deployment.com").put("web_directory", "/public")
            .put("wordpress", false).put("laravel", false).put("project_root", "/")
            .put("last_deploy_at", "2022-11-08 14:07:14").put("quick_deploy", quickDeploy)
            .put("created_at", "2022-11-08 13:45:21")
        if (repository != null) data.put("repository", repository)
        return JSONObject().put("data", data).toString()
    }

    private fun siteJson(): String = JSONObject().put("data", JSONObject()
        .put("id", 1).put("status", "active").put("server_id", 1)
        .put("domain", "example.com").put("test_domain", JSONObject.NULL)
        .put("deploy_script", "cd /home/ploi/example.com").put("web_directory", "/public")
        .put("project_type", JSONObject.NULL).put("project_root", "/")
        .put("last_deploy_at", "2021-08-24 14:38:00").put("system_user", "ploi")
        .put("php_version", "8.0").put("health_url", JSONObject.NULL)
        .put("has_repository", true).put("quick_deploy", true)
        .put("zero_downtime_deployment", false).put("fastcgi_cache", false)
        .put("created_at", "2021-08-24 16:37:31")).toString()

    // ---- GET/PATCH /api/servers/{server}/sites/{id}/deploy/script ----

    @Test fun deployScriptReadsDocumentedRoute() {
        installFakeClient()
        scriptedBody = """{"deploy_script":"cd /home/ploi/domain.com\ngit pull origin main"}"""
        assertEquals("cd /home/ploi/domain.com\ngit pull origin main", PloiApi.deployScript(token, 7, 1))
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/7/sites/1/deploy/script", request.url)
    }

    @Test fun updateDeployScriptPatchesValidatedContent() {
        installFakeClient()
        scriptedBody = """{"message":"Deploy script has been updated"}"""
        val message = PloiApi.updateDeployScript(token, 7, 1, "cd /home/ploi/domain1.com")
        assertEquals("Deploy script has been updated", message)
        val request = recorded.single()
        assertEquals("PATCH", request.method)
        assertEquals("https://ploi.io/api/servers/7/sites/1/deploy/script", request.url)
        assertEquals("cd /home/ploi/domain1.com", JSONObject(request.body.orEmpty()).getString("deploy_script"))
    }

    @Test fun updateDeployScriptEnforcesDocumentedLength() {
        assertThrowsIAE { PloiApi.updateDeployScript(token, 7, 1, " ") }
        assertThrowsIAE { PloiApi.updateDeployScript(token, 7, 1, "a".repeat(DEPLOY_SCRIPT_MAX_LENGTH + 1)) }
        installFakeClient()
        scriptedBody = """{"message":"ok"}"""
        PloiApi.updateDeployScript(token, 7, 1, "a".repeat(DEPLOY_SCRIPT_MAX_LENGTH))
        assertEquals(1, recorded.size)
    }

    // ---- POST /api/servers/{server}/sites/{id}/deploy ----

    @Test fun deploySitePostsEmptyBodyByDefault() {
        installFakeClient()
        scriptedBody = """{"message":"Deployment has been started"}"""
        assertEquals("Deployment has been started", PloiApi.deploySite(token, 7, 1))
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/sites/1/deploy", request.url)
        assertFalse(JSONObject(request.body.orEmpty()).has("scheduled"))
        assertFalse(JSONObject(request.body.orEmpty()).has("variables"))
    }

    @Test fun deploySiteSerializesScheduledAndVariables() {
        installFakeClient()
        scriptedBody = """{"message":"Deployment has been started"}"""
        PloiApi.deploySite(token, 7, 1, scheduled = "2026-01-01 10:00", variables = mapOf("branch" to "main"))
        val body = JSONObject(recorded.single().body.orEmpty())
        assertEquals("2026-01-01 10:00", body.getString("scheduled"))
        assertEquals("main", body.getJSONObject("variables").getString("branch"))
    }

    @Test fun deploySiteValidatesScheduledFormatAndVariableNames() {
        assertThrowsIAE { PloiApi.deploySite(token, 7, 1, scheduled = "tomorrow") }
        assertThrowsIAE { PloiApi.deploySite(token, 7, 1, scheduled = "2026-01-01T10:00") }
        assertThrowsIAE { PloiApi.deploySite(token, 7, 1, variables = mapOf(" " to "x")) }
    }

    // ---- POST /api/servers/{server}/sites/{id}/deploy-to-production ----

    @Test fun deployToProductionPostsDocumentedRoute() {
        installFakeClient()
        scriptedBody = """{"message":"Deployment to production has been started"}"""
        assertEquals("Deployment to production has been started", PloiApi.deployToProduction(token, 7, 1))
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/sites/1/deploy-to-production", request.url)
    }

    // ---- GET/POST/DELETE /api/servers/{server}/sites/{site}/repository ----

    @Test fun parsesRepositoryWithNestedDetails() {
        val repository = PloiApi.parseSiteRepository(repositoryJson())
        assertEquals(1L, repository.id)
        assertEquals("deployment.com", repository.domain)
        assertEquals("/public", repository.webDirectory)
        assertFalse(repository.wordpress)
        assertFalse(repository.laravel)
        assertEquals("/", repository.projectRoot)
        assertEquals("2022-11-08 14:07:14", repository.lastDeployAt)
        assertTrue(repository.quickDeploy)
        assertEquals("main", repository.branch)
        assertEquals("ploi-deploy", repository.repositoryUser)
        assertEquals("roadmap", repository.repositoryName)
        assertEquals("github", repository.provider)
    }

    @Test fun parsesRepositoryWithoutNestedObject() {
        val repository = PloiApi.parseSiteRepository(repositoryJson(repository = null, quickDeploy = false))
        assertEquals("", repository.branch)
        assertEquals("", repository.provider)
        assertFalse(repository.quickDeploy)
    }

    @Test fun repositoryEndpointsHitDocumentedRoutes() {
        installFakeClient()
        scriptedBody = repositoryJson()
        PloiApi.repository(token, 7, 1)
        assertEquals("GET", recorded[0].method)
        assertEquals("https://ploi.io/api/servers/7/sites/1/repository", recorded[0].url)
        PloiApi.deleteRepository(token, 7, 1)
        assertEquals("DELETE", recorded[1].method)
        assertEquals("https://ploi.io/api/servers/7/sites/1/repository", recorded[1].url)
        assertNull(recorded[1].body)
    }

    @Test fun installRepositoryPostsRequiredFieldsOnly() {
        installFakeClient()
        scriptedBody = repositoryJson()
        PloiApi.installRepository(token, 7, 1, InstallRepositoryRequest("github", "main", "user/repository"))
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/sites/1/repository", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("github", body.getString("provider"))
        assertEquals("main", body.getString("branch"))
        assertEquals("user/repository", body.getString("name"))
        assertFalse(body.has("source_provider_id"))
        assertFalse(body.has("install_composer"))
    }

    @Test fun installRepositorySerializesOptionalFields() {
        val body = JSONObject(
            InstallRepositoryRequest("github", "main", "user/repo", sourceProviderId = 3, installComposer = true).toJson()
        )
        assertEquals(3L, body.getLong("source_provider_id"))
        assertTrue(body.getBoolean("install_composer"))
    }

    @Test fun installRepositoryRejectsInvalidValues() {
        assertThrowsIAE { InstallRepositoryRequest("bitbucket2", "main", "user/repo") }
        assertThrowsIAE { InstallRepositoryRequest("github", " ", "user/repo") }
        assertThrowsIAE { InstallRepositoryRequest("github", "main", " ") }
        assertThrowsIAE { InstallRepositoryRequest("custom", "main", "https://example.com/repo") }
        assertThrowsIAE { InstallRepositoryRequest("github", "main", "user/repo", sourceProviderId = 0) }
        // custom provider accepts a GIT URL ending with .git
        InstallRepositoryRequest("custom", "main", "https://example.com/repo.git")
    }

    // ---- POST …/repository/custom-deployments ----

    @Test fun enableCustomDeploymentsPostsOptionalScript() {
        installFakeClient()
        scriptedBody = repositoryJson(
            repository = JSONObject().put("branch", "").put("user", "").put("provider", "none")
        )
        val without = PloiApi.enableCustomDeployments(token, 7, 1)
        assertEquals("none", without.provider)
        assertFalse(JSONObject(recorded[0].body.orEmpty()).has("script"))
        assertEquals("https://ploi.io/api/servers/7/sites/1/repository/custom-deployments", recorded[0].url)
        PloiApi.enableCustomDeployments(token, 7, 1, script = "git clone https://github.com/laravel/laravel.git")
        assertEquals(
            "git clone https://github.com/laravel/laravel.git",
            JSONObject(recorded[1].body.orEmpty()).getString("script")
        )
    }

    // ---- POST …/repository/quick-deploy ----

    @Test fun toggleQuickDeployParsesSiteAndMessage() {
        installFakeClient()
        scriptedBody = JSONObject(siteJson()).put("message", "Quick deploy has been enabled").toString()
        val result = PloiApi.toggleQuickDeploy(token, 7, 1)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/sites/1/repository/quick-deploy", request.url)
        assertEquals("example.com", result.site.domain)
        assertTrue(result.site.quickDeploy)
        assertEquals("Quick deploy has been enabled", result.message)
    }

    // ---- GET/PATCH /api/servers/{server}/sites/{id}/env ----

    @Test fun environmentFileReadsContent() {
        installFakeClient()
        scriptedBody = """{"content":"APP_NAME=Laravel\nAPP_ENV=production"}"""
        assertEquals("APP_NAME=Laravel\nAPP_ENV=production", PloiApi.environmentFile(token, 7, 1))
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/7/sites/1/env", request.url)
    }

    @Test fun updateEnvironmentFilePatchesValidatedContent() {
        installFakeClient()
        scriptedBody = """{"message":"Environment file has been updated"}"""
        val message = PloiApi.updateEnvironmentFile(token, 7, 1, "APP_URL=https://ploi.io")
        assertEquals("Environment file has been updated", message)
        val request = recorded.single()
        assertEquals("PATCH", request.method)
        assertEquals("https://ploi.io/api/servers/7/sites/1/env", request.url)
        assertEquals("APP_URL=https://ploi.io", JSONObject(request.body.orEmpty()).getString("content"))
        assertThrowsIAE { PloiApi.updateEnvironmentFile(token, 7, 1, "x") }
        assertThrowsIAE { PloiApi.updateEnvironmentFile(token, 7, 1, "") }
    }

    // ---- Error handling ----

    @Test fun malformedRepositoryPayloadSurfacesTypedError() {
        installFakeClient()
        scriptedBody = """{"unexpected":true}"""
        try {
            PloiApi.repository(token, 7, 1)
            fail("Expected PloiMalformedPayloadException")
        } catch (expected: PloiMalformedPayloadException) {
            // typed error, not a raw JSONException
        }
    }

    @Test fun mutationsRejectInvalidResourceIds() {
        assertThrowsIAE { PloiApi.deployScript(token, 0, 1) }
        assertThrowsIAE { PloiApi.deployScript(token, 7, 0) }
        assertThrowsIAE { PloiApi.updateDeployScript(token, 0, 1, "x") }
        assertThrowsIAE { PloiApi.deploySite(token, 7, -1) }
        assertThrowsIAE { PloiApi.deployToProduction(token, 0, 1) }
        assertThrowsIAE { PloiApi.repository(token, 7, 0) }
        assertThrowsIAE {
            PloiApi.installRepository(token, 0, 1, InstallRepositoryRequest("github", "main", "u/r"))
        }
        assertThrowsIAE { PloiApi.enableCustomDeployments(token, 7, 0) }
        assertThrowsIAE { PloiApi.deleteRepository(token, 0, 1) }
        assertThrowsIAE { PloiApi.toggleQuickDeploy(token, 7, 0) }
        assertThrowsIAE { PloiApi.environmentFile(token, 0, 1) }
        assertThrowsIAE { PloiApi.updateEnvironmentFile(token, 7, 0, "xx") }
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
