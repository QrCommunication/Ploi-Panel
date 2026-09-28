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

    @Test fun accountAndServerTabsAreBoundedAndKeepSelectionVisible() {
        val main = source("${ui}MainActivity.kt")
        val detail = source("${ui}ServerScreen.kt")
        assertTrue(main.contains("LazyRow(") && main.contains("animateScrollToItem(sections.indexOfFirst { it.first == panelTab }.coerceAtLeast(0))"))
        assertTrue(main.contains("0 to R.string.servers, 8 to R.string.deploy_global_tab"))
        assertTrue(detail.contains("LazyRow(") && detail.contains("tabState.animateScrollToItem("))
        assertTrue(main.contains("Modifier.weight(1f).fillMaxWidth()"))
        assertTrue(detail.contains("FilterChip(") && main.contains("FilterChip("))
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
        assertTrue(main.contains("ServerItemCard(server)"))
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
