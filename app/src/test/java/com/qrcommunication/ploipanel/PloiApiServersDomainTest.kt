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

/** Parsing, validation and wire-level coverage for every documented route of the `servers` domain. */
class PloiApiServersDomainTest {
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

    private fun detailJson(extra: String = "") = """{"data":{
        "id":1,"status":"active","status_id":2,"type":"server","database_type":"mysql",
        "name":"awesome-server","ip_address":"1.1.1.1","internal_ip":null,"ssh_port":22,
        "reboot_required":false,"php_version":8.2,"php_cli_version":"8.2","mysql_version":5.7,
        "sites_count":4,"monitoring":true,"opcache":false,"installed_php_versions":["8.2"],
        "updates":{"packages":3,"security":1},"description":null,
        "provider":{"id":1,"name":"Katapult"},
        "created_at":"2023-08-13 19:23:58","created_human":"1 week ago","uptime_human":null
        ${if (extra.isNotEmpty()) ",$extra" else ""}}}"""

    // ---- GET /api/servers/{id} ----

    @Test fun parsesServerDetailWithNumericAndStringVersions() {
        val detail = PloiApi.parseServerDetail(detailJson())
        assertEquals(1L, detail.id)
        assertEquals("awesome-server", detail.name)
        assertEquals("8.2", detail.phpVersion)
        assertEquals("8.2", detail.phpCliVersion)
        assertEquals("5.7", detail.mysqlVersion)
        assertEquals("", detail.internalIp)
        assertEquals(22, detail.sshPort)
        assertEquals(4, detail.sitesCount)
        assertTrue(detail.monitoring)
        assertEquals(listOf("8.2"), detail.installedPhpVersions)
        assertEquals(3, detail.updatesPackages)
        assertEquals(1, detail.updatesSecurity)
        assertEquals("Katapult", detail.providerName)
        assertEquals("", detail.uptimeHuman)
    }

    @Test fun serverDetailToleratesMissingOptionalFields() {
        val detail = PloiApi.parseServerDetail("""{"data":{"id":2,"name":"minimal"}}""")
        assertEquals(2L, detail.id)
        assertEquals("", detail.type)
        assertEquals(0, detail.sshPort)
        assertFalse(detail.rebootRequired)
        assertTrue(detail.installedPhpVersions.isEmpty())
        assertEquals(0, detail.updatesPackages)
    }

    @Test fun serverRequestsDetailPath() {
        installFakeClient()
        scriptedBody = detailJson()
        PloiApi.server(token, 42)
        assertEquals("https://ploi.io/api/servers/42", recorded.single().url)
        assertEquals("GET", recorded.single().method)
    }

    // ---- POST /api/servers ----

    private fun validRequest() = CreateServerRequest(
        plan = "1xCPU-1GB", region = "nl-ams1", credential = 1, type = "server",
        databaseType = "mysql", webserverType = "nginx", phpVersion = "8.4"
    )

    @Test fun createServerPostsRequiredFieldsOnly() {
        installFakeClient()
        scriptedBody = detailJson()
        PloiApi.createServer(token, validRequest())
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("1xCPU-1GB", body.getString("plan"))
        assertEquals("nl-ams1", body.getString("region"))
        assertEquals(1L, body.getLong("credential"))
        assertEquals("mysql", body.getString("database_type"))
        assertEquals("nginx", body.getString("webserver_type"))
        assertEquals("8.4", body.getString("php_version"))
        assertFalse(body.has("name"))
        assertFalse(body.has("install_monitoring"))
        assertFalse(body.has("webhook_url"))
    }

    @Test fun createServerSerializesOptionalFields() {
        val request = validRequest().copy(
            name = "awesome-server", description = "note", installMonitoring = true,
            ipType = "ipv4-ipv6", webhookUrl = "https://example.com/hook"
        )
        val body = JSONObject(request.toJson())
        assertEquals("awesome-server", body.getString("name"))
        assertEquals("note", body.getString("description"))
        assertTrue(body.getBoolean("install_monitoring"))
        assertEquals("ipv4-ipv6", body.getString("ip_type"))
        assertEquals("https://example.com/hook", body.getString("webhook_url"))
    }

