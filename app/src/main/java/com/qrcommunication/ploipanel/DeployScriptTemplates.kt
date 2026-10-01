package com.qrcommunication.ploipanel

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.UUID

internal data class DeployScriptTemplate(val id: String, val name: String, val content: String)

/** Device-encrypted, profile-scoped local templates. No undocumented global Ploi API is used. */
internal class DeployScriptTemplateStore(private val prefs: ProfilePrefs, private val cipher: TokenCipher) {
    companion object {
        const val MAX_TEMPLATES = 10
        private const val VERSION = 1
        private const val PREFIX = "deploy.templates."
        fun key(profileId: String): String {
            require(profileId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid profile ID" }
            return PREFIX + profileId
        }

        fun validate(templates: List<DeployScriptTemplate>) {
            require(templates.size <= MAX_TEMPLATES) { "Too many templates" }
            templates.forEach(::validateOne)
            require(templates.map { it.id }.distinct().size == templates.size) { "Duplicate template ID" }
            require(templates.map { it.name.lowercase(Locale.ROOT) }.distinct().size == templates.size) {
                "Duplicate template name"
            }
        }

        private fun validateOne(template: DeployScriptTemplate) {
            require(template.id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid template ID" }
            require(template.name == template.name.trim() && template.name.length in 1..60 &&
                template.name.none(Char::isISOControl)) { "Invalid template name" }
            require(template.content.isNotBlank() && template.content.length <= DEPLOY_SCRIPT_MAX_LENGTH) {
                "Invalid deployment script"
            }
        }
    }

    fun list(profileId: String): List<DeployScriptTemplate> {
        val value = prefs.read(key(profileId)) ?: return emptyList()
        return try {
            val decoded = Base64.getDecoder().decode(value)
            val plain = cipher.decrypt(decoded)
            val raw = try { String(plain, StandardCharsets.UTF_8) } finally { plain.fill(0) }
            val root = JSONObject(raw)
            require(root.getInt("version") == VERSION)
            val array = root.getJSONArray("templates")
            require(array.length() <= MAX_TEMPLATES)
            val templates = (0 until array.length()).map { index ->
                array.getJSONObject(index).let { row ->
                    DeployScriptTemplate(row.getString("id"), row.getString("name"), row.getString("content"))
                }
            }
            validate(templates)
            templates
        } catch (invalid: Exception) {
            throw IllegalStateException("Unreadable deployment templates", invalid)
        }
    }

    fun save(profileId: String, id: String?, name: String, content: String): DeployScriptTemplate {
        val current = list(profileId)
        val cleaned = name.trim()
        val template = DeployScriptTemplate(id ?: UUID.randomUUID().toString(), cleaned, content)
        validate(listOf(template))
        val next = if (id == null) {
            require(current.size < MAX_TEMPLATES) { "Too many templates" }
            current + template
        } else {
            require(current.any { it.id == id }) { "Unknown template" }
            current.map { if (it.id == id) template else it }
        }
        prefs.write(key(profileId), encrypt(next))
        return template
    }

    fun delete(profileId: String, id: String) {
        val current = list(profileId)
        require(current.any { it.id == id }) { "Unknown template" }
        val next = current.filterNot { it.id == id }
        prefs.write(key(profileId), if (next.isEmpty()) null else encrypt(next))
    }

    fun clear(profileId: String) = prefs.write(key(profileId), null)

    /** Compute an encrypted value without mutating prefs, for atomic portable import. */
    fun prepareImport(templates: List<DeployScriptTemplate>): String? =
        if (templates.isEmpty()) null else encrypt(templates)

    private fun encrypt(templates: List<DeployScriptTemplate>): String {
        validate(templates)
        val raw = JSONObject().put("version", VERSION).put("templates", JSONArray().also { array ->
            templates.forEach { template ->
                array.put(JSONObject().put("id", template.id).put("name", template.name).put("content", template.content))
            }
        }).toString()
        return Base64.getEncoder().encodeToString(cipher.encrypt(raw.toByteArray(StandardCharsets.UTF_8)))
    }

}
