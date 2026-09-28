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
        const val MAX_MULTI_SERVERS = 4
        val METRICS = setOf("cpu", "ram", "disk", "load")
    }
}

internal fun widgetServerIds(ids: List<Long>, single: Boolean): List<Long> =
    ids.take(if (single) 1 else WidgetConfig.MAX_MULTI_SERVERS)

internal data class WidgetReading(val name: String, val sample: MonitorSample, val fetchedAt: Long)

internal fun sampleTime(date: String): Long? = try {
    Instant.parse(date).toEpochMilli()
} catch (_: DateTimeParseException) {
    try { OffsetDateTime.parse(date).toInstant().toEpochMilli() } catch (_: DateTimeParseException) { null }
}

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
        // Previous app versions allowed six servers; preserve the first four on upgrade.
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
                    row.getString("disk"), row.getString("load"), row.getString("date")), row.getLong("fetched"))
        }.toMap()
    } catch (_: Exception) { emptyMap() }
    }

    fun saveReadings(id: Int, config: WidgetConfig, rows: Map<Long, WidgetReading>) {
        val data = JSONArray()
        rows.filterKeys { it in config.serverIds }.forEach { (serverId, reading) ->
            data.put(JSONObject().put("server", serverId).put("name", reading.name)
                .put("cpu", reading.sample.cpu).put("ram", reading.sample.ram)
                .put("disk", reading.sample.disk).put("load", reading.sample.load)
                .put("date", reading.sample.date).put("fetched", reading.fetchedAt))
        }
        val raw = JSONObject().put("profile", config.profileId).put("rows", data).toString()
        val blob = cipher.encrypt(raw.toByteArray(StandardCharsets.UTF_8))
        prefs.edit { putString("cache.$id", Base64.getEncoder().encodeToString(blob)) }
    }

    fun delete(id: Int) { prefs.edit { remove("config.$id"); remove("cache.$id") } }
}
