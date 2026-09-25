package com.qrcommunication.ploipanel

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

/** Wire-level request. Headers and body may carry secrets: never log or persist them. */
internal data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String? = null
)

/** Wire-level response with header names normalized to lowercase. */
internal data class HttpResponse(val status: Int, val body: String, val headers: Map<String, String>)

internal fun interface HttpTransport {
    @Throws(IOException::class)
    fun execute(request: HttpRequest): HttpResponse
}

/** Default transport on top of HttpURLConnection. Redirects are disabled to avoid leaking the bearer token. */
internal class UrlConnectionTransport(
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 15_000
) : HttpTransport {
    override fun execute(request: HttpRequest): HttpResponse {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (request.body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { (name, _) -> name.lowercase(Locale.US) }
                .mapValues { (_, values) -> values.firstOrNull().orEmpty() }
            return HttpResponse(status, body, headers)
        } finally {
            connection.disconnect()
        }
    }
}

/** Device is offline or the host is unreachable. */
internal class PloiOfflineException(cause: IOException) : IOException("Ploi unreachable", cause)

/** The API answered 2xx but the payload cannot be parsed. */
internal class PloiMalformedPayloadException(cause: Exception) : Exception("Malformed Ploi payload", cause)

/**
 * Blocking HTTP client for the Ploi API. Designed to be called from a background dispatcher.
 *
 * - One bearer token per profile, validated and sent as an Authorization header only.
 * - Identical in-flight GET requests are deduplicated: concurrent callers share one network call.
 * - 429 responses are retried after the server-provided Retry-After delay (seconds or HTTP date),
 *   capped and bounded; once retries are exhausted a [PloiHttpException] with status 429 is thrown.
 * - I/O failures surface as [PloiOfflineException]; other non-2xx surface as [PloiHttpException].
 */
internal class PloiHttpClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val maxRateLimitRetries: Int = 2,
    private val maxRetryDelayMillis: Long = 30_000,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    init {
        require(maxRateLimitRetries >= 0 && maxRetryDelayMillis > 0) { "Invalid retry policy" }
        require(
            baseUrl.startsWith("https://") ||
                baseUrl.startsWith("http://127.0.0.1") ||
                baseUrl.startsWith("http://localhost")
        ) { "Ploi client requires HTTPS (plain HTTP only for loopback tests)" }
        require(!baseUrl.endsWith("/")) { "Base URL must not end with a slash" }
    }

    private class SharedCall {
        private val done = CountDownLatch(1)
        private var body: String? = null
        private var failure: Exception? = null

        fun complete(result: String?, error: Exception?) {
            body = result
            failure = error
            done.countDown()
        }

        fun await(): String {
            done.await()
            failure?.let { throw it }
            return body ?: throw PloiMalformedPayloadException(IllegalStateException("Missing shared result"))
        }
    }

    private val inFlight = ConcurrentHashMap<String, SharedCall>()

    /**
     * Sends any documented verb. Only plain GETs are deduplicated; writes are always executed
     * once per call so a retry by the caller never collapses into another caller's mutation.
     */
    fun request(method: String, path: String, token: String, body: String? = null): String {
        require(method in VERBS) { "Unsupported HTTP verb: $method" }
        require(path.startsWith("/")) { "API path must start with a slash" }
        require(body == null || method != "GET") { "GET requests must not carry a body" }
        if (method == "GET") return get(path, token)
        PloiApi.validateToken(token)
        return executeWithRateLimitRetry(method, path, token, body)
    }

    fun get(path: String, token: String): String {
        require(path.startsWith("/")) { "API path must start with a slash" }
        // Deduplication key hashes the token so secret material is not copied into map keys.
        val key = "GET " + sha256(PloiApi.validateToken(token)) + " " + path
        val call = SharedCall()
        val leader = inFlight.putIfAbsent(key, call) == null
        if (!leader) return inFlight.getValue(key).await()
        try {
            val body = executeWithRateLimitRetry("GET", path, token, null)
            call.complete(body, null)
            return body
        } catch (failure: Exception) {
            call.complete(null, failure)
            throw failure
        } finally {
            inFlight.remove(key, call)
        }
    }

    private fun executeWithRateLimitRetry(method: String, path: String, token: String, body: String?): String {
        var attempt = 0
        while (true) {
            val response = try {
                transport.execute(
                    HttpRequest(
                        method = method,
                        url = baseUrl + path,
                        headers = buildMap {
                            put("Authorization", "Bearer $token")
                            put("Accept", "application/json")
                            if (body != null) put("Content-Type", "application/json")
                        },
                        body = body
                    )
                )
            } catch (offline: IOException) {
                throw PloiOfflineException(offline)
            }
            if (response.status in 200..299) return response.body
            val retryAfter = response.headers["retry-after"]
            if (response.status == 429 && attempt < maxRateLimitRetries) {
                val delayMillis = parseRetryAfterMillis(retryAfter)?.coerceAtMost(maxRetryDelayMillis)
                if (delayMillis != null && delayMillis > 0) {
                    sleeper(delayMillis)
                    attempt++
                    continue
                }
            }
            throw PloiHttpException(response.status, retryAfter)
        }
    }

    /** Accepts delta-seconds or an HTTP-date per RFC 9110; returns null when unusable. */
    internal fun parseRetryAfterMillis(value: String?): Long? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        trimmed.toLongOrNull()?.let { seconds ->
            return if (seconds in 0..86_400) seconds * 1_000 else null
        }
        val parser = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("GMT") }
        val date = runCatching { parser.parse(trimmed) }.getOrNull() ?: return null
        return (date.time - nowMillis()).coerceAtLeast(0)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    internal companion object {
        const val DEFAULT_BASE_URL = "https://ploi.io/api"
        private val VERBS = setOf("GET", "POST", "PATCH", "PUT", "DELETE")
    }
}
