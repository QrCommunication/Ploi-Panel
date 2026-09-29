package com.qrcommunication.ploipanel.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.qrcommunication.ploipanel.MonitorSample
import com.qrcommunication.ploipanel.R
import com.qrcommunication.ploipanel.ServerStatusKind
import com.qrcommunication.ploipanel.monitoringPercent
import com.qrcommunication.ploipanel.serverStatusKind
import com.qrcommunication.ploipanel.statusKindLabel

internal data class GaugeValue(val metric: String, val percent: Float)

/** Only known percentages can be represented as bars; load is not a percentage. */
internal fun gaugeValues(sample: MonitorSample, metrics: Set<String>): List<GaugeValue> =
    listOf("cpu" to sample.cpu, "ram" to sample.ram, "disk" to sample.disk)
        .filter { (metric, _) -> metric in metrics }
        .mapNotNull { (metric, raw) -> monitoringPercent(raw)?.let { GaugeValue(metric, it) } }

/** Colour band of a gauge. The number is always printed beside it, so colour is never alone. */
internal enum class GaugeLevel { OK, WARN, CRIT }

internal const val GAUGE_WARN_PERCENT = 75f
internal const val GAUGE_CRIT_PERCENT = 90f

internal fun gaugeLevel(percent: Float): GaugeLevel = when {
    percent >= GAUGE_CRIT_PERCENT -> GaugeLevel.CRIT
    percent >= GAUGE_WARN_PERCENT -> GaugeLevel.WARN
    else -> GaugeLevel.OK
}

/** View ids of one metric block: container, label, value and one bar per colour band. */
internal data class GaugeSlot(val container: Int, val label: Int, val value: Int, val ok: Int, val warn: Int, val crit: Int)

internal val singleGaugeSlots = mapOf(
    "cpu" to GaugeSlot(R.id.widget_metric_cpu, R.id.widget_metric_cpu_label, R.id.widget_metric_cpu_value,
        R.id.widget_metric_cpu_ok, R.id.widget_metric_cpu_warn, R.id.widget_metric_cpu_crit),
    "ram" to GaugeSlot(R.id.widget_metric_ram, R.id.widget_metric_ram_label, R.id.widget_metric_ram_value,
        R.id.widget_metric_ram_ok, R.id.widget_metric_ram_warn, R.id.widget_metric_ram_crit),
    "disk" to GaugeSlot(R.id.widget_metric_disk, R.id.widget_metric_disk_label, R.id.widget_metric_disk_value,
        R.id.widget_metric_disk_ok, R.id.widget_metric_disk_warn, R.id.widget_metric_disk_crit)
)

internal val rowGaugeSlots = mapOf(
    "cpu" to GaugeSlot(R.id.widget_row_cpu, R.id.widget_row_cpu_label, R.id.widget_row_cpu_value,
        R.id.widget_row_cpu_ok, R.id.widget_row_cpu_warn, R.id.widget_row_cpu_crit),
    "ram" to GaugeSlot(R.id.widget_row_ram, R.id.widget_row_ram_label, R.id.widget_row_ram_value,
        R.id.widget_row_ram_ok, R.id.widget_row_ram_warn, R.id.widget_row_ram_crit),
    "disk" to GaugeSlot(R.id.widget_row_disk, R.id.widget_row_disk_label, R.id.widget_row_disk_value,
        R.id.widget_row_disk_ok, R.id.widget_row_disk_warn, R.id.widget_row_disk_crit)
)

/**
 * Shows one native bar per selected metric that has a real percentage; hides the others.
 * A missing or unparseable reading hides its bar (never drawn as zero). Returns how many were shown.
 */
internal fun bindGauges(
    context: Context, view: RemoteViews, slots: Map<String, GaugeSlot>, sample: MonitorSample?, metrics: Set<String>
): Int {
    val known = sample?.let { gaugeValues(it, metrics) }.orEmpty().associateBy { it.metric }
    slots.forEach { (metric, slot) ->
        val value = known[metric]
        if (value == null) {
            view.setViewVisibility(slot.container, View.GONE)
            return@forEach
        }
        val labelRes = when (metric) {
            "cpu" -> R.string.metric_cpu
            "ram" -> R.string.metric_ram
            else -> R.string.metric_disk
        }
        val percent = value.percent.coerceIn(0f, 100f)
        view.setViewVisibility(slot.container, View.VISIBLE)
        view.setTextViewText(slot.label, context.getString(labelRes))
        view.setTextViewText(slot.value, "${percent.toInt()} %")
        val level = gaugeLevel(percent)
        listOf(GaugeLevel.OK to slot.ok, GaugeLevel.WARN to slot.warn, GaugeLevel.CRIT to slot.crit).forEach { (band, id) ->
            if (band == level) {
                view.setViewVisibility(id, View.VISIBLE)
                view.setProgressBar(id, 100, percent.toInt(), false)
            } else view.setViewVisibility(id, View.GONE)
        }
    }
    return known.size
}

/** "● Actif" style label: the dot's colour follows the grouped status and the word is always shown. */
internal fun bindStatus(context: Context, view: RemoteViews, id: Int, rawStatus: String) {
    if (rawStatus.isBlank()) {
        view.setViewVisibility(id, View.GONE)
        return
    }
    val kind = serverStatusKind(rawStatus)
    val color = context.getColor(
        when (kind) {
            ServerStatusKind.HEALTHY -> R.color.widget_ok
            ServerStatusKind.PENDING -> R.color.widget_warn
            ServerStatusKind.ERROR -> R.color.widget_crit
            ServerStatusKind.UNKNOWN -> R.color.widget_unknown
        }
    )
    view.setViewVisibility(id, View.VISIBLE)
    view.setTextViewText(id, "● ${context.getString(statusKindLabel(kind))}")
    view.setTextColor(id, color)
}
