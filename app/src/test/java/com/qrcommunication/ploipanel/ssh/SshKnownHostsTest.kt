package com.qrcommunication.ploipanel.ssh

import com.qrcommunication.ploipanel.ProfilePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

private class TrustMemoryPrefs : ProfilePrefs {
    val map = mutableMapOf<String, String>()
    override fun read(key: String): String? = map[key]
    override fun write(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

private const val KEY_A =
    "AAAAC3NzaC1lZDI1NTE5AAAAIN0Cqqs6FIVYhyWIVWfM2lz1aGaJWODQAPdh9xbbekDt"
private const val KEY_B =
    "AAAAC3NzaC1lZDI1NTE5AAAAIIh5kvO0gKkLC6mN9jV6f0o1IOMeZfFqE0J3y8X0Zq3t"

private fun blob(type: String, tail: Byte): ByteArray {
    val name = type.toByteArray(Charsets.US_ASCII)
    return byteArrayOf(0, 0, 0, name.size.toByte()) + name + byteArrayOf(tail)
}

private fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64)

class SshKnownHostsTest {
    private fun store(prefs: TrustMemoryPrefs = TrustMemoryPrefs(), profile: String = "p1") =
        SshHostTrustStore(prefs, profile)

    @Test
    fun `fingerprint matches openssh sha256 format`() {
        val key = SshHostKey("example.com", 22, "ssh-ed25519", decode(KEY_A))
        assertEquals("SHA256:C4Scukm+JnWwDpb8zmy6tA22S+7yLRwddfr0Kqys6rI", key.fingerprint())
        assertEquals("SHA256:/ykgyo7Pl2FPMlthqFjQVAgUJTu+kFzBqJgRaSOQso4",
            SshHostKey("example.com", 22, "ssh-ed25519", decode(KEY_B)).fingerprint())
    }

    @Test
    fun `first contact is unknown then trusted once pinned`() {
        val prefs = TrustMemoryPrefs()
        val s = store(prefs)
        assertEquals(SshTrustDecision.UNKNOWN, s.evaluate("EXAMPLE.com", 22, "ssh-ed25519", decode(KEY_A)))
        s.trust("example.com", 22, "ssh-ed25519", decode(KEY_A))
        assertEquals(SshTrustDecision.TRUSTED, s.evaluate("example.com", 22, "ssh-ed25519", decode(KEY_A)))
        // Pins survive a store reload (fresh instance, same prefs).
        assertEquals(SshTrustDecision.TRUSTED,
            store(prefs).evaluate("example.com", 22, "ssh-ed25519", decode(KEY_A)))
    }

    @Test
    fun `changed key mismatches and cannot be silently overwritten`() {
        val s = store()
        s.trust("example.com", 22, "ssh-ed25519", decode(KEY_A))
        assertEquals(SshTrustDecision.MISMATCH, s.evaluate("example.com", 22, "ssh-ed25519", decode(KEY_B)))
        assertThrows(IllegalStateException::class.java) {
            s.trust("example.com", 22, "ssh-ed25519", decode(KEY_B))
        }
        // Still pinned to A after the refused overwrite.
        assertEquals(SshTrustDecision.TRUSTED, s.evaluate("example.com", 22, "ssh-ed25519", decode(KEY_A)))
    }

    @Test
    fun `explicit repin replaces a mismatched key`() {
        val s = store()
        s.trust("example.com", 22, "ssh-ed25519", decode(KEY_A))
        assertThrows(IllegalArgumentException::class.java) {
            s.repin("other.com", 22, "ssh-ed25519", decode(KEY_B))
        }
        s.repin("example.com", 22, "ssh-ed25519", decode(KEY_B))
        assertEquals(SshTrustDecision.TRUSTED, s.evaluate("example.com", 22, "ssh-ed25519", decode(KEY_B)))
        assertEquals(SshTrustDecision.MISMATCH, s.evaluate("example.com", 22, "ssh-ed25519", decode(KEY_A)))
    }

    @Test
    fun `pins are scoped by host port and key type`() {
        val s = store()
        s.trust("example.com", 22, "ssh-ed25519", decode(KEY_A))
        assertEquals(SshTrustDecision.UNKNOWN, s.evaluate("example.com", 2222, "ssh-ed25519", decode(KEY_A)))
        assertEquals(SshTrustDecision.UNKNOWN, s.evaluate("other.com", 22, "ssh-ed25519", decode(KEY_A)))
        assertEquals(SshTrustDecision.UNKNOWN,
            s.evaluate("example.com", 22, "ecdsa-sha2-nistp256", decode(KEY_A)))
    }

    @Test
    fun `revoke removes pins for one algorithm or all`() {
        val s = store()
        s.trust("example.com", 22, "ssh-ed25519", decode(KEY_A))
        s.trust("example.com", 22, "ecdsa-sha2-nistp256", blob("ecdsa-sha2-nistp256", 7))
        s.revoke("example.com", 22, "ssh-ed25519")
        assertEquals(SshTrustDecision.UNKNOWN, s.evaluate("example.com", 22, "ssh-ed25519", decode(KEY_A)))
        assertEquals(SshTrustDecision.TRUSTED,
            s.evaluate("example.com", 22, "ecdsa-sha2-nistp256", blob("ecdsa-sha2-nistp256", 7)))
        s.revoke("example.com", 22)
        assertTrue(s.entries().isEmpty())
    }

