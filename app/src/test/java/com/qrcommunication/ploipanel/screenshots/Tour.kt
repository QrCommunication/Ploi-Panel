package com.qrcommunication.ploipanel.screenshots

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.captureToImage
import java.io.File

/** Small driving helpers for the screenshot tour. Everything is read-only navigation. */
internal class Tour(
    private val compose: ComposeTestRule, private val outDir: File, private val device: String,
    private val activity: () -> android.app.Activity
) {
    /** Draws the whole window (including dialogs are separate windows, so only the main one). */
    private fun drawWindow(): Bitmap {
        var result: Bitmap? = null
        compose.runOnUiThread {
            val view = activity().window.decorView
            val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            result = bitmap
        }
        return result!!
    }

    val shots = mutableListOf<String>()

    fun exists(matcher: SemanticsMatcher): Boolean =
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = 25_000): Boolean = try {
        compose.waitUntil(timeoutMs) { exists(matcher) }
        true
    } catch (_: Throwable) { false }

    fun waitGone(matcher: SemanticsMatcher, timeoutMs: Long = 20_000) {
        try { compose.waitUntil(timeoutMs) { !exists(matcher) } } catch (_: Throwable) { }
    }

    /** Scrolls the innermost scrollable ancestor list to the node if needed, then clicks it. */
    fun tap(matcher: SemanticsMatcher, required: Boolean = true): Boolean {
        val target = matcher and hasClickAction()
        if (!exists(target)) {
            val scrollables = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
            for (index in scrollables.indices.reversed()) {
                try {
                    compose.onAllNodes(hasScrollAction())[index].performScrollToNode(matcher)
                    if (exists(target)) break
                } catch (_: Throwable) { }
            }
        }
        if (!exists(target)) {
            if (required) error("[$device] not found: $matcher")
            return false
        }
        compose.onAllNodes(target).onFirst().performClick()
        compose.waitForIdle()
        return true
    }

    fun tapText(text: String, substring: Boolean = false, required: Boolean = true) =
        tap(hasText(text, substring = substring), required)

    fun scrollTo(matcher: SemanticsMatcher) {
        val scrollables = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
        for (index in scrollables.indices.reversed()) {
            try {
                compose.onAllNodes(hasScrollAction())[index].performScrollToNode(matcher)
                return
            } catch (_: Throwable) { }
        }
    }

    fun shot(name: String, root: SemanticsNodeInteraction? = null) {
        compose.waitForIdle()
        val bitmap = if (root != null) root.captureToImage().asAndroidBitmap() else drawWindow()
        val file = File(outDir, "$device-${"%02d".format(shots.size + 1)}-$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        shots += file.name
    }
}
