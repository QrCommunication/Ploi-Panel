package com.qrcommunication.ploipanel

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.io.ByteArrayInputStream
import java.io.InputStream

class PortableConfigurationTest {
    private class Prefs : ProfilePrefs {
        val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String?) {
            if (value == null) values.remove(key) else values[key] = value
        }
        override fun writeBatch(values: Map<String, String?>) {
            this.values.putAll(values.filterValues { it != null }.mapValues { it.value!! })
            values.filterValues { it == null }.keys.forEach(this.values::remove)
        }
    }
    private class CipherForTest : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain.reversedArray()
        override fun decrypt(blob: ByteArray) = blob.reversedArray()
    }
    private class DeviceCipher(private val mask: Int) : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor mask).toByte() }.toByteArray()
        override fun decrypt(blob: ByteArray) = blob.map { (it.toInt() xor mask).toByte() }.toByteArray()
    }
    private val password = "correct-horse-battery-staple"
    private fun manager(prefs: Prefs) = PortableConfigurationManager(prefs, CipherForTest())
    private inline fun <reified T : Throwable> rejects(block: () -> Unit): T {
        try { block() } catch (failure: Throwable) {
            if (failure is T) return failure
            throw failure
        }
        fail("Expected ${T::class.simpleName}")
        error("unreachable")
    }

    @Test fun portableRoundTripToDifferentVaultKeepsPinAndProfilesUsable() {
        val source = Prefs()
        val first = ProfileStore(source, CipherForTest()).add("Prod", "private-token-prod")
        ProfileStore(source, CipherForTest()).add("Stage", "private-token-stage")
        UiPreferences(source).setTheme(AppTheme.DARK)
        UiPreferences(source).setLanguage(AppLanguage.FRENCH)
        val archive = manager(source).export(password)
        val raw = String(archive, StandardCharsets.UTF_8)
        assertFalse(raw.contains("private-token-prod"))
        assertFalse(raw.contains("private-token-stage"))
        assertFalse(raw.contains("\"theme\":\"DARK\""))
        val destination = Prefs()
        val lock = AppLock(destination)
        lock.setPin("8492")
        assertEquals(2, manager(destination).import(archive, password))
        val restored = ProfileStore(destination, CipherForTest())
        assertNotEquals(first.id, restored.profiles().first().id)
        assertEquals("private-token-prod", restored.tokenFor(restored.profiles()[0].id))
        assertEquals("private-token-stage", restored.tokenFor(restored.profiles()[1].id))
        assertEquals(restored.profiles()[0].id, restored.activeProfileId())
        assertEquals(AppTheme.DARK, UiPreferences(destination).theme())
        assertEquals(AppLanguage.FRENCH, UiPreferences(destination).language())
        assertEquals(AppLock.UnlockResult.Unlocked, lock.verify("8492"))
    }

    @Test fun twoDifferentDeviceKeysReencryptImportedTokens() {
        val source = Prefs()
        val sourceCipher = DeviceCipher(0x35)
        val sourceProfile = ProfileStore(source, sourceCipher).add("Prod", "secret-from-device-a")
        val archive = PortableConfigurationManager(source, sourceCipher).export(password)
        val destination = Prefs()
        val destinationCipher = DeviceCipher(0x67)
        assertEquals(1, PortableConfigurationManager(destination, destinationCipher).import(archive, password))
        val restored = ProfileStore(destination, destinationCipher).profiles().single()
        assertEquals("secret-from-device-a", ProfileStore(destination, destinationCipher).tokenFor(restored.id))
        assertNotEquals(source.values[ProfileStore.TOKEN_PREFIX + sourceProfile.id],
            destination.values[ProfileStore.TOKEN_PREFIX + restored.id])
    }

    @Test fun identicalImportIsIdempotentAndPreservesActiveProfile() {
        val source = Prefs()
        ProfileStore(source, CipherForTest()).add("Prod", "token-one")
        val archive = manager(source).export(password)
        val destination = Prefs()
        val existing = ProfileStore(destination, CipherForTest()).add("Staging", "token-two")
        ProfileStore(destination, CipherForTest()).activate(existing.id)
        assertEquals(1, manager(destination).import(archive, password))
        assertEquals(0, manager(destination).import(archive, password))
        assertEquals(existing.id, ProfileStore(destination, CipherForTest()).activeProfileId())
        assertEquals(2, ProfileStore(destination, CipherForTest()).profiles().size)
    }

    @Test fun conflictingProfileOrLimitLeavesEveryPreferenceUnchanged() {
        val source = Prefs()
        ProfileStore(source, CipherForTest()).add("Prod", "new-token")
        val archive = manager(source).export(password)
        val destination = Prefs()
        ProfileStore(destination, CipherForTest()).add("prod", "old-token")
        UiPreferences(destination).setLanguage(AppLanguage.ENGLISH)
        val before = destination.values.toMap()
        rejects<IllegalArgumentException> { manager(destination).import(archive, password) }
        assertEquals(before, destination.values)
        val full = Prefs()
        repeat(ProfileStore.MAX_PROFILES) { ProfileStore(full, CipherForTest()).add("p$it", "token-$it") }
        val fullBefore = full.values.toMap()
        rejects<IllegalArgumentException> { manager(full).import(archive, password) }
        assertEquals(fullBefore, full.values)
    }

    @Test fun badPasswordTamperAndUnsupportedVersionNeverChangeDestination() {
        val source = Prefs()
        ProfileStore(source, CipherForTest()).add("Prod", "token")
        val archive = manager(source).export(password)
        val dest = Prefs()
        val before = dest.values.toMap()
        rejects<IllegalArgumentException> { manager(dest).import(archive, "this-is-wrong-password") }
        val tampered = JSONObject(String(archive, StandardCharsets.UTF_8)).apply {
            val ciphertext = getString("ciphertext").toCharArray()
            val i = ciphertext.indexOfFirst { it != '=' }
            ciphertext[i] = if (ciphertext[i] == 'A') 'B' else 'A'
            put("ciphertext", String(ciphertext))
        }.toString().toByteArray(StandardCharsets.UTF_8)
        rejects<IllegalArgumentException> { manager(dest).import(tampered, password) }
        val version = JSONObject(String(archive, StandardCharsets.UTF_8))
            .put("version", 99).toString().toByteArray(StandardCharsets.UTF_8)
        rejects<IllegalArgumentException> { manager(dest).import(version, password) }
        assertEquals(before, dest.values)
    }

    @Test fun oversizedOrMalformedArchiveCannotBeImported() {
        val prefs = Prefs()
        rejects<IllegalArgumentException> {
            manager(prefs).import(ByteArray(PortableConfigurationCodec.MAX_ARCHIVE_BYTES + 1), password)
        }
        rejects<Exception> { manager(prefs).import("not json".toByteArray(), password) }
        assertTrue(prefs.values.isEmpty())
        assertNull(ProfileStore(prefs, CipherForTest()).activeProfileId())
    }

    @Test fun boundedReaderRejectsOversizedContentAndHandlesZeroLengthReads() {
        rejects<IllegalArgumentException> {
            readLimitedArchive(ByteArrayInputStream(ByteArray(PortableConfigurationCodec.MAX_ARCHIVE_BYTES + 1)))
        }
        val stream = object : InputStream() {
            private var first = true
            override fun read(): Int = if (first) { first = false; 42 } else -1
            override fun read(buffer: ByteArray, off: Int, len: Int): Int = 0
        }
        assertTrue(readLimitedArchive(stream).contentEquals(byteArrayOf(42)))
    }

    @Test fun archiveUsesFreshSaltAndNonce() {
        val prefs = Prefs()
        val first = manager(prefs).export(password)
        val second = manager(prefs).export(password)
        assertFalse(first.contentEquals(second))
    }
}
