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
 * `databases` and `database-users` domains (12 routes).
 */
class PloiApiDatabasesDomainTest {
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

    private fun databaseJson(withSite: Boolean = false, type: Any? = "mysql"): String {
        val data = JSONObject()
            .put("id", 1).put("name", "my_database").put("server_id", 7)
            .put("status", "active").put("created_at", "2019-07-16 13:05:58")
        if (type == null) data.put("type", JSONObject.NULL) else data.put("type", type)
        if (withSite) data.put("site", JSONObject().put("id", 3).put("root_domain", "domain.com"))
        return JSONObject().put("data", data).toString()
    }

    private fun databaseListJson(withSite: Boolean = false): String {
        val root = JSONObject(databaseJson(withSite))
        val item = root.getJSONObject("data")
        return JSONObject()
            .put("data", org.json.JSONArray().put(item))
            .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
            .toString()
    }

    private fun userJson(): String = JSONObject().put("data", JSONObject()
        .put("id", 5).put("user", "db_user").put("remote", false)
        .put("remote_ip", "%").put("readonly", false)
        .put("created_at", "2022-02-16 10:45:34")).toString()

    private fun userListJson(): String {
        val root = JSONObject(userJson())
        return JSONObject()
            .put("data", org.json.JSONArray().put(root.getJSONObject("data")))
            .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
            .toString()
    }

    // ---- GET /api/servers/{server}/databases ----

    @Test fun parsesDatabaseListWithAndWithoutSite() {
        val page = PloiApi.parseDatabases(databaseListJson(withSite = true))
        assertEquals(1, page.currentPage)
        assertFalse(page.hasNext)
        val database = page.databases.single()
        assertEquals(1L, database.id)
        assertEquals("mysql", database.type)
        assertEquals("my_database", database.name)
        assertEquals(7L, database.serverId)
        assertEquals("active", database.status)
        assertEquals(3L, database.siteId)
        assertEquals("domain.com", database.siteDomain)
        assertEquals("2019-07-16 13:05:58", database.createdAt)

        val withoutSite = PloiApi.parseDatabases(databaseListJson()).databases.single()
        assertNull(withoutSite.siteId)
        assertEquals("", withoutSite.siteDomain)
    }

    @Test fun databasesHitsDocumentedRouteWithPagination() {
        installFakeClient()
        scriptedBody = databaseListJson()
        PloiApi.databases(token, 7, page = 2, perPage = 50)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/7/databases?page=2&per_page=50", request.url)
        assertThrowsIAE { PloiApi.databases(token, 7, page = 0) }
        assertThrowsIAE { PloiApi.databases(token, 7, perPage = 51) }
    }

    // ---- GET /api/servers/{server}/databases/{id} ----

    @Test fun databaseReadsDocumentedRoute() {
        installFakeClient()
        scriptedBody = databaseJson()
        val database = PloiApi.database(token, 7, 1)
        assertEquals("my_database", database.name)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/7/databases/1", request.url)
    }

    // ---- POST /api/servers/{server}/databases ----

    @Test fun createDatabasePostsRequiredNameOnly() {
        installFakeClient()
        scriptedBody = databaseJson()
        PloiApi.createDatabase(token, 7, CreateDatabaseRequest("my_database"))
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/databases", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("my_database", body.getString("name"))
        assertFalse(body.has("user"))
        assertFalse(body.has("password"))
        assertFalse(body.has("description"))
        assertFalse(body.has("site_id"))
    }

    @Test fun createDatabaseSerializesOptionalFields() {
        val body = JSONObject(
            CreateDatabaseRequest(
                name = "shop", user = "shop_user", password = "s3cret",
                description = "Boutique", siteId = 3
            ).toJson()
        )
        assertEquals("shop_user", body.getString("user"))
        assertEquals("s3cret", body.getString("password"))
        assertEquals("Boutique", body.getString("description"))
        assertEquals(3L, body.getLong("site_id"))
    }

