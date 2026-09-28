package com.qrcommunication.ploipanel

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

/**
 * Local site HTTP(S) checks, independent from the Ploi API: the device itself probes
 * user-configured targets. Best effort only — checks run while the app or its worker
 * is alive and must never be presented as guaranteed monitoring.
 *
 * This file holds the pure configuration, persistence and evaluation core so the whole
 * behavior (validation, debounce, state transitions) is unit-testable on the JVM.
 * Scheduling (WorkManager) and widgets build on top of it in later milestones.
 */

internal enum class CheckMethod { GET, HEAD }

/** One user-configured local check target. No secrets are involved: URLs are public probes. */
internal data class MonitoredTarget(
    val id: String,
    val label: String,
    val url: String,
    val method: CheckMethod = CheckMethod.HEAD,
    val timeoutMs: Int = LocalCheckRules.DEFAULT_TIMEOUT_MS,
    val expectedStatuses: Set<Int> = setOf(LocalCheckRules.DEFAULT_EXPECTED_STATUS),
    val debounce: Int = LocalCheckRules.DEFAULT_DEBOUNCE,
)

internal object LocalCheckRules {
    const val MAX_TARGETS = 50
    const val MAX_LABEL_LENGTH = 60
    const val MIN_TIMEOUT_MS = 1_000
    const val MAX_TIMEOUT_MS = 30_000
    const val DEFAULT_TIMEOUT_MS = 10_000
    const val DEFAULT_EXPECTED_STATUS = 200
    const val DEFAULT_DEBOUNCE = 2
    const val MAX_DEBOUNCE = 10

    /** Validates and normalizes a target definition; throws IllegalArgumentException when invalid. */
    fun validate(
        label: String,
        url: String,
        timeoutMs: Int,
        expectedStatuses: Set<Int>,
        debounce: Int,
    ): String {
        val cleanLabel = label.trim()
        require(cleanLabel.isNotEmpty()) { "Check label required" }
        require(cleanLabel.length <= MAX_LABEL_LENGTH) { "Check label too long" }
        require(cleanLabel.none { it.isISOControl() }) { "Invalid characters in check label" }
        val uri = try {
            URI(url.trim())
        } catch (invalid: Exception) {
            throw IllegalArgumentException("Invalid check URL")
        }
        require(uri.scheme == "http" || uri.scheme == "https") { "Check URL must be http or https" }
        require(!uri.host.isNullOrBlank()) { "Check URL must include a host" }
        require(timeoutMs in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) { "Check timeout out of range" }
        require(expectedStatuses.isNotEmpty()) { "At least one accepted status required" }
        require(expectedStatuses.all { it in 100..599 }) { "Accepted status out of range" }
        require(debounce in 1..MAX_DEBOUNCE) { "Debounce out of range" }
        return cleanLabel
    }
}

internal enum class CheckState { UNKNOWN, UP, DOWN }

/** User-visible state transition between two consecutive evaluations of a target. */
internal enum class CheckTransition { NONE, WENT_DOWN, RECOVERED }

/**
 * Decides whether a transition deserves an alert: only the edges UNKNOWN/UP -> DOWN and
 * DOWN -> UP notify; staying DOWN across repeated failures must not spam notifications.
 */
internal fun transitionFor(previous: TargetStatus, next: TargetStatus): CheckTransition = when {
    next.state == CheckState.DOWN && previous.state != CheckState.DOWN -> CheckTransition.WENT_DOWN
    next.state == CheckState.UP && previous.state == CheckState.DOWN -> CheckTransition.RECOVERED
    else -> CheckTransition.NONE
}

/** Parses a comma/space separated list of HTTP statuses; throws IllegalArgumentException on junk. */
internal fun parseExpectedStatuses(text: String): Set<Int> {
    val statuses = text.split(',', ' ', ';').filter { it.isNotBlank() }.map { token ->
        token.toIntOrNull() ?: throw IllegalArgumentException("Invalid accepted status")
    }.toSet()
    require(statuses.isNotEmpty()) { "At least one accepted status required" }
    require(statuses.all { it in 100..599 }) { "Accepted status out of range" }
    return statuses
}

/** Last known evaluation of a target. Stale timestamps must be shown as such in the UI. */
internal data class TargetStatus(
    val state: CheckState = CheckState.UNKNOWN,
    val consecutiveFailures: Int = 0,
    val lastCheckedAt: Long? = null,
    val lastLatencyMs: Long? = null,
    val lastHttpStatus: Int? = null,
)

