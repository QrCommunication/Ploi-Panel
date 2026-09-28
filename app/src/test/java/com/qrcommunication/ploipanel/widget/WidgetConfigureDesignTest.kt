package com.qrcommunication.ploipanel.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Configuration-only contract: paging, selection and localized labels. */
class WidgetConfigureDesignTest {
    private fun source(path: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val file = File(dir, path)
            if (file.isFile) return file.readText()
            dir = dir.parentFile
        }
        error("Missing $path")
    }

    @Test fun selectionIsBoundedAndSurvivesPaging() {
        val config = source("app/src/main/java/com/qrcommunication/ploipanel/widget/WidgetConfigureActivity.kt")
        assertTrue(config.contains("WidgetConfig.MAX_MULTI_SERVERS"))
        assertTrue(config.contains("selected = setOf(server.id)"))
        assertTrue(config.contains("take(if (single) 1 else WidgetConfig.MAX_MULTI_SERVERS)"))
        assertTrue(config.contains("Role.RadioButton"))
        assertTrue(config.contains("selected.size < maxServers"))
        assertTrue(config.contains("limitReached = true"))
        assertTrue(config.contains("PloiApi.servers(token, page, 50)"))
        assertTrue(config.contains("serverPage = null"))
        assertTrue(config.contains("retry++"))
        assertTrue(config.contains("serverPage?.hasNext == true"))
        assertTrue(config.contains("selectedNames[serverId]"))
        assertTrue(config.contains("selected = selected - serverId"))
        assertTrue(config.contains("loadedProfile == profile"))
        assertFalse(config.contains("page <= 20"))
        assertFalse(config.contains("selected.all { id -> servers?"))
        assertTrue(config.contains("override fun onStop()"))
        assertTrue(config.contains("authorized = false"))
        assertTrue(config.contains("safeDrawingPadding().imePadding()"))
    }

    @Test fun designLabelsExistInFrenchAndEnglish() {
        val keys = listOf("profile", "servers", "selected", "remove", "count", "limit", "page_error", "retry",
            "previous", "next", "page", "cpu", "ram", "disk", "load")
        for (locale in listOf("values", "values-en")) {
            val xml = source("app/src/main/res/$locale/widget_config_design.xml")
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(xml.byteInputStream())
            val names = document.getElementsByTagName("string")
            val available = (0 until names.length).map { names.item(it).attributes.getNamedItem("name").nodeValue }
            assertTrue("Missing $locale labels", keys.all { "widget_config_$it" in available })
        }
    }
}