    @Test fun createDatabaseValidatesDocumentedRules() {
        assertThrowsIAE { CreateDatabaseRequest("a") } // min 2
        assertThrowsIAE { CreateDatabaseRequest("x".repeat(65)) } // max 64
        assertThrowsIAE { CreateDatabaseRequest("bad name") } // spaces rejected
        assertThrowsIAE { CreateDatabaseRequest("bad.name") } // dots rejected
        assertThrowsIAE { CreateDatabaseRequest("valid_name", user = "x") } // user min 2
        assertThrowsIAE { CreateDatabaseRequest("valid_name", user = "bad user") }
        assertThrowsIAE { CreateDatabaseRequest("valid_name", password = " ") }
        assertThrowsIAE { CreateDatabaseRequest("valid_name", siteId = 0) }
        // documented charset passes: alpha-numeric, dashes, underscores
        CreateDatabaseRequest("valid-name_2")
    }

    // ---- DELETE /api/servers/{server}/databases/{id} ----

    @Test fun deleteDatabaseToleratesEmptyBody() {
        installFakeClient()
        scriptedBody = ""
        PloiApi.deleteDatabase(token, 7, 1)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/7/databases/1", request.url)
        assertNull(request.body)
    }

    // ---- POST /api/servers/{server}/databases/acknowledge ----

    @Test fun acknowledgeDatabasePostsValidatedName() {
        installFakeClient()
        scriptedBody = databaseJson()
        val database = PloiApi.acknowledgeDatabase(token, 7, "external_db")
        assertEquals("my_database", database.name)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/databases/acknowledge", request.url)
        assertEquals("external_db", JSONObject(request.body.orEmpty()).getString("name"))
        assertThrowsIAE { PloiApi.acknowledgeDatabase(token, 7, "x") }
        assertThrowsIAE { PloiApi.acknowledgeDatabase(token, 7, "bad name") }
    }

    // ---- DELETE /api/servers/{server}/databases/{id}/forget ----

