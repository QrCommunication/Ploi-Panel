package com.qrcommunication.ploipanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Reversible stand-in for the Keystore cipher; the JVM has no AndroidKeyStore. */
private class ReversingCipher : TokenCipher {
    override fun encrypt(plain: ByteArray): ByteArray = plain.reversedArray()
    override fun decrypt(blob: ByteArray): ByteArray = blob.reversedArray()
}

/** A cipher bound to another device: decryption of a foreign blob fails. */
private class ForeignCipher : TokenCipher {
    override fun encrypt(plain: ByteArray): ByteArray = plain.map { (it + 1).toByte() }.toByteArray()
    override fun decrypt(blob: ByteArray): ByteArray = throw IllegalStateException("Wrong device key")
}

private class CachePrefs : ProfilePrefs {
    val map = mutableMapOf<String, String>()
    override fun read(key: String): String? = map[key]
    override fun write(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

class OfflineCacheTest {
    private val profile = "11111111-2222-3333-4444-555555555555"

    private fun page(vararg names: String, current: Int = 1, last: Int = 1) = ServerPage(
        names.mapIndexed { index, name -> Server(index + 1L, name, "active", "10.0.0.${index + 1}") },
        current, last
    )

    private fun newCache(
        prefs: CachePrefs = CachePrefs(),
        now: () -> Long = { 1_000L },
        cipher: TokenCipher = ReversingCipher()
    ) = OfflineCache(prefs, cipher, now = now)

    @Test fun readsBackTheSavedPage() {
        val cache = newCache(now = { 5_000L })
        cache.saveServers(profile, 2, page("web-1", "web-2", current = 2, last = 4))
        val cached = cache.servers(profile, 2)
        assertNotNull(cached)
        assertEquals(5_000L, cached!!.fetchedAt)
        assertEquals(listOf("web-1", "web-2"), cached.page.servers.map { it.name })
        assertEquals(2, cached.page.currentPage)
        assertEquals(4, cached.page.lastPage)
        assertTrue(cached.page.hasNext)
    }

    @Test fun keepsPagesIndependent() {
        val cache = newCache()
        cache.saveServers(profile, 1, page("a", current = 1, last = 2))
        cache.saveServers(profile, 2, page("b", current = 2, last = 2))
        assertEquals("a", cache.servers(profile, 1)!!.page.servers.single().name)
        assertEquals("b", cache.servers(profile, 2)!!.page.servers.single().name)
        assertNull(cache.servers(profile, 3))
    }

    @Test fun replacingAPageDropsItsPreviousContent() {
        val cache = newCache()
        cache.saveServers(profile, 1, page("old-1", "old-2"))
        cache.saveServers(profile, 1, page("new-1"))
        assertEquals(listOf("new-1"), cache.servers(profile, 1)!!.page.servers.map { it.name })
    }

    @Test fun evictsTheOldestPageBeyondTheBound() {
        val prefs = CachePrefs()
        var clock = 0L
        val cache = newCache(prefs, now = { clock })
        repeat(OfflineCache.MAX_PAGES + 1) { index ->
            clock = (index + 1) * 1_000L
            cache.saveServers(profile, index + 1, page("server-${index + 1}"))
        }
        assertNull("Oldest page must be evicted", cache.servers(profile, 1))
        assertNotNull(cache.servers(profile, 2))
        assertNotNull(cache.servers(profile, OfflineCache.MAX_PAGES + 1))
    }

    @Test fun cachesAreScopedPerProfile() {
        val prefs = CachePrefs()
        val other = "99999999-8888-7777-6666-555555555555"
        val cache = newCache(prefs)
        cache.saveServers(profile, 1, page("mine"))
        assertNull(cache.servers(other, 1))
        cache.saveServers(other, 1, page("theirs"))
        assertEquals("mine", cache.servers(profile, 1)!!.page.servers.single().name)
        cache.clear(profile)
        assertNull(cache.servers(profile, 1))
        assertEquals("theirs", cache.servers(other, 1)!!.page.servers.single().name)
    }

    @Test fun expiredEntriesAreNotServed() {
        val prefs = CachePrefs()
        var clock = 1_000L
        val cache = newCache(prefs, now = { clock })
        cache.saveServers(profile, 1, page("stale"))
        clock = 1_000L + OfflineCache.MAX_AGE_MILLIS
        assertNotNull("The boundary itself is still readable", cache.servers(profile, 1))
        clock = 1_000L + OfflineCache.MAX_AGE_MILLIS + 1
        assertNull(cache.servers(profile, 1))
    }

    @Test fun entriesTimestampedInTheFutureAreRejected() {
        val prefs = CachePrefs()
        var clock = 10_000L
        val cache = newCache(prefs, now = { clock })
        cache.saveServers(profile, 1, page("clock-skew"))
        clock = 10_000L - OfflineCache.MAX_CLOCK_SKEW_MILLIS - 1
        assertNull(cache.servers(profile, 1))
    }

    @Test fun storesNothingInClearText() {
        val prefs = CachePrefs()
        newCache(prefs).saveServers(profile, 1, page("secret-hostname"))
        val stored = prefs.map.values.joinToString(" ")
        assertTrue("Cache must be written through the cipher", stored.isNotEmpty())
        assertTrue("Server names must not be readable at rest", !stored.contains("secret-hostname"))
    }

    @Test fun unreadableBlobYieldsNoCacheInsteadOfCrashing() {
        val prefs = CachePrefs()
        newCache(prefs).saveServers(profile, 1, page("web"))
        val foreign = OfflineCache(prefs, ForeignCipher(), now = { 1_000L })
        assertNull(foreign.servers(profile, 1))
        assertTrue("A transient key failure must not destroy the entry", prefs.map.isNotEmpty())
    }

    @Test fun tamperedPayloadIsIgnored() {
        val prefs = CachePrefs()
        val cache = newCache(prefs)
        cache.saveServers(profile, 1, page("web"))
        val key = prefs.map.keys.single()
        val plain = String(ReversingCipher().decrypt(Base64.getDecoder().decode(prefs.map.getValue(key))),
            StandardCharsets.UTF_8)
        val broken = plain.replace("\"version\":${OfflineCache.VERSION}", "\"version\":${OfflineCache.VERSION + 1}")
        prefs.map[key] = Base64.getEncoder().encodeToString(
            ReversingCipher().encrypt(broken.toByteArray(StandardCharsets.UTF_8))
        )
        assertNull(cache.servers(profile, 1))
    }

    @Test fun garbageValueYieldsNoCache() {
        val prefs = CachePrefs()
        prefs.map[OfflineCache.key(profile)] = "not-base64-@@@"
        assertNull(newCache(prefs).servers(profile, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnInvalidProfileId() {
        newCache().saveServers("../other", 1, page("web"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsANonPositivePage() {
        newCache().saveServers(profile, 0, page("web"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInconsistentPaginationMetadata() {
        newCache().saveServers(profile, 1, ServerPage(emptyList(), 3, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMoreRowsThanPloiCanReturn() {
        val servers = (1..51L).map { Server(it, "s$it", "active", "10.0.0.1") }
        newCache().saveServers(profile, 1, ServerPage(servers, 1, 1))
    }

    @Test fun cachesAnEmptyPageAsAFact() {
        val cache = newCache()
        cache.saveServers(profile, 1, ServerPage(emptyList(), 1, 1))
        val cached = cache.servers(profile, 1)
        assertNotNull(cached)
        assertTrue(cached!!.page.servers.isEmpty())
    }

    @Test fun profileRemovalWipesTheOfflineCache() {
        val prefs = CachePrefs()
        val store = ProfileStore(prefs, ReversingCipher())
        val added = store.add("Production", "token-one")
        OfflineCache(prefs, ReversingCipher(), now = { 1_000L }).saveServers(added.id, 1, page("web"))
        assertTrue(prefs.map.containsKey(OfflineCache.key(added.id)))
        store.remove(added.id)
        assertTrue("Deleting a profile must wipe its cached data",
            !prefs.map.containsKey(OfflineCache.key(added.id)))
    }
}
