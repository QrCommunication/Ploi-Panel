package com.qrcommunication.ploipanel.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.qrcommunication.ploipanel.MonitorSample
import com.qrcommunication.ploipanel.monitoringPercent

internal data class GaugeValue(val metric: String, val percent: Float)

/** Only known percentages can be represented as bars; load is not a percentage. */
internal fun gaugeValues(sample: MonitorSample, metrics: Set<String>): List<GaugeValue> =
    listOf("cpu" to sample.cpu, "ram" to sample.ram, "disk" to sample.disk)
        .filter { (metric, _) -> metric in metrics }
        .mapNotNull { (metric, raw) -> monitoringPercent(raw)?.let { GaugeValue(metric, it) } }

/** Compact bars rendered from the same real sample as the widget text. */
internal fun gaugeBitmap(values: List<GaugeValue>, labels: Map<String, String>): Bitmap? {
    if (values.isEmpty()) return null
    val width = 540
    val rowHeight = 24
    val height = rowHeight * values.size
    val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 16f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 100, 90) }
    val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(133, 226, 192) }
    values.forEachIndexed { index, entry ->
        val y = index * rowHeight.toFloat()
        canvas.drawText(labels[entry.metric] ?: entry.metric.uppercase(), 0f, y + 17f, label)
        val bounds = RectF(90f, y + 7f, width - 6f, y + 17f)
        canvas.drawRoundRect(bounds, 5f, 5f, track)
        val filled = RectF(bounds.left, bounds.top, bounds.left + bounds.width() * entry.percent.coerceIn(0f, 100f) / 100f, bounds.bottom)
        if (filled.width() > 0f) canvas.drawRoundRect(filled, 5f, 5f, bar)
    }
    return bitmap
}
