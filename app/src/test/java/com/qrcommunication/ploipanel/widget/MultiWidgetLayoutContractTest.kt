package com.qrcommunication.ploipanel.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MultiWidgetLayoutContractTest {
    private fun source(path: String): String {
        var directory: File? = File(System.getProperty("user.dir") ?: ".")
        while (directory != null) {
            val file = File(directory, path)
            if (file.isFile) return file.readText()
            directory = directory.parentFile
        }
        error("Missing $path")
    }
    private val widget = "app/src/main/java/com/qrcommunication/ploipanel/widget/"
    private val res = "app/src/main/res/"

    @Test fun multiWidgetBindsScrollableCollectionWithIndividualRealGauges() {
        val renderer = source("${widget}MonitoringWidgets.kt")
        val factory = source("${widget}MultiServerRowsService.kt")
        assertTrue(renderer.contains("if (single) R.layout.widget_monitoring else R.layout.widget_multi"))
        assertTrue(renderer.contains("view.setRemoteAdapter(R.id.widget_server_list, adapter)"))
        assertTrue(renderer.contains("manager.notifyAppWidgetViewDataChanged(id, R.id.widget_server_list)"))
        assertTrue(factory.contains("gaugeBitmap(gaugeValues(reading.sample, config.metrics)"))
        assertTrue(factory.contains("getViewAt(position: Int)"))
        assertTrue(factory.contains("isDeviceLocked"))
        assertTrue(source("${res}layout/widget_multi.xml").contains("<ListView"))
        assertTrue(source("${res}layout/widget_multi_row.xml").contains("widget_row_chart"))
        assertTrue(source("app/src/main/AndroidManifest.xml").contains("android.permission.BIND_REMOTEVIEWS"))
        assertFalse(factory.contains("PloiApi."))
        assertFalse(renderer.contains("putExtra(\"token\""))
    }

    @Test fun collectionAndWorkerAreBoundedToWidgetMode() {
        val renderer = source("${widget}MonitoringWidgets.kt")
        val factory = source("${widget}MultiServerRowsService.kt")
        val data = source("${widget}WidgetData.kt")
        assertTrue(renderer.contains("widgetServerIds(saved.serverIds, single)"))
        assertTrue(renderer.contains("widgetServerIds(saved.serverIds, WidgetRefresh.isSingle(context, id))"))
        assertTrue(factory.contains("config.serverIds.getOrNull(position)"))
        assertTrue(data.contains("const val MAX_MULTI_SERVERS = 4"))
    }
}
