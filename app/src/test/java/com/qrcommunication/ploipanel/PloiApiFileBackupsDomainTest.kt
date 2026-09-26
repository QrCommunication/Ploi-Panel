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
 * `site` backups domain (/api/backups/file, 9 routes).
 */
class PloiApiFileBackupsDomainTest {
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

    /** List documented shape: interval as text, active as 0/1, server_id null, nested site. */
    private fun backupListJson(): String = JSONObject()
        .put(
            "data",
            JSONArray().put(
                JSONObject()
                    .put("id", 1).put("status", "active").put("label", "My AWS provider")
                    .put("type", "aws-s3").put("type_human", "AWS S3")
                    .put("path", "/home/ploi/ploi.info").put("remote_path", "/")
                    .put("locations", JSONObject.NULL).put("interval", "daily")
                    .put("table_exclusions", JSONObject.NULL).put("keep_backup_amount", 5)
                    .put("active", 0).put("server_id", JSONObject.NULL)
                    .put("site", JSONObject().put("id", 1).put("root_domain", "ploi.info"))
                    .put("last_backup_at", "2024-08-02 00:00:00")
                    .put("created_at", "2024-07-18 00:00:00")
            )
        )
        .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
        .toString()

    /** Update documented shape, richer than the list one (numeric interval, nested server…). */
    private fun backupUpdateJson(): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("id", 1).put("type", "local").put("path", "/home/ploi/example.com/public")
                .put("local_path", "/backups/sites").put("compression", "zip")
                .put("interval", 720).put("keep_backup_amount", 5)
                .put("custom_name", "site-backup.zip").put("delete_on_fail", false)
                .put("excluded", JSONArray().put("node_modules").put("vendor").put(".git"))
                .put("locations", "production-sites").put("status", "active")
                .put("last_backup_at", JSONObject.NULL)
                .put("next_backup_at", "2025-01-15T12:00:00.000000Z")
                .put("created_at", "2025-01-14T10:30:00.000000Z")
                .put(
                    "site",
                    JSONObject().put("id", 1).put("root_domain", "example.com").put("server_id", 1)
                        .put(
                            "server",
                            JSONObject().put("id", 1).put("name", "Production Server")
                                .put("status_id", 1).put("ip", "192.168.1.1")
                        )
                )
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

    // ---- GET /api/backups/file ----

    @Test fun parsesBackupListToleratingDocumentedShapes() {
        val page = PloiApi.parseFileBackups(backupListJson())
        assertEquals(1, page.currentPage)
        assertFalse(page.hasNext)
        val backup = page.backups.single()
        assertEquals(1L, backup.id)
        assertEquals("active", backup.status)
        assertEquals("My AWS provider", backup.label)
        assertEquals("aws-s3", backup.type)
        assertEquals("AWS S3", backup.typeHuman)
        assertEquals("/home/ploi/ploi.info", backup.path)
        assertEquals("/", backup.remotePath)
        assertEquals("", backup.locations) // documented null
        assertNull(backup.intervalMinutes) // documented text interval
        assertEquals("daily", backup.intervalLabel)
        assertEquals(5, backup.keepBackupAmount)
        assertFalse(backup.active) // documented 0
        assertEquals(0L, backup.serverId) // documented null
        assertEquals(1L, backup.siteId)
        assertEquals("ploi.info", backup.siteDomain)
        assertEquals("2024-08-02 00:00:00", backup.lastBackupAt)
        assertEquals("2024-07-18 00:00:00", backup.createdAt)
        assertEquals("", backup.compression) // absent from the list shape
        assertTrue(backup.excluded.isEmpty())
    }

    @Test fun fileBackupsHitsDocumentedRouteWithFiltersAndPagination() {
        installFakeClient()
        scriptedBody = backupListJson()
        PloiApi.fileBackups(token, serverId = 7, siteId = 3, page = 2, perPage = 50)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/backups/file?page=2&per_page=50&server=7&site=3", request.url)
        recorded.clear()
        PloiApi.fileBackups(token)
        assertEquals("https://ploi.io/api/backups/file?page=1&per_page=15", recorded.single().url)
        assertThrowsIAE { PloiApi.fileBackups(token, page = 0) }
        assertThrowsIAE { PloiApi.fileBackups(token, perPage = 51) }
        assertThrowsIAE { PloiApi.fileBackups(token, serverId = 0) }
        assertThrowsIAE { PloiApi.fileBackups(token, siteId = -1) }
    }

