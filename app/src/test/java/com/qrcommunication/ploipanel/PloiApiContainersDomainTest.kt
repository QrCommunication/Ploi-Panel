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
 * `containers` domain (/api/servers/{server}/docker/containers, 10 routes).
 */
class PloiApiContainersDomainTest {
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

    private fun containerEntry(): JSONObject = JSONObject()
        .put("id", 1)
        .put("status", "active")
        .put("name", "nginx-app")
        .put("deploy_script", "version: '3'\nservices:\n  web:\n    image: nginx:latest")
        .put("path", "/home/ploi/containers/nginx-app")
        .put("last_deploy_at", JSONObject.NULL)
        .put("created_at", "2025-04-01T10:00:00.000000Z")
        .put("type", "docker")
        .put("state", "running")

    private fun containerListJson(): String = JSONObject()
        .put("data", JSONArray().put(containerEntry()))
        .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
        .toString()

    // ---- GET /api/servers/{server}/docker/containers ----

    @Test fun parsesContainerListWithDocumentedShape() {
        val page = PloiApi.parseContainers(containerListJson())
        assertFalse(page.hasNext)
        val container = page.containers.single()
        assertEquals(1L, container.id)
        assertEquals("active", container.status)
        assertEquals("nginx-app", container.name)
        assertEquals("version: '3'\nservices:\n  web:\n    image: nginx:latest", container.deployScript)
        assertEquals("/home/ploi/containers/nginx-app", container.path)
        assertEquals("", container.lastDeployAt)
        assertEquals("2025-04-01T10:00:00.000000Z", container.createdAt)
        assertEquals("docker", container.type)
        assertEquals("running", container.state)
    }

    @Test fun containersHitsDocumentedRouteWithPagination() {
        installFakeClient()
        scriptedBody = containerListJson()
        PloiApi.containers(token, 1, page = 2, perPage = 50)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/docker/containers?page=2&per_page=50", request.url)
    }

    // ---- GET /api/servers/{server}/docker/containers/{container} ----

    @Test fun parsesSingleContainerAtRootWithoutDataWrapper() {
        val container = PloiApi.parseContainer(containerEntry().toString())
        assertEquals(1L, container.id)
        assertEquals("nginx-app", container.name)
    }

