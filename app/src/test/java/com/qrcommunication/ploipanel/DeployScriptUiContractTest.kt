package com.qrcommunication.ploipanel

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeployScriptUiContractTest {
    private fun source(name: String): String {
        var root: File? = File(System.getProperty("user.dir") ?: ".")
        while (root != null) {
            val file = File(root, "app/src/main/java/com/qrcommunication/ploipanel/$name.kt")
            if (file.isFile) return file.readText()
            root = root.parentFile
        }
        error("Missing source: $name")
    }

    @Test fun perSiteSaveDoesNotIssuePatchUntilFreshAuthentication() {
        val screen = source("DeploymentsScreen")
        assertTrue(screen.contains("pendingSave = draft"))
        assertTrue(screen.contains("SensitiveConfirmDialog(lock = lock, activity = activity,"))
        assertTrue(screen.contains("onConfirmed = {\n                pendingSave = null"))
        assertTrue(screen.contains("PloiApi.updateDeployScript(token, serverId, siteId, pending)"))
        assertTrue(screen.contains("PloiApi.deployScript(token, serverId, siteId)"))
    }

    @Test fun globalApplyUsesSiteSelectionAndOneAuthenticationAtCommitTime() {
        val screen = source("GlobalDeployScriptsScreen")
        assertTrue(screen.contains("selected + (key to DeployTarget("))
        assertTrue(screen.contains("pendingApply = script to targetsSnapshot"))
        assertTrue(screen.contains("SensitiveConfirmDialog(lock, activity,"))
        assertTrue(screen.contains("DeployScriptBatch(gateway).apply(contents, targetsSnapshot)"))
        val main = source("MainActivity")
        assertTrue(main.contains("GlobalDeployScriptsScreen(token, profileId, lock, activity,"))
    }

    @Test fun verificationIsTriggeredByCommitAndCannotBypassFailedPinOrStaleBiometric() {
        val gate = source("SensitiveConfirm")
        assertTrue(gate.contains("KeyboardActions(onDone = { verifyPin() })"))
        assertTrue(gate.contains("lock.verify(pin)"))
        assertTrue(gate.contains("showBiometricPrompt(activity, onSuccess = { verified() }"))
        assertTrue(gate.contains("if (active.get() && !lock.isLocked() && submitted.compareAndSet(false, true))"))
    }
}