    @Test fun createServerRejectsInvalidValues() {
        assertThrowsIAE { validRequest().copy(plan = " ") }
        assertThrowsIAE { validRequest().copy(region = "") }
        assertThrowsIAE { validRequest().copy(credential = 0) }
        assertThrowsIAE { validRequest().copy(type = "storage-server") }
        assertThrowsIAE { validRequest().copy(databaseType = "mysql80") }
        assertThrowsIAE { validRequest().copy(webserverType = "apache") }
        assertThrowsIAE { validRequest().copy(phpVersion = "8.6") }
        assertThrowsIAE { validRequest().copy(osType = "ubuntu-22-04-lts") }
        assertThrowsIAE { validRequest().copy(ipType = "ipv5") }
        assertThrowsIAE { validRequest().copy(name = "bad name!") }
        assertThrowsIAE { validRequest().copy(webhookUrl = "http://example.com") }
    }

    // ---- POST /api/servers/custom and /start ----

    private fun validCustomRequest() = CreateCustomServerRequest(
        type = "server", ip = "12.34.56.78", sshPort = 22, databaseType = "mysql", phpVersion = "8.4"
    )

    @Test fun createCustomServerPostsAndParsesSetupPayload() {
        installFakeClient()
        scriptedBody = """{"id":1,"name":"awesome-server","public_key":"ssh-rsa AAAA ploi-worker@ploi.io",
            "ssh_command":"mkdir -p /root/.ssh && echo ssh-rsa AAAA >> /root/.ssh/authorized_keys",
            "start_installation_url":"https://ploi.io/api/servers/custom/1/start","message":"Server has been created!"}"""
        val creation = PloiApi.createCustomServer(token, validCustomRequest())
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/custom", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("12.34.56.78", body.getString("ip"))
        assertEquals(22, body.getInt("ssh_port"))
        assertEquals(1L, creation.id)
        assertTrue(creation.publicKey.startsWith("ssh-rsa"))
        assertTrue(creation.sshCommand.contains("authorized_keys"))
        assertEquals("https://ploi.io/api/servers/custom/1/start", creation.startInstallationUrl)
    }

    @Test fun createCustomServerAcceptsStorageTypeAndIpv6() {
        val request = validCustomRequest().copy(type = "storage-server", ip = "2001:db8::1", phpVersion = "none")
        val body = JSONObject(request.toJson())
        assertEquals("storage-server", body.getString("type"))
        assertEquals("2001:db8::1", body.getString("ip"))
    }

    @Test fun createCustomServerRejectsInvalidValues() {
        assertThrowsIAE { validCustomRequest().copy(type = "loadbalancer") }
        assertThrowsIAE { validCustomRequest().copy(ip = "999.1.1.1") }
        assertThrowsIAE { validCustomRequest().copy(ip = "not-an-ip") }
        assertThrowsIAE { validCustomRequest().copy(sshPort = 0) }
        assertThrowsIAE { validCustomRequest().copy(sshPort = 70_000) }
        assertThrowsIAE { validCustomRequest().copy(name = "bad name!") }
    }

    @Test fun startCustomServerInstallationPostsOptions() {
        installFakeClient()
        scriptedBody = """{"message":"Server installation has started."}"""
        val message = PloiApi.startCustomServerInstallation(token, 1, installMonitoring = true, webhookUrl = "https://example.com/hook")
        assertEquals("Server installation has started.", message)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/custom/1/start", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertTrue(body.getBoolean("install_monitoring"))
        assertEquals("https://example.com/hook", body.getString("webhook_url"))
    }

    @Test fun startCustomServerInstallationRejectsPlainHttpWebhook() {
        assertThrowsIAE { PloiApi.startCustomServerInstallation(token, 1, webhookUrl = "http://example.com") }
    }

    // ---- PATCH /api/servers/{server} ----

