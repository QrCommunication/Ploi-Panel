package com.qrcommunication.ploipanel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Parsing, validation and wire-level coverage for every documented route of the
 * `auth-users` domain (/api/servers/{server}/sites/{id}/auth-users, 4 routes).
 */
class PloiApiAuthUsersDomainTest {
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

    private fun userEntry(): JSONObject = JSONObject()
        .put("id", 1)
        .put("name", "admin")
        .put("path", "/wp-admin")
        .put("created_at", "2024-01-15 10:30:00")

    // ---- GET /api/servers/{server}/sites/{id}/auth-users ----

    @Test fun parsesUserListWithDocumentedShape() {
        val users = PloiApi.parseAuthUsers(
            JSONObject().put(
                "data",
                JSONArray()
                    .put(userEntry())
                    .put(JSONObject().put("id", 2).put("name", "user").put("path", JSONObject.NULL).put("created_at", "2024-01-16 14:20:00"))
            ).toString()
        )
        assertEquals(2, users.size)
        assertEquals("admin", users[0].name)
        assertEquals("/wp-admin", users[0].path)
        assertEquals("2024-01-15 10:30:00", users[0].createdAt)
        assertEquals("", users[1].path)
    }

    @Test fun authUsersHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject().put("data", JSONArray().put(userEntry())).toString()
        PloiApi.authUsers(token, 1, 1)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/auth-users", request.url)
    }

    // ---- GET /api/servers/{server}/sites/{id}/auth-users/{authUserId} ----

    @Test fun authUserHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject().put("data", userEntry()).toString()
        val user = PloiApi.authUser(token, 1, 1, 1)
        assertEquals(1L, user.id)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/auth-users/1", request.url)
    }

    // ---- POST /api/servers/{server}/sites/{id}/auth-users ----

    @Test fun createAuthUserSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = JSONObject().put("data", userEntry()).toString()
        PloiApi.createAuthUser(token, 1, 1, CreateAuthUserRequest("admin", "s3cret", "/wp-admin"))
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/auth-users", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("admin", body.getString("name"))
        assertEquals("s3cret", body.getString("password"))
        assertEquals("/wp-admin", body.getString("path"))
    }

    @Test fun createAuthUserRequestRejectsInvalidInput() {
        listOf(
            { CreateAuthUserRequest("", "s3cret") },
            { CreateAuthUserRequest("admin", "") }
        ).forEach { invalid ->
            try {
                invalid()
                fail("Expected rejection")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // ---- DELETE /api/servers/{server}/sites/{id}/auth-users/{authUserId} ----

    @Test fun deleteAuthUserHitsDocumentedRouteAndParsesRemovedUser() {
        installFakeClient()
        scriptedBody = JSONObject().put("data", userEntry()).toString()
        val removed = PloiApi.deleteAuthUser(token, 1, 1, 1)
        assertEquals("admin", removed.name)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/auth-users/1", request.url)
    }
}