    @Test fun forgetDatabaseHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = ""
        PloiApi.forgetDatabase(token, 7, 1)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/7/databases/1/forget", request.url)
        assertNull(request.body)
    }

    // ---- POST /api/servers/{server}/databases/{database}/duplicate ----

    @Test fun duplicateDatabaseParsesDatabaseAndMessage() {
        installFakeClient()
        scriptedBody = JSONObject(databaseJson(type = null))
            .put("message", "Database my_database is being cloned to new_database").toString()
        val result = PloiApi.duplicateDatabase(token, 7, 1, name = "new_database", user = "u", password = "p")
        assertEquals("new_database", JSONObject(recorded.single().body.orEmpty()).getString("name"))
        assertEquals("", result.database.type) // documented null type tolerated
        assertEquals("Database my_database is being cloned to new_database", result.message)
        assertEquals("https://ploi.io/api/servers/7/databases/1/duplicate", recorded.single().url)
    }

    @Test fun duplicateDatabaseValidatesDocumentedLengths() {
        assertThrowsIAE { PloiApi.duplicateDatabase(token, 7, 1, name = " ") }
        assertThrowsIAE { PloiApi.duplicateDatabase(token, 7, 1, name = "x".repeat(256)) }
        assertThrowsIAE { PloiApi.duplicateDatabase(token, 7, 1, name = "ok", user = "u".repeat(256)) }
        assertThrowsIAE { PloiApi.duplicateDatabase(token, 7, 1, name = "ok", password = "p".repeat(51)) }
    }

    @Test fun duplicateDatabaseOmitsBlankOptionalFields() {
        installFakeClient()
        scriptedBody = JSONObject(databaseJson()).put("message", "cloning").toString()
        PloiApi.duplicateDatabase(token, 7, 1, name = "copy")
        val body = JSONObject(recorded.single().body.orEmpty())
        assertFalse(body.has("user"))
        assertFalse(body.has("password"))
    }

    // ---- GET/POST /api/servers/{server}/databases/{database}/users ----

    @Test fun parsesDatabaseUserList() {
        val page = PloiApi.parseDatabaseUsers(userListJson())
        val user = page.users.single()
        assertEquals(5L, user.id)
        assertEquals("db_user", user.user)
        assertFalse(user.remote)
        assertEquals("%", user.remoteIp)
        assertFalse(user.readonly)
        assertEquals("2022-02-16 10:45:34", user.createdAt)
    }

    @Test fun databaseUsersHitDocumentedRoutes() {
        installFakeClient()
        scriptedBody = userListJson()
        PloiApi.databaseUsers(token, 7, 1)
        assertEquals("GET", recorded[0].method)
        assertEquals("https://ploi.io/api/servers/7/databases/1/users?page=1&per_page=15", recorded[0].url)
        scriptedBody = userJson()
        PloiApi.databaseUser(token, 7, 1, 5)
        assertEquals("https://ploi.io/api/servers/7/databases/1/users/5", recorded[1].url)
    }

    @Test fun createDatabaseUserPostsRequiredFieldsOnly() {
        installFakeClient()
        scriptedBody = userJson()
        PloiApi.createDatabaseUser(token, 7, 1, CreateDatabaseUserRequest("my_user", "db_password"))
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/databases/1/users", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("my_user", body.getString("user"))
        assertEquals("db_password", body.getString("password"))
        assertFalse(body.has("remote"))
        assertFalse(body.has("remote_ip"))
        assertFalse(body.has("readonly"))
    }

    @Test fun createDatabaseUserSerializesRemoteAndReadonly() {
        val body = JSONObject(
            CreateDatabaseUserRequest("u2", "pw", remote = true, remoteIp = "203.0.113.10", readonly = true).toJson()
        )
        assertTrue(body.getBoolean("remote"))
        assertEquals("203.0.113.10", body.getString("remote_ip"))
        assertTrue(body.getBoolean("readonly"))
    }

    @Test fun createDatabaseUserValidatesDocumentedRules() {
        assertThrowsIAE { CreateDatabaseUserRequest("bad-user", "pw") } // documented: no dashes
        assertThrowsIAE { CreateDatabaseUserRequest("bad user", "pw") }
        assertThrowsIAE { CreateDatabaseUserRequest(" ", "pw") }
        assertThrowsIAE { CreateDatabaseUserRequest("valid", " ") } // password required
        assertThrowsIAE { CreateDatabaseUserRequest("valid", "pw", remote = true) } // remote_ip required
        assertThrowsIAE { CreateDatabaseUserRequest("valid", "pw", remote = true, remoteIp = " ") }
        // wildcard remote IP is a documented value
        CreateDatabaseUserRequest("valid", "pw", remote = true, remoteIp = "%")
    }

    // ---- DELETE …/users/{user} ----

    @Test fun deleteDatabaseUserToleratesEmptyBody() {
        installFakeClient()
        scriptedBody = ""
        PloiApi.deleteDatabaseUser(token, 7, 1, 5)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/7/databases/1/users/5", request.url)
        assertNull(request.body)
    }

    // ---- POST …/users/attach ----

    @Test fun attachDatabaseUserPostsUserId() {
        installFakeClient()
        scriptedBody = userJson()
        val attached = PloiApi.attachDatabaseUser(token, 7, 1, 5)
        assertEquals("db_user", attached.user)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/databases/1/users/attach", request.url)
        assertEquals(5L, JSONObject(request.body.orEmpty()).getLong("user_id"))
        assertThrowsIAE { PloiApi.attachDatabaseUser(token, 7, 1, 0) }
    }

    // ---- Error handling ----

    @Test fun malformedDatabasePayloadSurfacesTypedError() {
        installFakeClient()
        scriptedBody = """{"unexpected":true}"""
        try {
            PloiApi.database(token, 7, 1)
            fail("Expected PloiMalformedPayloadException")
        } catch (expected: PloiMalformedPayloadException) {
            // typed error, not a raw JSONException
        }
    }

    @Test fun mutationsRejectInvalidResourceIds() {
        assertThrowsIAE { PloiApi.databases(token, 0) }
        assertThrowsIAE { PloiApi.database(token, 7, 0) }
        assertThrowsIAE { PloiApi.createDatabase(token, 0, CreateDatabaseRequest("valid_name")) }
        assertThrowsIAE { PloiApi.deleteDatabase(token, 7, -1) }
        assertThrowsIAE { PloiApi.acknowledgeDatabase(token, 0, "valid_name") }
        assertThrowsIAE { PloiApi.forgetDatabase(token, 0, 1) }
        assertThrowsIAE { PloiApi.duplicateDatabase(token, 7, 0, name = "copy") }
        assertThrowsIAE { PloiApi.databaseUsers(token, 7, 0) }
        assertThrowsIAE { PloiApi.databaseUser(token, 7, 1, 0) }
        assertThrowsIAE {
            PloiApi.createDatabaseUser(token, 0, 1, CreateDatabaseUserRequest("valid", "pw"))
        }
        assertThrowsIAE { PloiApi.deleteDatabaseUser(token, 7, 0, 5) }
        assertThrowsIAE { PloiApi.attachDatabaseUser(token, 0, 1, 5) }
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
