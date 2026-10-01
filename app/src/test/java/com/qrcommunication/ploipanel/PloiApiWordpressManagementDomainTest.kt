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
 * `wordpress-management` domain (/api/servers/{server}/sites/{id}/wordpress…, 21 routes).
 */
class PloiApiWordpressManagementDomainTest {
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

    private fun okJson(message: String): String =
        JSONObject().put("status", "ok").put("message", message).toString()

    private fun lastRequest(): HttpRequest = recorded.last()

    // ---- POST …/wordpress/complete-install ----

    @Test fun completeInstallSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = okJson("WordPress install completed.")
        PloiApi.completeWordpressInstall(token, 1, 1, "My Site", "admin", "admin@example.com", "s3cret")
        val request = lastRequest()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/complete-install", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("My Site", body.getString("site_title"))
        assertEquals("admin", body.getString("admin_username"))
        assertEquals("admin@example.com", body.getString("admin_email"))
        assertEquals("s3cret", body.getString("admin_password"))
    }

    @Test fun completeInstallRejectsInvalidInput() {
        listOf(
            { PloiApi.completeWordpressInstall(token, 1, 1, "", "admin", "a@b.c", "x") },
            { PloiApi.completeWordpressInstall(token, 1, 1, "Site", "", "a@b.c", "x") },
            { PloiApi.completeWordpressInstall(token, 1, 1, "Site", "admin", "no-at", "x") },
            { PloiApi.completeWordpressInstall(token, 1, 1, "Site", "admin", "a@b.c", "") }
        ).forEach { invalid ->
            try {
                invalid()
                fail("Expected rejection")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // ---- GET …/wordpress/plugins & themes ----

    private fun extensionsJson(): String = JSONObject()
        .put(
            "data",
            JSONArray()
                .put(
                    JSONObject()
                        .put("name", "akismet")
                        .put("title", "Akismet Anti-spam: Spam Protection")
                        .put("status", "inactive")
                        .put("version", "5.6")
                        .put("update_version", "")
                        .put("auto_update", "off")
                )
                .put(
                    JSONObject()
                        .put("name", "wordpress-seo")
                        .put("title", "Yoast SEO")
                        .put("status", "active")
                        .put("version", "27.2")
                        .put("update_version", "")
                        .put("auto_update", "off")
                )
        )
        .toString()

    @Test fun parsesExtensionListWithDocumentedShape() {
        val extensions = PloiApi.parseWpExtensions(extensionsJson())
        assertEquals(2, extensions.size)
        assertEquals("akismet", extensions[0].name)
        assertEquals("Akismet Anti-spam: Spam Protection", extensions[0].title)
        assertEquals("inactive", extensions[0].status)
        assertEquals("5.6", extensions[0].version)
        assertEquals("", extensions[0].updateVersion)
        assertEquals("off", extensions[0].autoUpdate)
        assertEquals("wordpress-seo", extensions[1].name)
    }

    @Test fun wpPluginsHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = extensionsJson()
        PloiApi.wpPlugins(token, 1, 1)
        val request = lastRequest()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/plugins", request.url)
    }

    @Test fun wpPluginsOndemandAddsQueryParameter() {
        installFakeClient()
        scriptedBody = extensionsJson()
        PloiApi.wpPlugins(token, 1, 1, ondemand = true)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/plugins?ondemand=true", lastRequest().url)
    }

    @Test fun wpThemesHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = extensionsJson()
        PloiApi.wpThemes(token, 1, 1)
        val request = lastRequest()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/themes", request.url)
    }

    // ---- Plugin actions ----

    @Test fun activatePluginSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = okJson("Plugin activated.")
        val ack = PloiApi.activateWpPlugin(token, 1, 1, "akismet", ondemand = true)
        assertEquals("Plugin activated.", ack.message)
        val request = lastRequest()
        assertEquals("POST", request.method)
        // The docs place ondemand in the query string, not the JSON body.
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/plugins/activate?ondemand=true", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("akismet", body.getString("plugin"))
        assertEquals(1, body.length())
    }

    @Test fun deactivatePluginHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = okJson("Plugin deactivated.")
        PloiApi.deactivateWpPlugin(token, 1, 1, "akismet")
        val request = lastRequest()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/plugins/deactivate", request.url)
        assertEquals("akismet", JSONObject(request.body.orEmpty()).getString("plugin"))
    }

    @Test fun updatePluginHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = okJson("Plugin updated.")
        PloiApi.updateWpPlugin(token, 1, 1, "akismet")
        val request = lastRequest()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/plugins/update", request.url)
    }

    @Test fun installPluginSendsActivateFlag() {
        installFakeClient()
        scriptedBody = okJson("Plugin installed.")
        PloiApi.installWpPlugin(token, 1, 1, "akismet", activate = true)
        val request = lastRequest()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/plugins/install", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("akismet", body.getString("plugin"))
        assertTrue(body.getBoolean("activate"))
    }

    @Test fun deletePluginUsesDeleteWithDocumentedBody() {
        installFakeClient()
        scriptedBody = okJson("Plugin deleted.")
        PloiApi.deleteWpPlugin(token, 1, 1, "akismet")
        val request = lastRequest()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/plugins/delete", request.url)
        assertEquals("akismet", JSONObject(request.body.orEmpty()).getString("plugin"))
    }

    // ---- Theme actions ----

    @Test fun activateThemeSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = okJson("Theme activated.")
        PloiApi.activateWpTheme(token, 1, 1, "twentytwentyfive")
        val request = lastRequest()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/themes/activate", request.url)
        assertEquals("twentytwentyfive", JSONObject(request.body.orEmpty()).getString("theme"))
    }

    @Test fun updateThemeHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = okJson("Theme updated.")
        PloiApi.updateWpTheme(token, 1, 1, "twentytwentyfive")
        val request = lastRequest()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/themes/update", request.url)
    }

    @Test fun installThemeSendsActivateFlag() {
        installFakeClient()
        scriptedBody = okJson("Theme installed.")
        PloiApi.installWpTheme(token, 1, 1, "twentytwentyfive", activate = true)
        val request = lastRequest()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/themes/install", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("twentytwentyfive", body.getString("theme"))
        assertTrue(body.getBoolean("activate"))
    }

    @Test fun deleteThemeUsesDeleteWithDocumentedBody() {
        installFakeClient()
        scriptedBody = okJson("Theme deleted.")
        PloiApi.deleteWpTheme(token, 1, 1, "twentytwentyfour")
        val request = lastRequest()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/themes/delete", request.url)
        assertEquals("twentytwentyfour", JSONObject(request.body.orEmpty()).getString("theme"))
    }

    @Test fun wpSlugIsValidated() {
        listOf("bad slug", "bad/slug", "").forEach { invalid ->
            try {
                PloiApi.activateWpPlugin(token, 1, 1, invalid)
                fail("Expected rejection for '$invalid'")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // ---- POST …/wordpress/toggle-xmlrpc ----

    @Test fun toggleXmlrpcSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = okJson("XML-RPC has been blocked.")
        val ack = PloiApi.toggleWpXmlrpc(token, 1, 1, block = true)
        assertEquals("XML-RPC has been blocked.", ack.message)
        val request = lastRequest()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/toggle-xmlrpc", request.url)
        assertTrue(JSONObject(request.body.orEmpty()).getBoolean("block"))
    }

    // ---- POST …/wordpress/wp-cli/run ----

    @Test fun runWpCliSendsDocumentedBodyAndParsesOutput() {
        installFakeClient()
        scriptedBody = JSONObject().put("output", "6.7.2\n").toString()
        val output = PloiApi.runWpCli(token, 1, 1, "core version")
        assertEquals("6.7.2\n", output)
        val request = lastRequest()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/wp-cli/run", request.url)
        assertEquals("core version", JSONObject(request.body.orEmpty()).getString("command"))
    }

    @Test fun runWpCliRejectsBlankCommand() {
        try {
            PloiApi.runWpCli(token, 1, 1, " ")
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    // ---- POST …/wordpress/search-replace ----

    @Test fun searchReplaceSendsDocumentedBodyAndParsesOutput() {
        installFakeClient()
        scriptedBody = JSONObject().put("output", "+---+\n| 12 replacements |\n").toString()
        val output = PloiApi.searchReplaceWp(token, 1, 1, "http://old.com", "https://new.com", dryRun = true)
        assertTrue(output.contains("12 replacements"))
        val request = lastRequest()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/search-replace", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("http://old.com", body.getString("search"))
        assertEquals("https://new.com", body.getString("replace"))
        assertTrue(body.getBoolean("dry_run"))
    }

    // ---- Repositories ----

    private fun repositoriesJson(): String = JSONObject()
        .put(
            "data",
            JSONArray().put(
                JSONObject()
                    .put("id", 1)
                    .put("user", "acme")
                    .put("name", "custom-theme")
                    .put("branch", "main")
                    .put("type", "github")
                    .put("target_directory", "public/wp-content/themes/custom-theme")
                    .put("deploy_script", "cd /home/ploi/domain.com\ngit pull origin main")
                    .put("status", "installed")
            )
        )
        .toString()

    @Test fun parsesRepositoryListWithDocumentedShape() {
        val repositories = PloiApi.parseWpRepositories(repositoriesJson())
        val repository = repositories.single()
        assertEquals(1L, repository.id)
        assertEquals("acme", repository.user)
        assertEquals("custom-theme", repository.name)
        assertEquals("main", repository.branch)
        assertEquals("github", repository.type)
        assertEquals("public/wp-content/themes/custom-theme", repository.targetDirectory)
        assertEquals("cd /home/ploi/domain.com\ngit pull origin main", repository.deployScript)
        assertEquals("installed", repository.status)
    }

    @Test fun wpRepositoriesHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = repositoriesJson()
        PloiApi.wpRepositories(token, 1, 1)
        val request = lastRequest()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/repositories", request.url)
    }

    @Test fun installWpRepositorySendsDocumentedBody() {
        installFakeClient()
        scriptedBody = okJson("Repository is being installed.")
        PloiApi.installWpRepository(
            token, 1, 1, "github", "acme/custom-theme", "main", "public/wp-content/themes/custom-theme",
            sourceProviderId = 5
        )
        val request = lastRequest()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/repositories", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("github", body.getString("provider"))
        assertEquals("acme/custom-theme", body.getString("name"))
        assertEquals("main", body.getString("branch"))
        assertEquals("public/wp-content/themes/custom-theme", body.getString("target_directory"))
        assertEquals(5L, body.getLong("source_provider_id"))
    }

    @Test fun installWpRepositoryRejectsInvalidName() {
        try {
            PloiApi.installWpRepository(token, 1, 1, "github", "no-slash", "main", "target")
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun updateWpRepositorySendsDocumentedBody() {
        installFakeClient()
        scriptedBody = okJson("Repository updated.")
        PloiApi.updateWpRepository(token, 1, 1, 7, "acme", "custom-theme", "develop")
        val request = lastRequest()
        assertEquals("PATCH", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/repositories/7", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("acme", body.getString("user"))
        assertEquals("custom-theme", body.getString("name"))
        assertEquals("develop", body.getString("branch"))
    }

    @Test fun updateWpRepositoryDeployScriptSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = okJson("Deploy script saved.")
        PloiApi.updateWpRepositoryDeployScript(token, 1, 1, 7, "git pull origin main")
        val request = lastRequest()
        assertEquals("PATCH", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/repositories/7/deploy-script", request.url)
        assertEquals("git pull origin main", JSONObject(request.body.orEmpty()).getString("deploy_script"))
    }

    @Test fun deployWpRepositoryHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = okJson("Deployment started.")
        PloiApi.deployWpRepository(token, 1, 1, 7)
        val request = lastRequest()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/repositories/7/deploy", request.url)
    }

    @Test fun deployAllWpRepositoriesHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = okJson("Deploying 3 repositories.")
        PloiApi.deployAllWpRepositories(token, 1, 1)
        val request = lastRequest()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/repositories/deploy-all", request.url)
    }

    @Test fun deleteWpRepositoryHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = okJson("Repository is being removed.")
        val ack = PloiApi.deleteWpRepository(token, 1, 1, 7)
        assertEquals("Repository is being removed.", ack.message)
        val request = lastRequest()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/wordpress/repositories/7", request.url)
    }
}
