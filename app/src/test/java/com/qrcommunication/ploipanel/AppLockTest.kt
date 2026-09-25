package com.qrcommunication.ploipanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class LockMemoryPrefs : ProfilePrefs {
    val map = mutableMapOf<String, String>()
    override fun read(key: String): String? = map[key]
    override fun write(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

class AppLockTest {
    private var now = 1_000_000L
    private fun newLock(prefs: LockMemoryPrefs = LockMemoryPrefs()) = AppLock(prefs) { now }

    @Test fun startsWithoutPinAndBiometricsOff() {
        val lock = newLock()
        assertFalse(lock.hasPin())
        assertFalse(lock.biometricEnabled())
        assertFalse(lock.isLocked())
    }

    @Test fun setAndVerifyPin() {
        val lock = newLock()
        lock.setPin("8351")
        assertTrue(lock.hasPin())
        assertEquals(AppLock.UnlockResult.Unlocked, lock.verify("8351"))
    }

    @Test fun credentialIsNeverStoredInClear() {
        val prefs = LockMemoryPrefs()
        newLock(prefs).setPin("8351")
        val stored = prefs.map[AppLock.KEY_CREDENTIAL]
        assertTrue(stored != null && !stored.contains("8351"))
        assertTrue(stored!!.startsWith("v1:"))
    }

    @Test fun samePinProducesDifferentSaltsAcrossStores() {
        val first = LockMemoryPrefs()
        val second = LockMemoryPrefs()
        newLock(first).setPin("8351")
        newLock(second).setPin("8351")
        assertNotEquals(first.map[AppLock.KEY_CREDENTIAL], second.map[AppLock.KEY_CREDENTIAL])
    }

    @Test fun wrongPinDecrementsAttempts() {
        val lock = newLock()
        lock.setPin("8351")
        assertEquals(AppLock.UnlockResult.WrongPin(4), lock.verify("0000"))
        assertEquals(AppLock.UnlockResult.WrongPin(3), lock.verify("0000"))
        assertFalse(lock.isLocked())
    }

    @Test fun correctPinResetsFailureCounter() {
        val lock = newLock()
        lock.setPin("8351")
        lock.verify("0000")
        assertEquals(AppLock.UnlockResult.Unlocked, lock.verify("8351"))
        assertEquals(AppLock.UnlockResult.WrongPin(4), lock.verify("0000"))
    }

    @Test fun lockoutEngagesAfterMaxAttempts() {
        val lock = newLock()
        lock.setPin("8351")
        repeat(AppLock.MAX_ATTEMPTS - 1) { assertTrue(lock.verify("0000") is AppLock.UnlockResult.WrongPin) }
        val result = lock.verify("0000")
        assertTrue(result is AppLock.UnlockResult.Locked)
        assertTrue(lock.isLocked())
    }

    @Test fun correctPinStaysLockedDuringLockout() {
        val lock = newLock()
        lock.setPin("8351")
        repeat(AppLock.MAX_ATTEMPTS) { lock.verify("0000") }
        assertTrue(lock.verify("8351") is AppLock.UnlockResult.Locked)
    }

    @Test fun lockoutExpiresAndAllowsUnlockAgain() {
        val lock = newLock()
        lock.setPin("8351")
        repeat(AppLock.MAX_ATTEMPTS) { lock.verify("0000") }
        now += AppLock.BASE_LOCKOUT_MS + 1
        assertFalse(lock.isLocked())
        assertEquals(AppLock.UnlockResult.Unlocked, lock.verify("8351"))
        assertEquals(0L, lock.remainingLockMs())
    }

    @Test fun lockoutDurationEscalatesAndIsCapped() {
        val lock = newLock()
        lock.setPin("8351")
        val durations = mutableListOf<Long>()
        repeat(12) {
            val result = run {
                var attempt: AppLock.UnlockResult
                do {
                    attempt = lock.verify("0000")
                } while (attempt is AppLock.UnlockResult.WrongPin)
                attempt
            }
            durations.add((result as AppLock.UnlockResult.Locked).remainingMs)
            now += AppLock.MAX_LOCKOUT_MS + 1
        }
        assertEquals(AppLock.BASE_LOCKOUT_MS, durations[0])
        assertEquals(AppLock.BASE_LOCKOUT_MS * 2, durations[1])
        assertTrue(durations.all { it <= AppLock.MAX_LOCKOUT_MS })
        assertEquals(AppLock.MAX_LOCKOUT_MS, durations.last())
    }

    @Test fun tamperedCredentialFailsClosed() {
        val prefs = LockMemoryPrefs()
        val lock = newLock(prefs)
        lock.setPin("8351")
        prefs.map[AppLock.KEY_CREDENTIAL] = "garbage"
        assertTrue(lock.verify("8351") is AppLock.UnlockResult.WrongPin)
        prefs.map[AppLock.KEY_CREDENTIAL] = "v1:abc:not-base64:###"
        assertTrue(lock.verify("8351") is AppLock.UnlockResult.WrongPin)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTooShortPin() { newLock().setPin("123") }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTooLongPin() { newLock().setPin("1".repeat(AppLock.MAX_PIN_LENGTH + 1).replace("11", "12")) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonDigitPin() { newLock().setPin("12a4") }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRepeatedDigitPin() { newLock().setPin("1111") }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAscendingPin() { newLock().setPin("1234") }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDescendingPin() { newLock().setPin("9876") }

    @Test fun acceptsSixDigitNonTrivialPin() {
        val lock = newLock()
        lock.setPin("924061")
        assertEquals(AppLock.UnlockResult.Unlocked, lock.verify("924061"))
    }

    @Test fun setPinRotatesCredentialAndClearsLockout() {
        val prefs = LockMemoryPrefs()
        val lock = newLock(prefs)
        lock.setPin("8351")
        repeat(AppLock.MAX_ATTEMPTS) { lock.verify("0000") }
        assertTrue(lock.isLocked())
        val before = prefs.map[AppLock.KEY_CREDENTIAL]
        lock.setPin("5927")
        assertNotEquals(before, prefs.map[AppLock.KEY_CREDENTIAL])
        assertFalse(lock.isLocked())
        assertTrue(lock.verify("8351") is AppLock.UnlockResult.WrongPin)
        assertEquals(AppLock.UnlockResult.Unlocked, lock.verify("5927"))
    }

    @Test fun biometricFlagRoundTripAndRequiresPin() {
        val lock = newLock()
        try {
            lock.setBiometricEnabled(true)
            error("Expected require() to reject enabling biometrics without a PIN")
        } catch (expected: IllegalArgumentException) {
        }
        lock.setPin("8351")
        lock.setBiometricEnabled(true)
        assertTrue(lock.biometricEnabled())
        lock.setBiometricEnabled(false)
        assertFalse(lock.biometricEnabled())
    }

    @Test fun clearWipesEverything() {
        val prefs = LockMemoryPrefs()
        val lock = newLock(prefs)
        lock.setPin("8351")
        lock.setBiometricEnabled(true)
        repeat(AppLock.MAX_ATTEMPTS) { lock.verify("0000") }
        lock.clear()
        assertFalse(lock.hasPin())
        assertFalse(lock.biometricEnabled())
        assertFalse(lock.isLocked())
        assertNull(prefs.map[AppLock.KEY_CREDENTIAL])
        assertNull(prefs.map[AppLock.KEY_ATTEMPTS])
        assertNull(prefs.map[AppLock.KEY_LOCKED_UNTIL])
        assertNull(prefs.map[AppLock.KEY_LOCKOUT_LEVEL])
        assertNull(prefs.map[AppLock.KEY_BIOMETRIC])
    }
}
