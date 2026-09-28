package com.qrcommunication.ploipanel

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal data class PortableProfile(
    val label: String, val token: String, val templates: List<DeployScriptTemplate> = emptyList()
)
internal data class PortableConfiguration(
    val profiles: List<PortableProfile>, val theme: AppTheme, val language: AppLanguage
)

/** Passphrase-derived, authenticated, versioned archive; never serialize a Keystore blob. */
internal object PortableConfigurationCodec {
    const val MAX_ARCHIVE_BYTES = 2_097_152
    const val MIN_PASSPHRASE_LENGTH = 12
    private const val MAX_PASSPHRASE_LENGTH = 256
    private const val FORMAT = "ploi-panel-config"
    private const val VERSION = 2
    private const val LEGACY_VERSION = 1
    private const val ITERATIONS = 310_000
    private const val SALT_LENGTH = 16
    private const val NONCE_LENGTH = 12
    private fun aad(version: Int) = "$FORMAT:$version".toByteArray(StandardCharsets.US_ASCII)
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun seal(config: PortableConfiguration, passphrase: String): ByteArray {
        validatePassphrase(passphrase)
        require(config.profiles.size <= ProfileStore.MAX_PROFILES)
        val plain = JSONObject().put("version", VERSION)
            .put("theme", config.theme.name).put("language", config.language.name)
            .put("profiles", JSONArray().also { array ->
                config.profiles.forEach { profile ->
                    validateProfile(profile)
                    array.put(JSONObject().put("label", profile.label).put("token", profile.token)
                        .put("templates", JSONArray().also { templates ->
                            profile.templates.forEach { template ->
                                templates.put(JSONObject().put("id", template.id)
                                    .put("name", template.name).put("content", template.content))
                            }
                        }))
                }
            }).toString().toByteArray(StandardCharsets.UTF_8)
        require(plain.size <= MAX_ARCHIVE_BYTES / 2) { "Configuration too large" }
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(NONCE_LENGTH).also { SecureRandom().nextBytes(it) }
        val encrypted = try { crypt(Cipher.ENCRYPT_MODE, passphrase, salt, nonce, plain, VERSION) }
            finally { plain.fill(0) }
        return JSONObject().put("format", FORMAT).put("version", VERSION)
            .put("kdf", "PBKDF2WithHmacSHA256").put("iterations", ITERATIONS)
            .put("salt", encoder.encodeToString(salt)).put("nonce", encoder.encodeToString(nonce))
            .put("ciphertext", encoder.encodeToString(encrypted))
            .toString().toByteArray(StandardCharsets.UTF_8).also {
                require(it.size <= MAX_ARCHIVE_BYTES) { "Archive too large" }
            }
    }

    fun open(archive: ByteArray, passphrase: String): PortableConfiguration {
        validatePassphrase(passphrase)
        require(archive.size in 1..MAX_ARCHIVE_BYTES) { "Invalid archive size" }
        try {
            val envelope = JSONObject(String(archive, StandardCharsets.UTF_8))
            val version = envelope.getInt("version")
            require(envelope.getString("format") == FORMAT && version in LEGACY_VERSION..VERSION) {
                "Unsupported archive format"
            }
            val sizeLimit = if (version == LEGACY_VERSION) 1_048_576 else MAX_ARCHIVE_BYTES
            require(envelope.getString("kdf") == "PBKDF2WithHmacSHA256" && envelope.getInt("iterations") == ITERATIONS) {
                "Unsupported key derivation"
            }
            val salt = decoder.decode(envelope.getString("salt"))
            val nonce = decoder.decode(envelope.getString("nonce"))
            val ciphertext = decoder.decode(envelope.getString("ciphertext"))
            require(archive.size <= sizeLimit && salt.size == SALT_LENGTH && nonce.size == NONCE_LENGTH &&
                ciphertext.size in 16..sizeLimit) {
                "Invalid archive parameters"
            }
            val plain = try {
                crypt(Cipher.DECRYPT_MODE, passphrase, salt, nonce, ciphertext, version)
            } catch (invalid: AEADBadTagException) {
                throw IllegalArgumentException("Wrong passphrase or damaged archive", invalid)
            }
            try {
                require(plain.size <= sizeLimit / 2) { "Configuration too large" }
                val data = JSONObject(String(plain, StandardCharsets.UTF_8))
                require(data.getInt("version") == version) { "Unsupported configuration version" }
                val profiles = data.getJSONArray("profiles")
                require(profiles.length() <= ProfileStore.MAX_PROFILES) { "Too many profiles" }
                val items = (0 until profiles.length()).map { i ->
                    val entry = profiles.getJSONObject(i)
                    val templates = if (version == LEGACY_VERSION) emptyList() else entry.getJSONArray("templates").let { array ->
                        (0 until array.length()).map { index ->
                            array.getJSONObject(index).let {
                                DeployScriptTemplate(it.getString("id"), it.getString("name"), it.getString("content"))
                            }
                        }
                    }
                    PortableProfile(entry.getString("label"), entry.getString("token"), templates)
                        .also(::validateProfile)
                }
                require(items.map { it.label.lowercase(java.util.Locale.ROOT) }.distinct().size == items.size) {
                    "Duplicate profile labels"
                }
                return PortableConfiguration(items, AppTheme.valueOf(data.getString("theme")),
                    AppLanguage.valueOf(data.getString("language")))
            } finally {
                plain.fill(0)
            }
        } catch (invalid: IllegalArgumentException) {
            throw invalid
        } catch (invalid: Exception) {
            throw IllegalArgumentException("Invalid archive", invalid)
        }
    }

