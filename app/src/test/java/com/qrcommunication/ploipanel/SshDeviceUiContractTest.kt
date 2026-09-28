package com.qrcommunication.ploipanel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Source-level guardrails for the device SSH management UI; not a device UI test. */
class SshDeviceUiContractTest {
    private fun screen(name: String): String {
        var root: File? = File(System.getProperty("user.dir") ?: ".")
        while (root != null) {
            val file = File(root, "app/src/main/java/com/qrcommunication/ploipanel/$name.kt")
            if (file.isFile) return file.readText()
            root = root.parentFile
        }
        error("Missing screen: $name")
    }

    @Test fun settingsExposeDeviceSshManagement() {
        assertTrue(screen("SettingsScreen").contains("SshDeviceSection(active?.id, lock, activity)"))
    }

    @Test fun privateKeysNeverLeaveTheVaultForDisplay() {
        val ui = screen("SshDeviceScreen")
        assertFalse("private PEM must never be read back into the UI", ui.contains("privateKeyPem("))
        assertTrue(ui.contains("KeystoreTokenCipher(SSH_VAULT_ALIAS)"))
        assertTrue("key deletion requires a fresh PIN/biometric gate", ui.contains("SensitiveConfirmDialog("))
        assertTrue(ui.contains("SshKeyVault.parsePublicKeyLine(publicLine)"))
    }

    @Test fun hostTrustUiKeepsExplicitOperatorActions() {
        val ui = screen("SshDeviceScreen")
        assertTrue(ui.contains("trustStore.revoke(entry.host, entry.port, entry.keyType)"))
        assertTrue(ui.contains("trustStore.importKnownHosts(text)"))
        assertTrue(ui.contains("trustStore.clear()"))
        assertTrue(ui.contains("entry.fingerprint()"))
        assertFalse("re-pin belongs to connection time, never to this management UI", ui.contains("repin("))
        assertFalse("trusting a key belongs to connection time, never to this management UI", ui.contains(".trust("))
    }

    @Test fun failedImportFormsStayOpenForRetry() {
        val ui = screen("SshDeviceScreen")
        assertTrue("dialog closes only through the success callback", ui.contains("onImported = {"))
        assertTrue(ui.contains("error = mapSshError(failure)"))
        assertTrue(ui.contains("enabled = !busy && label.isNotBlank()"))
    }
}