/**
 * Persistent store for local check targets and their last known status. Labels and URLs
 * are not secrets, so entries live in plain preferences like profile labels do.
 */
internal class LocalCheckStore(private val prefs: ProfilePrefs) {
    companion object {
        const val KEY_TARGETS = "local_checks.targets"
        const val KEY_ALERTS = "local_checks.alerts"
        const val STATUS_PREFIX = "local_checks.status."
    }

    /** Outage/recovery notifications are opt-in: disabled until the user enables them. */
    fun alertsEnabled(): Boolean = prefs.read(KEY_ALERTS) == "1"
    fun setAlertsEnabled(enabled: Boolean) = prefs.write(KEY_ALERTS, if (enabled) "1" else "0")

    fun targets(): List<MonitoredTarget> {
        val raw = prefs.read(KEY_TARGETS) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { index -> decodeTarget(array.getJSONObject(index)) }
        } catch (invalid: Exception) {
            emptyList()
        }
    }

    fun statusOf(id: String): TargetStatus {
        val raw = prefs.read(STATUS_PREFIX + id) ?: return TargetStatus()
        return try {
            val entry = JSONObject(raw)
            TargetStatus(
                state = CheckState.valueOf(entry.getString("state")),
                consecutiveFailures = entry.getInt("failures"),
                lastCheckedAt = if (entry.isNull("checkedAt")) null else entry.getLong("checkedAt"),
                lastLatencyMs = if (entry.isNull("latencyMs")) null else entry.getLong("latencyMs"),
                lastHttpStatus = if (entry.isNull("httpStatus")) null else entry.getInt("httpStatus"),
            )
        } catch (invalid: Exception) {
            TargetStatus()
        }
    }

    fun add(
        label: String,
        url: String,
        method: CheckMethod = CheckMethod.HEAD,
        timeoutMs: Int = LocalCheckRules.DEFAULT_TIMEOUT_MS,
        expectedStatuses: Set<Int> = setOf(LocalCheckRules.DEFAULT_EXPECTED_STATUS),
        debounce: Int = LocalCheckRules.DEFAULT_DEBOUNCE,
    ): MonitoredTarget {
        val cleanLabel = LocalCheckRules.validate(label, url, timeoutMs, expectedStatuses, debounce)
        val existing = targets()
        require(existing.size < LocalCheckRules.MAX_TARGETS) { "Too many check targets" }
        require(existing.none { it.label.equals(cleanLabel, ignoreCase = true) }) { "Duplicate check label" }
        val target = MonitoredTarget(
            id = UUID.randomUUID().toString(),
            label = cleanLabel,
            url = url.trim(),
            method = method,
            timeoutMs = timeoutMs,
            expectedStatuses = expectedStatuses,
            debounce = debounce,
        )
        persist(existing + target)
        return target
    }

    fun update(target: MonitoredTarget) {
        LocalCheckRules.validate(target.label, target.url, target.timeoutMs, target.expectedStatuses, target.debounce)
        val existing = targets()
        require(existing.any { it.id == target.id }) { "Unknown check target" }
        require(existing.none { it.id != target.id && it.label.equals(target.label, ignoreCase = true) }) {
            "Duplicate check label"
        }
        persist(existing.map { if (it.id == target.id) target else it })
    }

    /** Removing a target also wipes its recorded status. */
    fun remove(id: String) {
        persist(targets().filterNot { it.id == id })
        prefs.write(STATUS_PREFIX + id, null)
    }

    fun recordStatus(id: String, status: TargetStatus) {
        require(targets().any { it.id == id }) { "Unknown check target" }
        val entry = JSONObject()
            .put("state", status.state.name)
            .put("failures", status.consecutiveFailures)
            .put("checkedAt", status.lastCheckedAt?.let { JSONObject.wrap(it) } ?: JSONObject.NULL)
            .put("latencyMs", status.lastLatencyMs?.let { JSONObject.wrap(it) } ?: JSONObject.NULL)
            .put("httpStatus", status.lastHttpStatus?.let { JSONObject.wrap(it) } ?: JSONObject.NULL)
        prefs.write(STATUS_PREFIX + id, entry.toString())
    }

    private fun persist(targets: List<MonitoredTarget>) {
        val array = JSONArray()
        targets.forEach { array.put(encodeTarget(it)) }
        prefs.write(KEY_TARGETS, array.toString())
    }

    private fun encodeTarget(target: MonitoredTarget): JSONObject {
        val statuses = JSONArray()
        target.expectedStatuses.sorted().forEach { statuses.put(it) }
        return JSONObject()
            .put("id", target.id)
            .put("label", target.label)
            .put("url", target.url)
            .put("method", target.method.name)
            .put("timeoutMs", target.timeoutMs)
            .put("expectedStatuses", statuses)
            .put("debounce", target.debounce)
    }

    private fun decodeTarget(entry: JSONObject): MonitoredTarget {
        val statuses = entry.getJSONArray("expectedStatuses")
        return MonitoredTarget(
            id = entry.getString("id"),
            label = entry.getString("label"),
            url = entry.getString("url"),
            method = CheckMethod.valueOf(entry.getString("method")),
            timeoutMs = entry.getInt("timeoutMs"),
            expectedStatuses = (0 until statuses.length()).map { statuses.getInt(it) }.toSet(),
            debounce = entry.getInt("debounce"),
        )
    }
}

