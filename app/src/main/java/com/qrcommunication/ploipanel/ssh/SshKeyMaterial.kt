package com.qrcommunication.ploipanel.ssh

import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64

/** What can be learnt from a private key block without its passphrase. */
internal data class SshKeyInspection(
    val keyType: String,
    /** Base64 SSH public key blob, or null when an encrypted legacy PEM hides it. */
    val publicKeyBase64: String?,
    val encrypted: Boolean,
)

/** A freshly generated key pair, as OpenSSH private PEM plus the matching public blob. */
internal data class GeneratedSshKey(val keyType: String, val privatePem: String, val publicKeyBase64: String) {
    override fun toString(): String = "GeneratedSshKey($keyType)" // never print the private key
}

/**
 * Parses, validates and generates SSH key material with JSch. Pure JVM: no Android API, so the
 * exact code that runs on the phone is covered by unit tests.
 */
internal object SshKeyMaterial {
    private val SUPPORTED = setOf("ssh-ed25519", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521", "ssh-rsa")

    /**
     * Loads [privatePem] and reports its type, public blob and encryption. Rejects anything JSch
     * cannot load or a type outside [SUPPORTED] (DSA is obsolete and refused by modern OpenSSH).
     */
    fun inspect(privatePem: String): SshKeyInspection {
        val pem = SshKeyVault.validatePrivateKeyPem(privatePem, allowEncrypted = true)
        val pair = load(pem)
        try {
            val keyType = pair.keyTypeString
            require(keyType in SUPPORTED) { "Unsupported key type" }
            val blob = pair.publicKeyBlob?.takeIf { it.isNotEmpty() }
            if (blob != null) {
                require(SshHostTrustStore.keyBlobType(blob) == keyType) { "Public key does not match $keyType" }
            }
            return SshKeyInspection(keyType, blob?.let(Base64.getEncoder()::encodeToString), pair.isEncrypted)
        } finally {
            pair.dispose()
        }
    }

    /**
     * Checks that a passphrase opens the key. Returns false on a wrong passphrase; the key is
     * disposed either way so the decrypted material does not linger in the JSch object.
     */
    fun unlocks(privatePem: String, passphrase: ByteArray?): Boolean {
        val pair = load(SshKeyVault.validatePrivateKeyPem(privatePem, allowEncrypted = true))
        try {
            if (!pair.isEncrypted) return true
            if (passphrase == null || passphrase.isEmpty()) return false
            return pair.decrypt(passphrase)
        } finally {
            pair.dispose()
        }
    }

    /**
     * Generates an Ed25519 key (fast, small, accepted by every OpenSSH >= 6.5). The private key
     * is written unencrypted because it is immediately sealed by the Keystore-backed vault.
     */
    fun generateEd25519(): GeneratedSshKey {
        val jsch = JSchSupport.newJSch()
        val pair = KeyPair.genKeyPair(jsch, KeyPair.ED25519)
        try {
            val out = ByteArrayOutputStream()
            pair.writeOpenSSHv1PrivateKey(out, null)
            val pem = String(out.toByteArray(), StandardCharsets.US_ASCII).trim()
            out.reset()
            val blob = pair.publicKeyBlob
            return GeneratedSshKey("ssh-ed25519", pem, Base64.getEncoder().encodeToString(blob))
        } finally {
            pair.dispose()
        }
    }

    /** OpenSSH `authorized_keys` line for a stored public key. */
    fun authorizedKeyLine(keyType: String, publicKeyBase64: String, comment: String): String {
        val cleanComment = comment.filter { it.isLetterOrDigit() || it in "-_.@" }.take(64)
        return listOf(keyType, publicKeyBase64, cleanComment).filter(String::isNotEmpty).joinToString(" ")
    }

    private fun load(pem: String): KeyPair = try {
        KeyPair.load(JSchSupport.newJSch(), pem.toByteArray(StandardCharsets.US_ASCII), null)
    } catch (invalid: JSchException) {
        throw IllegalArgumentException("Malformed private key", invalid)
    }
}
