package com.qrcommunication.ploipanel.ssh

import com.jcraft.jsch.JSch

/**
 * Process-wide JSch configuration.
 *
 * JSch ships two implementations of the modern primitives: `jce.*` (platform JCA) and `bc.*`
 * (Bouncy Castle lightweight API). Android only exposes X25519 and Ed25519 through JCA on recent
 * releases, so on API 29-32 the `jce` classes silently disappear from negotiation and Ed25519
 * user keys cannot sign at all. Pinning the `bc` classes gives the same algorithm set on every
 * supported Android version, and on the JVM used by unit tests.
 *
 * Password authentication, host-key checking and algorithm lists stay per session (see
 * [JSchShellConnector]); only implementation classes are swapped here.
 */
internal object JSchSupport {
    @Volatile
    private var installed = false

    private val BOUNCY_CASTLE_IMPLEMENTATIONS = mapOf(
        "ssh-ed25519" to "com.jcraft.jsch.bc.SignatureEd25519",
        "ssh-ed448" to "com.jcraft.jsch.bc.SignatureEd448",
        "xdh" to "com.jcraft.jsch.bc.XDH",
        "keypairgen.eddsa" to "com.jcraft.jsch.bc.KeyPairGenEdDSA",
        "keypairgen_fromprivate.eddsa" to "com.jcraft.jsch.bc.KeyPairGenEdDSA",
    )

    /** Idempotent; safe to call before every JSch use. */
    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            BOUNCY_CASTLE_IMPLEMENTATIONS.forEach { (name, implementation) -> JSch.setConfig(name, implementation) }
            installed = true
        }
    }

    fun newJSch(): JSch {
        install()
        return JSch()
    }

    /**
     * Host-key algorithms to offer, pinned types first, like OpenSSH does for known hosts:
     * otherwise a server offering Ed25519 before the pinned ECDSA key would be reported as a
     * first contact although the host is already trusted. `ssh-rsa` pins map to the SHA-2 RSA
     * signature names that carry the same key.
     */
    fun hostKeyAlgorithms(pinnedTypes: Collection<String>, defaults: String): String {
        val base = defaults.split(',').map(String::trim).filter(String::isNotEmpty)
        val preferred = pinnedTypes.flatMap { type ->
            if (type == "ssh-rsa") listOf("rsa-sha2-512", "rsa-sha2-256", "ssh-rsa") else listOf(type)
        }
        return (preferred + base).distinct().joinToString(",")
    }
}