    @Test fun updateServerPatchesNameOnlyByDefault() {
        installFakeClient()
        scriptedBody = detailJson()
        PloiApi.updateServer(token, 7, "new-name")
        val request = recorded.single()
        assertEquals("PATCH", request.method)
        assertEquals("https://ploi.io/api/servers/7", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("new-name", body.getString("name"))
        assertFalse(body.has("ip"))
    }

    @Test fun updateServerSendsIpWithSshPort() {
        installFakeClient()
        scriptedBody = detailJson()
        PloiApi.updateServer(token, 7, "new-name", ip = "1.1.1.1", sshPort = 2222)
        val body = JSONObject(recorded.single().body.orEmpty())
        assertEquals("1.1.1.1", body.getString("ip"))
        assertEquals(2222, body.getInt("ssh_port"))
    }

    @Test fun updateServerRequiresSshPortWithIpAndValidName() {
        assertThrowsIAE { PloiApi.updateServer(token, 7, "new-name", ip = "1.1.1.1") }
        assertThrowsIAE { PloiApi.updateServer(token, 7, " ") }
        assertThrowsIAE { PloiApi.updateServer(token, 7, "bad name!") }
        assertThrowsIAE { PloiApi.updateServer(token, 7, "new-name", ip = "junk", sshPort = 22) }
        assertThrowsIAE { PloiApi.updateServer(token, 7, "new-name", ip = "1.1.1.1", sshPort = 0) }
    }

    // ---- DELETE /api/servers/{server} ----

    @Test fun deleteServerUsesDeleteVerbAndParsesDestroyingState() {
        installFakeClient()
        scriptedBody = """{"data":{"id":1,"status":"destroying","type":"server","name":"wandering-silence",
            "ip_address":"192.168.56.101","php_version":7.2,"mysql_version":5.7,"sites_count":0,
            "monitoring":false,"created_at":"2019-01-01 09:00:00"}}"""
        val detail = PloiApi.deleteServer(token, 1)
        assertEquals("destroying", detail.status)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1", request.url)
        assertNull(request.body)
    }

    // ---- POST /api/servers/{id}/restart ----

    @Test fun restartServerPostsAndParsesMessage() {
        installFakeClient()
        scriptedBody = """{"message":"Server is now rebooting"}"""
        assertEquals("Server is now rebooting", PloiApi.restartServer(token, 9))
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/9/restart", request.url)
    }

    // ---- GET /api/servers/{id}/logs ----

    @Test fun parsesServerLogsWithNullableSiteId() {
        val page = PloiApi.parseServerLogs("""{"data":[
            {"description":"Delete database user (database_user)","content":"","site_id":null,"server_id":74,"created_at":"2018-11-12 13:08:48"},
            {"description":"Add user to database ploi","content":"grant","site_id":12,"server_id":74,"created_at":"2018-11-12 13:08:33"}],
            "meta":{"current_page":1,"last_page":7}}""")
        assertTrue(page.hasNext)
        assertEquals(2, page.logs.size)
        assertNull(page.logs[0].siteId)
        assertEquals(12L, page.logs[1].siteId)
        assertEquals("Delete database user (database_user)", page.logs[0].description)
        assertEquals(74L, page.logs[0].serverId)
    }

    @Test fun serverLogsRequestsPaginatedPath() {
        installFakeClient()
        scriptedBody = """{"data":[],"meta":{"current_page":2,"last_page":2}}"""
        PloiApi.serverLogs(token, 74, page = 2, perPage = 50)
        assertEquals("https://ploi.io/api/servers/74/logs?page=2&per_page=50", recorded.single().url)
        assertThrowsIAE { PloiApi.serverLogs(token, 74, perPage = 51) }
    }

    // ---- GET /api/servers/monitored ----

    @Test fun parsesMonitoredServersWithStatistics() {
        val servers = PloiApi.parseMonitoredServers("""{"data":[{"id":1,"name":"colossal-brook","ip":"1.2.3.4",
            "url":"https://ploi.io/panel/servers/1","statistics":[
              {"cpu":"0","ram":"0","disk":"0","load_average":"0","date":"2022-12-06 14:34:05"},
              {"cpu":"0.8","ram":"36.32","disk":"86","load_average":"0","date":"2022-12-06 14:35:06"}]}]}""")
        val server = servers.single()
        assertEquals("colossal-brook", server.name)
        assertEquals(2, server.statistics.size)
        assertEquals("36.32", server.statistics.last().ram)
        assertEquals("2022-12-06 14:35:06", server.statistics.last().date)
    }

    @Test fun monitoredServersRequestsPath() {
        installFakeClient()
        scriptedBody = """{"data":[]}"""
        PloiApi.monitoredServers(token)
        assertEquals("https://ploi.io/api/servers/monitored", recorded.single().url)
        assertEquals("GET", recorded.single().method)
    }

    // ---- Error handling ----

    @Test fun malformedDetailPayloadSurfacesTypedError() {
        installFakeClient()
        scriptedBody = """{"unexpected":true}"""
        try {
            PloiApi.server(token, 1)
            fail("Expected PloiMalformedPayloadException")
        } catch (expected: PloiMalformedPayloadException) {
            // typed error, not a raw JSONException
        }
    }

    @Test fun mutationRejectsInvalidResourceIds() {
        assertThrowsIAE { PloiApi.server(token, 0) }
        assertThrowsIAE { PloiApi.deleteServer(token, -1) }
        assertThrowsIAE { PloiApi.restartServer(token, 0) }
        assertThrowsIAE { PloiApi.serverLogs(token, 0) }
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
