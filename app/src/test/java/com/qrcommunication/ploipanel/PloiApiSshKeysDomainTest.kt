package com.qrcommunication.ploipanel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Parsing, validation and wire-level coverage for every documented route of the
 * `ssh-keys` domain (/api/servers/{server}/ssh-keys, 4 routes).
 */
class PloiApiSshKeysDomainTest {
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

    private fun keyListJson(): String = JSONObject()
        .put(
            "data",
            JSONArray().put(
                JSONObject()
                    .put("id", 1)
                    .put("status", "active")
                    .put("name", "Macbook Pro")
                    .put("key", "ssh-rsa AAAAB3N....")
                    .put("system_user", "ploi")
            )
        )
        .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
        .toString()

    private fun keyJson(): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("id", 2)
                .put("status", "created")
                .put("name", "key")
                .put("key", "ssh-rsa AAA....")
                .put("system_user", "ploi")
        )
        .toString()

    // ---- GET /api/servers/{server}/ssh-keys ----

    @Test fun parsesKeyListWithDocumentedShape() {
        val page = PloiApi.parseSshKeys(keyListJson())
        assertFalse(page.hasNext)
        val key = page.keys.single()
        assertEquals(1L, key.id)
        assertEquals("active", key.status)
        assertEquals("Macbook Pro", key.name)
        assertEquals("ssh-rsa AAAAB3N....", key.key)
        assertEquals("ploi", key.systemUser)
    }

    @Test fun sshKeysHitsDocumentedRouteWithPagination() {
        installFakeClient()
        scriptedBody = keyListJson()
        PloiApi.sshKeys(token, 1, page = 2, perPage = 50)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/ssh-keys?page=2&per_page=50", request.url)
    }

    // ---- GET /api/servers/{server}/ssh-keys/{sshKey} ----

    @Test fun sshKeyHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = keyJson()
        val key = PloiApi.sshKey(token, 1, 2)
        assertEquals(2L, key.id)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/ssh-keys/2", request.url)
    }

    // ---- POST /api/servers/{server}/ssh-keys ----

    @Test fun createSshKeySendsDocumentedBody() {
        installFakeClient()
        scriptedBody = keyJson()
        val key = PloiApi.createSshKey(token, 1, CreateSshKeyRequest("key", "ssh-rsa AAA....", "ploi"))
        assertEquals("created", key.status)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/ssh-keys", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("key", body.getString("name"))
        assertEquals("ssh-rsa AAA....", body.getString("key"))
        assertEquals("ploi", body.getString("system_user"))
    }

    @Test fun createSshKeyRequestRejectsInvalidInput() {
        listOf(
            { CreateSshKeyRequest("", "ssh-rsa AAA", "ploi") },
            { CreateSshKeyRequest("key", "not-a-key", "ploi") },
            { CreateSshKeyRequest("key", "ssh-rsa AAA", "") }
        ).forEach { invalid ->
            try {
                invalid()
                fail("Expected rejection")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // ---- DELETE /api/servers/{server}/ssh-keys/{sshKey} ----

    @Test fun deleteSshKeyHitsDocumentedRouteAndParsesMessage() {
        installFakeClient()
        scriptedBody = JSONObject().put("message", "SSH key has been deleted.").toString()
        val message = PloiApi.deleteSshKey(token, 1, 2)
        assertEquals("SSH key has been deleted.", message)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/ssh-keys/2", request.url)
    }
}
