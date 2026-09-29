package com.qrcommunication.ploipanel

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Source-level guardrails for navigation and sensitive operations; not a device UI test. */
class UiWiringAuditContractTest {
    private fun screen(name: String): String {
        var root: File? = File(System.getProperty("user.dir") ?: ".")
        while (root != null) {
            val file = File(root, "app/src/main/java/com/qrcommunication/ploipanel/$name.kt")
            if (file.isFile) return file.readText()
            root = root.parentFile
        }
        error("Missing screen: $name")
    }

    @Test fun accountSectionsHaveIndependentPagination() {
        val account = screen("AccountScreen")
        listOf("backupPage", "channelsPage", "sourcePage").forEach { page ->
            assertTrue("$page must drive a fresh API read", account.contains("$page, refresh)"))
            assertTrue("$page must be wired to the pager", account.contains("$page = it"))
        }
    }

    @Test fun remoteCommandExecutionRequiresFreshConfirmation() {
        val scripts = screen("ScriptsScreen")
        assertTrue(scripts.contains("pendingRun = script to serverIds"))
        assertTrue(scripts.contains("pendingRun = content to user.trim()"))
        assertTrue(scripts.contains("message = stringResource(R.string.confirm_one_off_script)"))
        assertTrue(scripts.contains("message = pluralStringResource(R.plurals.confirm_run_script"))
        assertTrue(screen("ServerScreen").contains("OneOffScriptScreen(token, server.id, lock, activity)"))
        val services = screen("ServicesScreen")
        assertTrue(services.contains("pendingWpCommand = command.trim()"))
        assertTrue(services.contains("message = stringResource(R.string.confirm_wpcli_run)"))
    }

    @Test fun providerCreationPaginatesAndGuardsOneTimeSetupInstructions() {
        val creation = screen("CreateServerScreen")
        assertTrue(creation.contains("LaunchedEffect(token, providerPage)"))
        assertTrue(creation.contains("page = providerPage, perPage = 50"))
        assertTrue(creation.contains("BackHandler { if (customResultReady) confirmExit = true else onCancel() }"))
        assertTrue(creation.contains("credential = null; plan = \"\"; region = \"\"; providerPage = page.currentPage + 1"))
        // Navigation steps aside during creation so its exit guard cannot be bypassed.
        val main = screen("MainActivity")
        assertTrue(main.contains("val showNavigation = !creating && !keyboardVisible"))
        assertTrue(main.contains("if (rail && !creating) PanelNavigationRail("))
        assertTrue(main.contains("locked = globalBatchRunning || creating, showDisconnect = !creating"))
        assertTrue(main.contains("globalBatchRunning || creating -> null"))
    }

    @Test fun archivePickerSurvivesAppRelockAndNeverGetsRawTokensInIntent() {
        val activity = screen("MainActivity")
        val ui = screen("PortableConfigurationUi")
        assertTrue(activity.contains("rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument"))
        assertTrue(activity.contains("rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument"))
        assertTrue(activity.contains("pendingArchive = encrypted"))
        assertTrue(activity.contains("MessageDigest.isEqual(encrypted, saved)"))
        assertTrue(ui.contains("manager.export(passphrase)"))
        assertTrue(ui.contains("onSaveEncryptedArchive(encrypted)"))
        assertTrue(ui.contains("manager.import(bytes, passphrase)"))
    }

    @Test fun failedFormsRemainVisibleForRetry() {
        assertTrue(screen("ProjectsScreen").contains("onSuccess = { creating = false; editing = null }"))
        assertTrue(screen("StatusPagesScreen").contains("onSuccess = { creating = false }"))
        assertTrue(screen("ScriptsScreen").contains("onSuccess = { editing = null }"))
    }
}