/** Result of a single probe attempt against a target. */
internal sealed interface ProbeOutcome {
    /** The server answered; the HTTP status may still be unexpected. */
    data class Responded(val httpStatus: Int, val latencyMs: Long) : ProbeOutcome

    /** No usable answer: DNS failure, timeout, TLS error, connection reset... */
    data class Unreachable(val reason: String) : ProbeOutcome
}

/** Performs one probe. Implementations must honor the per-target timeout and never retry internally. */
internal fun interface SiteProber {
    fun probe(target: MonitoredTarget): ProbeOutcome
}

/**
 * Evaluates targets against their expected statuses with failure debouncing: a target only
 * flips to DOWN after [MonitoredTarget.debounce] consecutive failures, so a single flaky
 * probe never raises an alarm. Successes reset the failure counter immediately.
 * The clock is injectable so transitions are unit-testable on the JVM.
 */
internal class SiteCheckEngine(
    private val store: LocalCheckStore,
    private val prober: SiteProber,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Probes one target, persists and returns its new status. */
    fun check(targetId: String): TargetStatus {
        val target = store.targets().firstOrNull { it.id == targetId }
            ?: throw IllegalArgumentException("Unknown check target")
        val previous = store.statusOf(targetId)
        val now = clock()
        val next = when (val outcome = prober.probe(target)) {
            is ProbeOutcome.Responded ->
                if (outcome.httpStatus in target.expectedStatuses) {
                    TargetStatus(
                        state = CheckState.UP,
                        consecutiveFailures = 0,
                        lastCheckedAt = now,
                        lastLatencyMs = outcome.latencyMs,
                        lastHttpStatus = outcome.httpStatus,
                    )
                } else {
                    registerFailure(previous, target.debounce, now, outcome.latencyMs, outcome.httpStatus)
                }
            is ProbeOutcome.Unreachable ->
                registerFailure(previous, target.debounce, now, null, null)
        }
        store.recordStatus(targetId, next)
        return next
    }

    /** Probes every configured target once, in declaration order. */
    fun checkAll(): List<Pair<MonitoredTarget, TargetStatus>> =
        store.targets().map { target -> target to check(target.id) }

    private fun registerFailure(
        previous: TargetStatus,
        debounce: Int,
        now: Long,
        latencyMs: Long?,
        httpStatus: Int?,
    ): TargetStatus {
        val failures = previous.consecutiveFailures + 1
        // Below the debounce threshold, keep the previous state: no alarm on a single failure.
        val state = if (failures >= debounce) CheckState.DOWN else previous.state
        return TargetStatus(
            state = state,
            consecutiveFailures = failures,
            lastCheckedAt = now,
            lastLatencyMs = latencyMs,
            lastHttpStatus = httpStatus,
        )
    }
}

/**
 * Production prober on top of HttpURLConnection. Redirects are not followed: the observed
 * status is reported as-is and the operator decides which statuses are acceptable.
 * Blocking by design; callers must use a background dispatcher.
 */
internal class UrlConnectionSiteProber : SiteProber {
    override fun probe(target: MonitoredTarget): ProbeOutcome {
        val startedAt = System.nanoTime()
        return try {
            val connection = URI(target.url).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = target.method.name
                connection.instanceFollowRedirects = false
                connection.connectTimeout = target.timeoutMs
                connection.readTimeout = target.timeoutMs
                val status = connection.responseCode
                ProbeOutcome.Responded(status, (System.nanoTime() - startedAt) / NANOS_PER_MILLI)
            } finally {
                connection.disconnect()
            }
        } catch (offline: IOException) {
            ProbeOutcome.Unreachable(offline.javaClass.simpleName)
        } catch (invalid: Exception) {
            ProbeOutcome.Unreachable(invalid.javaClass.simpleName)
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
