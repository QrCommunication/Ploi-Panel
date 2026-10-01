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
 * `system-users` domain (/api/servers/{server}/system-users, 4 routes).
 */
class PloiApiSystemUsersDomainTest {
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

    /** Documented list shape (developers.ploi.io/system-users/list-system-users). */
    private fun userListJson(): String = JSONObject()
        .put(
            "data",
            JSONArray().put(
                JSONObject()
                    .put("id", 1)
                    .put("name", "customer")
                    .put("root", "/home/customer")
                    .put("created_at", "2020-01-01 12:00:00")
            )
        )
        .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
        .toString()

    /** Documented single-user shape (get + create responses share it). */
    private fun userJson(): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("id", 1)
                .put("name", "customer")
                .put("root", "/home/customer")
                .put("created_at", "2020-01-01 12:00:00")
        )
        .toString()

    // ---- GET /api/servers/{server}/system-users ----

    @Test fun parsesSystemUserListWithDocumentedShape() {
        val page = PloiApi.parseSystemUsers(userListJson())
        assertEquals(1, page.currentPage)
        assertFalse(page.hasNext)
        val user = page.users.single()
        assertEquals(1L, user.id)
        assertEquals("customer", user.name)
        assertEquals("/home/customer", user.root)
        assertEquals("2020-01-01 12:00:00", user.createdAt)
    }

    @Test fun systemUsersHitsDocumentedRouteWithPagination() {
        installFakeClient()
        scriptedBody = userListJson()
        val page = PloiApi.systemUsers(token, serverId = 7, page = 2, perPage = 50)
        assertEquals(1, page.users.size)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/7/system-users?page=2&per_page=50", request.url)
        recorded.clear()
        PloiApi.systemUsers(token, serverId = 7)
        assertEquals("https://ploi.io/api/servers/7/system-users?page=1&per_page=15", recorded.single().url)
        assertThrowsIAE { PloiApi.systemUsers(token, serverId = 0) }
        assertThrowsIAE { PloiApi.systemUsers(token, serverId = -1) }
        assertThrowsIAE { PloiApi.systemUsers(token, serverId = 7, page = 0) }
        assertThrowsIAE { PloiApi.systemUsers(token, serverId = 7, perPage = 51) }
    }

    // ---- GET /api/servers/{server}/system-users/{id} ----

    @Test fun systemUserReadsDocumentedRoute() {
        installFakeClient()
        scriptedBody = userJson()
        val user = PloiApi.systemUser(token, serverId = 7, userId = 1)
        assertEquals("customer", user.name)
        assertEquals("/home/customer", user.root)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/7/system-users/1", request.url)
        assertThrowsIAE { PloiApi.systemUser(token, serverId = 0, userId = 1) }
        assertThrowsIAE { PloiApi.systemUser(token, serverId = 7, userId = 0) }
    }

    // ---- POST /api/servers/{server}/system-users ----

    @Test fun createSystemUserPostsDocumentedRequiredField() {
        installFakeClient()
        scriptedBody = userJson()
        val created = PloiApi.createSystemUser(token, 7, CreateSystemUserRequest(name = "customer"))
        assertEquals(1L, created.user.id)
        assertEquals("", created.password) // no password without receive_password
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/system-users", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("customer", body.getString("name"))
        assertFalse(body.getBoolean("sudo"))
        assertFalse(body.has("receive_password")) // documented default false, omitted
    }

    @Test fun createSystemUserSendsDocumentedOptionalFlags() {
        installFakeClient()
        // Documented: with receive_password=true the response adds a top-level password.
        scriptedBody = JSONObject(userJson()).put("password", "LYn8r1B9BPt0RXbfZv8rezUM").toString()
        val created = PloiApi.createSystemUser(
            token, 7,
            CreateSystemUserRequest(name = "customer", sudo = true, receivePassword = true)
        )
        assertEquals("LYn8r1B9BPt0RXbfZv8rezUM", created.password)
        assertEquals("customer", created.user.name)
        val body = JSONObject(recorded.single().body.orEmpty())
        assertEquals("customer", body.getString("name"))
        assertTrue(body.getBoolean("sudo"))
        assertTrue(body.getBoolean("receive_password"))
    }

    @Test fun createSystemUserValidatesDocumentedConstraints() {
        // documented: name is the only required attribute
        assertThrowsIAE { CreateSystemUserRequest("") }
        assertThrowsIAE { CreateSystemUserRequest("   ") }
        assertThrowsIAE { CreateSystemUserRequest("two words") } // no whitespace in an account name
        assertThrowsIAE { CreateSystemUserRequest("bad\nname") }
        CreateSystemUserRequest("customer")
        CreateSystemUserRequest("customer", sudo = true)
        assertThrowsIAE { PloiApi.createSystemUser(token, 0, CreateSystemUserRequest("customer")) }
    }

    // ---- DELETE /api/servers/{server}/system-users/{id} ----

    @Test fun deleteSystemUserHitsDocumentedRouteAndToleratesEmptyBody() {
        installFakeClient()
        scriptedBody = "" // documented: empty response body
        PloiApi.deleteSystemUser(token, serverId = 7, userId = 1)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/7/system-users/1", request.url)
        assertNull(request.body)
        assertThrowsIAE { PloiApi.deleteSystemUser(token, serverId = 0, userId = 1) }
        assertThrowsIAE { PloiApi.deleteSystemUser(token, serverId = 7, userId = -1) }
    }

    // ---- Error handling ----

    @Test fun malformedSystemUserPayloadSurfacesTypedError() {
        installFakeClient()
        scriptedBody = """{"unexpected":true}"""
        try {
            PloiApi.systemUser(token, 7, 1)
            fail("Expected PloiMalformedPayloadException")
        } catch (expected: PloiMalformedPayloadException) {
            // typed error, not a raw JSONException
        }
        try {
            PloiApi.parseSystemUsers("""{"data":[{"root":"/home/x"}]}""") // missing documented id/name
            fail("Expected JSONException on missing documented fields")
        } catch (expected: org.json.JSONException) {
            // required fields are enforced by the parser
        }
        try {
            PloiApi.parseSystemUserCreateResponse("""{"data":"nope"}""")
            fail("Expected JSONException on unexpected create payload")
        } catch (expected: org.json.JSONException) {
            // data must be an object
        }
    }

    @Test fun paginationMetaDrivesHasNext() {
        val twoPages = JSONObject(userListJson())
            .put("meta", JSONObject().put("current_page", 1).put("last_page", 2))
            .toString()
        assertTrue(PloiApi.parseSystemUsers(twoPages).hasNext)
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
