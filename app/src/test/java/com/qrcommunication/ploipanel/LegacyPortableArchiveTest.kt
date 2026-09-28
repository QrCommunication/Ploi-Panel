package com.qrcommunication.ploipanel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class LegacyPortableArchiveTest {
    @Test fun versionOneArchivesWithoutTemplatesStillOpen() {
        val password = "correct-horse-battery-staple"
        val salt = ByteArray(16) { it.toByte() }
        val nonce = ByteArray(12) { (it + 16).toByte() }
        val plain = JSONObject().put("version", 1).put("theme", AppTheme.SYSTEM.name)
            .put("language", AppLanguage.SYSTEM.name)
            .put("profiles", JSONArray().put(JSONObject().put("label", "Prod").put("token", "token")))
            .toString().toByteArray()
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, 310_000, 256)).encoded
        val ciphertext = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD("ploi-panel-config:1".toByteArray())
            doFinal(plain)
        }
        val encoder = Base64.getEncoder()
        val archive = JSONObject().put("format", "ploi-panel-config").put("version", 1)
            .put("kdf", "PBKDF2WithHmacSHA256").put("iterations", 310_000)
            .put("salt", encoder.encodeToString(salt)).put("nonce", encoder.encodeToString(nonce))
            .put("ciphertext", encoder.encodeToString(ciphertext)).toString().toByteArray()
        val restored = PortableConfigurationCodec.open(archive, password)
        assertEquals("Prod", restored.profiles.single().label)
        assertEquals(emptyList<DeployScriptTemplate>(), restored.profiles.single().templates)
    }
}
