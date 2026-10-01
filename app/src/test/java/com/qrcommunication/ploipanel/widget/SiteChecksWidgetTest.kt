package com.qrcommunication.ploipanel.widget

import com.qrcommunication.ploipanel.CheckState
import com.qrcommunication.ploipanel.LocalCheckRules
import com.qrcommunication.ploipanel.MonitoredTarget
import com.qrcommunication.ploipanel.TargetStatus
import com.qrcommunication.ploipanel.isLocalCheckStale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteChecksWidgetTest {
    private fun target(id: String) = MonitoredTarget(id = id, label = "Site $id", url = "https://$id.example.com")

    @Test fun `check widget configuration rejects empty oversized duplicate or blank selections`() {
        assertThrows(IllegalArgumentException::class.java) { SiteCheckWidgetConfig(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            SiteCheckWidgetConfig((1..SiteCheckWidgetConfig.MAX_WIDGET_CHECKS + 1).map { "t$it" })
        }
        assertEquals(10, SiteCheckWidgetConfig.MAX_WIDGET_CHECKS)
        assertThrows(IllegalArgumentException::class.java) { SiteCheckWidgetConfig(listOf("a", "a")) }
        assertThrows(IllegalArgumentException::class.java) { SiteCheckWidgetConfig(listOf(" ")) }
        assertEquals(listOf("a"), SiteCheckWidgetConfig(listOf("a")).targetIds)
    }

    @Test fun `rows follow the selection order and skip removed targets`() {
        val targets = listOf(target("a"), target("b"), target("c"))
        val statuses = mapOf(
            "a" to TargetStatus(CheckState.UP, 0, 1_000L, 42L, 200),
            "c" to TargetStatus(CheckState.DOWN, 3, 1_000L, null, 503),
        )
        val rows = siteCheckRows(targets, { statuses[it] ?: TargetStatus() },
            SiteCheckWidgetConfig(listOf("c", "removed", "a")), now = 1_000L)
        assertEquals(listOf("c", "a"), rows.map { it.targetId })
        assertEquals(listOf("Site c", "Site a"), rows.map { it.label })
        assertEquals(CheckState.DOWN, rows[0].state)
        assertEquals(503, rows[0].httpStatus)
        assertNull(rows[0].latencyMs)
        assertEquals(CheckState.UP, rows[1].state)
        assertEquals(42L, rows[1].latencyMs)
    }

    @Test fun `rows carry only real probe fields and never invent data`() {
        val rows = siteCheckRows(listOf(target("a")), { TargetStatus() },
            SiteCheckWidgetConfig(listOf("a")), now = 1_000L)
        val row = rows.single()
        assertEquals(CheckState.UNKNOWN, row.state)
        assertNull(row.checkedAt)
        assertNull(row.latencyMs)
        assertNull(row.httpStatus)
        assertFalse(row.stale)
    }

    @Test fun `staleness follows the shared watchdog rule`() {
        val now = 10_000_000L
        assertFalse(isLocalCheckStale(null, now))
        assertFalse(isLocalCheckStale(now, now))
        assertFalse(isLocalCheckStale(now - LocalCheckRules.STALE_MS, now))
        assertTrue(isLocalCheckStale(now - LocalCheckRules.STALE_MS - 1, now))
        assertEquals(30 * 60_000L, LocalCheckRules.STALE_MS)
        val rows = siteCheckRows(listOf(target("a")), {
            TargetStatus(CheckState.UP, 0, now - LocalCheckRules.STALE_MS - 1, 10L, 200)
        }, SiteCheckWidgetConfig(listOf("a")), now)
        assertTrue(rows.single().stale)
    }
}
