package com.qrcommunication.ploipanel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards host declarations and the security-sensitive widget entry points. */
class WidgetContractTest {
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

    @Test fun providersConfigureAndRefreshWithoutPromisingFiveMinutes() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        val worker = source("${widget}MonitoringWidgets.kt")
        assertTrue(manifest.contains(".widget.SingleServerWidget"))
        assertTrue(manifest.contains(".widget.MultiServerWidget"))
        assertTrue(manifest.contains(".widget.WidgetConfigureActivity"))
        assertTrue(worker.contains("PeriodicWorkRequestBuilder<WidgetWorker>(15, TimeUnit.MINUTES)"))
        assertTrue(worker.contains("NetworkType.CONNECTED"))
        assertFalse(worker.contains("putExtra(\"token\""))
    }

    @Test fun configurationRequiresPinAndClosesOnBackground() {
        val config = source("${widget}WidgetConfigureActivity.kt")
        assertTrue(config.contains("override fun onStop()"))
        assertTrue(config.contains("authorized = false"))
        assertTrue(config.contains("lock.verify(pin)"))
        assertTrue(config.contains("safeDrawingPadding().imePadding()"))
        assertTrue(config.contains("LazyColumn("))
    }

    @Test fun widgetTapRechecksProfileAfterUnlock() {
        val main = source("app/src/main/java/com/qrcommunication/ploipanel/MainActivity.kt")
        assertTrue(main.contains("!unlocked -> PinUnlockScreen("))
        assertTrue(main.contains("LaunchedEffect(widgetRoute)"))
        assertTrue(main.contains("store.profiles().firstOrNull { it.id == route.profileId }"))
        assertTrue(main.contains("PloiApi.server(routeToken, route.serverId)"))
    }
}
