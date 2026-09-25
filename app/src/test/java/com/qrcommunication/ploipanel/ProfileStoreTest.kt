package com.qrcommunication.ploipanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic reversible cipher standing in for the Keystore-backed cipher on the JVM. */
private class FakeCipher : TokenCipher {
    override fun encrypt(plain: ByteArray): ByteArray = plain.reversedArray()
    override fun decrypt(blob: ByteArray): ByteArray = blob.reversedArray()
}

private class MemoryPrefs : ProfilePrefs {
    val map = mutableMapOf<String, String>()
    override fun read(key: String): String? = map[key]
    override fun write(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

class ProfileStoreTest {
    private fun newStore(prefs: MemoryPrefs = MemoryPrefs()) = ProfileStore(prefs, FakeCipher())

    @Test fun addsAndListsProfilesInOrder() {
        val store = newStore()
        val first = store.add("Production", "token-one")
        val second = store.add("Staging", "token-two")
        assertEquals(listOf(first, second), store.profiles())
        assertEquals("Production", store.profiles()[0].label)
        assertNotEquals(first.id, second.id)
    }

    @Test fun trimsLabels() {
        val store = newStore()
        assertEquals("Prod", store.add("  Prod  ", "token").label)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankLabel() {
        newStore().add("   ", "token")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTooLongLabel() {
        newStore().add("x".repeat(ProfileStore.MAX_LABEL_LENGTH + 1), "token")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsControlCharactersInLabel() {
        newStore().add("bad\nlabel", "token")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateLabelIgnoringCase() {
        val store = newStore()
        store.add("Prod", "token-one")
        store.add("prod", "token-two")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidToken() {
        newStore().add("Prod", "bad\ntoken")
    }

    @Test(expected = IllegalArgumentException::class)
    fun enforcesProfileLimit() {
        val store = newStore()
        repeat(ProfileStore.MAX_PROFILES) { store.add("profile-$it", "token-$it") }
        store.add("one-too-many", "token")
    }

    @Test fun storesTokenEncryptedNeverPlaintext() {
        val prefs = MemoryPrefs()
        val store = newStore(prefs)
        val profile = store.add("Prod", "super-secret-token")
        val blob = prefs.map[ProfileStore.TOKEN_PREFIX + profile.id]
        assertTrue(blob != null && !blob.contains("super-secret-token"))
        assertEquals("super-secret-token", store.tokenFor(profile.id))
    }

    @Test fun removeDeletesTokenAndProfile() {
        val prefs = MemoryPrefs()
        val store = newStore(prefs)
        val keep = store.add("Keep", "token-keep")
        val drop = store.add("Drop", "token-drop")
        store.remove(drop.id)
        assertEquals(listOf(keep), store.profiles())
        assertNull(prefs.map[ProfileStore.TOKEN_PREFIX + drop.id])
        assertNull(store.tokenFor(drop.id))
        assertEquals("token-keep", store.tokenFor(keep.id))
    }

    @Test fun activationRoundTripAndRemovalClearsActive() {
        val store = newStore()
        val first = store.add("A", "token-a")
        val second = store.add("B", "token-b")
        assertNull(store.activeProfileId())
        store.activate(first.id)
        assertEquals(first.id, store.activeProfileId())
        store.activate(second.id)
        assertEquals(second.id, store.activeProfileId())
        store.remove(second.id)
        assertNull(store.activeProfileId())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsActivatingUnknownProfile() {
        newStore().activate("does-not-exist")
    }

    @Test fun deactivateClearsActiveProfile() {
        val store = newStore()
        val profile = store.add("A", "token-a")
        store.activate(profile.id)
        store.deactivate()
        assertNull(store.activeProfileId())
    }

    @Test fun corruptedDirectoryYieldsNoProfilesAndNoActive() {
        val prefs = MemoryPrefs()
        prefs.map[ProfileStore.KEY_PROFILES] = "not json {{"
        prefs.map[ProfileStore.KEY_ACTIVE] = "ghost"
        val store = newStore(prefs)
        assertEquals(emptyList<PloiProfile>(), store.profiles())
        assertNull(store.activeProfileId())
    }

    @Test fun profilesSurviveStoreRecreation() {
        val prefs = MemoryPrefs()
        val profile = newStore(prefs).add("Prod", "persistent-token")
        val reloaded = newStore(prefs)
        assertEquals(listOf(profile), reloaded.profiles())
        assertEquals("persistent-token", reloaded.tokenFor(profile.id))
    }
}
