package com.qrcommunication.ploipanel

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

private class PinMemoryPrefs : ProfilePrefs {
    val map = mutableMapOf<String, String>()
    override fun read(key: String): String? = map[key]
    override fun write(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

/**
 * The PIN screens promise 4 to 12 digits; these drive the real keypad to prove a PIN longer than
 * four digits can be entered, confirmed, stored and then used to unlock.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PinEntryLengthTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int): String = compose.activity.createConfigurationContext(
        Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.ENGLISH) }
    ).getString(id)

    private fun setEnglish(content: @androidx.compose.runtime.Composable () -> Unit) {
        val activity = compose.activity
        val configuration = Configuration(activity.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        val localized = activity.createConfigurationContext(configuration)
        compose.setContent { LocalizedActivityScope(activity, localized, configuration) { PloiPanelTheme(false, content) } }
    }

    private fun type(digits: String) = digits.forEach { digit ->
        compose.onNode(hasText(digit.toString()) and hasClickAction()).performScrollTo().performClick()
    }

    private fun button(id: Int) = compose.onNode(hasText(string(id)) and hasClickAction())

    private fun digitsEntered(count: Int) =
        compose.onNodeWithContentDescription(string(R.string.pin_digits_entered).replace("%1\$d", count.toString()))

    @Test fun setupAcceptsTwelveDigitsAndStopsThere() {
        val lock = AppLock(PinMemoryPrefs())
        var done = false
        setEnglish { PinSetupScreen(lock) { done = true } }
        val pin = "135792468024"
        type(pin + "9") // thirteenth digit must be ignored
        digitsEntered(12).assertExists()
        button(R.string.pin_continue).performScrollTo().assertIsEnabled().performClick()
        type(pin)
        button(R.string.set_pin).performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        assertTrue(done)
        assertEquals(AppLock.UnlockResult.Unlocked, lock.verify(pin))
    }

    @Test fun setupAcceptsEightDigits() {
        val lock = AppLock(PinMemoryPrefs())
        var done = false
        setEnglish { PinSetupScreen(lock) { done = true } }
        button(R.string.pin_continue).performScrollTo().assertIsNotEnabled()
        type("13572468")
        digitsEntered(8).assertExists()
        button(R.string.pin_continue).performScrollTo().performClick()
        type("13572468")
        button(R.string.set_pin).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(done)
        assertEquals(AppLock.UnlockResult.Unlocked, lock.verify("13572468"))
    }
}