    // ---- GET /api/backups/file/{id} ----

    @Test fun fileBackupReadsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject(backupListJson()).let { list ->
            JSONObject().put("data", list.getJSONArray("data").getJSONObject(0)).toString()
        }
        val backup = PloiApi.fileBackup(token, 1)
        assertEquals("ploi.info", backup.siteDomain)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/backups/file/1", request.url)
    }

    // ---- POST /api/backups/file ----

    @Test fun createFileBackupPostsDocumentedRequiredFieldsOnly() {
        installFakeClient()
        scriptedBody = """{"status":"ok","message":"2 site file backup(s) have been created"}"""
        val message = PloiApi.createFileBackup(
            token,
            CreateFileBackupRequest(
                backupConfiguration = 1, server = 1, sites = listOf(1, 2), interval = 60,
                paths = mapOf(1L to "/home/ploi/ploi.io", 2L to "/home/ploi/examplehosting.com")
            )
        )
        assertEquals("2 site file backup(s) have been created", message)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/backups/file", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals(1L, body.getLong("backup_configuration"))
        assertEquals(1L, body.getLong("server"))
        assertEquals(listOf(1, 2), (0 until body.getJSONArray("sites").length()).map {
            body.getJSONArray("sites").getInt(it)
        })
        assertEquals(60, body.getInt("interval"))
        val paths = body.getJSONObject("path")
        assertEquals("/home/ploi/ploi.io", paths.getString("1"))
        assertEquals("/home/ploi/examplehosting.com", paths.getString("2"))
        assertFalse(body.has("local_path"))
        assertFalse(body.has("keep_backup_amount"))
        assertFalse(body.has("deleteOnFail"))
        assertFalse(body.has("next_backup_at"))
    }

    @Test fun createFileBackupSerializesOptionalFields() {
        val body = JSONObject(
            CreateFileBackupRequest(
                backupConfiguration = 2, server = 7, sites = listOf(4), interval = 0,
                paths = mapOf(4L to "/home/ploi/site"),
                locations = "folder", keepBackupAmount = 2, customName = "nightly.zip",
                localPath = "/backups/sites", password = "s3cret",
                nextBackupAt = "2025-01-16 03:00:00", deleteOnFail = true
            ).toJson()
        )
        assertEquals("folder", body.getString("locations"))
        assertEquals(2, body.getInt("keep_backup_amount"))
        assertEquals("nightly.zip", body.getString("custom_name"))
        assertEquals("/backups/sites", body.getString("local_path"))
        assertEquals("s3cret", body.getString("password"))
        assertEquals("2025-01-16 03:00:00", body.getString("next_backup_at"))
        assertTrue(body.getBoolean("deleteOnFail"))
    }

    @Test fun createFileBackupValidatesDocumentedRules() {
        val paths = mapOf(1L to "/home/ploi/site")
        assertThrowsIAE { CreateFileBackupRequest(0, 1, listOf(1), 10, paths) }
        assertThrowsIAE { CreateFileBackupRequest(1, 0, listOf(1), 10, paths) }
        assertThrowsIAE { CreateFileBackupRequest(1, 1, emptyList(), 10, paths) }
        assertThrowsIAE { CreateFileBackupRequest(1, 1, listOf(0), 10, mapOf(0L to "/x")) }
        assertThrowsIAE { CreateFileBackupRequest(1, 1, listOf(1), 7, paths) } // not documented
        assertThrowsIAE { CreateFileBackupRequest(1, 1, listOf(1), 10080, paths) } // update-only interval
        assertThrowsIAE { CreateFileBackupRequest(1, 1, listOf(1), 10, emptyMap()) } // missing path
        assertThrowsIAE { CreateFileBackupRequest(1, 1, listOf(1), 10, mapOf(1L to "relative")) }
        assertThrowsIAE { CreateFileBackupRequest(1, 1, listOf(1), 10, paths, keepBackupAmount = -1) }
        assertThrowsIAE { CreateFileBackupRequest(1, 1, listOf(1), 10, paths, localPath = "backups") }
        assertThrowsIAE { CreateFileBackupRequest(1, 1, listOf(1), 10, paths, nextBackupAt = "next week") }
        // every documented create interval passes
        FILE_BACKUP_CREATE_INTERVALS.forEach { CreateFileBackupRequest(1, 1, listOf(1), it, paths) }
        // documented schedule shapes pass
        CreateFileBackupRequest(1, 1, listOf(1), 0, paths, nextBackupAt = "2025-01-16 03:00")
        CreateFileBackupRequest(1, 1, listOf(1), 0, paths, nextBackupAt = "2025-01-16 03:00:00")
    }

    // ---- PATCH /api/backups/file/{id} ----

    @Test fun updateFileBackupParsesRicherDocumentedShape() {
        installFakeClient()
        scriptedBody = backupUpdateJson()
        val backup = PloiApi.updateFileBackup(
            token, 1,
            UpdateFileBackupRequest(interval = 720, keepBackupAmount = 5, path = "/home/ploi/example.com/public")
        )
        assertEquals(720, backup.intervalMinutes)
        assertEquals("zip", backup.compression)
        assertEquals("/backups/sites", backup.localPath)
        assertEquals(listOf("node_modules", "vendor", ".git"), backup.excluded)
        assertEquals("site-backup.zip", backup.customName)
        assertFalse(backup.deleteOnFail)
        assertEquals("production-sites", backup.locations)
        assertEquals("2025-01-15T12:00:00.000000Z", backup.nextBackupAt)
        assertEquals(1L, backup.backupConfigurationId)
        assertEquals("Local Backups", backup.backupConfigurationLabel)
        assertEquals("example.com", backup.siteDomain)
        assertEquals(1L, backup.serverId) // nested site.server
        assertEquals("Production Server", backup.serverName)
        val request = recorded.single()
        assertEquals("PATCH", request.method)
        assertEquals("https://ploi.io/api/backups/file/1", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals(720, body.getInt("interval"))
        assertEquals(5, body.getInt("keep_backup_amount"))
        assertEquals("/home/ploi/example.com/public", body.getString("path"))
    }

    @Test fun updateFileBackupSerializesAndValidatesDocumentedRules() {
        val body = JSONObject(
            UpdateFileBackupRequest(
                interval = 1440, keepBackupAmount = 5, path = "/home/ploi/example.com",
                deleteOnFail = false, customName = "daily.zip", localPath = "/backups/sites",
                compression = "zip", excluded = listOf("logs"), locations = "folder",
                nextBackupAt = "2025-01-16 03:00:00"
            ).toJson()
        )
        assertFalse(body.getBoolean("deleteOnFail")) // explicit false is still sent
        assertEquals("daily.zip", body.getString("custom_name"))
        assertEquals("/backups/sites", body.getString("local_path"))
        assertEquals("logs", body.getJSONArray("excluded").getString(0))
        assertThrowsIAE { UpdateFileBackupRequest(interval = 15, keepBackupAmount = 1, path = "/home/a/b") }
        assertThrowsIAE { UpdateFileBackupRequest(interval = 10, keepBackupAmount = 1, path = "/home/a/b") }
        assertThrowsIAE { UpdateFileBackupRequest(interval = 60, keepBackupAmount = -1, path = "/home/a/b") }
        assertThrowsIAE { UpdateFileBackupRequest(interval = 60, keepBackupAmount = 1, path = "/var/www") }
        assertThrowsIAE {
            UpdateFileBackupRequest(interval = 60, keepBackupAmount = 1, path = "/home/a/b", excluded = listOf(" "))
        }
        assertThrowsIAE {
            UpdateFileBackupRequest(interval = 60, keepBackupAmount = 1, path = "/home/a/b", nextBackupAt = "tomorrow")
        }
        // every documented update interval passes, including weekly and monthly
        FILE_BACKUP_UPDATE_INTERVALS.forEach {
            UpdateFileBackupRequest(interval = it, keepBackupAmount = 1, path = "/home/a/b")
        }
        // null deleteOnFail omits the field entirely
        assertFalse(JSONObject(UpdateFileBackupRequest(60, 1, "/home/a/b").toJson()).has("deleteOnFail"))
        assertThrowsIAE { PloiApi.updateFileBackup(token, 0, UpdateFileBackupRequest(60, 1, "/home/a/b")) }
    }

    // ---- POST /api/backups/file/{id}/run ----

    @Test fun runFileBackupHitsDocumentedRouteAndReturnsMessage() {
        installFakeClient()
        scriptedBody = """{"status":"ok","message":"Site file backup is running"}"""
        val message = PloiApi.runFileBackup(token, 1)
        assertEquals("Site file backup is running", message)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/backups/file/1/run", request.url)
        assertThrowsIAE { PloiApi.runFileBackup(token, 0) }
    }

    // ---- DELETE /api/backups/file/{id} ----

    @Test fun deleteFileBackupToleratesNullMessage() {
        installFakeClient()
        scriptedBody = """{"status":"ok","message":null}"""
        assertEquals("", PloiApi.deleteFileBackup(token, 1))
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/backups/file/1", request.url)
        assertNull(request.body)
        assertThrowsIAE { PloiApi.deleteFileBackup(token, -1) }
    }

    // ---- GET /api/backups/file/{id}/notification-channels ----

    @Test fun listsBackupNotificationChannels() {
        installFakeClient()
        scriptedBody = channelsJson()
        val channels = PloiApi.fileBackupNotificationChannels(token, 1)
        val channel = channels.single()
        assertEquals(12L, channel.id)
        assertEquals("slack", channel.type)
        assertEquals("Ops alerts", channel.label)
        assertEquals("failed-backup", channel.location)
        assertEquals("2026-09-24 10:12:00", channel.createdAt)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/backups/file/1/notification-channels", request.url)
    }

    // ---- POST /api/backups/file/{id}/notification-channels ----

    @Test fun attachBackupNotificationChannelPostsChannelAndLocation() {
        installFakeClient()
        scriptedBody = channelsJson()
        val channels = PloiApi.attachFileBackupNotificationChannel(token, 1, 12, "failed-backup")
        assertEquals(1, channels.size)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/backups/file/1/notification-channels", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals(12L, body.getLong("channel"))
        assertEquals("failed-backup", body.getString("location"))
        assertThrowsIAE { PloiApi.attachFileBackupNotificationChannel(token, 1, 0, "failed-backup") }
        assertThrowsIAE { PloiApi.attachFileBackupNotificationChannel(token, 1, 12, "weekly") }
        assertThrowsIAE { PloiApi.attachFileBackupNotificationChannel(token, 0, 12, "failed-backup") }
    }

    // ---- DELETE /api/backups/file/{id}/notification-channels/{channelId} ----

    @Test fun detachBackupNotificationChannelWithAndWithoutLocation() {
        installFakeClient()
        scriptedBody = """{"data":[]}"""
        val remaining = PloiApi.detachFileBackupNotificationChannel(token, 1, 12, "failed-backup")
        assertTrue(remaining.isEmpty())
        assertEquals("DELETE", recorded[0].method)
        assertEquals(
            "https://ploi.io/api/backups/file/1/notification-channels/12?location=failed-backup",
            recorded[0].url
        )
        PloiApi.detachFileBackupNotificationChannel(token, 1, 12)
        assertEquals("https://ploi.io/api/backups/file/1/notification-channels/12", recorded[1].url)
        assertThrowsIAE { PloiApi.detachFileBackupNotificationChannel(token, 1, 12, "weekly") }
        assertThrowsIAE { PloiApi.detachFileBackupNotificationChannel(token, 1, 0) }
        assertThrowsIAE { PloiApi.detachFileBackupNotificationChannel(token, 0, 12) }
    }

    // ---- Error handling ----

    @Test fun malformedBackupPayloadSurfacesTypedError() {
        installFakeClient()
        scriptedBody = """{"unexpected":true}"""
        try {
            PloiApi.fileBackup(token, 1)
            fail("Expected PloiMalformedPayloadException")
        } catch (expected: PloiMalformedPayloadException) {
            // typed error, not a raw JSONException
        }
    }

    @Test fun mutationsRejectInvalidResourceIds() {
        assertThrowsIAE { PloiApi.fileBackup(token, 0) }
        assertThrowsIAE { PloiApi.fileBackup(token, -1) }
        assertThrowsIAE { PloiApi.fileBackupNotificationChannels(token, 0) }
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
