package com.qrcommunication.ploipanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableDeployTemplatesTest {
    private class Prefs : ProfilePrefs {
        val data = mutableMapOf<String, String>()
        override fun read(key: String) = data[key]
        override fun write(key: String, value: String?) {
            if (value == null) data.remove(key) else data[key] = value
        }
        override fun writeBatch(values: Map<String, String?>) = values.forEach { (k, v) -> write(k, v) }
    }
    private class MaskCipher(private val mask: Int) : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor mask).toByte() }.toByteArray()
        override fun decrypt(blob: ByteArray) = encrypt(blob)
    }
    private val passphrase = "correct-horse-battery-staple"

    @Test fun templatesSurvivePortableTransferWithoutOverwritingExistingProfiles() {
        val source = Prefs()
        val account = ProfileStore(source, MaskCipher(1)).add("Prod", "token")
        val sourceTemplates = DeployScriptTemplateStore(source, MaskCipher(2))
        sourceTemplates.save(account.id, null, "Web", "cd /web && deploy")
        val archive = PortableConfigurationManager(source, MaskCipher(1), MaskCipher(2)).export(passphrase)
        assertFalse(String(archive).contains("cd /web && deploy"))
        val target = Prefs()
        val manager = PortableConfigurationManager(target, MaskCipher(3), MaskCipher(4))
        assertEquals(1, manager.import(archive, passphrase))
        val restoredId = ProfileStore(target, MaskCipher(3)).profiles().single().id
        assertEquals(sourceTemplates.list(account.id), DeployScriptTemplateStore(target, MaskCipher(4)).list(restoredId))
        assertTrue(source.data[DeployScriptTemplateStore.key(account.id)] != target.data[DeployScriptTemplateStore.key(restoredId)])
        assertEquals(0, manager.import(archive, passphrase))
        assertEquals(1, DeployScriptTemplateStore(target, MaskCipher(4)).list(restoredId).size)
        ProfileStore(target, MaskCipher(3)).remove(restoredId)
        assertFalse(target.data.containsKey(DeployScriptTemplateStore.key(restoredId)))
    }

    @Test fun matchingProfileImportsOnlyMissingTemplatesAndRejectsConflictsAtomically() {
        val source = Prefs()
        val account = ProfileStore(source, MaskCipher(1)).add("Prod", "token")
        DeployScriptTemplateStore(source, MaskCipher(2)).save(account.id, null, "Shared", "echo A")
        val archive = PortableConfigurationManager(source, MaskCipher(1), MaskCipher(2)).export(passphrase)
        val target = Prefs()
        val existing = ProfileStore(target, MaskCipher(3)).add("Prod", "token")
        val templates = DeployScriptTemplateStore(target, MaskCipher(4))
        templates.save(existing.id, null, "Local", "echo B")
        val manager = PortableConfigurationManager(target, MaskCipher(3), MaskCipher(4))
        assertEquals(0, manager.import(archive, passphrase))
        assertEquals(setOf("Local", "Shared"), templates.list(existing.id).map { it.name }.toSet())
        templates.save(existing.id, null, "Other", "echo B")
        val before = target.data.toMap()
        // A conflicting name must not overwrite any existing template or preference.
        val conflictSource = Prefs()
        val conflict = ProfileStore(conflictSource, MaskCipher(1)).add("Prod", "token")
        DeployScriptTemplateStore(conflictSource, MaskCipher(2)).save(conflict.id, null, "Local", "echo different")
        val conflictArchive = PortableConfigurationManager(conflictSource, MaskCipher(1), MaskCipher(2)).export(passphrase)
        try {
            manager.import(conflictArchive, passphrase)
            throw AssertionError("Expected template conflict")
        } catch (_: IllegalArgumentException) { /* unchanged */ }
        assertEquals(before, target.data)
    }
}
