package com.qrcommunication.ploipanel

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Regression for "tapping Monitoring closes the app". The monitoring card and the Surveillance tab
 * register an ActivityResult launcher (notification permission). Under the in-app language
 * override LocalContext is a configuration context, not the Activity, so the launcher found no
 * registry owner and threw. These render the real composables inside the app's localized scope.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LocalizedActivityScopeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun frenchScope(content: @androidx.compose.runtime.Composable () -> Unit) {
        val activity = compose.activity
        val configuration = Configuration(activity.resources.configuration).apply { setLocale(Locale.FRENCH) }
        val localized = activity.createConfigurationContext(configuration)
        compose.setContent { LocalizedActivityScope(activity, localized, configuration, content) }
    }

    @Test fun monitoringThresholdCardRendersUnderInAppLanguage() {
        frenchScope { ThresholdAlertCard("profile", Server(1, "web", "active", "10.0.0.1")) }
        val title = compose.activity.createConfigurationContext(
            Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.FRENCH) }
        ).getString(R.string.threshold_title)
        compose.onNodeWithText(title).assertIsDisplayed()
    }

    @Test fun surveillanceTabRendersUnderInAppLanguage() {
        // The screen syncs its periodic worker; the test host has no auto-initialised WorkManager.
        androidx.work.WorkManager.initialize(compose.activity, androidx.work.Configuration.Builder().build())
        frenchScope { LocalChecksScreen() }
        compose.waitForIdle()
    }
}
