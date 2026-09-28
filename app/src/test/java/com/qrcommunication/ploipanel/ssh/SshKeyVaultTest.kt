package com.qrcommunication.ploipanel.ssh

import com.qrcommunication.ploipanel.ProfilePrefs
import com.qrcommunication.ploipanel.TokenCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private class VaultMemoryPrefs : ProfilePrefs {
    val map = mutableMapOf<String, String>()
    override fun read(key: String): String? = map[key]
    override fun write(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

/** Reversible but integrity-free fake: any bit flip yields garbage JSON after decrypt. */
private class ReversingCipher : TokenCipher {
    override fun encrypt(plain: ByteArray): ByteArray = plain.reversedArray()
    override fun decrypt(blob: ByteArray): ByteArray = blob.reversedArray()
}

private const val PUBLIC_A =
    "AAAAC3NzaC1lZDI1NTE5AAAAIN0Cqqs6FIVYhyWIVWfM2lz1aGaJWODQAPdh9xbbekDt"

private val OPENSSH_PEM = """
-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
QyNTUxOQAAACDdAqqrOhSFYclhVnzNpY5c2WhmDlAOh3b8W25HpA==
-----END OPENSSH PRIVATE KEY-----
""".trimIndent()

private val LEGACY_RSA_PEM = """
-----BEGIN RSA PRIVATE KEY-----
MIIBOgIBAAJBAMockfakeb64payloadforvaulttestsOnly0123456789abcdef
-----END RSA PRIVATE KEY-----
""".trimIndent()

private fun rsaPublicBlob(): String {
    val name = "ssh-rsa".toByteArray(Charsets.US_ASCII)
    val blob = byteArrayOf(0, 0, 0, name.size.toByte()) + name + byteArrayOf(0, 1, 0, 1, 3)
    return java.util.Base64.getEncoder().encodeToString(blob)
}

class SshKeyVaultTest {
    private fun vault(prefs: VaultMemoryPrefs = VaultMemoryPrefs()) = SshKeyVault(prefs, ReversingCipher())

    @Test
    fun `import encrypts and round trips private key`() {
        val prefs = VaultMemoryPrefs()
        val v = vault(prefs)
        val entry = v.import("p1", " Deploy key ", "ssh-ed25519", OPENSSH_PEM, PUBLIC_A, createdAtEpochMillis = 42L)
        assertEquals("Deploy key", entry.label)
        assertEquals("ssh-ed25519", entry.keyType)
        assertEquals(42L, entry.createdAtEpochMillis)
        assertEquals(listOf(entry), v.list("p1"))
        assertEquals(OPENSSH_PEM, v.privateKeyPem("p1", entry.id))
        // Persisted payload is encrypted: the raw PEM never appears in storage.
        val stored = prefs.map.getValue(SshKeyVault.key("p1"))
        assertFalse(stored.contains("OPENSSH"))
        // Survives a vault reload on the same prefs.
        assertEquals(OPENSSH_PEM, vault(prefs).privateKeyPem("p1", entry.id))
    }

    @Test
    fun `labels are validated and unique`() {
        val v = vault()
        v.import("p1", "First", "ssh-ed25519", OPENSSH_PEM, PUBLIC_A)
        assertThrows(IllegalArgumentException::class.java) {
            v.import("p1", " first ", "ssh-ed25519", OPENSSH_PEM, PUBLIC_A)
        }
        assertThrows(IllegalArgumentException::class.java) {
            v.import("p1", "   ", "ssh-ed25519", OPENSSH_PEM, PUBLIC_A)
        }
        assertThrows(IllegalArgumentException::class.java) {
            v.import("p1", "x".repeat(41), "ssh-ed25519", OPENSSH_PEM, PUBLIC_A)
        }
        assertThrows(IllegalArgumentException::class.java) {
            v.import("p1", "bad\u0007label", "ssh-ed25519", OPENSSH_PEM, PUBLIC_A)
        }
        assertEquals(1, v.list("p1").size)
    }

    @Test
    fun `unsupported key type and malformed pem are rejected`() {
        val v = vault()
        assertThrows(IllegalArgumentException::class.java) {
            v.import("p1", "k", "ssh-dss", OPENSSH_PEM, PUBLIC_A)
        }
        assertThrows(IllegalArgumentException::class.java) {
            v.import("p1", "k", "ssh-ed25519", "not a pem", PUBLIC_A)
        }
        assertThrows(IllegalArgumentException::class.java) {
            v.import("p1", "k", "ssh-ed25519",
                OPENSSH_PEM.replace("END OPENSSH PRIVATE KEY", "END PRIVATE KEY"), PUBLIC_A)
        }
        assertThrows(IllegalArgumentException::class.java) {
            v.import("p1", "k", "ssh-ed25519",
                "-----BEGIN OPENSSH PRIVATE KEY-----\n!!!\n-----END OPENSSH PRIVATE KEY-----", PUBLIC_A)
        }
        assertTrue(v.list("p1").isEmpty())
    }

    @Test
    fun `passphrase protected pem blocks are rejected explicitly`() {
        val v = vault()
        val pkcs8Encrypted = """
-----BEGIN ENCRYPTED PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQ==
-----END ENCRYPTED PRIVATE KEY-----
""".trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            v.import("p1", "k", "ssh-ed25519", pkcs8Encrypted, PUBLIC_A)
        }
        val legacyEncrypted = """
-----BEGIN RSA PRIVATE KEY-----
Proc-Type: 4,ENCRYPTED
DEK-Info: AES-128-CBC,0123456789ABCDEF

b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQ==
-----END RSA PRIVATE KEY-----
""".trimIndent()
        val error = assertThrows(IllegalArgumentException::class.java) {
            v.import("p1", "k", "ssh-rsa", legacyEncrypted, rsaPublicBlob())
        }
        assertTrue(error.message!!.contains("Passphrase"))
    }

    @Test
    fun `legacy unencrypted rsa pem is accepted`() {
        val v = vault()
        val entry = v.import("p1", "legacy", "ssh-rsa", LEGACY_RSA_PEM, rsaPublicBlob())
        assertEquals("ssh-rsa", entry.keyType)
        assertEquals(LEGACY_RSA_PEM, v.privateKeyPem("p1", entry.id))
    }

    @Test
    fun `public key must decode and match the declared type`() {
        val v = vault()
        assertThrows(IllegalArgumentException::class.java) {
            v.import("p1", "k", "ssh-ed25519", OPENSSH_PEM, "!!!not-base64!!!")
        }
        assertThrows(IllegalArgumentException::class.java) {
            v.import("p1", "k", "ecdsa-sha2-nistp256", OPENSSH_PEM, PUBLIC_A)
        }
    }

    @Test
    fun `private key size is bounded`() {
        val v = vault()
        val huge = "-----BEGIN OPENSSH PRIVATE KEY-----\n" +
            "QUJD".repeat(SshKeyVault.MAX_PRIVATE_KEY_BYTES / 4) +
            "\n-----END OPENSSH PRIVATE KEY-----"
        assertThrows(IllegalArgumentException::class.java) {
            v.import("p1", "k", "ssh-ed25519", huge, PUBLIC_A)
        }
    }

    @Test
    fun `remove deletes only the targeted key and clears empty vault`() {
        val prefs = VaultMemoryPrefs()
        val v = vault(prefs)
        val first = v.import("p1", "one", "ssh-ed25519", OPENSSH_PEM, PUBLIC_A)
        val second = v.import("p1", "two", "ssh-ed25519", OPENSSH_PEM, PUBLIC_A)
        v.remove("p1", first.id)
        assertEquals(listOf(second), v.list("p1"))
        assertNull(v.privateKeyPem("p1", first.id))
        assertThrows(IllegalArgumentException::class.java) { v.remove("p1", first.id) }
        v.remove("p1", second.id)
        assertNull(prefs.map[SshKeyVault.key("p1")])
    }

    @Test
    fun `key count is capped`() {
        val v = vault()
        repeat(SshKeyVault.MAX_KEYS) { index ->
            v.import("p1", "key$index", "ssh-ed25519", OPENSSH_PEM, PUBLIC_A)
        }
        assertThrows(IllegalArgumentException::class.java) {
            v.import("p1", "overflow", "ssh-ed25519", OPENSSH_PEM, PUBLIC_A)
        }
    }

    @Test
    fun `tampered vault fails closed`() {
        val prefs = VaultMemoryPrefs()
        val v = vault(prefs)
        v.import("p1", "one", "ssh-ed25519", OPENSSH_PEM, PUBLIC_A)
        val key = SshKeyVault.key("p1")
        // Corrupt the JSON header deterministically (the fake cipher has no integrity tag,
        // unlike the production AES-GCM Keystore cipher which rejects any bit flip).
        val plain = java.util.Base64.getDecoder().decode(prefs.map.getValue(key)).reversedArray()
        plain[0] = 'X'.code.toByte()
        prefs.map[key] = java.util.Base64.getEncoder().encodeToString(plain.reversedArray())
        assertThrows(IllegalStateException::class.java) { v.list("p1") }
        v.clear("p1")
        assertTrue(v.list("p1").isEmpty())
    }

    @Test
    fun `vaults are isolated per profile`() {
        val prefs = VaultMemoryPrefs()
        val v = vault(prefs)
        v.import("p1", "mine", "ssh-ed25519", OPENSSH_PEM, PUBLIC_A)
        assertTrue(v.list("p2").isEmpty())
        assertThrows(IllegalArgumentException::class.java) { v.remove("p2", "whatever") }
    }

    @Test
    fun `public key line parses type and payload and drops comment`() {
        val parsed = SshKeyVault.parsePublicKeyLine("  ssh-ed25519  $PUBLIC_A  deploy@laptop ")
        assertEquals("ssh-ed25519", parsed.first)
        assertEquals(PUBLIC_A, parsed.second)
    }

    @Test
    fun `public key line rejects missing payload and unsupported types`() {
        assertThrows(IllegalArgumentException::class.java) { SshKeyVault.parsePublicKeyLine("") }
        assertThrows(IllegalArgumentException::class.java) { SshKeyVault.parsePublicKeyLine("ssh-ed25519") }
        // Hardware-backed sk-* types are host-key material only, never importable PEM keys.
        assertThrows(IllegalArgumentException::class.java) {
            SshKeyVault.parsePublicKeyLine("«redacted:sk-…»@openssh.com $PUBLIC_A")
        }
    }

    @Test
    fun `import through a parsed public key line round trips`() {
        val prefs = VaultMemoryPrefs()
        val v = vault(prefs)
        val (keyType, publicKey) = SshKeyVault.parsePublicKeyLine("ssh-ed25519 $PUBLIC_A comment")
        val entry = v.import("p1", "via line", keyType, OPENSSH_PEM, publicKey)
        assertEquals("ssh-ed25519", entry.keyType)
        assertEquals(PUBLIC_A, entry.publicKeyBase64)
    }
}
