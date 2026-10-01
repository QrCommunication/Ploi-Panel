package com.qrcommunication.ploipanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class SiteCategoryNavigationTest {
    @Test fun everyNestedScreenReturnsToItsCategory() {
        assertEquals(SiteCategory.OPERATIONS, siteSubsectionCategory(1))
        assertEquals(SiteCategory.OPERATIONS, siteSubsectionCategory(2))
        assertEquals(SiteCategory.SECURITY, siteSubsectionCategory(3))
        assertEquals(SiteCategory.SECURITY, siteSubsectionCategory(4))
        (5..9).forEach { assertEquals(SiteCategory.OPERATIONS, siteSubsectionCategory(it)) }
        listOf(0, 10).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { siteSubsectionCategory(invalid) }
        }
    }

    @Test fun overviewAndEveryCategoryAreRepresented() {
        assertEquals(5, SiteCategory.entries.size)
        assertTrue(SiteCategory.entries.containsAll(listOf(
            SiteCategory.OVERVIEW, SiteCategory.DEPLOYMENT, SiteCategory.CONFIGURATION,
            SiteCategory.SECURITY, SiteCategory.OPERATIONS
        )))
    }

    @Test fun categoriesAndNestedScreensRemainBoundedAndBackNavigable() {
        var root: File? = File(System.getProperty("user.dir") ?: ".")
        var source: String? = null
        while (root != null && source == null) {
            val file = File(root, "app/src/main/java/com/qrcommunication/ploipanel/SitesScreen.kt")
            if (file.isFile) source = file.readText()
            root = root.parentFile
        }
        requireNotNull(source) { "SitesScreen.kt missing" }
        assertTrue(source.contains("key(token, serverId, id) {"))
        assertTrue(source.contains("BackHandler(enabled = siteSection != 0 || siteCategory != null)"))
        assertTrue(source.contains("Box(Modifier.weight(1f).fillMaxWidth()) { when (siteSection)"))
        assertTrue(source.contains("siteCategory = siteSubsectionCategory(siteSection)"))
        assertTrue(source.contains("SiteCategory.entries.forEach"))
        assertTrue(source.contains("TestDomainSection(token, serverId, siteId, busy, lock, activity,"))
        assertTrue(source.contains("SensitiveConfirmDialog(lock = lock, activity = activity,"))
        listOf("queues_tab", "redirects_tab", "certificates_tab", "auth_users_tab", "aliases_tab",
            "tenants_tab", "site_monitors_tab", "apps_tab", "wordpress_tab").forEach { name ->
            assertTrue("Navigation missing $name", source.contains("SiteNavigationCard(R.string.$name"))
        }
        listOf("repository_title", "deploy_site", "deploy_production", "deploy_script",
            "edit_site", "php_version_change", "nginx_configuration", "env_file", "site_logs",
            "horizon_statistics", "reset_permissions", "clone_site", "suspend_site", "resume_site",
            "delete_site").forEach { name ->
            assertTrue("Action missing $name", source.contains("SiteNavigationCard(R.string.$name"))
        }
    }
}
