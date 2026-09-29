package com.qrcommunication.ploipanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the screen-reader contract for information this app draws rather than writes. A spinner or
 * a chart with no description is silent to TalkBack, so those regressions are failures here.
 */
class AccessibilityContractTest {
    private fun repoFile(path: String): File {
        var directory: File? = File(System.getProperty("user.dir") ?: ".")
        while (directory != null) {
            val file = File(directory, path)
            if (file.isFile) return file
            directory = directory.parentFile
        }
        error("Missing $path")
    }

    private fun source(path: String): String = repoFile(path).readText()

    private val ui = "app/src/main/java/com/qrcommunication/ploipanel/"

    private fun uiSources(): List<File> {
        val directory = repoFile("${ui}MainActivity.kt").parentFile
        checkNotNull(directory) { "UI sources have no parent directory" }
        return directory.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test fun percentFormattingDropsOnlyAnExactZeroDecimal() {
        assertEquals("42", formatPercent(42f))
        assertEquals("42.5", formatPercent(42.5f))
        assertEquals("0", formatPercent(0f))
        // An unknown reading has no rendering; callers must say "unknown" instead of printing zero.
        assertNull(formatPercent(null))
    }

    @Test fun trendSummarySpansOnlyKnownReadings() {
        val summary = trendSummary(listOf(10f, null, 90f, 55f))
        requireNotNull(summary)
        assertEquals(55f, summary.latest)
        assertEquals(10f, summary.min)
        assertEquals(90f, summary.max)
        assertTrue(summary.hasKnownReading)
    }

    @Test fun trendSummaryKeepsAnUnparseableLatestReadingUnknown() {
        val summary = trendSummary(listOf(40f, null))
        requireNotNull(summary)
        // The newest reading was unreadable: it must not be back-filled with the previous one.
        assertNull(summary.latest)
        assertEquals(40f, summary.min)
        assertTrue(summary.hasKnownReading)
    }

    @Test fun trendSummaryReportsASeriesWithNoKnownReading() {
        val summary = trendSummary(listOf(null, null))
        requireNotNull(summary)
        assertFalse(summary.hasKnownReading)
        assertNull(summary.min)
        assertNull(summary.max)
        // An empty series has nothing to describe at all.
        assertNull(trendSummary(emptyList()))
    }

    @Test fun noScreenDrawsAnUndescribedSpinner() {
        val offenders = uiSources()
            .filter { it.name != "Accessibility.kt" && it.readText().contains("CircularProgressIndicator") }
            .map { it.name }
        assertEquals(emptyList<String>(), offenders)
        // The single wrapper still carries a description of its own.
        val helper = source("${ui}Accessibility.kt")
        assertTrue(helper.contains("CircularProgressIndicator(modifier.semantics { contentDescription = label })"))
        assertTrue(helper.contains("R.string.a11y_loading"))
    }

    @Test fun monitoringGraphicsAreDescribedInsteadOfSilent() {
        val charts = source("${ui}MonitoringCharts.kt")
        // Both canvases are wrapped by a card that replaces its subtree semantics with one summary.
        assertEquals(2, Regex("clearAndSetSemantics \\{ contentDescription = spoken \\}").findAll(charts).count())
        assertTrue(charts.contains("R.string.a11y_chart_trend"))
        assertTrue(charts.contains("R.string.a11y_chart_single"))
        assertTrue(charts.contains("R.string.a11y_chart_unknown"))
        assertTrue(charts.contains("R.string.a11y_response_trend"))
    }

    @Test fun uptimeBarStatesThresholdInWordsNotOnlyInColour() {
        val monitors = source("${ui}SiteMonitorsScreen.kt")
        assertTrue(monitors.contains("percent >= UPTIME_HEALTHY_PERCENT"))
        assertTrue(monitors.contains("R.string.a11y_uptime_healthy"))
        assertTrue(monitors.contains("R.string.a11y_uptime_degraded"))
        // The threshold lives next to its description so the two can never drift apart.
        assertFalse(monitors.contains("percent >= 95f"))
    }

    @Test fun everyAccessibilityStringIsTranslatedInBothLocales() {
        val keys = Regex("R\\.string\\.(a11y_\\w+)")
            .findAll(uiSources().joinToString("\n") { it.readText() })
            .map { it.groupValues[1] }.toSortedSet()
        assertTrue(keys.isNotEmpty())
        listOf("app/src/main/res/values/strings.xml", "app/src/main/res/values-en/strings.xml").forEach { path ->
            val declared = Regex("<string name=\"(a11y_\\w+)\"").findAll(source(path))
                .map { it.groupValues[1] }.toSortedSet()
            assertEquals("$path is missing accessibility strings", keys, keys.intersect(declared).toSortedSet())
        }
    }
}
