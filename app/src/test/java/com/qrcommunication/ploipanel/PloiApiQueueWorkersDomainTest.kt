package com.qrcommunication.ploipanel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Parsing, validation and wire-level coverage for every documented route of the
 * `queue-workers` domain (/api/servers/{server}/sites/{id}/queues, 6 routes).
 */
class PloiApiQueueWorkersDomainTest {
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

    /** Documented full worker shape (note the upstream `enviroment` typo). */
    private fun workerEntry(): JSONObject = JSONObject()
        .put("id", 1)
        .put("connection", "database")
        .put("queue", "default")
        .put("maximum_seconds", 30)
        .put("maximum_tries", 3)
        .put("enviroment", JSONObject.NULL)
        .put("sleep", 10)
        .put("processes", 1)
        .put("backoff", 10)
        .put("status", "active")
        .put("site_id", 1)
        .put("server_id", 1)

    // ---- GET /api/servers/{server}/sites/{id}/queues ----

    @Test fun parsesWorkerListWithDocumentedShape() {
        val workers = PloiApi.parseQueueWorkers(
            JSONObject().put("data", JSONArray().put(workerEntry())).toString()
        )
        val worker = workers.single()
        assertEquals(1L, worker.id)
        assertEquals("database", worker.connection)
        assertEquals("default", worker.queue)
        assertEquals(30, worker.maximumSeconds)
        assertEquals(3, worker.maximumTries)
        assertEquals("", worker.environment)
        assertEquals(10, worker.sleep)
        assertEquals(1, worker.processes)
        assertEquals(10, worker.backoff)
        assertEquals("active", worker.status)
        assertEquals(1L, worker.siteId)
        assertEquals(1L, worker.serverId)
    }

    @Test fun queueWorkersHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject().put("data", JSONArray().put(workerEntry())).toString()
        PloiApi.queueWorkers(token, 1, 1)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/queues", request.url)
    }

    // ---- GET /api/servers/{server}/sites/{id}/queues/{queueId} ----

    @Test fun queueWorkerHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = JSONObject().put("data", workerEntry()).toString()
        val worker = PloiApi.queueWorker(token, 1, 1, 1)
        assertEquals(1L, worker.id)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/queues/1", request.url)
    }

    // ---- POST /api/servers/{server}/sites/{id}/queues ----

    @Test fun createQueueWorkerSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = JSONObject().put("data", workerEntry().put("status", "created").put("maximum_tries", JSONObject.NULL)).toString()
        val worker = PloiApi.createQueueWorker(
            token, 1, 1,
            CreateQueueWorkerRequest("database", "default", 30, 30, 2, 10, maximumTries = 3)
        )
        assertEquals("created", worker.status)
        assertNull(worker.maximumTries)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/queues", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("database", body.getString("connection"))
        assertEquals("default", body.getString("queue"))
        assertEquals(30, body.getInt("maximum_seconds"))
        assertEquals(30, body.getInt("sleep"))
        assertEquals(2, body.getInt("processes"))
        assertEquals(10, body.getInt("backoff"))
        assertEquals(3, body.getInt("maximum_tries"))
    }

    @Test fun createQueueWorkerOmitsNullMaximumTries() {
        val body = JSONObject(CreateQueueWorkerRequest("database", "default", 30, 30, 2, 10).toJson())
        assertFalse(body.has("maximum_tries"))
    }

    @Test fun createQueueWorkerRequestRejectsInvalidInput() {
        listOf(
            { CreateQueueWorkerRequest("", "default", 30, 10, 1, 10) },
            { CreateQueueWorkerRequest("database", "", 30, 10, 1, 10) },
            { CreateQueueWorkerRequest("database", "default", -1, 10, 1, 10) },
            { CreateQueueWorkerRequest("database", "default", 30, 10, 0, 10) },
            { CreateQueueWorkerRequest("database", "default", 30, 10, 121, 10) },
            { CreateQueueWorkerRequest("database", "default", 30, 10, 1, -1) },
            { CreateQueueWorkerRequest("database", "default", 30, 10, 1, 10, maximumTries = 0) }
        ).forEach { invalid ->
            try {
                invalid()
                fail("Expected rejection")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // ---- POST /api/servers/{server}/sites/{id}/queues/{queueId}/restart ----

    @Test fun restartQueueWorkerHitsDocumentedRouteAndParsesMessage() {
        installFakeClient()
        scriptedBody = JSONObject().put("message", "Queue worker has been restarted").toString()
        val message = PloiApi.restartQueueWorker(token, 1, 1, 1)
        assertEquals("Queue worker has been restarted", message)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/queues/1/restart", request.url)
    }

    // ---- POST /api/servers/{server}/sites/{id}/queues/{queueId}/toggle-pause ----

    @Test fun togglePauseParsesDocumentedSubset() {
        installFakeClient()
        scriptedBody = JSONObject().put(
            "data",
            JSONObject()
                .put("id", 1)
                .put("connection", "database")
                .put("queue", "default")
                .put("processes", 1)
                .put("status", "paused")
        ).toString()
        val worker = PloiApi.togglePauseQueueWorker(token, 1, 1, 1)
        assertEquals("paused", worker.status)
        assertEquals("database", worker.connection)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/queues/1/toggle-pause", request.url)
    }

    // ---- DELETE /api/servers/{server}/sites/{id}/queues/{queueId} ----

    @Test fun deleteQueueWorkerHitsDocumentedRouteAndParsesMessage() {
        installFakeClient()
        scriptedBody = JSONObject().put("message", "Queue worker has been deleted").toString()
        val message = PloiApi.deleteQueueWorker(token, 1, 1, 1)
        assertEquals("Queue worker has been deleted", message)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/1/sites/1/queues/1", request.url)
    }
}
