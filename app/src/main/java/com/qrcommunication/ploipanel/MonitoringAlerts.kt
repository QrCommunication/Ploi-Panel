package com.qrcommunication.ploipanel

import org.json.JSONArray
import org.json.JSONObject

/** Metric that can trigger a threshold alert. Load average is intentionally excluded: it is not a percentage. */
internal enum class AlertMetric { CPU, RAM, DISK }

/**
 * Threshold rule for one server of one profile. A null threshold disables that metric.
 * Thresholds are percentages in 1..100; [sustained] requires N consecutive breaching readings
 * before alerting, to avoid paging on a single spike.
 */
internal data class ThresholdRule(
    val profileId: String,
    val serverId: Long,
    val serverName: String,
    val cpu: Int?,
    val ram: Int?,
    val disk: Int?,
    val sustained: Int = 2,
) {
    init {
        require(serverId > 0) { "Invalid server" }
        require(listOf(cpu, ram, disk).all { it == null || it in 1..100 }) { "Threshold out of range" }
        require(listOf(cpu, ram, disk).any { it != null }) { "At least one threshold required" }
        require(sustained in 1..6) { "Invalid sustain count" }
        require(serverName.isNotBlank() && serverName.length <= 120) { "Invalid server name" }
    }

    fun threshold(metric: AlertMetric): Int? = when (metric) {
        AlertMetric.CPU -> cpu
        AlertMetric.RAM -> ram
        AlertMetric.DISK -> disk
    }

    val key: String get() = "$profileId:$serverId"
}

/** Edge-triggered state of one metric: how many breaching readings in a row, and whether alerted. */
internal data class MetricAlertState(val breaches: Int = 0, val alerting: Boolean = false)

internal enum class ThresholdEvent { BREACHED, RECOVERED }

internal data class ThresholdTransition(val metric: AlertMetric, val event: ThresholdEvent, val value: Float, val threshold: Int)

/**
 * Pure evaluation: returns the new per-metric states and the edges to notify. A missing reading
 * (monitoring not installed, API error) leaves the state untouched — unknown is never "recovered".
 */
internal fun evaluateThresholds(
    rule: ThresholdRule,
    sample: MonitorSample,
    previous: Map<AlertMetric, MetricAlertState>,
): Pair<Map<AlertMetric, MetricAlertState>, List<ThresholdTransition>> {
    val next = previous.toMutableMap()
    val transitions = mutableListOf<ThresholdTransition>()
    AlertMetric.entries.forEach { metric ->
        val threshold = rule.threshold(metric)
        if (threshold == null) {
            next.remove(metric)
            return@forEach
        }
        val raw = when (metric) {
            AlertMetric.CPU -> sample.cpu
            AlertMetric.RAM -> sample.ram
            AlertMetric.DISK -> sample.disk
        }
        val value = monitoringPercent(raw) ?: return@forEach
        val state = previous[metric] ?: MetricAlertState()
        if (value >= threshold) {
            val breaches = (state.breaches + 1).coerceAtMost(rule.sustained)
            val fire = !state.alerting && breaches >= rule.sustained
            next[metric] = MetricAlertState(breaches, state.alerting || fire)
            if (fire) transitions += ThresholdTransition(metric, ThresholdEvent.BREACHED, value, threshold)
        } else {
            // Small hysteresis: recover only once clearly below the threshold, to avoid flapping.
            val recovered = value < threshold - HYSTERESIS_POINTS
            if (state.alerting && recovered) {
                transitions += ThresholdTransition(metric, ThresholdEvent.RECOVERED, value, threshold)
                next[metric] = MetricAlertState()
            } else if (!state.alerting) {
                next[metric] = MetricAlertState()
            }
        }
    }
    return next to transitions
}

internal const val HYSTERESIS_POINTS = 3

/** Device-local store of threshold rules and their alert state. Contains no secret. */
internal class ThresholdAlertStore(private val prefs: ProfilePrefs) {
    companion object {
        const val KEY_RULES = "monitoring.threshold.rules"
        private const val STATE_PREFIX = "monitoring.threshold.state."
        const val MAX_RULES = 50
    }