    @Test fun containerHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = containerEntry().toString()
        PloiApi.container(token, 1, 1)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/docker/containers/1", request.url)
    }

    // ---- POST /api/servers/{server}/docker/containers ----

    @Test fun createContainerSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = containerEntry().put("id", 3).put("status", "created").put("state", "stopped").toString()
        val container = PloiApi.createContainer(
            token, 1, ContainerRequest("postgres-db", "version: '3'\nservices:\n  db:\n    image: postgres:15")
        )
        assertEquals(3L, container.id)
        assertEquals("created", container.status)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/docker/containers", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("postgres-db", body.getString("name"))
        assertEquals("version: '3'\nservices:\n  db:\n    image: postgres:15", body.getString("deploy_script"))
    }

    @Test fun containerRequestRejectsInvalidInput() {
        listOf(
            { ContainerRequest("bad name", "script") },
            { ContainerRequest("", "script") },
            { ContainerRequest("ok", "") }
        ).forEach { invalid ->
            try {
                invalid()
                fail("Expected rejection")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // ---- PATCH /api/servers/{server}/docker/containers/{container} ----

    @Test fun updateContainerSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = containerEntry().toString()
        PloiApi.updateContainer(token, 1, 3, ContainerRequest("postgres-database", "version: '3'"))
        val request = recorded.single()
        assertEquals("PATCH", request.method)
        assertEquals("https://ploi.io/api/servers/1/docker/containers/3", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("postgres-database", body.getString("name"))
        assertEquals("version: '3'", body.getString("deploy_script"))
    }

    // ---- DELETE /api/servers/{server}/docker/containers/{container} ----

    @Test fun deleteContainerHitsDocumentedRouteAndParsesMessage() {
        installFakeClient()
        scriptedBody = JSONObject().put("message", "Docker container postgres-database has been marked for deletion").toString()
        val message = PloiApi.deleteContainer(token, 1, 3)
        assertEquals("Docker container postgres-database has been marked for deletion", message)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/docker/containers/3", request.url)
    }

    // ---- POST /api/servers/{server}/docker/containers/{container}/up ----

    @Test fun startContainerHitsDocumentedRouteAndParsesMessage() {
        installFakeClient()
        scriptedBody = JSONObject().put("message", "Docker container nginx-app has been queued for startup").toString()
        val message = PloiApi.startContainer(token, 1, 1)
        assertEquals("Docker container nginx-app has been queued for startup", message)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/docker/containers/1/up", request.url)
        assertEquals(null, request.body)
    }

    @Test fun startContainerSendsFlagsWhenProvided() {
        installFakeClient()
        scriptedBody = JSONObject().put("message", "ok").toString()
        PloiApi.startContainer(token, 1, 1, flags = listOf("--build", "-d"))
        val body = JSONObject(recorded.single().body.orEmpty())
        assertEquals("--build", body.getJSONArray("flags").getString(0))
        assertEquals("-d", body.getJSONArray("flags").getString(1))
    }

    @Test fun startContainerRejectsInvalidFlags() {
        try {
            PloiApi.startContainer(token, 1, 1, flags = listOf("bad flag"))
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    // ---- POST /api/servers/{server}/docker/containers/{container}/down ----

    @Test fun stopContainerHitsDocumentedRouteAndParsesMessage() {
        installFakeClient()
        scriptedBody = JSONObject().put("message", "Docker container nginx-app has been queued for shutdown").toString()
        val message = PloiApi.stopContainer(token, 1, 1)
        assertEquals("Docker container nginx-app has been queued for shutdown", message)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/docker/containers/1/down", request.url)
    }

    // ---- GET /api/servers/{server}/docker/containers/{container}/logs ----

    @Test fun containerLogsHitsDocumentedRouteAndParsesContent() {
        installFakeClient()
        scriptedBody = JSONObject().put("content", "web-1  | 172.17.0.1 - - GET / HTTP/1.1 200").toString()
        val logs = PloiApi.containerLogs(token, 1, 1, lines = 250)
        assertEquals("web-1  | 172.17.0.1 - - GET / HTTP/1.1 200", logs)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/docker/containers/1/logs?lines=250", request.url)
    }

    @Test fun containerLogsRejectsInvalidLineCount() {
        try {
            PloiApi.containerLogs(token, 1, 1, lines = 0)
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    // ---- POST /api/servers/{server}/docker/containers/{container}/site/link ----

    @Test fun linkContainerSiteSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = JSONObject().put("message", "Docker container is being proxied via example.com on port 8080").toString()
        val message = PloiApi.linkContainerSite(token, 1, 1, siteId = 5, port = 8080, host = "example.com")
        assertEquals("Docker container is being proxied via example.com on port 8080", message)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/docker/containers/1/site/link", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals(5L, body.getLong("site_id"))
        assertEquals(8080, body.getInt("port"))
        assertEquals("example.com", body.getString("host"))
    }

    @Test fun linkContainerSiteRejectsInvalidPort() {
        try {
            PloiApi.linkContainerSite(token, 1, 1, siteId = 5, port = 0)
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    // ---- DELETE /api/servers/{server}/docker/containers/{container}/site/unlink ----

    @Test fun unlinkContainerSiteHitsDocumentedRouteAndParsesMessage() {
        installFakeClient()
        scriptedBody = JSONObject().put("message", "Docker container is being unlinked from example.com").toString()
        val message = PloiApi.unlinkContainerSite(token, 1, 1)
        assertEquals("Docker container is being unlinked from example.com", message)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/docker/containers/1/site/unlink", request.url)
    }
}
