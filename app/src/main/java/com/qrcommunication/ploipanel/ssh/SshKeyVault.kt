package com.qrcommunication.ploipanel.ssh

import com.qrcommunication.ploipanel.ProfilePrefs
import com.qrcommunication.ploipanel.TokenCipher
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.UUID

/** Public metadata of a stored SSH private key; the private material itself stays encrypted. */
internal data class SshKeyEntry(
    val id: String,
    val label: String,
    val keyType: String,
    val publicKeyBase64: String,
    val createdAtEpochMillis: Long,
)

/**
 * Per-profile vault of imported SSH private keys. The whole payload (including private key
 * material) is encrypted through [cipher] — Android Keystore in production — so plaintext
 * never reaches storage, logs or Android Auto Backup. `ssh-rsa` is accepted for legacy
 * hosts; callers must surface its weakness like [SshHostKey.isWeakSignature].
 *
 * Key generation, passphrase-protected PEM blocks and agent forwarding are intentionally
 * out of scope for now; encrypted PEM input is rejected with an explicit error.
 */
internal class SshKeyVault(private val prefs: ProfilePrefs, private val cipher: TokenCipher) {
    companion object {
        const val MAX_KEYS = 20
        const val MAX_LABEL_LENGTH = 40
        const val MAX_PRIVATE_KEY_BYTES = 32 * 1024
        private const val VERSION = 1
        private const val PREFIX = "ssh.keys."
        private val PROFILE_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
        private val PRIVATE_KEY_TYPES = listOf(
            "ssh-ed25519",
            "ecdsa-sha2-nistp256",
            "ecdsa-sha2-nistp384",
            "ecdsa-sha2-nistp521",
            "ssh-rsa",
        )
        private val PEM_BEGIN = Regex(
            "-----BEGIN (OPENSSH PRIVATE KEY|RSA PRIVATE KEY|EC PRIVATE KEY|PRIVATE KEY|ENCRYPTED PRIVATE KEY)-----"
        )
        private val BASE64_BODY = Regex("[A-Za-z0-9+/=\\s]+")

        fun key(profileId: String): String {
            require(profileId.matches(PROFILE_ID_PATTERN)) { "Invalid profile ID" }
            return PREFIX + profileId
        }

        internal fun validatePrivateKeyPem(pem: String): String {
            val trimmed = pem.trim()
            require(trimmed.length <= MAX_PRIVATE_KEY_BYTES) { "Private key too large" }
            val begin = PEM_BEGIN.find(trimmed)
                ?: throw IllegalArgumentException("Missing PEM private key header")
            require(trimmed.startsWith(begin.value)) { "Missing PEM private key header" }
            require(!begin.value.contains("ENCRYPTED")) { "Encrypted PEM keys are unsupported" }
            val marker = begin.value.removePrefix("-----BEGIN ").removeSuffix("-----")
            val endMarker = "-----END $marker-----"
            require(trimmed.endsWith(endMarker)) { "Missing PEM private key footer" }
            val body = trimmed.substring(begin.value.length, trimmed.length - endMarker.length)
            val lines = body.lines().map(String::trim).filter(String::isNotEmpty)
            require(lines.isNotEmpty()) { "Empty PEM private key body" }
            if (lines.first().startsWith("Proc-Type:") && lines.first().contains("ENCRYPTED")) {
                throw IllegalArgumentException("Passphrase-protected keys are unsupported")
            }
            val payload = lines.filterNot { it.contains(':') }.joinToString("")
            require(payload.isNotEmpty() && payload.matches(BASE64_BODY)) { "Malformed PEM body" }
            return trimmed
        }

        /**
         * Splits an OpenSSH public key line (`type base64 [comment]`) into its key type and
         * base64 payload. The optional comment is dropped; the payload itself is checked
         * structurally by [validatePublicKey] at import time.
         */
        internal fun parsePublicKeyLine(line: String): Pair<String, String> {
            val fields = line.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
            require(fields.size >= 2) { "Expected key type and key data" }
            val keyType = fields[0]
            require(keyType in PRIVATE_KEY_TYPES) { "Unsupported key type" }
            return keyType to fields[1]
        }

        internal fun validatePublicKey(keyType: String, publicKeyBase64: String): String {
            val trimmed = publicKeyBase64.trim()
            val blob = try {
                Base64.getDecoder().decode(trimmed)
            } catch (invalid: IllegalArgumentException) {
                throw IllegalArgumentException("Malformed public key data")
            }
            require(blob.isNotEmpty() && blob.size <= SshHostTrustStore.MAX_KEY_BLOB_BYTES) {
                "Public key size out of bounds"
            }
            require(SshHostTrustStore.keyBlobType(blob) == keyType) {
                "Public key does not match $keyType"
            }
            return trimmed
        }
    }

