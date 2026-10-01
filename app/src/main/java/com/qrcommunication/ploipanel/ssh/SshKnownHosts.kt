package com.qrcommunication.ploipanel.ssh

import com.qrcommunication.ploipanel.ProfilePrefs
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

/**
 * Host key algorithms accepted for pinning. `ssh-rsa` relies on SHA-1 signatures and is only
 * accepted for legacy hosts; callers must surface [SshHostKey.isWeakSignature] to the operator.
 */
internal val SSH_HOST_KEY_TYPES = listOf(
    "ssh-ed25519",
    "ecdsa-sha2-nistp256",
    "ecdsa-sha2-nistp384",
    "ecdsa-sha2-nistp521",
    "sk-ssh-ed25519@openssh.com",
    "sk-ecdsa-sha2-nistp256@openssh.com",
    "ssh-rsa",
)

/** One pinned host key, identified by (host, port, keyType) like an OpenSSH known_hosts row. */
internal data class SshHostKey(
    val host: String,
    val port: Int,
    val keyType: String,
    val keyBlob: ByteArray,
) {
    /** OpenSSH-style fingerprint, e.g. `SHA256:abc…` (base64 without padding). */
    fun fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(keyBlob)
        return "SHA256:" + Base64.getEncoder().encodeToString(digest).trimEnd('=')
    }

    val isWeakSignature: Boolean get() = keyType == "ssh-rsa"

    override fun equals(other: Any?): Boolean =
        other is SshHostKey && host == other.host && port == other.port &&
            keyType == other.keyType && keyBlob.contentEquals(other.keyBlob)

    override fun hashCode(): Int = 31 * (31 * (31 * host.hashCode() + port) + keyType.hashCode()) +
        keyBlob.contentHashCode()
}

/** Outcome of comparing a presented host key against the pinned trust store. */
internal enum class SshTrustDecision {
    /** No pinned key for this (host, port, keyType): first contact, explicit confirmation required. */
    UNKNOWN,

    /** Presented key matches the pinned key. */
    TRUSTED,

    /** A different key is pinned: possible MITM or host rebuild. Hard block, never auto-accepted. */
    MISMATCH,
}

/** Result of a bulk known_hosts import; conflicting or malformed lines are never applied. */
internal data class KnownHostsImport(val imported: Int, val errors: List<String>)

/**
 * Per-profile SSH host trust store (trust-on-first-use with explicit re-pin).
 *
 * Rules, by design and without any silent bypass:
 * - a first contact stays [SshTrustDecision.UNKNOWN] until [trust] is called deliberately;
 * - a changed key is [SshTrustDecision.MISMATCH] and [trust] refuses to overwrite it —
 *   only [repin], an explicit operator action, replaces a pinned key;
 * - a corrupted store makes [entries] throw instead of silently forgetting pins; [clear]
 *   is available as an explicit reset.
 *
 * Pins are not secrets (public host keys) and are stored unencrypted, scoped per Ploi profile.
 */
internal class SshHostTrustStore(private val prefs: ProfilePrefs, private val profileId: String) {
    companion object {
        const val DEFAULT_PORT = 22
        const val MAX_ENTRIES = 200
        const val MAX_KEY_BLOB_BYTES = 16 * 1024
        private const val VERSION = 1
        private const val PREFIX = "ssh.known_hosts."
        private val PROFILE_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
        private val HOST_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9.:-]{0,252}")

        fun key(profileId: String): String {
            require(profileId.matches(PROFILE_ID_PATTERN)) { "Invalid profile ID" }
            return PREFIX + profileId
        }

        internal fun validateHost(host: String): String {
            val normalized = host.trim().lowercase(Locale.ROOT)
            require(normalized.matches(HOST_PATTERN)) { "Invalid host name" }
            return normalized
        }

        internal fun validatePort(port: Int): Int {
            require(port in 1..65535) { "Invalid port" }
            return port
        }

