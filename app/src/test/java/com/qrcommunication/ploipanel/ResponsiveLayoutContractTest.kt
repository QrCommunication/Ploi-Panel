package com.qrcommunication.ploipanel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the layout decisions that keep navigation and focused forms on screen. */
class ResponsiveLayoutContractTest {
    private fun source(path: String): String {
        var directory: File? = File(System.getProperty("user.dir") ?: ".")
        while (directory != null) {
            val file = File(directory, path)
            if (file.isFile) return file.readText()
            directory = directory.parentFile
        }
        error("Missing $path")
    }

    private val ui = "app/src/main/java/com/qrcommunication/ploipanel/"

    @Test fun systemBarsAndKeyboardAreHandledAtRoot() {
        val main = source("${ui}MainActivity.kt")
        assertTrue(main.contains("Modifier.fillMaxSize().safeDrawingPadding().imePadding()"))
        assertTrue(source("app/src/main/AndroidManifest.xml").contains("android:windowSoftInputMode=\"adjustResize\""))
    }

    @Test fun accountAndServerNavigationIsBoundedAndKeepsSelectionVisible() {
        val main = source("${ui}MainActivity.kt")
        val nav = source("${ui}AppNavigation.kt")
        val detail = source("${ui}ServerScreen.kt")
        // Five fixed destinations (bar or rail) replace the old scrolling chip strip; the selected
        // destination is always on screen and derived from panelTab.
        assertTrue(main.contains("PanelNavigationBar(panelTab, locked = globalBatchRunning)"))
        assertTrue(main.contains("PanelNavigationRail(panelTab, locked = globalBatchRunning)"))
        assertTrue(nav.contains("selected = current == destination"))
        assertTrue(nav.contains("enabled = !locked || current == destination"))
        // Content lives in a weighted viewport between top bar and bottom bar.
        assertTrue(main.contains("Modifier.weight(1f).fillMaxWidth()"))
        // Server sub-screens open in a bounded viewport under their back row.
        assertTrue(detail.contains("Box(Modifier.weight(1f).fillMaxWidth())"))
        assertTrue(detail.contains("BackHandler(enabled = section != null) { section = null }"))
    }

    @Test fun settingsExposeProfileAppearanceLanguageAndBiometricUnlock() {
        val main = source("${ui}MainActivity.kt")
        val settings = source("${ui}SettingsScreen.kt")
        val lockScreen = source("${ui}LockScreen.kt")
        assertTrue(main.contains("7 -> SettingsScreen("))
        assertTrue(main.contains("LocalConfiguration provides configuration"))
        assertTrue(settings.contains("BiometricToggle(lock, activity)"))
        assertTrue(settings.contains("AppTheme.SYSTEM, AppTheme.LIGHT, AppTheme.DARK"))
        assertTrue(settings.contains("AppLanguage.SYSTEM, AppLanguage.FRENCH, AppLanguage.ENGLISH"))
        assertTrue(settings.contains("BuildConfig.VERSION_NAME"))
        assertTrue(lockScreen.contains("if (biometric && lockedMs <= 0)"))
        assertTrue(lockScreen.contains("R.string.use_biometric"))
    }

    @Test fun dashboardUsesActualPageAndDetailStaysInsideViewport() {
        val main = source("${ui}MainActivity.kt")
        val detail = source("${ui}ServerScreen.kt")
        val dashboard = source("${ui}ServerDashboard.kt")
        assertTrue(dashboard.contains("page.servers.size"))
        assertTrue(dashboard.contains("page.currentPage.toString(), page.lastPage.toString()"))
        assertTrue(main.contains("servers = null"))
        assertTrue(main.contains("ServerPageHero(pageData)"))
        // Rows still render through the shared card; offline mode only disables its click target.
        assertTrue(main.contains("ServerItemCard(server, enabled = !offline,"))
        assertTrue(main.contains("Box(Modifier.weight(1f).fillMaxWidth())"))
        assertTrue(detail.contains("Box(Modifier.weight(1f).fillMaxWidth())"))
    }

    @Test fun onboardingAndLongSiteActionsRemainScrollable() {
        val onboarding = source("${ui}LockScreen.kt")
        val site = source("${ui}SitesScreen.kt")
        assertTrue(onboarding.contains("fillMaxSize().verticalScroll(rememberScrollState())"))
        assertTrue(site.contains("FlowRow(horizontalArrangement"))
        assertFalse(site.contains("Row(horizontalArrangement = Arrangement.spacedBy(8.dp))"))
    }
}
