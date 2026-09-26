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
        assertTrue(main.contains("LazyRow(") && main.contains("animateScrollToItem(panelTab)"))
        assertTrue(detail.contains("LazyRow(") && detail.contains("tabState.animateScrollToItem("))
        assertTrue(main.contains("Modifier.weight(1f).fillMaxWidth()"))
        assertTrue(detail.contains("FilterChip(") && main.contains("FilterChip("))
    }

    @Test fun onboardingAndLongSiteActionsRemainScrollable() {
        val onboarding = source("${ui}LockScreen.kt")
        val site = source("${ui}SitesScreen.kt")
        assertTrue(onboarding.contains("fillMaxSize().verticalScroll(rememberScrollState())"))
        assertTrue(site.contains("FlowRow(horizontalArrangement"))
        assertFalse(site.contains("Row(horizontalArrangement = Arrangement.spacedBy(8.dp))"))
    }
}