        /** Reads the algorithm name embedded at the head of an SSH public key blob, or null. */
        internal fun keyBlobType(blob: ByteArray): String? {
            if (blob.size < 4) return null
            val length = ((blob[0].toInt() and 0xFF) shl 24) or ((blob[1].toInt() and 0xFF) shl 16) or
                ((blob[2].toInt() and 0xFF) shl 8) or (blob[3].toInt() and 0xFF)
            if (length <= 0 || length > 64 || blob.size < 4 + length) return null
            val name = String(blob, 4, length, Charsets.US_ASCII)
            return name.takeIf { it.all { c -> c in '!'..'~' } }
        }
    }

    /** @throws IllegalStateException when the persisted store is corrupted. */
    fun entries(): List<SshHostKey> = load()

    fun evaluate(host: String, port: Int, keyType: String, keyBlob: ByteArray): SshTrustDecision {
        val normalized = validateHost(host)
        validatePort(port)
        val pinned = load().firstOrNull {
            it.host == normalized && it.port == port && it.keyType == keyType
        } ?: return SshTrustDecision.UNKNOWN
        return if (MessageDigest.isEqual(pinned.keyBlob, keyBlob)) {
            SshTrustDecision.TRUSTED
        } else {
            SshTrustDecision.MISMATCH
        }
    }

    /** Pins a key on first contact. Refuses to replace a different pinned key: use [repin]. */
    fun trust(host: String, port: Int, keyType: String, keyBlob: ByteArray): SshHostKey {
        val entry = validated(host, port, keyType, keyBlob)
        when (evaluate(entry.host, entry.port, entry.keyType, entry.keyBlob)) {
            SshTrustDecision.TRUSTED -> return entry
            SshTrustDecision.MISMATCH ->
                throw IllegalStateException("Pinned host key differs; explicit re-pin required")
            SshTrustDecision.UNKNOWN -> Unit
        }
        val current = load()
        require(current.size < MAX_ENTRIES) { "Too many pinned hosts" }
        persist(current + entry)
        return entry
    }

    /** Deliberately replaces a pinned key that no longer matches. Never called implicitly. */
    fun repin(host: String, port: Int, keyType: String, keyBlob: ByteArray): SshHostKey {
        val entry = validated(host, port, keyType, keyBlob)
        require(
            evaluate(entry.host, entry.port, entry.keyType, entry.keyBlob) == SshTrustDecision.MISMATCH
        ) { "Re-pin requires a mismatched pinned key" }
        val next = load().filterNot {
            it.host == entry.host && it.port == entry.port && it.keyType == entry.keyType
        } + entry
        persist(next)
        return entry
    }

    /** Removes pins for a host/port; with [keyType] null, removes every algorithm. */
    fun revoke(host: String, port: Int, keyType: String? = null) {
        val normalized = validateHost(host)
        validatePort(port)
        val next = load().filterNot {
            it.host == normalized && it.port == port && (keyType == null || it.keyType == keyType)
        }
        persist(next)
    }

    /** Explicitly wipes every pinned host for this profile (e.g. corrupted store recovery). */
    fun clear() = prefs.write(key(profileId), null)

    /**
     * Imports OpenSSH known_hosts text. Hashed hosts (`|1|`), wildcard patterns and marker
     * lines (`@cert-authority`, `@revoked`) are unsupported and reported, never applied.
     * A line conflicting with an existing pin is reported and skipped, never overwritten.
     */
    fun importKnownHosts(text: String): KnownHostsImport {
        var imported = 0
        val errors = mutableListOf<String>()
        text.lines().forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            when (val outcome = parseKnownHostsLine(line)) {
                is KnownHostsLine.Invalid -> errors.add("line $lineNumber: ${outcome.reason}")
                is KnownHostsLine.Parsed -> outcome.keys.forEach { parsed ->
                    try {
                        when (evaluate(parsed.host, parsed.port, parsed.keyType, parsed.keyBlob)) {
                            SshTrustDecision.UNKNOWN -> {
                                trust(parsed.host, parsed.port, parsed.keyType, parsed.keyBlob)
                                imported++
                            }
                            SshTrustDecision.TRUSTED -> Unit
                            SshTrustDecision.MISMATCH ->
                                errors.add("line $lineNumber: conflicts with pinned key for ${parsed.host}")
                        }
                    } catch (rejected: Exception) {
                        errors.add("line $lineNumber: ${rejected.message}")
                    }
                }
            }
        }
        return KnownHostsImport(imported, errors)
    }

    private sealed interface KnownHostsLine {
        data class Parsed(val keys: List<SshHostKey>) : KnownHostsLine
        data class Invalid(val reason: String) : KnownHostsLine
    }

    private fun parseKnownHostsLine(line: String): KnownHostsLine {
        if (line.startsWith("@")) return KnownHostsLine.Invalid("marker lines are unsupported")
        val fields = line.split(Regex("\\s+"))
        if (fields.size < 3) return KnownHostsLine.Invalid("expected hosts, key type and key")
        val hosts = fields[0].split(",")
        if (hosts.any { it.isBlank() }) return KnownHostsLine.Invalid("empty host pattern")
        if (hosts.any { it.startsWith("|") }) return KnownHostsLine.Invalid("hashed hosts are unsupported")
        if (hosts.any { it.contains('*') || it.contains('?') || it.startsWith("!") }) {
            return KnownHostsLine.Invalid("host patterns are unsupported")
        }
        val keyType = fields[1]
        if (keyType !in SSH_HOST_KEY_TYPES) return KnownHostsLine.Invalid("unsupported key type $keyType")
        val blob = try {
            Base64.getDecoder().decode(fields[2])
        } catch (invalid: IllegalArgumentException) {
            return KnownHostsLine.Invalid("malformed key data")
        }
        if (blob.isEmpty() || blob.size > MAX_KEY_BLOB_BYTES) {
            return KnownHostsLine.Invalid("key blob size out of bounds")
        }
        if (keyBlobType(blob) != keyType) return KnownHostsLine.Invalid("key blob does not match $keyType")
        val keys = mutableListOf<SshHostKey>()
        for (rawHost in hosts) {
            var host = rawHost
            var port = DEFAULT_PORT
            if (rawHost.startsWith("[")) {
                val closing = rawHost.indexOf("]:")
                if (closing <= 0) return KnownHostsLine.Invalid("malformed [host]:port entry")
                port = rawHost.substring(closing + 2).toIntOrNull()
                    ?: return KnownHostsLine.Invalid("invalid port")
                host = rawHost.substring(1, closing)
            }
            try {
                keys.add(validated(host, port, keyType, blob))
            } catch (invalid: Exception) {
                return KnownHostsLine.Invalid(invalid.message ?: "invalid host entry")
            }
        }
        return KnownHostsLine.Parsed(keys)
    }

    private fun validated(host: String, port: Int, keyType: String, keyBlob: ByteArray): SshHostKey {
        require(keyType in SSH_HOST_KEY_TYPES) { "Unsupported key type" }
        require(keyBlob.isNotEmpty() && keyBlob.size <= MAX_KEY_BLOB_BYTES) { "Key blob size out of bounds" }
        require(keyBlobType(keyBlob) == keyType) { "Key blob does not match key type" }
        return SshHostKey(validateHost(host), validatePort(port), keyType, keyBlob.copyOf())
    }

    private fun load(): List<SshHostKey> {
        val value = prefs.read(key(profileId)) ?: return emptyList()
        return try {
            val root = JSONObject(value)
            require(root.getInt("version") == VERSION)
            val array = root.getJSONArray("entries")
            require(array.length() <= MAX_ENTRIES)
            (0 until array.length()).map { index ->
                val row = array.getJSONObject(index)
                validated(
                    row.getString("host"),
                    row.getInt("port"),
                    row.getString("keyType"),
                    Base64.getDecoder().decode(row.getString("key")),
                )
            }
        } catch (invalid: Exception) {
            throw IllegalStateException("Unreadable SSH host trust store", invalid)
        }
    }

    private fun persist(entries: List<SshHostKey>) {
        if (entries.isEmpty()) {
            prefs.write(key(profileId), null)
            return
        }
        val raw = JSONObject().put("version", VERSION).put("entries", JSONArray().also { array ->
            entries.forEach { entry ->
                array.put(
                    JSONObject()
                        .put("host", entry.host)
                        .put("port", entry.port)
                        .put("keyType", entry.keyType)
                        .put("key", Base64.getEncoder().encodeToString(entry.keyBlob))
                )
            }
        }).toString()
        prefs.write(key(profileId), raw)
    }
}
