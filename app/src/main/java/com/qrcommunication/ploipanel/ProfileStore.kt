package com.qrcommunication.ploipanel

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

internal data class PloiProfile(val id: String, val label: String)

/** Minimal key-value persistence so the store stays unit-testable on the JVM. */
internal interface ProfilePrefs {
    fun read(key: String): String?
    fun write(key: String, value: String?)
}

internal class SharedPreferencesProfilePrefs(context: Context) : ProfilePrefs {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    override fun read(key: String): String? = prefs.getString(key, null)
    override fun write(key: String, value: String?) {
        prefs.edit { if (value == null) remove(key) else putString(key, value) }
    }
    private companion object { const val PREFS_FILE = "ploi_profiles" }
}

/**
 * Multi-profile Ploi token vault. Labels are stored in clear, tokens always encrypted through
 * [cipher] (Android Keystore in production). Deleting a profile wipes only its own secret.
 */
internal class ProfileStore(private val prefs: ProfilePrefs, private val cipher: TokenCipher) {
    companion object {
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE = "active"
        const val TOKEN_PREFIX = "token."
        const val MAX_PROFILES = 10
        const val MAX_LABEL_LENGTH = 40
    }

    fun profiles(): List<PloiProfile> {
        val raw = prefs.read(KEY_PROFILES) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val entry = array.getJSONObject(index)
                PloiProfile(entry.getString("id"), entry.getString("label"))
            }
        } catch (invalid: Exception) {
            emptyList()
        }
    }

    fun activeProfileId(): String? {
        val active = prefs.read(KEY_ACTIVE) ?: return null
        return active.takeIf { id -> profiles().any { it.id == id } }
    }

    fun add(label: String, token: String): PloiProfile {
        val cleanLabel = validateLabel(label)
        val validToken = PloiApi.validateToken(token)
        val existing = profiles()
        require(existing.size < MAX_PROFILES) { "Too many profiles" }
        require(existing.none { it.label.equals(cleanLabel, ignoreCase = true) }) { "Duplicate profile label" }
        val profile = PloiProfile(UUID.randomUUID().toString(), cleanLabel)
        val blob = cipher.encrypt(validToken.toByteArray(StandardCharsets.UTF_8))
        prefs.write(TOKEN_PREFIX + profile.id, Base64.getEncoder().encodeToString(blob))
        persist(existing + profile)
        return profile
    }

    fun remove(id: String) {
        persist(profiles().filterNot { it.id == id })
        prefs.write(TOKEN_PREFIX + id, null)
        if (prefs.read(KEY_ACTIVE) == id) prefs.write(KEY_ACTIVE, null)
    }

    fun activate(id: String) {
        require(profiles().any { it.id == id }) { "Unknown profile" }
        prefs.write(KEY_ACTIVE, id)
    }

    fun deactivate() = prefs.write(KEY_ACTIVE, null)

    /** Returns null when the profile is unknown or its secret is unreadable (key wiped, tampering). */
    fun tokenFor(id: String): String? {
        if (profiles().none { it.id == id }) return null
        val encoded = prefs.read(TOKEN_PREFIX + id) ?: return null
        return try {
            String(cipher.decrypt(Base64.getDecoder().decode(encoded)), StandardCharsets.UTF_8)
        } catch (unreadable: Exception) {
            null
        }
    }

    private fun persist(profiles: List<PloiProfile>) {
        val array = JSONArray()
        profiles.forEach { array.put(JSONObject().put("id", it.id).put("label", it.label)) }
        prefs.write(KEY_PROFILES, array.toString())
    }

    private fun validateLabel(label: String): String {
        val clean = label.trim()
        require(clean.isNotEmpty()) { "Profile label required" }
        require(clean.length <= MAX_LABEL_LENGTH) { "Profile label too long" }
        require(clean.none { it.isISOControl() }) { "Invalid characters in profile label" }
        return clean
    }
}
