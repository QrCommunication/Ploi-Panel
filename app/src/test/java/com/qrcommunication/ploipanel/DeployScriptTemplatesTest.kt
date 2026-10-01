package com.qrcommunication.ploipanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeployScriptTemplatesTest {
    private class Prefs : ProfilePrefs {
        val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String?) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }
    private class Cipher : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x6a).toByte() }.toByteArray()
        override fun decrypt(blob: ByteArray) = encrypt(blob)
    }

    @Test fun templateCrudIsEncryptedAndIsolatedByProfile() {
        val prefs = Prefs()
        val store = DeployScriptTemplateStore(prefs, Cipher())
        val first = store.save("profile-a", null, "Production", "echo private-command")
        assertEquals(listOf(first), store.list("profile-a"))
        assertEquals(emptyList<DeployScriptTemplate>(), store.list("profile-b"))
        assertFalse(prefs.values.values.any { "private-command" in it })
        val updated = store.save("profile-a", first.id, "Production", "echo updated")
        assertEquals(first.id, updated.id)
        assertEquals(listOf(updated), store.list("profile-a"))
        store.delete("profile-a", first.id)
        assertTrue(store.list("profile-a").isEmpty())
    }

    @Test fun rejectsDuplicateNamesInvalidContentAndCorruptionWithoutErasingData() {
        val prefs = Prefs()
        val store = DeployScriptTemplateStore(prefs, Cipher())
        val original = store.save("p", null, "My template", "echo hello")
        assertThrows(IllegalArgumentException::class.java) { store.save("p", null, "my TEMPLATE", "echo world") }
        assertThrows(IllegalArgumentException::class.java) { store.save("p", null, "", "echo hello") }
        assertThrows(IllegalArgumentException::class.java) { store.save("p", null, "Other", " ") }
        assertThrows(IllegalArgumentException::class.java) { store.save("p", null, "Other", "x".repeat(DEPLOY_SCRIPT_MAX_LENGTH + 1)) }
        assertThrows(IllegalArgumentException::class.java) { store.save("p", "not-an-id", "Other", "echo no") }
        assertEquals(listOf(original), store.list("p"))
        prefs.write(DeployScriptTemplateStore.key("p"), "invalid cipher blob")
        assertThrows(IllegalStateException::class.java) { store.list("p") }
        assertThrows(IllegalStateException::class.java) { store.save("p", null, "Other", "echo no") }
    }

    @Test fun replacingProfileDoesNotExposeOrReuseAnotherProfileTemplate() {
        val prefs = Prefs()
        val store = DeployScriptTemplateStore(prefs, Cipher())
        val a = store.save("a", null, "Shared name", "echo a")
        val b = store.save("b", null, "Shared name", "echo b")
        assertNotEquals(a.id, b.id)
        assertEquals("echo b", store.list("b").single().content)
        store.clear("a")
        assertTrue(store.list("a").isEmpty())
        assertEquals("echo b", store.list("b").single().content)
    }
}
