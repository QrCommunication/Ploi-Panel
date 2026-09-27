package com.qrcommunication.ploipanel.widget

import com.qrcommunication.ploipanel.MonitorSample
import org.junit.Assert.*
import org.junit.Test

class WidgetDataTest {
    private fun sample(date: String) = MonitorSample("12.7%", "bad", "101", "0.92", date)

    @Test fun `widget configuration rejects invalid ids and metrics`() {
        assertThrows(IllegalArgumentException::class.java) { WidgetConfig("", listOf(1), setOf("cpu")) }
        assertThrows(IllegalArgumentException::class.java) { WidgetConfig("profile", listOf(1, 1), setOf("cpu")) }
        assertThrows(IllegalArgumentException::class.java) { WidgetConfig("profile", listOf(0), setOf("cpu")) }
        assertThrows(IllegalArgumentException::class.java) { WidgetConfig("profile", listOf(1), setOf("token")) }
    }

    @Test fun `invalid and future timestamp are stale`() {
        assertTrue(isStale(sample("not a date"), 100_000L))
        assertTrue(isStale(sample("2025-01-01T00:00:00Z"), 1_900_000_000_000L))
        assertFalse(isStale(sample("2025-01-01T00:00:00Z"), 1_735_689_700_000L))
        assertTrue(isStale(sample("2025-01-01T00:00:00Z"), 1_735_689_700_001L + 30 * 60_000L))
    }

    @Test fun `widget gauge uses selected valid percentages only`() {
        assertEquals(
            listOf(GaugeValue("cpu", 12.7f), GaugeValue("disk", 100f)),
            gaugeValues(sample("2025-01-01T00:00:00Z"), setOf("cpu", "disk", "load"))
        )
        assertTrue(gaugeValues(sample(""), setOf("ram", "load")).isEmpty())
    }

    @Test fun `only real validated monitoring values rendered`() {
        assertEquals("12%", metricValue(sample(""), "cpu"))
        assertEquals("—", metricValue(sample(""), "ram"))
        assertEquals("100%", metricValue(sample(""), "disk"))
        assertEquals("0.92", metricValue(sample(""), "load"))
        assertEquals("—", metricValue(MonitorSample("", "", "", "1e999", ""), "load"))
    }
}