    fun rules(): List<ThresholdRule> {
        val raw = prefs.read(KEY_RULES) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until minOf(array.length(), MAX_RULES)).mapNotNull { index ->
                val o = array.getJSONObject(index)
                runCatching {
                    ThresholdRule(
                        o.getString("profileId"), o.getLong("serverId"), o.getString("serverName"),
                        o.optInt("cpu", 0).takeIf { it > 0 }, o.optInt("ram", 0).takeIf { it > 0 },
                        o.optInt("disk", 0).takeIf { it > 0 }, o.optInt("sustained", 2)
                    )
                }.getOrNull()
            }
        } catch (corrupted: Exception) {
            emptyList()
        }
    }

    fun rule(profileId: String, serverId: Long): ThresholdRule? =
        rules().firstOrNull { it.profileId == profileId && it.serverId == serverId }

    fun save(rule: ThresholdRule) {
        val others = rules().filterNot { it.key == rule.key }
        require(others.size < MAX_RULES) { "Too many rules" }
        persist(others + rule)
        // New thresholds start from a clean state so an old alert does not linger.
        prefs.write(STATE_PREFIX + rule.key, null)
    }

    fun remove(profileId: String, serverId: Long) {
        persist(rules().filterNot { it.profileId == profileId && it.serverId == serverId })
        prefs.write("$STATE_PREFIX$profileId:$serverId", null)
    }

    fun removeProfile(profileId: String) {
        rules().filter { it.profileId == profileId }.forEach { prefs.write(STATE_PREFIX + it.key, null) }
        persist(rules().filterNot { it.profileId == profileId })
    }

    fun state(rule: ThresholdRule): Map<AlertMetric, MetricAlertState> {
        val raw = prefs.read(STATE_PREFIX + rule.key) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            AlertMetric.entries.mapNotNull { metric ->
                o.optJSONObject(metric.name)?.let { m ->
                    metric to MetricAlertState(m.optInt("breaches", 0), m.optBoolean("alerting", false))
                }
            }.toMap()
        } catch (corrupted: Exception) {
            emptyMap()
        }
    }

    fun saveState(rule: ThresholdRule, state: Map<AlertMetric, MetricAlertState>) {
        val o = JSONObject()
        state.forEach { (metric, s) -> o.put(metric.name, JSONObject().put("breaches", s.breaches).put("alerting", s.alerting)) }
        prefs.write(STATE_PREFIX + rule.key, o.toString())
    }

    private fun persist(rules: List<ThresholdRule>) {
        if (rules.isEmpty()) {
            prefs.write(KEY_RULES, null)
            return
        }
        val array = JSONArray()
        rules.forEach { r ->
            array.put(
                JSONObject().put("profileId", r.profileId).put("serverId", r.serverId).put("serverName", r.serverName)
                    .put("cpu", r.cpu ?: 0).put("ram", r.ram ?: 0).put("disk", r.disk ?: 0).put("sustained", r.sustained)
            )
        }
        prefs.write(KEY_RULES, array.toString())
    }
}

/**
 * Relock policy after the app leaves the foreground. `0` relocks immediately (default, like
 * before). Measured on a monotonic clock so changing the phone time cannot extend the grace.
 */
internal object AutoLockPolicy {
    const val KEY = "lock.grace_seconds"
    val CHOICES = listOf(0, 30, 60, 300, 900)

    fun graceSeconds(prefs: ProfilePrefs): Int = prefs.read(KEY)?.toIntOrNull()?.takeIf { it in CHOICES } ?: 0

    fun setGraceSeconds(prefs: ProfilePrefs, seconds: Int) {
        require(seconds in CHOICES) { "Unsupported delay" }
        prefs.write(KEY, seconds.toString())
    }

    /** True when the app must ask for the PIN again. Unknown or backwards time always relocks. */
    fun mustRelock(stoppedAtElapsedMs: Long?, nowElapsedMs: Long, graceSeconds: Int): Boolean {
        if (stoppedAtElapsedMs == null) return false
        val elapsed = nowElapsedMs - stoppedAtElapsedMs
        return elapsed < 0 || elapsed >= graceSeconds * 1_000L
    }
}