    private fun validateProfile(profile: PortableProfile) {
        require(profile.label == profile.label.trim() && profile.label.isNotBlank() &&
            profile.label.length <= ProfileStore.MAX_LABEL_LENGTH && profile.label.none(Char::isISOControl)) {
            "Invalid profile label"
        }
        require(profile.token.length <= 8192) { "Token too long" }
        PloiApi.validateToken(profile.token)
        DeployScriptTemplateStore.validate(profile.templates)
    }

    private fun validatePassphrase(passphrase: String) {
        require(passphrase.length in MIN_PASSPHRASE_LENGTH..MAX_PASSPHRASE_LENGTH) {
            "Invalid archive passphrase"
        }
    }

    private fun crypt(mode: Int, passphrase: String, salt: ByteArray, nonce: ByteArray,
        data: ByteArray, version: Int): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256)
        val key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(aad(version))
                doFinal(data)
            }
        } finally { key.fill(0) }
    }
}

internal fun readLimitedArchive(input: InputStream): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        if (n == 0) {
            val one = input.read()
            if (one < 0) break
            require(out.size() < PortableConfigurationCodec.MAX_ARCHIVE_BYTES) { "Archive too large" }
            out.write(one)
            continue
        }
        require(out.size() + n <= PortableConfigurationCodec.MAX_ARCHIVE_BYTES) { "Archive too large" }
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

/** Preflight an entire import before a single batch write; preserve existing PIN and active profile. */
internal class PortableConfigurationManager(
    private val prefs: ProfilePrefs, private val cipher: TokenCipher,
    templatesCipher: TokenCipher = cipher
) {
    private val store = ProfileStore(prefs, cipher)
    private val templateStore = DeployScriptTemplateStore(prefs, templatesCipher)

    fun export(passphrase: String): ByteArray {
        val profiles = store.profiles().map { profile ->
            PortableProfile(profile.label, store.tokenFor(profile.id)
                ?: throw IllegalStateException("Unreadable profile token"), templateStore.list(profile.id))
        }
        return PortableConfigurationCodec.seal(
            PortableConfiguration(profiles, UiPreferences(prefs).theme(), UiPreferences(prefs).language()), passphrase
        )
    }

    /** Existing matching profiles are skipped; conflicting labels fail without modifying anything. */
    fun import(archive: ByteArray, passphrase: String): Int {
        val config = PortableConfigurationCodec.open(archive, passphrase)
        val existing = store.profiles()
        val pending = config.profiles.filter { incoming ->
            val match = existing.firstOrNull { it.label.equals(incoming.label, ignoreCase = true) }
                ?: return@filter true
            require(store.tokenFor(match.id) == incoming.token) { "Conflicting profile label" }
            false
        }
        require(existing.size + pending.size <= ProfileStore.MAX_PROFILES) { "Too many profiles" }
        // Merge only missing templates for an existing identical profile; reject conflicting content.
        val merged = config.profiles.mapNotNull { incoming ->
            val profile = existing.firstOrNull { it.label.equals(incoming.label, ignoreCase = true) }
                ?: return@mapNotNull null
            val current = templateStore.list(profile.id)
            val additionsForProfile = incoming.templates.filter { template ->
                val sameId = current.firstOrNull { it.id == template.id }
                if (sameId != null) {
                    require(sameId == template) { "Conflicting deployment template ID" }
                    return@filter false
                }
                val sameName = current.firstOrNull { it.name.equals(template.name, ignoreCase = true) }
                if (sameName != null) {
                    require(sameName.content == template.content) { "Conflicting deployment template name" }
                    return@filter false
                }
                true
            }
            (profile.id to (current + additionsForProfile)).takeIf { additionsForProfile.isNotEmpty() }
        }
        val additions = pending.map { entry ->
            PloiProfile(UUID.randomUUID().toString(), entry.label) to
                encoder.encodeToString(cipher.encrypt(entry.token.toByteArray(StandardCharsets.UTF_8)))
        }
        val all = existing + additions.map { it.first }
        val directory = JSONArray().also { array ->
            all.forEach { array.put(JSONObject().put("id", it.id).put("label", it.label)) }
        }.toString()
        val changes = linkedMapOf<String, String?>(
            ProfileStore.KEY_PROFILES to directory,
            UiPreferences.KEY_THEME to config.theme.name,
            UiPreferences.KEY_LANGUAGE to config.language.name
        )
        if (existing.isEmpty() && all.isNotEmpty()) changes[ProfileStore.KEY_ACTIVE] = all.first().id
        additions.forEach { (profile, blob) -> changes[ProfileStore.TOKEN_PREFIX + profile.id] = blob }
        pending.zip(additions).forEach { (entry, saved) ->
            templateStore.prepareImport(entry.templates)?.let { blob ->
                changes[DeployScriptTemplateStore.key(saved.first.id)] = blob
            }
        }
        merged.forEach { (profileId, templates) ->
            changes[DeployScriptTemplateStore.key(profileId)] = templateStore.prepareImport(templates)
        }
        prefs.writeBatch(changes)
        return additions.size
    }

    private companion object { val encoder: Base64.Encoder = Base64.getEncoder() }
}
