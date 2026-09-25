package com.qrcommunication.ploipanel

import org.json.JSONArray
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
 * `database` backups domain (/api/backups/database, 9 routes).
 */
class PloiApiDatabaseBackupsDomainTest {
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

    /** List/get documented shape (developers.ploi.io/database/list-database-backups). */
    private fun backupListJson(): String = JSONObject()
        .put(
            "data",
            JSONArray().put(
                JSONObject()
                    .put("id", 1).put("status", "active").put("label", "My AWS provider")
                    .put("type", "aws-s3").put("type_human", "AWS S3")
                    .put("path", "/").put("remote_path", "/")
                    .put("locations", JSONObject.NULL).put("interval", 0)
                    .put("table_exclusions", JSONObject.NULL).put("keep_backup_amount", 0)
                    .put("active", true)
                    .put("server", JSONObject().put("id", 1).put("name", "dizzy-leaf"))
                    .put("database", JSONObject().put("id", 1).put("name", "wordpress_blog"))
                    .put("last_backup_at", "2024-08-01 00:00:00")
                    .put("created_at", "2024-08-01 00:00:00")
            )
        )
        .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
        .toString()

    /** Update documented shape, richer than the list one (excluded array, compression…). */
    private fun backupUpdateJson(): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("id", 1).put("type", "local").put("path", "/backups")
                .put("compression", "gzip").put("interval", 120).put("keep_backup_amount", 10)
                .put("custom_name", "my-backup.zip").put("delete_on_fail", true)
                .put("excluded", JSONArray().put("sessions").put("cache"))
                .put("locations", "production-backups").put("status", "active")
                .put("last_backup_at", JSONObject.NULL)
                .put("next_backup_at", "2025-01-15T12:00:00.000000Z")
                .put("created_at", "2025-01-14T10:30:00.000000Z")
                .put("server", JSONObject().put("id", 1).put("name", "Production Server"))
                .put("database", JSONObject().put("id", 1).put("name", "my_database"))
                .put(
                    "backup_configuration",
                    JSONObject().put("id", 1).put("label", "Local Backups").put("type", "local")
                )
        )
        .toString()

    private fun channelsJson(): String = JSONObject()
        .put(
            "data",
            JSONArray().put(
                JSONObject()
                    .put("id", 12).put("type", "slack").put("label", "Ops alerts")
                    .put("location", "failed-backup").put("created_at", "2026-09-24 10:12:00")
            )
        )
        .toString()

    // ---- GET /api/backups/database ----

    @Test fun parsesBackupListToleratingDocumentedNulls() {
        val page = PloiApi.parseDatabaseBackups(backupListJson())
        assertEquals(1, page.currentPage)
        assertFalse(page.hasNext)
        val backup = page.backups.single()
        assertEquals(1L, backup.id)
        assertEquals("active", backup.status)
        assertEquals("My AWS provider", backup.label)
        assertEquals("aws-s3", backup.type)
        assertEquals("AWS S3", backup.typeHuman)
        assertEquals("/", backup.path)
        assertEquals("/", backup.remotePath)
        assertEquals("", backup.locations) // documented null
        assertEquals(0, backup.interval)
        assertEquals("", backup.tableExclusions) // documented null
        assertEquals(0, backup.keepBackupAmount)
        assertTrue(backup.active)
        assertEquals(1L, backup.serverId)
        assertEquals("dizzy-leaf", backup.serverName)
        assertEquals(1L, backup.databaseId)
        assertEquals("wordpress_blog", backup.databaseName)
        assertEquals("2024-08-01 00:00:00", backup.lastBackupAt)
        assertEquals("2024-08-01 00:00:00", backup.createdAt)
        assertEquals("", backup.compression) // absent from the list shape
        assertTrue(backup.excludedTables.isEmpty())
    }

    @Test fun databaseBackupsHitsDocumentedRouteWithFiltersAndPagination() {
        installFakeClient()
        scriptedBody = backupListJson()
        PloiApi.databaseBackups(token, serverId = 7, siteId = 3, page = 2, perPage = 50)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/backups/database?page=2&per_page=50&server=7&site=3", request.url)
        recorded.clear()
        PloiApi.databaseBackups(token)
        assertEquals("https://ploi.io/api/backups/database?page=1&per_page=15", recorded.single().url)
        assertThrowsIAE { PloiApi.databaseBackups(token, page = 0) }
        assertThrowsIAE { PloiApi.databaseBackups(token, perPage = 51) }
        assertThrowsIAE { PloiApi.databaseBackups(token, serverId = 0) }
        assertThrowsIAE { PloiApi.databaseBackups(token, siteId = -1) }
    }

    // ---- GET /api/backups/database/{id} ----

    @Test fun databaseBackupReadsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject(backupListJson()).let { list ->
            JSONObject().put("data", list.getJSONArray("data").getJSONObject(0)).toString()
        }
        val backup = PloiApi.databaseBackup(token, 1)
        assertEquals("wordpress_blog", backup.databaseName)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/backups/database/1", request.url)
    }

    // ---- POST /api/backups/database ----

    @Test fun createDatabaseBackupPostsDocumentedRequiredFieldsOnly() {
        installFakeClient()
        scriptedBody = """{"status":"ok","message":"Backup settings have been saved for 1 database"}"""
        val message = PloiApi.createDatabaseBackup(
            token,
            CreateDatabaseBackupRequest(backupConfiguration = 1, server = 1, databases = listOf(1, 2, 3), interval = 10)
        )
        assertEquals("Backup settings have been saved for 1 database", message)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/backups/database", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals(1L, body.getLong("backup_configuration"))
        assertEquals(1L, body.getLong("server"))
        assertEquals(listOf(1, 2, 3), (0 until body.getJSONArray("databases").length()).map {
            body.getJSONArray("databases").getInt(it)
        })
        assertEquals(10, body.getInt("interval"))
        assertFalse(body.has("table_exclusions"))
        assertFalse(body.has("keep_backup_amount"))
        assertFalse(body.has("deleteOnFail"))
        assertFalse(body.has("next_backup_at"))
    }

    @Test fun createDatabaseBackupSerializesOptionalFields() {
        val body = JSONObject(
            CreateDatabaseBackupRequest(
                backupConfiguration = 2, server = 7, databases = listOf(4), interval = 0,
                tableExclusions = "sessions,cache", locations = "folder", path = "/backups",
                keepBackupAmount = 2, customName = "nightly.zip", password = "s3cret",
                nextBackupAt = "2025-01-16 03:00:00", deleteOnFail = true
            ).toJson()
        )
        assertEquals("sessions,cache", body.getString("table_exclusions"))
        assertEquals("folder", body.getString("locations"))
        assertEquals("/backups", body.getString("path"))
        assertEquals(2, body.getInt("keep_backup_amount"))
        assertEquals("nightly.zip", body.getString("custom_name"))
        assertEquals("s3cret", body.getString("password"))
        assertEquals("2025-01-16 03:00:00", body.getString("next_backup_at"))
        assertTrue(body.getBoolean("deleteOnFail"))
    }

    @Test fun createDatabaseBackupValidatesDocumentedRules() {
        assertThrowsIAE { CreateDatabaseBackupRequest(0, 1, listOf(1), 10) }
        assertThrowsIAE { CreateDatabaseBackupRequest(1, 0, listOf(1), 10) }
        assertThrowsIAE { CreateDatabaseBackupRequest(1, 1, emptyList(), 10) }
        assertThrowsIAE { CreateDatabaseBackupRequest(1, 1, listOf(0), 10) }
        assertThrowsIAE { CreateDatabaseBackupRequest(1, 1, listOf(1), 7) } // not a documented interval
        assertThrowsIAE { CreateDatabaseBackupRequest(1, 1, listOf(1), 10, keepBackupAmount = -1) }
        assertThrowsIAE { CreateDatabaseBackupRequest(1, 1, listOf(1), 10, nextBackupAt = "next week") }
        // every documented interval passes, including weekly and monthly
        BACKUP_INTERVALS.forEach { CreateDatabaseBackupRequest(1, 1, listOf(1), it) }
        // documented schedule shapes pass
        CreateDatabaseBackupRequest(1, 1, listOf(1), 0, nextBackupAt = "2025-01-16 03:00")
        CreateDatabaseBackupRequest(1, 1, listOf(1), 0, nextBackupAt = "2025-01-16 03:00:00")
    }

    // ---- PATCH /api/backups/database/{id} ----

    @Test fun updateDatabaseBackupParsesRicherDocumentedShape() {
        installFakeClient()
        scriptedBody = backupUpdateJson()
        val backup = PloiApi.updateDatabaseBackup(
            token, 1, UpdateDatabaseBackupRequest(interval = 120, keepBackupAmount = 10)
        )
        assertEquals("gzip", backup.compression)
        assertEquals(listOf("sessions", "cache"), backup.excludedTables)
        assertEquals("my-backup.zip", backup.customName)
        assertTrue(backup.deleteOnFail)
        assertEquals("production-backups", backup.locations)
        assertEquals("2025-01-15T12:00:00.000000Z", backup.nextBackupAt)
        assertEquals(1L, backup.backupConfigurationId)
        assertEquals("Local Backups", backup.backupConfigurationLabel)
        assertEquals("my_database", backup.databaseName)
        val request = recorded.single()
        assertEquals("PATCH", request.method)
        assertEquals("https://ploi.io/api/backups/database/1", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals(120, body.getInt("interval"))
        assertEquals(10, body.getInt("keep_backup_amount"))
    }

    @Test fun updateDatabaseBackupSerializesAndValidatesDocumentedRules() {
        val body = JSONObject(
            UpdateDatabaseBackupRequest(
                interval = 1440, keepBackupAmount = 5, deleteOnFail = false,
                customName = "daily.zip", path = "/b", compression = "zip",
                excluded = listOf("logs"), locations = "folder", nextBackupAt = "2025-01-16 03:00:00"
            ).toJson()
        )
        assertFalse(body.getBoolean("deleteOnFail")) // explicit false is still sent
        assertEquals("daily.zip", body.getString("custom_name"))
        assertEquals("logs", body.getJSONArray("excluded").getString(0))
        assertThrowsIAE { UpdateDatabaseBackupRequest(interval = 15, keepBackupAmount = 1) }
        assertThrowsIAE { UpdateDatabaseBackupRequest(interval = 10, keepBackupAmount = -1) }
        assertThrowsIAE { UpdateDatabaseBackupRequest(interval = 10, keepBackupAmount = 1, excluded = listOf(" ")) }
        assertThrowsIAE {
            UpdateDatabaseBackupRequest(interval = 10, keepBackupAmount = 1, nextBackupAt = "tomorrow")
        }
        // null deleteOnFail omits the field entirely
        assertFalse(JSONObject(UpdateDatabaseBackupRequest(10, 1).toJson()).has("deleteOnFail"))
        assertThrowsIAE { PloiApi.updateDatabaseBackup(token, 0, UpdateDatabaseBackupRequest(10, 1)) }
    }

    // ---- POST /api/backups/database/{id}/run ----

    @Test fun runDatabaseBackupHitsDocumentedRouteAndReturnsMessage() {
        installFakeClient()
        scriptedBody = """{"status":"ok","message":"Database backup is running"}"""
        val message = PloiApi.runDatabaseBackup(token, 1)
        assertEquals("Database backup is running", message)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/backups/database/1/run", request.url)
        assertThrowsIAE { PloiApi.runDatabaseBackup(token, 0) }
    }

    // ---- DELETE /api/backups/database/{id} ----

    @Test fun deleteDatabaseBackupToleratesNullMessage() {
        installFakeClient()
        scriptedBody = """{"status":"ok","message":null}"""
        assertEquals("", PloiApi.deleteDatabaseBackup(token, 1))
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/backups/database/1", request.url)
        assertNull(request.body)
        assertThrowsIAE { PloiApi.deleteDatabaseBackup(token, -1) }
    }

    // ---- GET /api/backups/database/{id}/notification-channels ----

    @Test fun listsBackupNotificationChannels() {
        installFakeClient()
        scriptedBody = channelsJson()
        val channels = PloiApi.databaseBackupNotificationChannels(token, 1)
        val channel = channels.single()
        assertEquals(12L, channel.id)
        assertEquals("slack", channel.type)
        assertEquals("Ops alerts", channel.label)
        assertEquals("failed-backup", channel.location)
        assertEquals("2026-09-24 10:12:00", channel.createdAt)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/backups/database/1/notification-channels", request.url)
    }

    // ---- POST /api/backups/database/{id}/notification-channels ----

    @Test fun attachBackupNotificationChannelPostsChannelAndLocation() {
        installFakeClient()
        scriptedBody = channelsJson()
        val channels = PloiApi.attachDatabaseBackupNotificationChannel(token, 1, 12, "failed-backup")
        assertEquals(1, channels.size)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/backups/database/1/notification-channels", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals(12L, body.getLong("channel"))
        assertEquals("failed-backup", body.getString("location"))
        // every documented location passes
        BACKUP_CHANNEL_LOCATIONS.forEach { validateBackupChannelLocation(it) }
        assertThrowsIAE { PloiApi.attachDatabaseBackupNotificationChannel(token, 1, 0, "failed-backup") }
        assertThrowsIAE { PloiApi.attachDatabaseBackupNotificationChannel(token, 1, 12, "weekly") }
        assertThrowsIAE { PloiApi.attachDatabaseBackupNotificationChannel(token, 0, 12, "failed-backup") }
    }

    // ---- DELETE /api/backups/database/{id}/notification-channels/{channelId} ----

    @Test fun detachBackupNotificationChannelWithAndWithoutLocation() {
        installFakeClient()
        scriptedBody = """{"data":[]}"""
        val remaining = PloiApi.detachDatabaseBackupNotificationChannel(token, 1, 12, "failed-backup")
        assertTrue(remaining.isEmpty())
        assertEquals("DELETE", recorded[0].method)
        assertEquals(
            "https://ploi.io/api/backups/database/1/notification-channels/12?location=failed-backup",
            recorded[0].url
        )
        PloiApi.detachDatabaseBackupNotificationChannel(token, 1, 12)
        assertEquals("https://ploi.io/api/backups/database/1/notification-channels/12", recorded[1].url)
        assertThrowsIAE { PloiApi.detachDatabaseBackupNotificationChannel(token, 1, 12, "weekly") }
        assertThrowsIAE { PloiApi.detachDatabaseBackupNotificationChannel(token, 1, 0) }
        assertThrowsIAE { PloiApi.detachDatabaseBackupNotificationChannel(token, 0, 12) }
    }

    // ---- Error handling ----

    @Test fun malformedBackupPayloadSurfacesTypedError() {
        installFakeClient()
        scriptedBody = """{"unexpected":true}"""
        try {
            PloiApi.databaseBackup(token, 1)
            fail("Expected PloiMalformedPayloadException")
        } catch (expected: PloiMalformedPayloadException) {
            // typed error, not a raw JSONException
        }
    }

    @Test fun mutationsRejectInvalidResourceIds() {
        assertThrowsIAE { PloiApi.databaseBackup(token, 0) }
        assertThrowsIAE { PloiApi.databaseBackup(token, -1) }
        assertThrowsIAE { PloiApi.databaseBackupNotificationChannels(token, 0) }
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
