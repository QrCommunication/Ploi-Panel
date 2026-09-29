package com.qrcommunication.ploipanel

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Screen-reader support for information this app draws instead of writing: progress spinners and
 * the monitoring graphics. Descriptions are built only from readings the API actually returned; a
 * missing reading is announced as unknown and never smoothed into a number.
 */

/** Uptime at or above this percentage is drawn in the accent colour instead of the error colour. */
internal const val UPTIME_HEALTHY_PERCENT = 95f

/**
 * Renders a percentage the way the monitoring cards do: whole numbers without a decimal part.
 * Returns null for an unknown reading so callers must say so rather than print a placeholder zero.
 */
internal fun formatPercent(value: Float?): String? = value?.let {
    if (it % 1f == 0f) it.toInt().toString() else it.toString()
}

/**
 * What a series of percentage readings really contains. [latest] is the most recent reading as
 * received, so it stays null when that reading is unparseable; [min]/[max] span the known readings
 * only. Null for an empty series: there is nothing to describe.
 */
internal data class TrendSummary(val latest: Float?, val min: Float?, val max: Float?) {
    val hasKnownReading: Boolean get() = min != null
}

internal fun trendSummary(values: List<Float?>): TrendSummary? {
    if (values.isEmpty()) return null
    val known = values.filterNotNull()
    return TrendSummary(values.last(), known.minOrNull(), known.maxOrNull())
}

/** An indeterminate spinner that announces itself; a silent spinner is a blank screen to TalkBack. */
@Composable
internal fun BusyIndicator(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.a11y_loading)
    CircularProgressIndicator(modifier.semantics { contentDescription = label })
}

/** Speaks a percentage as words, or says the reading is unknown. */
@Composable
internal fun percentSpeech(value: Float?): String = formatPercent(value)
    ?.let { stringResource(R.string.a11y_percent, it) }
    ?: stringResource(R.string.a11y_unknown)
