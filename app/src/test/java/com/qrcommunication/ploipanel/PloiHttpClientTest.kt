package com.qrcommunication.ploipanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class PloiHttpClientTest {
    private val token = "test-token"

    private class ScriptedTransport(vararg val responses: Any) : HttpTransport {
        val requests = CopyOnWriteArrayList<HttpRequest>()
        private val calls = AtomicInteger(0)

        override fun execute(request: HttpRequest): HttpResponse {
            requests.add(request)
            val index = calls.getAndIncrement()
            val scripted = responses.getOrElse(index) { responses.last() }
            if (scripted is IOException) throw scripted
            return scripted as HttpResponse
        }
    }

    private fun ok(body: String = "{\"data\":[]}") = HttpResponse(200, body, emptyMap())
    private fun error(status: Int, retryAfter: String? = null) =
        HttpResponse(status, "{}", if (retryAfter == null) emptyMap() else mapOf("retry-after" to retryAfter))

    private fun client(
        transport: HttpTransport,
        retries: Int = 2,
        sleeps: MutableList<Long> = mutableListOf(),
        now: () -> Long = { 1_000_000L }
    ) = PloiHttpClient(
        transport = transport,
        maxRateLimitRetries = retries,
        maxRetryDelayMillis = 30_000,
        sleeper = { sleeps.add(it) },
        nowMillis = now
    )

    @Test fun sendsBearerAndAcceptHeaders() {
        val transport = ScriptedTransport(ok())
        client(transport).get("/servers?page=1", token)
        val request = transport.requests.single()
        assertEquals("Bearer $token", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Accept"])
        assertEquals("https://ploi.io/api/servers?page=1", request.url)
    }

    @Test fun maps401ToTypedHttpError() {
        val failure = runCatching { client(ScriptedTransport(error(401))).get("/servers", token) }
            .exceptionOrNull()
        assertTrue(failure is PloiHttpException)
        assertEquals(401, (failure as PloiHttpException).status)
    }

    @Test fun maps403ToTypedHttpError() {
        val failure = runCatching { client(ScriptedTransport(error(403))).get("/servers", token) }
            .exceptionOrNull()
        assertEquals(403, (failure as PloiHttpException).status)
    }

    @Test fun retriesOnceAfter429WithDeltaSecondsThenSucceeds() {
        val sleeps = mutableListOf<Long>()
        val transport = ScriptedTransport(error(429, retryAfter = "2"), ok("{\"data\":[1]}"))
        val body = client(transport, sleeps = sleeps).get("/servers", token)
        assertEquals("{\"data\":[1]}", body)
        assertEquals(2, transport.requests.size)
        assertEquals(listOf(2_000L), sleeps)
    }

    @Test fun givesUpAfterMaxRetriesAndKeepsRetryAfterHint() {
        val sleeps = mutableListOf<Long>()
        val transport = ScriptedTransport(error(429, retryAfter = "5"))
        val failure = runCatching {
            client(transport, retries = 2, sleeps = sleeps).get("/servers", token)
        }.exceptionOrNull()
        assertEquals(3, transport.requests.size)
        assertEquals(listOf(5_000L, 5_000L), sleeps)
        assertEquals(429, (failure as PloiHttpException).status)
        assertEquals("5", failure.retryAfterSeconds)
    }

    @Test fun capsRetryDelayAtConfiguredMaximum() {
        val sleeps = mutableListOf<Long>()
        val transport = ScriptedTransport(error(429, retryAfter = "120"), ok())
        client(transport, retries = 1, sleeps = sleeps).get("/servers", token)
        assertEquals(listOf(30_000L), sleeps)
    }

    @Test fun doesNotRetry429WithoutUsableRetryAfter() {
        val transport = ScriptedTransport(error(429))
        val failure = runCatching { client(transport).get("/servers", token) }
            .exceptionOrNull()
        assertEquals(1, transport.requests.size)
        assertEquals(429, (failure as PloiHttpException).status)
    }

    @Test fun honorsHttpDateRetryAfter() {
        val now = 1_700_000_000_000L
        val httpDate = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("GMT") }
            .format(Date(now + 7_000))
        val client = client(ScriptedTransport(ok()), now = { now })
        assertEquals(7_000L, client.parseRetryAfterMillis(httpDate))
    }

    @Test fun rejectsInvalidRetryAfterValues() {
        val client = client(ScriptedTransport(ok()))
        assertNull(client.parseRetryAfterMillis(null))
        assertNull(client.parseRetryAfterMillis(""))
        assertNull(client.parseRetryAfterMillis("soon"))
        assertNull(client.parseRetryAfterMillis("-3"))
        assertNull(client.parseRetryAfterMillis("99999999"))
        assertEquals(0L, client.parseRetryAfterMillis("0"))
        assertEquals(45_000L, client.parseRetryAfterMillis("45"))
    }

    @Test fun wrapsIoFailureAsOffline() {
        val failure = runCatching {
            client(ScriptedTransport(IOException("no route"))).get("/servers", token)
        }.exceptionOrNull()
        assertTrue(failure is PloiOfflineException)
    }

    @Test fun deduplicatesConcurrentIdenticalGets() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger(0)
        val transport = HttpTransport { request ->
            calls.incrementAndGet()
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            ok("shared")
        }
        val client = client(transport)
        val first = AtomicReference<Result<String>>()
        val second = AtomicReference<Result<String>>()
        val threadA = Thread { first.set(runCatching { client.get("/servers", token) }) }
        val threadB = Thread { second.set(runCatching { client.get("/servers", token) }) }
        threadA.start()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        threadB.start()
        Thread.sleep(200)
        release.countDown()
        threadA.join(5_000)
        threadB.join(5_000)
        assertEquals(1, calls.get())
        assertEquals("shared", first.get().getOrThrow())
        assertEquals("shared", second.get().getOrThrow())
    }

    @Test fun doesNotDeduplicateSequentialCalls() {
        val transport = ScriptedTransport(ok())
        val client = client(transport)
        client.get("/servers", token)
        client.get("/servers", token)
        assertEquals(2, transport.requests.size)
    }

    @Test fun dedupFailurePropagatesToFollowers() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val transport = HttpTransport {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            error(500)
        }
        val client = client(transport)
        val first = AtomicReference<Result<String>>()
        val second = AtomicReference<Result<String>>()
        val threadA = Thread { first.set(runCatching { client.get("/servers", token) }) }
        val threadB = Thread { second.set(runCatching { client.get("/servers", token) }) }
        threadA.start()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        threadB.start()
        Thread.sleep(200)
        release.countDown()
        threadA.join(5_000)
        threadB.join(5_000)
        assertEquals(500, (first.get().exceptionOrNull() as PloiHttpException).status)
        assertEquals(500, (second.get().exceptionOrNull() as PloiHttpException).status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPlainHttpBaseUrl() {
        PloiHttpClient(baseUrl = "http://ploi.io/api", transport = ScriptedTransport(ok()))
    }

    @Test fun allowsLoopbackHttpForTests() {
        val transport = ScriptedTransport(ok())
        PloiHttpClient(baseUrl = "http://127.0.0.1:8080", transport = transport).get("/servers", token)
        assertEquals("http://127.0.0.1:8080/servers", transport.requests.single().url)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankToken() {
        client(ScriptedTransport(ok())).get("/servers", "  ")
    }

    @Test fun malformedPayloadSurfacesAsTypedErrorThroughPloiApi() {
        val transport = ScriptedTransport(ok("not-json-at-all"))
        val previous = PloiApi.httpClient
        PloiApi.httpClient = client(transport)
        try {
            PloiApi.servers(token)
            fail("Expected PloiMalformedPayloadException")
        } catch (expected: PloiMalformedPayloadException) {
            // typed error reached the caller
        } finally {
            PloiApi.httpClient = previous
        }
    }

    @Test fun validPageFlowsThroughPloiApiWithInjectedClient() {
        val payload = """{"data":[{"id":1,"name":"web","status":"active","ip_address":"192.0.2.9"}],
            |"meta":{"current_page":1,"last_page":1}}""".trimMargin()
        val transport = ScriptedTransport(ok(payload))
        val previous = PloiApi.httpClient
        PloiApi.httpClient = client(transport)
        try {
            val page = PloiApi.servers(token)
            assertEquals("web", page.servers.single().name)
        } finally {
            PloiApi.httpClient = previous
        }
    }
}