    @Test
    fun `import parses comments multi hosts and bracket ports`() {
        val s = store()
        val report = s.importKnownHosts(
            """
            # a comment line

            example.com,alias.com ssh-ed25519 $KEY_A optional comment
            [2001:db8::1]:2222 ssh-ed25519 $KEY_B
            """.trimIndent()
        )
        assertEquals(3, report.imported)
        assertTrue(report.errors.isEmpty())
        assertEquals(SshTrustDecision.TRUSTED, s.evaluate("example.com", 22, "ssh-ed25519", decode(KEY_A)))
        assertEquals(SshTrustDecision.TRUSTED, s.evaluate("alias.com", 22, "ssh-ed25519", decode(KEY_A)))
        assertEquals(SshTrustDecision.TRUSTED, s.evaluate("2001:db8::1", 2222, "ssh-ed25519", decode(KEY_B)))
    }

    @Test
    fun `import rejects markers hashed hosts and wildcards`() {
        val s = store()
        val report = s.importKnownHosts(
            """
            @cert-authority example.com ssh-ed25519 $KEY_A
            |1|hashedhost ssh-ed25519 $KEY_A
            *.example.com ssh-ed25519 $KEY_A
            !bad.com,ok.com ssh-ed25519 $KEY_A
            """.trimIndent()
        )
        assertEquals(0, report.imported)
        assertEquals(4, report.errors.size)
        assertTrue(s.entries().isEmpty())
    }

    @Test
    fun `import rejects malformed key data and type mismatch`() {
        val s = store()
        val report = s.importKnownHosts(
            """
            a.com ssh-ed25519 !!!not-base64!!!
            b.com ecdsa-sha2-nistp256 $KEY_A
            c.com unknown-type $KEY_A
            d.com ssh-ed25519
            """.trimIndent()
        )
        assertEquals(0, report.imported)
        assertEquals(4, report.errors.size)
    }

    @Test
    fun `import never overwrites a conflicting pin`() {
        val s = store()
        s.trust("example.com", 22, "ssh-ed25519", decode(KEY_A))
        val report = s.importKnownHosts("example.com ssh-ed25519 $KEY_B\nexample.com ssh-ed25519 $KEY_A")
        assertEquals(0, report.imported)
        assertEquals(1, report.errors.size)
        assertTrue(report.errors[0].contains("conflicts"))
        assertEquals(SshTrustDecision.TRUSTED, s.evaluate("example.com", 22, "ssh-ed25519", decode(KEY_A)))
    }

    @Test
    fun `host port and blob bounds are enforced`() {
        val s = store()
        assertThrows(IllegalArgumentException::class.java) { s.trust("", 22, "ssh-ed25519", decode(KEY_A)) }
        assertThrows(IllegalArgumentException::class.java) { s.trust("bad host", 22, "ssh-ed25519", decode(KEY_A)) }
        assertThrows(IllegalArgumentException::class.java) { s.trust("ok.com", 0, "ssh-ed25519", decode(KEY_A)) }
        assertThrows(IllegalArgumentException::class.java) { s.trust("ok.com", 65536, "ssh-ed25519", decode(KEY_A)) }
        assertThrows(IllegalArgumentException::class.java) {
            s.trust("ok.com", 22, "ssh-ed25519", ByteArray(SshHostTrustStore.MAX_KEY_BLOB_BYTES + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            s.trust("ok.com", 22, "ssh-rsa", decode(KEY_A))
        }
        assertTrue(s.entries().isEmpty())
    }

    @Test
    fun `entry count is capped`() {
        val s = store()
        repeat(SshHostTrustStore.MAX_ENTRIES) { index ->
            s.trust("host$index.example.com", 22, "ssh-ed25519", decode(KEY_A))
        }
        assertThrows(IllegalArgumentException::class.java) {
            s.trust("one-too-many.example.com", 22, "ssh-ed25519", decode(KEY_A))
        }
    }

    @Test
    fun `corrupted store fails closed and clear recovers`() {
        val prefs = TrustMemoryPrefs()
        val s = store(prefs)
        s.trust("example.com", 22, "ssh-ed25519", decode(KEY_A))
        prefs.map[SshHostTrustStore.key("p1")] = "{not json"
        assertThrows(IllegalStateException::class.java) { s.entries() }
        assertThrows(IllegalStateException::class.java) {
            s.evaluate("example.com", 22, "ssh-ed25519", decode(KEY_A))
        }
        s.clear()
        assertTrue(s.entries().isEmpty())
        assertEquals(SshTrustDecision.UNKNOWN, s.evaluate("example.com", 22, "ssh-ed25519", decode(KEY_A)))
    }

    @Test
    fun `stores are isolated per profile`() {
        val prefs = TrustMemoryPrefs()
        store(prefs, "p1").trust("example.com", 22, "ssh-ed25519", decode(KEY_A))
        assertEquals(SshTrustDecision.UNKNOWN,
            store(prefs, "p2").evaluate("example.com", 22, "ssh-ed25519", decode(KEY_A)))
    }

    @Test
    fun `rsa pins are flagged as weak signatures`() {
        assertTrue(SshHostKey("h", 22, "ssh-rsa", blob("ssh-rsa", 1)).isWeakSignature)
        assertFalse(SshHostKey("h", 22, "ssh-ed25519", decode(KEY_A)).isWeakSignature)
    }
}
