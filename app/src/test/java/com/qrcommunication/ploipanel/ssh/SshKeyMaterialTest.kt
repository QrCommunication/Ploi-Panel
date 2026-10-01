package com.qrcommunication.ploipanel.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64

class SshKeyMaterialTest {
    @Test fun `generated ed25519 key inspects to the same public key`() {
        val key = SshKeyMaterial.generateEd25519()
        assertEquals("ssh-ed25519", key.keyType)
        assertTrue(key.privatePem.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"))
        val inspection = SshKeyMaterial.inspect(key.privatePem)
        assertEquals("ssh-ed25519", inspection.keyType)
        assertEquals(key.publicKeyBase64, inspection.publicKeyBase64)
        assertFalse(inspection.encrypted)
        assertFalse(key.toString().contains("PRIVATE"))
        assertNotEquals(key.publicKeyBase64, SshKeyMaterial.generateEd25519().publicKeyBase64)
    }

    @Test fun `generated key is accepted by the vault validation`() {
        val key = SshKeyMaterial.generateEd25519()
        assertEquals(key.publicKeyBase64, SshKeyVault.validatePublicKey(key.keyType, key.publicKeyBase64))
        SshKeyVault.validatePrivateKeyPem(key.privatePem)
    }

    @Test fun `passphrase protected OpenSSH key is detected and unlocked only with its passphrase`() {
        val jsch = JSchSupport.newJSch()
        val pair = com.jcraft.jsch.KeyPair.genKeyPair(jsch, com.jcraft.jsch.KeyPair.ED25519)
        val out = ByteArrayOutputStream()
        pair.writeOpenSSHv1PrivateKey(out, "correct horse".toByteArray())
        val publicBlob = Base64.getEncoder().encodeToString(pair.publicKeyBlob)
        pair.dispose()
        val pem = out.toString(Charsets.US_ASCII.name())
        val inspection = SshKeyMaterial.inspect(pem)
        assertTrue(inspection.encrypted)
        assertEquals("ssh-ed25519", inspection.keyType)
        // OpenSSH v1 keeps the public key readable even when the private part is encrypted.
        assertEquals(publicBlob, inspection.publicKeyBase64)
        assertTrue(SshKeyMaterial.unlocks(pem, "correct horse".toByteArray()))
        assertFalse(SshKeyMaterial.unlocks(pem, "wrong".toByteArray()))
        assertFalse(SshKeyMaterial.unlocks(pem, null))
    }

    @Test fun `rsa and ecdsa keys are inspected`() {
        val jsch = JSchSupport.newJSch()
        listOf(com.jcraft.jsch.KeyPair.RSA to "ssh-rsa", com.jcraft.jsch.KeyPair.ECDSA to "ecdsa-sha2-nistp256").forEach { (kind, name) ->
            val pair = com.jcraft.jsch.KeyPair.genKeyPair(jsch, kind, if (kind == com.jcraft.jsch.KeyPair.RSA) 2048 else 256)
            val out = ByteArrayOutputStream()
            pair.writePrivateKey(out)
            pair.dispose()
            assertEquals(name, SshKeyMaterial.inspect(out.toString(Charsets.US_ASCII.name())).keyType)
        }
    }

    @Test fun `garbage and DSA keys are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SshKeyMaterial.inspect("not a key") }
        val body = Base64.getMimeEncoder().encodeToString(ByteArray(64) { 7 })
        assertThrows(IllegalArgumentException::class.java) {
            SshKeyMaterial.inspect("-----BEGIN OPENSSH PRIVATE KEY-----\n$body\n-----END OPENSSH PRIVATE KEY-----")
        }
        val jsch = JSchSupport.newJSch()
        val dsa = com.jcraft.jsch.KeyPair.genKeyPair(jsch, com.jcraft.jsch.KeyPair.DSA, 1024)
        val out = ByteArrayOutputStream()
        dsa.writePrivateKey(out)
        dsa.dispose()
        assertThrows(IllegalArgumentException::class.java) { SshKeyMaterial.inspect(out.toString(Charsets.US_ASCII.name())) }
    }

    @Test fun `authorized key line strips unsafe comment characters`() {
        // Whitespace and shell punctuation are dropped so the line stays a single authorized_keys entry.
        assertEquals("ssh-ed25519 AAAA ploi-panel@phonerm", SshKeyMaterial.authorizedKeyLine("ssh-ed25519", "AAAA", "ploi-panel@phone\n; rm"))
        assertEquals("ssh-ed25519 AAAA", SshKeyMaterial.authorizedKeyLine("ssh-ed25519", "AAAA", ""))
    }

    @Test fun `host key algorithms prefer pinned types`() {
        assertEquals(
            "ecdsa-sha2-nistp256,ssh-ed25519,rsa-sha2-512",
            JSchSupport.hostKeyAlgorithms(listOf("ecdsa-sha2-nistp256"), "ssh-ed25519,ecdsa-sha2-nistp256,rsa-sha2-512")
        )
        assertEquals(
            "rsa-sha2-512,rsa-sha2-256,ssh-rsa,ssh-ed25519",
            JSchSupport.hostKeyAlgorithms(listOf("ssh-rsa"), "ssh-ed25519,rsa-sha2-512")
        )
    }

    @Test fun `vault stores passphrase protected keys only when declared`() {
        val prefs = object : com.qrcommunication.ploipanel.ProfilePrefs {
            val map = mutableMapOf<String, String>()
            override fun read(key: String) = map[key]
            override fun write(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
        }
        val cipher = object : com.qrcommunication.ploipanel.TokenCipher {
            override fun encrypt(plain: ByteArray) = plain.reversedArray()
            override fun decrypt(blob: ByteArray) = blob.reversedArray()
        }
        val vault = SshKeyVault(prefs, cipher)
        val legacy = "-----BEGIN RSA PRIVATE KEY-----\nProc-Type: 4,ENCRYPTED\nDEK-Info: AES-128-CBC,00\n\nAAAA\n-----END RSA PRIVATE KEY-----"
        val rsaBlob = run {
            val name = "ssh-rsa".toByteArray()
            Base64.getEncoder().encodeToString(byteArrayOf(0, 0, 0, name.size.toByte()) + name + byteArrayOf(0, 1, 0, 1, 3))
        }
        assertThrows(IllegalArgumentException::class.java) { vault.import("p1", "k", "ssh-rsa", legacy, rsaBlob) }
        val entry = vault.import("p1", "k", "ssh-rsa", legacy, rsaBlob, passphraseProtected = true)
        assertTrue(entry.passphraseProtected)
        assertTrue(vault.list("p1").single().passphraseProtected)
        assertNull(vault.privateKeyPem("p1", "missing"))
    }
}
