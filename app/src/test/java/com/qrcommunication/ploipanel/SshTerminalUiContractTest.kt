package com.qrcommunication.ploipanel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Source-level guardrails for the SSH terminal; not a device UI test. */
class SshTerminalUiContractTest {
    private fun source(path: String): String {
        var root: File? = File(System.getProperty("user.dir") ?: ".")
        while (root != null) {
            val file = File(root, "app/src/main/java/com/qrcommunication/ploipanel/$path")
            if (file.isFile) return file.readText()
            root = root.parentFile
        }
        error("Missing source: $path")
    }

    @Test fun terminalIsReachableFromPanelAndServers() {
        val main = source("MainActivity.kt")
        val nav = source("AppNavigation.kt")
        assertTrue(nav.contains("TERMINAL(10, R.string.nav_terminal)"))
        assertTrue(nav.contains("10 -> R.string.ssh_term_tab"))
        assertTrue(main.contains("SshTerminalScreen(profileId, token, lock, activity)"))
        val server = source("ServerScreen.kt")
        assertTrue(server.contains("TerminalNavigator.open(SshTarget("))
        assertTrue("server cards offer a one-tap terminal", source("ServerDashboard.kt").contains("TerminalNavigator.open(SshTarget("))
        assertTrue("uses the Ploi-reported SSH port", server.contains("current.sshPort.takeIf { it > 0 }"))
    }

    @Test fun connectionNeverPinsOrBypassesHostKeys() {
        val ui = source("SshTerminalScreen.kt")
        assertFalse("pinning belongs to the probe dialog", ui.contains(".trust("))
        assertFalse(ui.contains("repin("))
        assertTrue("unknown host routes to the probe", ui.contains("SshHostProbeDialog("))
        val shell = source("ssh/SshShell.kt")
        assertTrue(shell.contains("setConfig(\"StrictHostKeyChecking\", \"yes\")"))
        assertTrue("repository never pins", shell.contains("override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit"))
        assertTrue(shell.contains("override fun promptYesNo(message: String?): Boolean = false"))
        assertTrue("agent forwarding disabled", shell.contains("channel.setAgentForwarding(false)"))
    }

    @Test fun secretsAreNotPersistedAndAreWiped() {
        val ui = source("SshTerminalScreen.kt")
        assertTrue(ui.contains("credential.password.fill('\\u0000')"))
        assertTrue(ui.contains("PasswordVisualTransformation()"))
        assertFalse("passwords are never bookmarked", source("ssh/SshBookmarks.kt").contains("put(\"password\""))
        assertTrue("copied output is flagged sensitive", ui.contains("ClipDescription.EXTRA_IS_SENSITIVE"))
    }

    @Test fun keyInstallIsGatedAndUsesDocumentedEndpoint() {
        val ui = source("SshTerminalScreen.kt")
        assertTrue(ui.contains("PloiApi.createSshKey(token, serverId, CreateSshKeyRequest("))
        assertTrue(ui.contains("SensitiveConfirmDialog("))
    }

    @Test fun terminalCleanupFollowsProfileRemoval() {
        val store = source("ProfileStore.kt")
        assertTrue(store.contains("SshBookmarkStore.key(id)"))
        assertTrue(store.contains("TerminalSessions.closeProfile(id)"))
    }
}
