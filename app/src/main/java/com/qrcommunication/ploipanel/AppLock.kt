package com.qrcommunication.ploipanel

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Application lock: PIN credential (PBKDF2, never stored in clear), escalating lockout after
 * repeated failures, and an opt-in biometric unlock flag. Biometrics never bypass the PIN:
 * enabling them requires a verified PIN and the credential stays mandatory on the device.
 * The clock is injectable so lockout timing is unit-testable on the JVM.
 */
internal class AppLock(
    private val prefs: ProfilePrefs,
    private val clock: () -> Long = System::currentTimeMillis
) {
    companion object {
        const val KEY_CREDENTIAL = "lock.credential"
        const val KEY_ATTEMPTS = "lock.attempts"
        const val KEY_LOCKED_UNTIL = "lock.locked_until"
        const val KEY_LOCKOUT_LEVEL = "lock.lockout_level"
        const val KEY_BIOMETRIC = "lock.biometric"
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 12
        const val MAX_ATTEMPTS = 5
        const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 300_000L
        private const val ITERATIONS = 210_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
        private const val FORMAT_VERSION = "v1"
        private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    }

    /** Outcome of a PIN verification attempt. */
    sealed interface UnlockResult {
        data object Unlocked : UnlockResult
        data class WrongPin(val attemptsLeft: Int) : UnlockResult
        data class Locked(val remainingMs: Long) : UnlockResult
    }

    fun hasPin(): Boolean = prefs.read(KEY_CREDENTIAL) != null

    fun isLocked(): Boolean = remainingLockMs() > 0

    fun remainingLockMs(): Long {
        val until = prefs.read(KEY_LOCKED_UNTIL)?.toLongOrNull() ?: return 0L
        return (until - clock()).coerceAtLeast(0L)
    }

    fun biometricEnabled(): Boolean = prefs.read(KEY_BIOMETRIC) == "1"

    /** Biometric unlock requires an existing PIN credential; it can never replace it. */
    fun setBiometricEnabled(enabled: Boolean) {
        require(!enabled || hasPin()) { "PIN required before enabling biometrics" }
        prefs.write(KEY_BIOMETRIC, if (enabled) "1" else null)
    }

    /**
     * Stores a new PIN credential after validation, rotating salt and hash. Resets the failure
     * counter and any active lockout: re-provisioning is a trusted operation performed after
     * authentication at the UI layer.
     */
    fun setPin(pin: String) {
        validatePin(pin)
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        val encoder = Base64.getEncoder()
        prefs.write(KEY_CREDENTIAL, "$FORMAT_VERSION:$ITERATIONS:${encoder.encodeToString(salt)}:${encoder.encodeToString(hash)}")
        resetFailures()
    }

    /** Fails closed: a tampered credential consumes an attempt like a wrong PIN. */
    fun verify(pin: String): UnlockResult {
        val remaining = remainingLockMs()
        if (remaining > 0) return UnlockResult.Locked(remaining)
        val stored = prefs.read(KEY_CREDENTIAL) ?: return UnlockResult.WrongPin(MAX_ATTEMPTS)
        val matches = try {
            val parts = stored.split(":")
            require(parts.size == 4 && parts[0] == FORMAT_VERSION)
            val iterations = parts[1].toInt()
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            MessageDigest.isEqual(derive(pin, salt, iterations), expected)
        } catch (tampered: Exception) {
            false
        }
        if (matches) {
            resetFailures()
            return UnlockResult.Unlocked
        }
        val failures = (prefs.read(KEY_ATTEMPTS)?.toIntOrNull() ?: 0) + 1
        prefs.write(KEY_ATTEMPTS, failures.toString())
        if (failures >= MAX_ATTEMPTS) {
            val level = (prefs.read(KEY_LOCKOUT_LEVEL)?.toIntOrNull() ?: 0) + 1
            val duration = (BASE_LOCKOUT_MS shl (level - 1)).coerceAtMost(MAX_LOCKOUT_MS)
            prefs.write(KEY_LOCKOUT_LEVEL, level.toString())
            prefs.write(KEY_LOCKED_UNTIL, (clock() + duration).toString())
            prefs.write(KEY_ATTEMPTS, "0")
            return UnlockResult.Locked(duration)
        }
        return UnlockResult.WrongPin(MAX_ATTEMPTS - failures)
    }

    /** Wipes the credential, failure state and biometric flag. Irreversible: PIN must be set again. */
    fun clear() {
        prefs.write(KEY_CREDENTIAL, null)
        prefs.write(KEY_BIOMETRIC, null)
        resetFailures()
    }

    private fun resetFailures() {
        prefs.write(KEY_ATTEMPTS, null)
        prefs.write(KEY_LOCKED_UNTIL, null)
        prefs.write(KEY_LOCKOUT_LEVEL, null)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return factory.generateSecret(spec).encoded
    }

    /**
     * PIN policy: digits only, 4–12 characters, and no trivial patterns (repeated digit or
     * straight ascending/descending run such as 1111, 1234 or 9876).
     */
    internal fun validatePin(pin: String) {
        require(pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH) { "PIN length out of range" }
        require(pin.all { it.isDigit() }) { "PIN must be digits only" }
        require(pin.toSet().size > 1) { "PIN too weak" }
        val ascending = pin.zipWithNext().all { (a, b) -> b - a == 1 }
        val descending = pin.zipWithNext().all { (a, b) -> a - b == 1 }
        require(!ascending && !descending) { "PIN too weak" }
    }
}
