package com.qrcommunication.ploipanel.ssh

import com.qrcommunication.ploipanel.ProfilePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

private class ProbeMemoryPrefs : ProfilePrefs {
    val map = mutableMapOf<String, String>()
    override fun read(key: String): String? = map[key]
    override fun write(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

private const val PROBE_KEY_A =
    "AAAAC3NzaC1lZDI1NTE5AAAAIN0Cqqs6FIVYhyWIVWfM2lz1aGaJWODQAPdh9xbbekDt"
private const val PROBE_KEY_B =
    "AAAAC3NzaC1lZDI1NTE5AAAAIIh5kvO0gKkLC6mN9jV6f0o1IOMeZfFqE0J3y8X0Zq3t"

private fun probeBlob(base64: String): ByteArray = Base64.getDecoder().decode(base64)

class SshHostKeyProbeTest {
    private fun store(prefs: ProbeMemoryPrefs = ProbeMemoryPrefs()) =
        SshHostTrustStore(prefs, "p1")

    @Test
    fun `unknown presented key is a first contact requiring confirmation`() {
        val prefs = ProbeMemoryPrefs()
        val outcome = assessPresentedKey(store(prefs), "example.com", 22, "ssh-ed25519", probeBlob(PROBE_KEY_A))
        assertTrue(outcome is SshProbeAssessment.FirstContact)
        val presented = (outcome as SshProbeAssessment.FirstContact).presented
        assertEquals("example.com", presented.host)
        assertEquals(22, presented.port)
        assertEquals("ssh-ed25519", presented.keyType)
        // Nothing pinned without an explicit confirmation.
        assertTrue(store(prefs).entries().isEmpty())
    }

    @Test
    fun `pinned key matching the presentation is trusted`() {
        val prefs = ProbeMemoryPrefs()
        store(prefs).trust("example.com", 22, "ssh-ed25519", probeBlob(PROBE_KEY_A))
        val outcome = assessPresentedKey(store(prefs), "EXAMPLE.com", 22, "ssh-ed25519", probeBlob(PROBE_KEY_A))
        assertTrue(outcome is SshProbeAssessment.Trusted)
    }

    @Test
    fun `changed presented key is a hard mismatch exposing both fingerprints`() {
        val prefs = ProbeMemoryPrefs()
        store(prefs).trust("example.com", 22, "ssh-ed25519", probeBlob(PROBE_KEY_A))
        val outcome = assessPresentedKey(store(prefs), "example.com", 22, "ssh-ed25519", probeBlob(PROBE_KEY_B))
        assertTrue(outcome is SshProbeAssessment.KeyMismatch)
        val mismatch = outcome as SshProbeAssessment.KeyMismatch
        assertEquals(
            SshHostKey("example.com", 22, "ssh-ed25519", probeBlob(PROBE_KEY_A)).fingerprint(),
            mismatch.pinned.fingerprint()
        )
        assertEquals(
            SshHostKey("example.com", 22, "ssh-ed25519", probeBlob(PROBE_KEY_B)).fingerprint(),
            mismatch.presented.fingerprint()
        )
        // The pin is unchanged until an explicit re-pin.
        assertEquals(
            SshTrustDecision.MISMATCH,
            store(prefs).evaluate("example.com", 22, "ssh-ed25519", probeBlob(PROBE_KEY_B))
        )
    }

    @Test
    fun `a different algorithm on the same host is a separate first contact`() {
        val prefs = ProbeMemoryPrefs()
        store(prefs).trust("example.com", 22, "ssh-ed25519", probeBlob(PROBE_KEY_A))
        val ecdsa = run {
            val name = "ecdsa-sha2-nistp256".toByteArray(Charsets.US_ASCII)
            byteArrayOf(0, 0, 0, name.size.toByte()) + name + byteArrayOf(7)
        }
        val outcome = assessPresentedKey(
            store(prefs), "example.com", 22, "ecdsa-sha2-nistp256", ecdsa
        )
        assertTrue(outcome is SshProbeAssessment.FirstContact)
    }

    @Test
    fun `unsupported or inconsistent presented keys are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            assessPresentedKey(store(), "example.com", 22, "ssh-dss", probeBlob(PROBE_KEY_A))
        }
        assertThrows(IllegalArgumentException::class.java) {
            assessPresentedKey(store(), "example.com", 22, "ssh-rsa", probeBlob(PROBE_KEY_A))
        }
        assertThrows(IllegalArgumentException::class.java) {
            assessPresentedKey(store(), "example.com", 22, "ssh-ed25519", byteArrayOf())
        }
        assertThrows(IllegalArgumentException::class.java) {
            assessPresentedKey(store(), "bad host!", 22, "ssh-ed25519", probeBlob(PROBE_KEY_A))
        }
        assertThrows(IllegalArgumentException::class.java) {
            assessPresentedKey(store(), "example.com", 70000, "ssh-ed25519", probeBlob(PROBE_KEY_A))
        }
    }

    @Test
    fun `non default port is part of the assessment identity`() {
        val prefs = ProbeMemoryPrefs()
        store(prefs).trust("example.com", 2222, "ssh-ed25519", probeBlob(PROBE_KEY_A))
        assertTrue(
            assessPresentedKey(store(prefs), "example.com", 2222, "ssh-ed25519", probeBlob(PROBE_KEY_A))
                is SshProbeAssessment.Trusted
        )
        assertTrue(
            assessPresentedKey(store(prefs), "example.com", 22, "ssh-ed25519", probeBlob(PROBE_KEY_A))
                is SshProbeAssessment.FirstContact
        )
    }
}
