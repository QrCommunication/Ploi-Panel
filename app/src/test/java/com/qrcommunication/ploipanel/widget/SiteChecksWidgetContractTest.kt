package com.qrcommunication.ploipanel.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Host declarations, lock behavior and localization contract for the watchdog widget. */
class SiteChecksWidgetContractTest {
    private fun source(path: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val file = File(dir, path)
            if (file.isFile) return file.readText()
            dir = dir.parentFile
        }
        error("Missing $path")
    }

    private val widget = "app/src/main/java/com/qrcommunication/ploipanel/widget/"
    private val main = "app/src/main/java/com/qrcommunication/ploipanel/"

    @Test fun hostDeclaresProviderServiceAndConfiguration() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains(".widget.SiteChecksWidget"))
        assertTrue(manifest.contains(".widget.SiteCheckRowsService"))
        assertTrue(manifest.contains("@xml/widget_checks_info"))
        assertTrue(manifest.contains("android.permission.BIND_REMOTEVIEWS"))
        val info = source("app/src/main/res/xml/widget_checks_info.xml")
        assertTrue(info.contains("com.qrcommunication.ploipanel.widget.WidgetConfigureActivity"))
        assertTrue(info.contains("@layout/widget_checks"))
        assertTrue(info.contains("@string/widget_checks"))
        assertTrue(info.contains("android:updatePeriodMillis=\"0\""))
        assertTrue(source("app/src/main/res/layout/widget_checks.xml").contains("widget_checks_list"))
        assertTrue(source("app/src/main/res/layout/widget_checks_row.xml").contains("widget_check_status"))
    }

    @Test fun rendererKeepsDataLocalBoundedAndLockAware() {
        val renderer = source("${widget}SiteChecksWidget.kt")
        assertTrue(renderer.contains("ploipanel-widget://checks/"))
        assertTrue(renderer.contains("notifyAppWidgetViewDataChanged"))
        assertTrue(renderer.contains("isDeviceLocked"))
        assertTrue(renderer.contains("R.string.widget_locked"))
        assertTrue(renderer.contains("const val MAX_WIDGET_CHECKS = 10"))
        assertTrue(renderer.contains("LocalCheckStore"))
        assertFalse(renderer.contains("PloiApi."))
        assertFalse(renderer.contains("putExtra(\"token\""))
    }

    @Test fun workerAndScreenRefreshCheckWidgetsAfterStoreChanges() {
        assertTrue(source("${main}LocalCheckWorker.kt").contains("SiteChecksWidgetRefresh.refreshAll"))
        assertTrue(source("${main}LocalChecksScreen.kt").contains("SiteChecksWidgetRefresh.refreshAll"))
    }

    @Test fun configurationKeepsPinGateForCheckWidgets() {
        val config = source("${widget}WidgetConfigureActivity.kt")
        assertTrue(config.contains("SiteChecksWidget::class.java.name"))
        assertTrue(config.contains("SiteCheckWidgetConfig.MAX_WIDGET_CHECKS"))
        assertTrue(config.contains("lock.verify(pin)"))
        assertTrue(config.contains("override fun onStop()"))
        assertTrue(config.contains("authorized = false"))
        assertTrue(config.contains("safeDrawingPadding().imePadding()"))
        assertTrue(config.contains("Role.Checkbox"))
    }

    @Test fun watchdogStaleRuleIsSharedBetweenScreenAndWidget() {
        assertTrue(source("${main}LocalSiteCheck.kt").contains("const val STALE_MS = 30 * 60_000L"))
        assertTrue(source("${main}LocalChecksScreen.kt").contains("isLocalCheckStale(status.lastCheckedAt"))
        assertFalse(source("${main}LocalChecksScreen.kt").contains("30 * 60_000L"))
    }

    @Test fun checkWidgetLabelsExistInFrenchAndEnglish() {
        val keys = listOf("widget_checks", "widget_checks_empty", "widget_checks_updated",
            "widget_checks_notice", "widget_config_checks", "widget_config_no_checks",
            "widget_config_checks_limit")
        for (locale in listOf("values", "values-en")) {
            val xml = source("app/src/main/res/$locale/widget_checks_strings.xml")
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(xml.byteInputStream())
            val names = document.getElementsByTagName("string")
            val available = (0 until names.length).map { names.item(it).attributes.getNamedItem("name").nodeValue }
            assertTrue("Missing $locale labels", keys.all { it in available })
        }
    }
}
