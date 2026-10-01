package com.qrcommunication.ploipanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private class AlertPrefs : ProfilePrefs {
    val map = mutableMapOf<String, String>()
    override fun read(key: String): String? = map[key]
    override fun write(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

class MonitoringAlertsTest {
    private val rule = ThresholdRule("p1", 7, "web-1", cpu = 90, ram = null, disk = 80, sustained = 2)
    private fun sample(cpu: String, disk: String = "10", ram: String = "99") = MonitorSample(cpu, ram, disk, "0.5", "2026-09-29")

    @Test fun `alert fires only after sustained breaches and only once`() {
        var state = emptyMap<AlertMetric, MetricAlertState>()
        var (next, events) = evaluateThresholds(rule, sample("95"), state)
        assertTrue(events.isEmpty())
        state = next
        evaluateThresholds(rule, sample("96"), state).also { (n, e) -> next = n; events = e }
        assertEquals(listOf(AlertMetric.CPU), events.map { it.metric })
        assertEquals(ThresholdEvent.BREACHED, events.single().event)
        state = next
        evaluateThresholds(rule, sample("97"), state).also { (n, e) -> next = n; events = e }
        assertTrue("no repeat while still breaching", events.isEmpty())
        state = next
        // Just below threshold: hysteresis keeps the alert.
        evaluateThresholds(rule, sample("89"), state).also { (n, e) -> next = n; events = e }
        assertTrue(events.isEmpty())
        state = next
        evaluateThresholds(rule, sample("50"), state).also { (n, e) -> next = n; events = e }
        assertEquals(ThresholdEvent.RECOVERED, events.single().event)
        assertFalse(next.getValue(AlertMetric.CPU).alerting)
    }

    @Test fun `disabled metric never alerts and unknown value keeps state`() {
        val (_, events) = evaluateThresholds(rule.copy(sustained = 1), sample("1", ram = "100"), emptyMap())
        assertTrue(events.none { it.metric == AlertMetric.RAM })
        val alerting = mapOf(AlertMetric.CPU to MetricAlertState(2, true))
        val (kept, silent) = evaluateThresholds(rule, sample("n/a"), alerting)
        assertTrue(silent.isEmpty())
        assertTrue(kept.getValue(AlertMetric.CPU).alerting)
    }

    @Test fun `single spike below sustain count resets`() {
        val (afterSpike, _) = evaluateThresholds(rule, sample("99"), emptyMap())
        val (afterDrop, _) = evaluateThresholds(rule, sample("10"), afterSpike)
        val (afterSpike2, events) = evaluateThresholds(rule, sample("99"), afterDrop)
        assertTrue(events.isEmpty())
        assertEquals(1, afterSpike2.getValue(AlertMetric.CPU).breaches)
    }

    @Test fun `rule validation`() {
        assertThrows(IllegalArgumentException::class.java) { ThresholdRule("p", 1, "s", null, null, null) }
        assertThrows(IllegalArgumentException::class.java) { ThresholdRule("p", 1, "s", 101, null, null) }
        assertThrows(IllegalArgumentException::class.java) { ThresholdRule("p", 0, "s", 50, null, null) }
        assertThrows(IllegalArgumentException::class.java) { ThresholdRule("p", 1, "s", 50, null, null, sustained = 0) }
    }

    @Test fun `store round trips rules and state, and cleans a profile`() {
        val prefs = AlertPrefs()
        val store = ThresholdAlertStore(prefs)
        store.save(rule)
        store.save(ThresholdRule("p2", 8, "db", null, 70, null))
        assertEquals(rule, store.rule("p1", 7))
        store.saveState(rule, mapOf(AlertMetric.CPU to MetricAlertState(2, true)))
        assertTrue(store.state(rule).getValue(AlertMetric.CPU).alerting)
        // Re-saving a rule resets its state.
        store.save(rule.copy(cpu = 95))
        assertTrue(store.state(rule).isEmpty())
        store.removeProfile("p1")
        assertNull(store.rule("p1", 7))
        assertEquals(1, store.rules().size)
        prefs.map[ThresholdAlertStore.KEY_RULES] = "garbage"
        assertTrue(store.rules().isEmpty())
    }

    @Test fun `auto lock policy uses grace on a monotonic clock`() {
        val prefs = AlertPrefs()
        assertEquals(0, AutoLockPolicy.graceSeconds(prefs))
        AutoLockPolicy.setGraceSeconds(prefs, 60)
        assertEquals(60, AutoLockPolicy.graceSeconds(prefs))
        assertThrows(IllegalArgumentException::class.java) { AutoLockPolicy.setGraceSeconds(prefs, 7) }
        assertFalse(AutoLockPolicy.mustRelock(null, 1_000, 60))
        assertFalse(AutoLockPolicy.mustRelock(1_000, 30_000, 60))
        assertTrue(AutoLockPolicy.mustRelock(1_000, 61_000, 60))
        assertTrue("clock going backwards relocks", AutoLockPolicy.mustRelock(5_000, 1_000, 60))
        prefs.map[AutoLockPolicy.KEY] = "12345"
        assertEquals("unknown stored value falls back to immediate", 0, AutoLockPolicy.graceSeconds(prefs))
    }

    @Test fun `ui wiring contracts`() {
        fun source(name: String): String {
            var root: File? = File(System.getProperty("user.dir") ?: ".")
            while (root != null) {
                val f = File(root, "app/src/main/java/com/qrcommunication/ploipanel/$name")
                if (f.isFile) return f.readText()
                root = root.parentFile
            }
            error(name)
        }
        assertTrue(source("ServerScreen.kt").contains("ThresholdAlertCard(profileId, server)"))
        assertTrue(source("SettingsScreen.kt").contains("AutoLockSetting()"))
        val main = source("MainActivity.kt")
        assertTrue(main.contains("AutoLockPolicy.mustRelock(stoppedAt, SystemClock.elapsedRealtime()"))
        assertTrue(source("ProfileStore.kt").contains("ThresholdAlertStore(prefs).removeProfile(id)"))
        assertTrue("notifications hide details on lock screen", source("ThresholdAlertWorker.kt").contains("VISIBILITY_PRIVATE"))
    }
}
