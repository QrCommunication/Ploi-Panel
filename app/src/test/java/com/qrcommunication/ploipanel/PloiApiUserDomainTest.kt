package com.qrcommunication.ploipanel

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Parsing and wire-level coverage for every documented route of the `user` domain. */
class PloiApiUserDomainTest {
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

    @Test fun parsesUserInformationWithNullables() {
        val info = PloiApi.parseUser("""{"data":{
          "avatar":"https://www.gravatar.com/avatar/hash?s=300","name":"John Doe","email":"john@doe.com",
          "billing_details":null,"country":"en","timezone":"UTC","created_at":"2018-03-01 00:00:00",
          "plan":"Unlimited","plan_expires_at":"2030-01-01 00:00:00"}}""")
        assertEquals("John Doe", info.name)
        assertEquals("john@doe.com", info.email)
        assertEquals("Unlimited", info.plan)
        assertEquals("2030-01-01 00:00:00", info.planExpiresAt)
        assertEquals("", info.billingDetails)
    }

    @Test fun userRequestsUserPath() {
        installFakeClient()
        scriptedBody = """{"data":{"name":"A","email":"a@b.c","plan":"Pro"}}"""
        PloiApi.user(token)
        assertEquals("https://ploi.io/api/user", recorded.single().url)
    }

    @Test fun parsesBackupConfigurationsPage() {
        val page = PloiApi.parseBackupConfigurations("""{
          "data":[{"id":1,"label":"Dropbox","type":"dropbox","humanType":"Dropbox",
            "created_at":"2023-09-18T12:29:04.000000Z"}],
          "meta":{"current_page":1,"last_page":1}}""")
        val configuration = page.configurations.single()
        assertEquals(1L, configuration.id)
        assertEquals("dropbox", configuration.type)
        assertEquals("Dropbox", configuration.humanType)
        assertFalse(page.hasNext)
    }

    @Test fun parsesBackupConfigurationDetailWithNullLabel() {
        val configuration = PloiApi.parseBackupConfiguration(
            """{"data":{"id":2,"label":null,"type":"s3","humanType":"S3","created_at":"2024-01-01 00:00:00"}}"""
        )
        assertEquals(2L, configuration.id)
        assertEquals("", configuration.label)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedBackupConfigurationPagination() {
        PloiApi.parseBackupConfigurations("""{"data":[],"meta":{"current_page":3,"last_page":2}}""")
    }

    @Test fun backupConfigurationRequestsDocumentedPaths() {
        installFakeClient()
        scriptedBody = """{"data":[],"meta":{"current_page":1,"last_page":1}}"""
        PloiApi.backupConfigurations(token, page = 2, perPage = 50)
        scriptedBody = """{"data":{"id":9,"label":null,"type":"dropbox","humanType":"Dropbox","created_at":""}}"""
        PloiApi.backupConfiguration(token, 9)
        assertEquals("https://ploi.io/api/user/backup-configurations?page=2&per_page=50", recorded[0].url)
        assertEquals("https://ploi.io/api/user/backup-configurations/9", recorded[1].url)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidBackupConfigurationId() {
        PloiApi.backupConfiguration(token, 0)
    }

    @Test fun parsesNotificationChannelsWithoutSecrets() {
        val page = PloiApi.parseNotificationChannels("""{
          "data":[{"id":12,"type":"slack","label":"Ops alerts","created_at":"2026-09-24 10:12:00"}],
          "meta":{"current_page":1,"last_page":2}}""")
        val channel = page.channels.single()
        assertEquals(12L, channel.id)
        assertEquals("slack", channel.type)
        assertEquals("Ops alerts", channel.label)
        assertTrue(page.hasNext)
    }

    @Test fun notificationChannelsRequestsDocumentedPath() {
        installFakeClient()
        scriptedBody = """{"data":[],"meta":{"current_page":1,"last_page":1}}"""
        PloiApi.notificationChannels(token)
        assertEquals("https://ploi.io/api/user/notification-channels?page=1&per_page=15", recorded.single().url)
    }

    @Test fun parsesSourceControlProvidersAndDisplayNameFallback() {
        val page = PloiApi.parseSourceControlProviders("""{
          "data":[
            {"id":1,"label":null,"name":"ploi","provider":"github","created_at":"2024-01-01 00:00:00"},
            {"id":2,"label":"Work","name":"acme","provider":"gitlab","created_at":"2024-01-02 00:00:00"}
          ],"meta":{"current_page":1,"last_page":1}}""")
        assertEquals(2, page.providers.size)
        assertEquals("ploi", page.providers[0].displayName)
        assertEquals("Work", page.providers[1].displayName)
        assertEquals("gitlab", page.providers[1].provider)
    }

    @Test fun parsesSourceControlProviderDetail() {
        val provider = PloiApi.parseSourceControlProvider(
            """{"data":{"id":1,"label":null,"name":"ploi","provider":"gitlab","created_at":"2024-01-01 00:00:00"}}"""
        )
        assertEquals(1L, provider.id)
        assertEquals("gitlab", provider.provider)
    }

    @Test fun sourceControlRequestsDocumentedPaths() {
        installFakeClient()
        scriptedBody = """{"data":[],"meta":{"current_page":1,"last_page":1}}"""
        PloiApi.sourceControlProviders(token)
        scriptedBody = """{"data":{"id":4,"label":null,"name":"x","provider":"github","created_at":""}}"""
        PloiApi.sourceControlProvider(token, 4)
        assertEquals("https://ploi.io/api/user/source-control?page=1&per_page=15", recorded[0].url)
        assertEquals("https://ploi.io/api/user/source-control/4", recorded[1].url)
    }

    @Test fun parsesSourceControlRepositories() {
        val repositories = PloiApi.parseSourceControlRepositories("""{"data":{"repositories":[
          {"label":"ploi/ploi","name":"ploi/ploi","created_at":"2017-05-01 00:00:00"},
          {"label":null,"name":"acme/api","created_at":"2020-01-01 00:00:00"}]}}""")
        assertEquals(2, repositories.size)
        assertEquals("ploi/ploi", repositories[0].name)
        assertEquals("", repositories[1].label)
    }

    @Test fun sourceControlRepositoriesRequestsDocumentedPath() {
        installFakeClient()
        scriptedBody = """{"data":{"repositories":[]}}"""
        PloiApi.sourceControlRepositories(token, 4)
        assertEquals("https://ploi.io/api/user/source-control/4/repositories", recorded.single().url)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidSourceControlProviderId() {
        PloiApi.sourceControlRepositories(token, -1)
    }
}
