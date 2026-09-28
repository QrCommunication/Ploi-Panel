package com.qrcommunication.ploipanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class CheckMemoryPrefs : ProfilePrefs {
    val map = mutableMapOf<String, String>()
    override fun read(key: String): String? = map[key]
    override fun write(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

/** Scripted prober: each probe consumes the next queued outcome, cycling on the last one. */
private class FakeProber(vararg outcomes: ProbeOutcome) : SiteProber {
    private val queue = outcomes.toMutableList()
    val probedUrls = mutableListOf<String>()
    override fun probe(target: MonitoredTarget): ProbeOutcome {
        probedUrls.add(target.url)
        if (queue.isEmpty()) return ProbeOutcome.Unreachable("no-outcome")
        val next = queue.removeAt(0)
        if (queue.isEmpty()) queue.add(next) // keep repeating the last outcome
        return next
    }
}

class LocalSiteCheckTest {
    private fun newStore(prefs: CheckMemoryPrefs = CheckMemoryPrefs()) = LocalCheckStore(prefs)

    private fun engine(
        store: LocalCheckStore,
        prober: SiteProber,
        clock: () -> Long = { 1_000L },
    ) = SiteCheckEngine(store, prober, clock)

    // --- Target validation -------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankLabel() {
        newStore().add("   ", "https://example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTooLongLabel() {
        newStore().add("x".repeat(LocalCheckRules.MAX_LABEL_LENGTH + 1), "https://example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsControlCharactersInLabel() {
        newStore().add("bad\u0007label", "https://example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonHttpScheme() {
        newStore().add("ftp", "ftp://example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUrlWithoutHost() {
        newStore().add("nohost", "https://")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedUrl() {
        newStore().add("broken", "ht tp://exa mple")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTimeoutBelowRange() {
        newStore().add("t", "https://example.com", timeoutMs = LocalCheckRules.MIN_TIMEOUT_MS - 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTimeoutAboveRange() {
        newStore().add("t", "https://example.com", timeoutMs = LocalCheckRules.MAX_TIMEOUT_MS + 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyExpectedStatuses() {
        newStore().add("t", "https://example.com", expectedStatuses = emptySet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangeExpectedStatus() {
        newStore().add("t", "https://example.com", expectedStatuses = setOf(200, 99))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDebounceAboveRange() {
        newStore().add("t", "https://example.com", debounce = LocalCheckRules.MAX_DEBOUNCE + 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroDebounce() {
        newStore().add("t", "https://example.com", debounce = 0)
    }

    @Test
    fun acceptsPlainHttpTarget() {
        val target = newStore().add("plain", "http://192.168.1.10:8080/health")
        assertEquals("http://192.168.1.10:8080/health", target.url)
    }

    // --- Store behavior ------------------------------------------------------

    @Test
    fun addsAndListsTargetsInOrderWithDefaults() {
        val store = newStore()
        val first = store.add("Site A", "https://a.example.com")
        val second = store.add("Site B", "https://b.example.com", method = CheckMethod.GET)
        assertEquals(listOf(first, second), store.targets())
        assertEquals(CheckMethod.HEAD, first.method)
        assertEquals(CheckMethod.GET, second.method)
        assertEquals(LocalCheckRules.DEFAULT_TIMEOUT_MS, first.timeoutMs)
        assertEquals(setOf(LocalCheckRules.DEFAULT_EXPECTED_STATUS), first.expectedStatuses)
        assertNotEquals(first.id, second.id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateLabelCaseInsensitive() {
        val store = newStore()
        store.add("Prod", "https://a.example.com")
        store.add("prod", "https://b.example.com")
    }

    @Test
    fun enforcesTargetLimit() {
        val store = newStore()
        repeat(LocalCheckRules.MAX_TARGETS) { store.add("site-$it", "https://$it.example.com") }
        try {
            store.add("overflow", "https://overflow.example.com")
            throw AssertionError("Expected target limit rejection")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Too many check targets", expected.message)
        }
    }

    @Test
    fun targetsAndStatusSurviveStoreRecreation() {
        val prefs = CheckMemoryPrefs()
        val store = LocalCheckStore(prefs)
        val target = store.add(
            "Persisted", "https://p.example.com",
            method = CheckMethod.GET, timeoutMs = 5_000,
            expectedStatuses = setOf(200, 204), debounce = 3,
        )
        store.recordStatus(
            target.id,
            TargetStatus(CheckState.DOWN, consecutiveFailures = 3, lastCheckedAt = 42L, lastLatencyMs = 120L, lastHttpStatus = 500),
        )
        val reloaded = LocalCheckStore(prefs)
        assertEquals(listOf(target), reloaded.targets())
        assertEquals(
            TargetStatus(CheckState.DOWN, 3, 42L, 120L, 500),
            reloaded.statusOf(target.id),
        )
    }

    @Test
    fun removeWipesTargetAndItsStatus() {
        val prefs = CheckMemoryPrefs()
        val store = LocalCheckStore(prefs)
        val kept = store.add("Keep", "https://keep.example.com")
        val dropped = store.add("Drop", "https://drop.example.com")
        store.recordStatus(dropped.id, TargetStatus(CheckState.UP, 0, 1L, 10L, 200))
        store.recordStatus(kept.id, TargetStatus(CheckState.UP, 0, 1L, 11L, 200))
        store.remove(dropped.id)
        assertEquals(listOf(kept), store.targets())
        assertEquals(TargetStatus(), LocalCheckStore(prefs).statusOf(dropped.id))
        assertEquals(CheckState.UP, LocalCheckStore(prefs).statusOf(kept.id).state)
    }

    @Test
    fun updateReplacesTargetDefinition() {
        val store = newStore()
        val target = store.add("Old", "https://old.example.com")
        store.update(target.copy(label = "New", url = "https://new.example.com", debounce = 5))
        val reloaded = store.targets().single()
        assertEquals("New", reloaded.label)
        assertEquals("https://new.example.com", reloaded.url)
        assertEquals(5, reloaded.debounce)
    }

    @Test(expected = IllegalArgumentException::class)
    fun updateRejectsUnknownTarget() {
        val store = newStore()
        store.add("Known", "https://known.example.com")
        store.update(MonitoredTarget("missing-id", "Ghost", "https://ghost.example.com"))
    }

    @Test
    fun corruptedStorageReadsAsEmpty() {
        val prefs = CheckMemoryPrefs()
        prefs.map[LocalCheckStore.KEY_TARGETS] = "{not-json"
        assertTrue(LocalCheckStore(prefs).targets().isEmpty())
    }

    // --- Engine transitions ---------------------------------------------------

    @Test
    fun acceptedStatusMarksTargetUpWithLatency() {
        val store = newStore()
        val target = store.add("A", "https://a.example.com")
        val status = engine(store, FakeProber(ProbeOutcome.Responded(200, 87L))).check(target.id)
        assertEquals(CheckState.UP, status.state)
        assertEquals(0, status.consecutiveFailures)
        assertEquals(87L, status.lastLatencyMs)
        assertEquals(200, status.lastHttpStatus)
        assertEquals(1_000L, status.lastCheckedAt)
        assertEquals(status, store.statusOf(target.id))
    }

    @Test
    fun customExpectedStatusesAreHonored() {
        val store = newStore()
        val target = store.add("A", "https://a.example.com", expectedStatuses = setOf(200, 301, 302))
        val status = engine(store, FakeProber(ProbeOutcome.Responded(301, 10L))).check(target.id)
        assertEquals(CheckState.UP, status.state)
    }

    @Test
    fun singleFailureBelowDebounceKeepsUnknownState() {
        val store = newStore()
        val target = store.add("A", "https://a.example.com", debounce = 3)
        val check = engine(store, FakeProber(ProbeOutcome.Unreachable("SocketTimeoutException")))
        val status = check.check(target.id)
        assertEquals(CheckState.UNKNOWN, status.state)
        assertEquals(1, status.consecutiveFailures)
    }

    @Test
    fun targetGoesDownOnlyAfterDebounceThreshold() {
        val store = newStore()
        val target = store.add("A", "https://a.example.com", debounce = 3)
        val check = engine(store, FakeProber(ProbeOutcome.Unreachable("ConnectException")))
        check.check(target.id)
        check.check(target.id)
        assertEquals(CheckState.UNKNOWN, store.statusOf(target.id).state)
        val third = check.check(target.id)
        assertEquals(CheckState.DOWN, third.state)
        assertEquals(3, third.consecutiveFailures)
        assertNull(third.lastHttpStatus)
    }

    @Test
    fun unexpectedStatusCountsAsFailureButKeepsHttpStatus() {
        val store = newStore()
        val target = store.add("A", "https://a.example.com", debounce = 1)
        val status = engine(store, FakeProber(ProbeOutcome.Responded(503, 250L))).check(target.id)
        assertEquals(CheckState.DOWN, status.state)
        assertEquals(503, status.lastHttpStatus)
        assertEquals(250L, status.lastLatencyMs)
    }

    @Test
    fun successResetsFailureCounterImmediately() {
        val store = newStore()
        val target = store.add("A", "https://a.example.com", debounce = 2)
        val check = engine(
            store,
            FakeProber(
                ProbeOutcome.Unreachable("ConnectException"),
                ProbeOutcome.Unreachable("ConnectException"),
                ProbeOutcome.Responded(200, 30L),
            ),
        )
        check.check(target.id)
        assertEquals(CheckState.DOWN, check.check(target.id).state)
        val recovered = check.check(target.id)
        assertEquals(CheckState.UP, recovered.state)
        assertEquals(0, recovered.consecutiveFailures)
    }

    @Test
    fun singleFailureWhileUpDoesNotFlapToDown() {
        val store = newStore()
        val target = store.add("A", "https://a.example.com", debounce = 2)
        val check = engine(
            store,
            FakeProber(
                ProbeOutcome.Responded(200, 10L),
                ProbeOutcome.Responded(500, 20L),
            ),
        )
        assertEquals(CheckState.UP, check.check(target.id).state)
        val flaky = check.check(target.id)
        assertEquals(CheckState.UP, flaky.state)
        assertEquals(1, flaky.consecutiveFailures)
        assertEquals(500, flaky.lastHttpStatus)
    }

    @Test(expected = IllegalArgumentException::class)
    fun checkingUnknownTargetThrows() {
        engine(newStore(), FakeProber(ProbeOutcome.Responded(200, 1L))).check("missing-id")
    }

    @Test
    fun checkAllProbesEveryTargetInOrder() {
        val store = newStore()
        val first = store.add("A", "https://a.example.com")
        val second = store.add("B", "https://b.example.com")
        val prober = FakeProber(ProbeOutcome.Responded(200, 5L))
        val results = engine(store, prober).checkAll()
        assertEquals(listOf(first, second), results.map { it.first })
        assertTrue(results.all { it.second.state == CheckState.UP })
        assertEquals(listOf(first.url, second.url), prober.probedUrls)
    }

    // --- Alerts preference ------------------------------------------------------

    @Test
    fun alertsDefaultToDisabledAndPersist() {
        val prefs = CheckMemoryPrefs()
        assertTrue(!LocalCheckStore(prefs).alertsEnabled())
        LocalCheckStore(prefs).setAlertsEnabled(true)
        assertTrue(LocalCheckStore(prefs).alertsEnabled())
        LocalCheckStore(prefs).setAlertsEnabled(false)
        assertTrue(!LocalCheckStore(prefs).alertsEnabled())
    }

    // --- Status parsing -----------------------------------------------------------

    @Test
    fun parsesStatusListsWithMixedSeparators() {
        assertEquals(setOf(200), parseExpectedStatuses("200"))
        assertEquals(setOf(200, 204, 301), parseExpectedStatuses("200, 204;301"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonNumericStatusToken() {
        parseExpectedStatuses("200, ok")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankStatusList() {
        parseExpectedStatuses(" , ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangeStatusInList() {
        parseExpectedStatuses("200, 42")
    }

    // --- Notification transitions -------------------------------------------------

    @Test
    fun notifiesOnlyOnStateEdges() {
        val unknown = TargetStatus(CheckState.UNKNOWN, 0)
        val up = TargetStatus(CheckState.UP, 0)
        val down = TargetStatus(CheckState.DOWN, 3)
        assertEquals(CheckTransition.WENT_DOWN, transitionFor(unknown, down))
        assertEquals(CheckTransition.WENT_DOWN, transitionFor(up, down))
        assertEquals(CheckTransition.RECOVERED, transitionFor(down, up))
        assertEquals(CheckTransition.NONE, transitionFor(down, down))
        assertEquals(CheckTransition.NONE, transitionFor(up, up))
        assertEquals(CheckTransition.NONE, transitionFor(unknown, up))
        assertEquals(CheckTransition.NONE, transitionFor(up, unknown))
    }
}
