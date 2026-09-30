package com.qrcommunication.ploipanel.widget

import android.content.Context
import androidx.core.content.edit
import com.qrcommunication.ploipanel.KeystoreTokenCipher
import com.qrcommunication.ploipanel.MonitorSample
import com.qrcommunication.ploipanel.ProfileStore
import com.qrcommunication.ploipanel.SharedPreferencesProfilePrefs
import com.qrcommunication.ploipanel.monitoringPercent
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Base64

internal data class WidgetConfig(val profileId: String, val serverIds: List<Long>, val metrics: Set<String>) {
    init {
        require(profileId.isNotBlank() && serverIds.isNotEmpty() && serverIds.size <= MAX_MULTI_SERVERS)
        require(serverIds.all { it > 0 } && serverIds.distinct().size == serverIds.size)
        require(metrics.isNotEmpty() && metrics.all { it in METRICS })
    }
    companion object {
        /** Multi-server widget shows at most three servers; the single widget exactly one. */
        const val MAX_MULTI_SERVERS = 3
        val METRICS = setOf("cpu", "ram", "disk", "load")
    }
}

internal fun widgetServerIds(ids: List<Long>, single: Boolean): List<Long> =
    ids.take(if (single) 1 else WidgetConfig.MAX_MULTI_SERVERS)

/** Most servers a widget of this kind may hold: 1 for the single widget, 3 for the multi widget. */
internal fun widgetServerLimit(single: Boolean): Int = if (single) 1 else WidgetConfig.MAX_MULTI_SERVERS

/**
 * Selection rule of the configuration screen. Single: choosing a server replaces the previous
 * one (radio). Multi: adding beyond the limit is refused and the selection is returned unchanged.
 */
internal fun toggleWidgetServer(selected: Set<Long>, serverId: Long, wanted: Boolean, single: Boolean): Set<Long> = when {
    single -> if (wanted) setOf(serverId) else selected
    !wanted -> selected - serverId
    serverId in selected -> selected
    selected.size >= widgetServerLimit(false) -> selected
    else -> selected + serverId
}

/** Whether an unchecked row may still be chosen; used to disable rows once the cap is reached. */
internal fun canPickWidgetServer(selected: Set<Long>, serverId: Long, single: Boolean): Boolean =
    single || serverId in selected || selected.size < widgetServerLimit(false)

internal data class WidgetReading(val name: String, val sample: MonitorSample, val fetchedAt: Long, val status: String = "")

/**
 * Monitoring timestamps. ISO-8601 with an offset is honoured; Ploi's `/monitor` endpoint actually
 * sends `yyyy-MM-dd HH:mm:ss[.SSS]` without a zone, which is UTC (cross-checked against the
 * account-local labels of `/servers/monitored`). Anything else is unknown (null), never "now".
 */
internal fun sampleTime(date: String): Long? {
    val text = date.trim()
    if (text.isEmpty()) return null
    try { return Instant.parse(text).toEpochMilli() } catch (_: DateTimeParseException) { }
    try { return OffsetDateTime.parse(text).toInstant().toEpochMilli() } catch (_: DateTimeParseException) { }
    return try {
        java.time.LocalDateTime.parse(text, PLOI_SAMPLE_FORMAT).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
    } catch (_: DateTimeParseException) { null }
}

private val PLOI_SAMPLE_FORMAT: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .optionalStart().appendFraction(java.time.temporal.ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
        .toFormatter(java.util.Locale.ROOT)

internal fun isStale(sample: MonitorSample, now: Long): Boolean {
    val time = sampleTime(sample.date) ?: return true
    return time > now + 60_000L || now - time > 20 * 60_000L
}

internal fun metricValue(sample: MonitorSample, metric: String): String = when (metric) {
    "cpu" -> monitoringPercent(sample.cpu)?.let { "${it.toInt()}%" } ?: "—"
    "ram" -> monitoringPercent(sample.ram)?.let { "${it.toInt()}%" } ?: "—"
    "disk" -> monitoringPercent(sample.disk)?.let { "${it.toInt()}%" } ?: "—"
    "load" -> sample.load.trim().takeIf { it.matches(Regex("[0-9]+(?:\\.[0-9]+)?")) } ?: "—"
    else -> "—"
}

/** Config contains only opaque profile/server IDs. Readings are encrypted separately from config. */
internal class WidgetData(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ploi_widget", Context.MODE_PRIVATE)
    private val cipher = KeystoreTokenCipher("ploi-panel.widget-readings")
    val profiles = ProfileStore(SharedPreferencesProfilePrefs(context), KeystoreTokenCipher())

    fun config(id: Int): WidgetConfig? = try {
        val obj = JSONObject(prefs.getString("config.$id", null) ?: return null)
        val ids = obj.getJSONArray("servers")
        val metrics = obj.getJSONArray("metrics")
        // Older versions allowed six, then four servers; keep the first three on upgrade.
        WidgetConfig(obj.getString("profile"), (0 until ids.length()).map { ids.getLong(it) }
            .take(WidgetConfig.MAX_MULTI_SERVERS),
            (0 until metrics.length()).map { metrics.getString(it) }.toSet())
    } catch (_: Exception) { null }

    fun save(id: Int, config: WidgetConfig) {
        val obj = JSONObject().put("profile", config.profileId)
            .put("servers", JSONArray(config.serverIds)).put("metrics", JSONArray(config.metrics.sorted()))
        prefs.edit { putString("config.$id", obj.toString()); remove("cache.$id") }
    }

    fun readings(id: Int, config: WidgetConfig): Map<Long, WidgetReading> {
      return try {
        val blob = Base64.getDecoder().decode(prefs.getString("cache.$id", null) ?: return emptyMap())
        val obj = JSONObject(String(cipher.decrypt(blob), StandardCharsets.UTF_8))
        if (obj.getString("profile") != config.profileId) return emptyMap()
        val rows = obj.getJSONArray("rows")
        (0 until rows.length()).mapNotNull { i ->
            val row = rows.getJSONObject(i)
            val serverId = row.getLong("server")
            if (serverId !in config.serverIds) null else serverId to WidgetReading(
                row.getString("name"), MonitorSample(row.getString("cpu"), row.getString("ram"),
                    row.getString("disk"), row.getString("load"), row.getString("date")), row.getLong("fetched"),
                row.optString("status"))
        }.toMap()
    } catch (_: Exception) { emptyMap() }
    }

    fun saveReadings(id: Int, config: WidgetConfig, rows: Map<Long, WidgetReading>) {
        val data = JSONArray()
        rows.filterKeys { it in config.serverIds }.forEach { (serverId, reading) ->
            data.put(JSONObject().put("server", serverId).put("name", reading.name)
                .put("cpu", reading.sample.cpu).put("ram", reading.sample.ram)
                .put("disk", reading.sample.disk).put("load", reading.sample.load)
                .put("date", reading.sample.date).put("fetched", reading.fetchedAt)
                .put("status", reading.status))
        }
        val raw = JSONObject().put("profile", config.profileId).put("rows", data).toString()
        val blob = cipher.encrypt(raw.toByteArray(StandardCharsets.UTF_8))
        prefs.edit { putString("cache.$id", Base64.getEncoder().encodeToString(blob)) }
    }

    fun delete(id: Int) { prefs.edit { remove("config.$id"); remove("cache.$id") } }
}
