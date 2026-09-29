package com.qrcommunication.ploipanel.widget

import com.qrcommunication.ploipanel.MonitorSample
import org.junit.Assert.*
import org.junit.Test

class WidgetDataTest {
    private fun sample(date: String) = MonitorSample("12.7%", "bad", "101", "0.92", date)

    @Test fun `widget configuration rejects invalid ids and metrics`() {
        assertThrows(IllegalArgumentException::class.java) { WidgetConfig("", listOf(1), setOf("cpu")) }
        assertThrows(IllegalArgumentException::class.java) { WidgetConfig("profile", listOf(1, 2, 3, 4), setOf("cpu")) }
        assertEquals(3, WidgetConfig.MAX_MULTI_SERVERS)
        assertEquals(3, WidgetConfig("profile", listOf(1, 2, 3), setOf("cpu")).serverIds.size)
        assertThrows(IllegalArgumentException::class.java) { WidgetConfig("profile", listOf(1, 1), setOf("cpu")) }
        assertThrows(IllegalArgumentException::class.java) { WidgetConfig("profile", listOf(0), setOf("cpu")) }
        assertThrows(IllegalArgumentException::class.java) { WidgetConfig("profile", listOf(1), setOf("token")) }
    }

    @Test fun `widget modes expose one or up to three distinct server ids`() {
        val ids = listOf(11L, 12L, 13L, 14L, 15L)
        assertEquals(listOf(11L), widgetServerIds(ids, single = true))
        assertEquals(listOf(11L, 12L, 13L), widgetServerIds(ids, single = false))
    }

    @Test fun `single widget keeps exactly one server and replaces it`() {
        var selected = emptySet<Long>()
        selected = toggleWidgetServer(selected, 1, true, single = true)
        selected = toggleWidgetServer(selected, 2, true, single = true)
        assertEquals(setOf(2L), selected)
        assertEquals(1, widgetServerLimit(single = true))
        assertTrue(canPickWidgetServer(selected, 3, single = true))
    }

    @Test fun `multi widget refuses a fourth server and disables other rows`() {
        var selected = emptySet<Long>()
        listOf(1L, 2L, 3L, 4L).forEach { selected = toggleWidgetServer(selected, it, true, single = false) }
        assertEquals(setOf(1L, 2L, 3L), selected)
        assertFalse(canPickWidgetServer(selected, 4, single = false))
        assertTrue("checked rows stay tappable to remove", canPickWidgetServer(selected, 2, single = false))
        selected = toggleWidgetServer(selected, 2, false, single = false)
        assertTrue(canPickWidgetServer(selected, 4, single = false))
        assertEquals(setOf(1L, 3L, 4L), toggleWidgetServer(selected, 4, true, single = false))
    }

    @Test fun `gauge colour band follows the reading`() {
        assertEquals(GaugeLevel.OK, gaugeLevel(0f))
        assertEquals(GaugeLevel.OK, gaugeLevel(74.9f))
        assertEquals(GaugeLevel.WARN, gaugeLevel(75f))
        assertEquals(GaugeLevel.CRIT, gaugeLevel(90f))
        assertEquals(GaugeLevel.CRIT, gaugeLevel(100f))
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
