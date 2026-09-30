package com.qrcommunication.ploipanel.screenshots

import android.security.keystore.KeyGenParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Test-only stand-in for the "AndroidKeyStore" JCA provider, which Robolectric does not ship.
 * Keys are software AES keys held in memory for the test JVM only; production code is unchanged
 * and still talks to the real hardware-backed Keystore on devices.
 */
object FakeAndroidKeyStore {
    private val keys = ConcurrentHashMap<String, SecretKey>()

    fun install() {
        if (Security.getProvider("AndroidKeyStore") == null) Security.addProvider(ProviderImpl())
    }

    fun reset() = keys.clear()

    @Suppress("DEPRECATION")
    class ProviderImpl : Provider("AndroidKeyStore", 1.0, "Test-only in-memory AndroidKeyStore") {
        init {
            put("KeyStore.AndroidKeyStore", StoreSpi::class.java.name)
            put("KeyGenerator.AES", AesGeneratorSpi::class.java.name)
        }
    }

    class StoreSpi : KeyStoreSpi() {
        override fun engineGetKey(alias: String, password: CharArray?): Key? = keys[alias]
        override fun engineGetCertificateChain(alias: String?): Array<Certificate>? = null
        override fun engineGetCertificate(alias: String?): Certificate? = null
        override fun engineGetCreationDate(alias: String?): Date = Date(0)
        override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<out Certificate>?) {
            keys[alias] = key as SecretKey
        }
        override fun engineSetKeyEntry(alias: String?, key: ByteArray?, chain: Array<out Certificate>?) = Unit
        override fun engineSetCertificateEntry(alias: String?, cert: Certificate?) = Unit
        override fun engineDeleteEntry(alias: String) { keys.remove(alias) }
        override fun engineAliases(): Enumeration<String> = Collections.enumeration(keys.keys.toList())
        override fun engineContainsAlias(alias: String): Boolean = keys.containsKey(alias)
        override fun engineSize(): Int = keys.size
        override fun engineIsKeyEntry(alias: String): Boolean = keys.containsKey(alias)
        override fun engineIsCertificateEntry(alias: String?): Boolean = false
        override fun engineGetCertificateAlias(cert: Certificate?): String? = null
        override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
        override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
        override fun engineGetEntry(alias: String, protParam: KeyStore.ProtectionParameter?): KeyStore.Entry? =
            keys[alias]?.let { KeyStore.SecretKeyEntry(it) }
    }

    class AesGeneratorSpi : KeyGeneratorSpi() {
        private var alias: String? = null
        private var bits = 256
        override fun engineInit(random: SecureRandom?) = Unit
        override fun engineInit(params: AlgorithmParameterSpec?, random: SecureRandom?) {
            val spec = params as KeyGenParameterSpec
            alias = spec.keystoreAlias
            if (spec.keySize > 0) bits = spec.keySize
        }
        override fun engineInit(keysize: Int, random: SecureRandom?) { bits = keysize }
        override fun engineGenerateKey(): SecretKey {
            val bytes = ByteArray(bits / 8).also { SecureRandom().nextBytes(it) }
            return SecretKeySpec(bytes, "AES").also { key -> alias?.let { keys[it] = key } }
        }
    }
}
