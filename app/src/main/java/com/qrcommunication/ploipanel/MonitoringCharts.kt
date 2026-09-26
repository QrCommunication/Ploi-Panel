package com.qrcommunication.ploipanel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Only the readings supplied by the API are drawn. A single reading has a gauge, never a trend. */
@Composable
internal fun MonitoringCharts(samples: List<MonitorSample>, modifier: Modifier = Modifier) {
    if (samples.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PercentChart(stringResource(R.string.metric_cpu), samples.map { monitoringPercent(it.cpu) }, MaterialTheme.colorScheme.primary)
        PercentChart(stringResource(R.string.metric_ram), samples.map { monitoringPercent(it.ram) }, MaterialTheme.colorScheme.tertiary)
        PercentChart(stringResource(R.string.metric_disk), samples.map { monitoringPercent(it.disk) }, MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun PercentChart(label: String, values: List<Float?>, accent: Color) {
    val latest = values.lastOrNull()
    val grid = MaterialTheme.colorScheme.outlineVariant
    // An invalid point breaks the line; do not interpolate across an unknown reading.
    val hasTrend = values.size >= 2 && values.zipWithNext().any { (a, b) -> a != null && b != null }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(
                    latest?.let { "${if (it % 1f == 0f) it.toInt().toString() else it.toString()} %" } ?: "—",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent
                )
            }
            PercentProgress(latest, accent)
            if (hasTrend) {
                Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                    val inset = 4.dp.toPx()
                    val width = (size.width - inset * 2).coerceAtLeast(0f)
                    val height = (size.height - inset * 2).coerceAtLeast(0f)
                    for (level in 0..2) {
                        val y = inset + height * level / 2f
                        drawLine(grid, Offset(inset, y), Offset(inset + width, y), 1.dp.toPx())
                    }
                    val points = values.mapIndexed { index, value ->
                        value?.let {
                            Offset(inset + width * index / (values.size - 1), inset + height * (1f - it / 100f))
                        }
                    }
                    points.zipWithNext().forEach { (a, b) ->
                        if (a != null && b != null) drawLine(accent, a, b, 2.dp.toPx(), StrokeCap.Round)
                    }
                    points.forEach { point -> if (point != null) drawCircle(accent, 2.5.dp.toPx(), point) }
                }
            }
        }
    }
}

/** Discard malformed readings rather than plot an invented point. */
internal fun responseTrendValues(responses: List<UptimeResponse>): List<Double> = responses
    .asSequence()
    .filter { it.responseTime.isFinite() && it.responseTime >= 0.0 }
    .sortedBy { it.createdAt }
    .map { it.responseTime }
    .toList()
    .takeLast(50)

@Composable
internal fun ResponseTimeTrend(responses: List<UptimeResponse>) {
    val values = responseTrendValues(responses)
    if (values.size < 2) return
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val min = values.min()
    val max = values.max()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.monitor_response_time), style = MaterialTheme.typography.titleSmall)
            Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                val inset = 4.dp.toPx()
                val width = (size.width - inset * 2).coerceAtLeast(0f)
                val height = (size.height - inset * 2).coerceAtLeast(0f)
                val delta = max - min
                for (level in 0..2) {
                    val y = inset + height * level / 2f
                    drawLine(grid, Offset(inset, y), Offset(inset + width, y), 1.dp.toPx())
                }
                val points = values.mapIndexed { index, value ->
                    val ratio = if (delta == 0.0) 0.5f else ((value - min) / delta).toFloat().coerceIn(0f, 1f)
                    Offset(inset + width * index / (values.size - 1), inset + height * (1f - ratio))
                }
                points.zipWithNext().forEach { (a, b) -> drawLine(accent, a, b, 2.dp.toPx(), StrokeCap.Round) }
                points.forEach { drawCircle(accent, 2.5.dp.toPx(), it) }
            }
            Text(
                stringResource(
                    R.string.monitor_response_range,
                    String.format(Locale.US, "%.3f", min), String.format(Locale.US, "%.3f", max)
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
internal fun PercentProgress(value: Float?, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.fillMaxWidth().height(9.dp)) {
        val y = size.height / 2f
        drawLine(track, Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
        if (value != null && value > 0f) {
            drawLine(color, Offset(0f, y), Offset(size.width * value.coerceIn(0f, 100f) / 100f, y), size.height, StrokeCap.Round)
        }
    }
}
