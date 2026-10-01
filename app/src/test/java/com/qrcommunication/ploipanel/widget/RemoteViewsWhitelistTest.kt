package com.qrcommunication.ploipanel.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Launchers inflate widget layouts through RemoteViews, which only accepts a fixed set of view
 * classes. Any other tag (e.g. a plain <View> spacer) makes the widget show "Can't load widget".
 */
class RemoteViewsWhitelistTest {
    private val allowed = setOf(
        "FrameLayout", "LinearLayout", "RelativeLayout", "GridLayout", "AnalogClock", "Button", "Chronometer",
        "ImageButton", "ImageView", "ProgressBar", "TextView", "ViewFlipper", "ListView", "GridView", "StackView",
        "AdapterViewFlipper", "TextClock", "CheckBox", "Switch", "RadioButton", "RadioGroup", "ViewStub", "merge", "include"
    )

    private fun layoutDir(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val candidate = File(dir, "app/src/main/res/layout")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("layout dir not found")
    }

    @Test fun widgetLayoutsOnlyUseRemoteViewsClasses() {
        val widgets = layoutDir().listFiles { f -> f.name.startsWith("widget_") && f.name.endsWith(".xml") }!!
        assertTrue(widgets.size >= 5)
        widgets.forEach { file ->
            val tags = Regex("<([A-Za-z][A-Za-z0-9_.]*)[\\s>/]").findAll(file.readText()).map { it.groupValues[1] }
                .filterNot { it == "xml" }.toSet()
            val bad = tags - allowed
            assertTrue("${file.name} uses non-RemoteViews classes: $bad", bad.isEmpty())
        }
    }
}