    /** @throws IllegalStateException when the vault is unreadable (key wiped, tampering). */
    fun list(profileId: String): List<SshKeyEntry> = load(profileId).map { it.entry }

    fun import(
        profileId: String,
        label: String,
        keyType: String,
        privateKeyPem: String,
        publicKeyBase64: String,
        createdAtEpochMillis: Long = System.currentTimeMillis(),
    ): SshKeyEntry {
        val cleaned = validateLabel(label, list(profileId))
        require(keyType in PRIVATE_KEY_TYPES) { "Unsupported key type" }
        val pem = validatePrivateKeyPem(privateKeyPem)
        val publicKey = validatePublicKey(keyType, publicKeyBase64)
        val current = load(profileId)
        require(current.size < MAX_KEYS) { "Too many SSH keys" }
        val entry = SshKeyEntry(UUID.randomUUID().toString(), cleaned, keyType, publicKey, createdAtEpochMillis)
        persist(profileId, current + StoredKey(entry, pem))
        return entry
    }

    /** Decrypts and returns the PEM block, or null when the key id is unknown. */
    fun privateKeyPem(profileId: String, id: String): String? =
        load(profileId).firstOrNull { it.entry.id == id }?.privatePem

    fun remove(profileId: String, id: String) {
        val current = load(profileId)
        require(current.any { it.entry.id == id }) { "Unknown SSH key" }
        persist(profileId, current.filterNot { it.entry.id == id })
    }

    fun clear(profileId: String) = prefs.write(key(profileId), null)

    private fun validateLabel(label: String, existing: List<SshKeyEntry>): String {
        val cleaned = label.trim()
        require(
            cleaned.length in 1..MAX_LABEL_LENGTH && cleaned.none(Char::isISOControl)
        ) { "Invalid key label" }
        require(existing.none { it.label.equals(cleaned, ignoreCase = true) }) { "Duplicate key label" }
        return cleaned
    }

    private data class StoredKey(val entry: SshKeyEntry, val privatePem: String)

    private fun load(profileId: String): List<StoredKey> {
        val value = prefs.read(key(profileId)) ?: return emptyList()
        return try {
            val plain = cipher.decrypt(Base64.getDecoder().decode(value))
            val raw = try { String(plain, StandardCharsets.UTF_8) } finally { plain.fill(0) }
            val root = JSONObject(raw)
            require(root.getInt("version") == VERSION)
            val array = root.getJSONArray("keys")
            require(array.length() <= MAX_KEYS)
            val keys = (0 until array.length()).map { index ->
                val row = array.getJSONObject(index)
                val entry = SshKeyEntry(
                    row.getString("id"),
                    row.getString("label"),
                    row.getString("keyType"),
                    row.getString("publicKey"),
                    row.getLong("createdAt"),
                )
                require(entry.keyType in PRIVATE_KEY_TYPES)
                require(entry.label.lowercase(Locale.ROOT).isNotEmpty())
                StoredKey(entry, row.getString("privateKey"))
            }
            require(keys.map { it.entry.label.lowercase(Locale.ROOT) }.distinct().size == keys.size)
            keys
        } catch (invalid: Exception) {
            throw IllegalStateException("Unreadable SSH key vault", invalid)
        }
    }

    private fun persist(profileId: String, keys: List<StoredKey>) {
        if (keys.isEmpty()) {
            prefs.write(key(profileId), null)
            return
        }
        val raw = JSONObject().put("version", VERSION).put("keys", JSONArray().also { array ->
            keys.forEach { stored ->
                array.put(
                    JSONObject()
                        .put("id", stored.entry.id)
                        .put("label", stored.entry.label)
                        .put("keyType", stored.entry.keyType)
                        .put("publicKey", stored.entry.publicKeyBase64)
                        .put("createdAt", stored.entry.createdAtEpochMillis)
                        .put("privateKey", stored.privatePem)
                )
            }
        }).toString()
        val plain = raw.toByteArray(StandardCharsets.UTF_8)
        val blob = try { cipher.encrypt(plain) } finally { plain.fill(0) }
        prefs.write(key(profileId), Base64.getEncoder().encodeToString(blob))
    }
}
