package com.qrcommunication.ploipanel

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guard against reintroducing prior-page data and clipping nested site navigation. */
class ServerSiteAuditContractTest {
    private fun screen(name: String): String {
        var root: File? = File(System.getProperty("user.dir") ?: ".")
        while (root != null) {
            val file = File(root, "app/src/main/java/com/qrcommunication/ploipanel/${name}.kt")
            if (file.isFile) return file.readText()
            root = root.parentFile
        }
        error("Missing screen: $name")
    }

    @Test fun paginatedScreensClearPreviousPageBeforeLoading() {
        listOf("SitesScreen", "SiteMonitorsScreen", "DatabasesScreen", "DatabaseBackupsScreen",
            "FileBackupsScreen", "CertificatesScreen", "RedirectsScreen").forEach { name ->
            val code = screen(name)
            assertTrue("$name must clear previous page before a request", Regex(
                "loading = true\\s+error = null\\s+result = null\\s+try \\{\\s+result ="
            ).containsMatchIn(code))
        }
    }

    @Test fun siteAndNestedTabsHaveBoundedViewports() {
        val sites = screen("SitesScreen")
        assertTrue(sites.contains("Box(Modifier.weight(1f).fillMaxWidth()) { detail(selectedId!!) }"))
        assertTrue(sites.contains("Box(Modifier.weight(1f).fillMaxWidth()) { when (siteSection)"))
        assertTrue(sites.contains("LazyColumn(Modifier.weight(1f)"))
        assertTrue(screen("WordPressScreen").contains("Box(Modifier.weight(1f).fillMaxWidth()) { when (tab)"))
        assertTrue(screen("FileBackupsScreen").contains("Box(Modifier.weight(1f).fillMaxWidth()) { if (section == 0)"))
    }

    @Test fun optionLoadingDoesNotSpinForeverAfterFailure() {
        listOf("BackupChannelsUi", "DatabaseBackupsScreen", "FileBackupsScreen").forEach { name ->
            assertTrue("$name", screen(name).contains("PagedOptionPicker("))
        }
        val picker = screen("PagedOptionPicker")
        assertTrue(picker.contains("error = failure"))
        assertTrue(picker.contains("loading = false"))
        assertTrue(picker.contains("retry++"))
    }

    @Test fun backupPickersRequestPagesAndKeepSelectionInForm() {
        val database = screen("DatabaseBackupsScreen")
        val file = screen("FileBackupsScreen")
        val channel = screen("BackupChannelsUi")
        val projects = screen("ProjectsScreen")
        listOf(database, file, channel, projects).forEach {
            assertTrue(it.contains("page = page, perPage = 50"))
        }
        assertTrue(database.contains("database.id in selectedDatabases"))
        assertTrue(file.contains("site.id in selectedSites"))
        assertTrue(file.contains("sitePaths[site.id]"))
        assertTrue(channel.contains("channelId == channel.id"))
        assertTrue(channel.contains("pendingDetach = channel.id to channel.location"))
        assertTrue(channel.contains("stringResource(R.string.confirm_detach_backup_channel)"))
        assertTrue(projects.contains("toggleProjectId(servers"))
        assertTrue(projects.contains("toggleProjectId(sites"))
        assertTrue(screen("SiteMonitorsScreen").contains("items(responses)"))
        assertTrue(!screen("SiteMonitorsScreen").contains("responses.take(50)"))
    }

    @Test fun wpCliAndNonDryRunReplaceRequireFreshConfirmation() {
        val wordpress = screen("WordPressScreen")
        assertTrue(wordpress.contains("pendingCli = command.trim()"))
        assertTrue(wordpress.contains("pendingReplace = search to replace"))
        assertTrue(wordpress.contains("message = stringResource(R.string.audit_confirm_wp_cli)"))
        assertTrue(wordpress.contains("message = stringResource(R.string.audit_confirm_wp_replace)"))
    }
}
