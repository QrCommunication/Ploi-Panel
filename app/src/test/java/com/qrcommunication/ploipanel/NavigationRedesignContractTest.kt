package com.qrcommunication.ploipanel

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the redesigned navigation model: destinations, rail/bar switch, "More" hub coverage,
 * profile switcher wiring and the server category hub. Source-level and pure-logic checks only;
 * nothing here proves how the screens render on a device.
 */
class NavigationRedesignContractTest {
    private fun source(name: String): String {
        var root: File? = File(System.getProperty("user.dir") ?: ".")
        while (root != null) {
            val file = File(root, "app/src/main/java/com/qrcommunication/ploipanel/$name")
            if (file.isFile) return file.readText()
            root = root.parentFile
        }
        error("Missing source: $name")
    }

    private fun res(path: String): String {
        var root: File? = File(System.getProperty("user.dir") ?: ".")
        while (root != null) {
            val file = File(root, "app/src/main/res/$path")
            if (file.isFile) return file.readText()
            root = root.parentFile
        }
        error("Missing resource: $path")
    }

    @Test fun fivePrimaryDestinationsMapToTheHistoricalTabs() {
        assertEquals(
            listOf(0, 10, 9, 8, MORE_HUB_TAB),
            PanelDestination.entries.map { it.tab }
        )
        PanelDestination.entries.forEach { assertEquals(it, destinationForTab(it.tab)) }
    }

    @Test fun moreHubCoversEverySecondaryTabAndReturnsToIt() {
        // 0, 8, 9, 10 are destinations; every other historical tab (1..7) must be reachable from More.
        assertEquals((1..7).toList(), MoreEntry.entries.map { it.tab }.sorted())
        MoreEntry.entries.forEach { entry ->
            assertEquals(PanelDestination.MORE, destinationForTab(entry.tab))
            assertEquals(MORE_HUB_TAB, parentTab(entry.tab))
        }
        PanelDestination.entries.forEach { assertNull(parentTab(it.tab)) }
        val main = source("MainActivity.kt")
        assertTrue(main.contains("MORE_HUB_TAB -> MoreHubScreen(onOpen = { panelTab = it })"))
        (1..10).forEach { tab -> assertTrue("panelTab $tab is not rendered", main.contains("$tab -> ")) }
    }

    @Test fun railReplacesTheBottomBarFromMediumWidth() {
        assertFalse(usesNavigationRail(599.dp))
        assertTrue(usesNavigationRail(600.dp))
        assertTrue(usesNavigationRail(840.dp))
        val main = source("MainActivity.kt")
        assertTrue(main.contains("val rail = usesNavigationRail(maxWidth)"))
        assertTrue(main.contains("if (!rail && showNavigation) PanelNavigationBar("))
        // Master/detail still needs the wider threshold inside the content area.
        assertTrue(main.contains("val expanded = maxWidth >= 720.dp"))
    }

    @Test fun automaticRedirectsAndGlobalDeployLockSurvive() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("if (terminalRequest != null && token != null && !globalBatchRunning) panelTab = 10"))
        assertTrue(main.contains("if ((importUri != null || transferStatus != 0) && token != null && !globalBatchRunning) panelTab = 7"))
        assertTrue(main.contains("LaunchedEffect(widgetRoute, globalBatchRunning)"))
        assertTrue(main.contains("onRunningChange = { globalBatchRunning = it }"))
        assertTrue(main.contains("if (globalBatchRunning) NavigationLockedNotice()"))
    }

    @Test fun topBarSwitchesProfilesThroughTheSettingsCodePath() {
        val main = source("MainActivity.kt")
        val nav = source("AppNavigation.kt")
        assertTrue(main.contains("onActiveChanged = { profile -> applyActiveProfile(profile) }"))
        assertTrue("switcher checks the token before activating", main.contains("if (store.tokenFor(profile.id) == null)"))
        assertTrue(main.contains("store.activate(profile.id)\n                                profileError = 0\n                                applyActiveProfile(profile)"))
        assertTrue("manage opens Settings", main.contains("onManageProfiles = { panelTab = 7 }"))
        assertTrue("disconnect stays reachable", nav.contains("R.string.disconnect"))
        assertTrue(nav.contains("contentDescription = stringResource(R.string.lock_now)"))
        assertTrue("no token is ever rendered", !nav.contains("tokenFor("))
    }

    @Test fun serverHubCoversAllSixteenSubScreensInFiveCategories() {
        assertEquals((0..15).toList(), ServerSection.entries.map { it.id }.sorted())
        assertEquals(5, ServerCategory.entries.size)
        ServerCategory.entries.forEach { category ->
            assertTrue("$category is empty", ServerSection.entries.any { it.category == category })
        }
        val server = source("ServerScreen.kt")
        ServerSection.entries.forEach { section ->
            assertTrue("$section is not rendered", server.contains("ServerSection.${section.name} -> "))
        }
        assertTrue(server.contains("ServerSection.entries.filter { it.category == category }.forEach"))
        assertTrue("terminal stays reachable from the Access category",
            server.contains("category == ServerCategory.ACCESS && server.ipAddress.isNotBlank()"))
        assertTrue(server.contains("R.string.ssh_term_open_server"))
    }

    @Test fun statusGroupingNeverGuessesHealthy() {
        assertEquals(ServerStatusKind.HEALTHY, serverStatusKind("active"))
        assertEquals(ServerStatusKind.HEALTHY, serverStatusKind(" Active "))
        assertEquals(ServerStatusKind.PENDING, serverStatusKind("installing"))
        assertEquals(ServerStatusKind.ERROR, serverStatusKind("installation-failed"))
        assertEquals(ServerStatusKind.ERROR, serverStatusKind("offline"))
        assertEquals(ServerStatusKind.UNKNOWN, serverStatusKind(""))
        assertEquals(ServerStatusKind.UNKNOWN, serverStatusKind("mystery"))
        // The pill always shows words and an icon, and keeps the raw Ploi value when it differs.
        val ui = source("DesignComponents.kt")
        assertTrue(ui.contains("R.string.status_with_raw, grouped, trimmed"))
        assertTrue(ui.contains("Icon(icon, contentDescription = null"))
    }

    @Test fun serverFilterOnlySearchesTheLoadedPage() {
        val rows = listOf(
            Server(1, "web-1", "active", "10.0.0.1"),
            Server(2, "db-main", "installing", "10.0.0.2")
        )
        assertEquals(rows, filterLoadedServers(rows, "  "))
        assertEquals(listOf(rows[1]), filterLoadedServers(rows, "DB"))
        assertEquals(listOf(rows[0]), filterLoadedServers(rows, "10.0.0.1"))
        assertEquals(listOf(rows[1]), filterLoadedServers(rows, "install"))
        listOf("values", "values-en").forEach { qualifier ->
            val xml = res("$qualifier/ui_redesign.xml")
            val scope = Regex("name=\"servers_search_scope\">(.*?)</string>").find(xml)!!.groupValues[1]
            assertTrue("$qualifier must say the filter is limited to the page", scope.contains("%1\$d"))
        }
    }

    @Test fun everyRedesignStringExistsInBothLocales() {
        fun names(xml: String) = Regex("<string name=\"(\\w+)\"").findAll(xml).map { it.groupValues[1] }.toSet()
        val fr = names(res("values/ui_redesign.xml"))
        val en = names(res("values-en/ui_redesign.xml"))
        assertTrue(fr.isNotEmpty())
        assertEquals(fr, en)
    }
}
