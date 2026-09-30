package com.qrcommunication.ploipanel.screenshots

import android.content.Context
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import com.qrcommunication.ploipanel.MainActivity
import com.qrcommunication.ploipanel.R
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.Executors

/**
 * Live, read-only screenshot tour of the real app against a real Ploi account, rendered with
 * Robolectric native graphics at several window sizes (phone, foldable folded/unfolded, tablet).
 * Opt-in (`-Pscreenshots`): needs network and PLOI_SCREENSHOT_TOKEN_FILE. It only navigates and
 * reads: no create, update, delete, deploy, restart or script action is ever triggered.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LiveScreenshotTour {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val out: File by lazy {
        File(System.getProperty("ploi.screenshots.out") ?: "build/screenshots").apply { mkdirs() }
    }

    private fun token(): String? = System.getenv("PLOI_SCREENSHOT_TOKEN_FILE")
        ?.let(::File)?.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }

    private fun s(id: Int): String = compose.activity.getString(id)

    companion object {
        init {
            FakeAndroidKeyStore.install()
        }
    }

    @Before fun setUp() {
        FakeAndroidKeyStore.install()
        val context = ApplicationProvider.getApplicationContext<Context>()
        runCatching {
            WorkManager.initialize(context, Configuration.Builder().setExecutor(Executors.newSingleThreadExecutor()).build())
        }
    }

    private fun onboard(tour: Tour, token: String, shots: Boolean) {
        val pin = "135790"
        tour.waitFor(hasText(s(R.string.pin_setup_title)))
        if (shots) tour.shot("pin-setup")
        pin.forEach { tour.tap(hasText(it.toString())) }
        if (shots) tour.shot("pin-setup-typed")
        tour.tapText(s(R.string.pin_continue))
        pin.forEach { tour.tap(hasText(it.toString())) }
        tour.tapText(s(R.string.set_pin))
        tour.waitFor(hasText(s(R.string.welcome_title)))
        if (shots) tour.shot("welcome")
        compose.onAllNodes(hasSetTextAction() and hasText(s(R.string.profile_label))).onFirst().performTextInput("Ploi QR")
        compose.onAllNodes(hasSetTextAction() and hasText(s(R.string.token))).onFirst().performTextInput(token)
        tour.tapText(s(R.string.add_profile))
    }

    private fun waitServers(tour: Tour) {
        tour.scrollTo(hasText("resto-zen-prod"))
        if (!tour.waitFor(hasText("resto-zen-prod"), 40_000)) {
            tour.shot("FAILED-waiting-servers")
            error("server list did not load")
        }
    }

    private fun backToHub(tour: Tour) {
        tour.tap(hasContentDescription(s(R.string.server_all_categories)), required = false)
    }

    private fun openSection(tour: Tour, title: Int, name: String, settleText: String? = null) {
        if (!tour.tapText(s(title), required = false)) return
        if (settleText != null) tour.waitFor(hasText(settleText, substring = true), 20_000)
        tour.waitGone(hasContentDescription(s(R.string.a11y_loading)), 25_000)
        tour.shot(name)
        backToHub(tour)
    }

    private fun fullTour(device: String, deep: Boolean) {
        val token = token()
        assumeTrue("PLOI_SCREENSHOT_TOKEN_FILE not set", token != null)
        val tour = Tour(compose, out, device) { compose.activity }
        onboard(tour, token!!, shots = deep)
        waitServers(tour)
        tour.shot("servers")
        // Rechecks of unreachable servers run automatically; let them finish.
        tour.waitGone(hasText(s(R.string.recheck_running)), 30_000)
        tour.shot("servers-rechecked")
        tour.scrollTo(hasText("giga-apps"))
        tour.shot("servers-unreachable")

        // Active server: hub + every read-only sub-screen.
        tour.tapText("scell-io")
        tour.waitFor(hasText(s(R.string.server_category_overview)))
        tour.shot("server-hub")
        openSection(tour, R.string.server_section_monitoring_description, "server-monitoring", s(R.string.metric_cpu))
        if (deep) {
            openSection(tour, R.string.server_section_sites_description, "server-sites", "scell.io")
            openSection(tour, R.string.server_section_infos_description, "server-infos")
            openSection(tour, R.string.server_section_logs_description, "server-logs")
            openSection(tour, R.string.server_section_daemons_description, "server-daemons")
            openSection(tour, R.string.server_section_network_rules_description, "server-firewall")
            openSection(tour, R.string.server_section_databases_description, "server-databases")
            openSection(tour, R.string.server_section_backups_description, "server-backups")
            openSection(tour, R.string.server_section_insights_description, "server-insights")
        }
        // Compact: back to the list. Master/detail (>= 720 dp): the list stays visible on the left.
        tour.tap(hasContentDescription(s(R.string.back)), required = false)

        // Unreachable server: automatic retest on open.
        tour.scrollTo(hasText("giga-apps"))
        tour.tapText("giga-apps")
        tour.waitFor(hasText(s(R.string.recheck_title)))
        tour.waitGone(hasText(s(R.string.recheck_running)), 30_000)
        tour.shot("server-unreachable-hub")
        tour.tap(hasContentDescription(s(R.string.back)), required = false)

        // Monitored overview.
        tour.scrollTo(hasContentDescription(s(R.string.monitored_overview)))
        if (tour.tap(hasContentDescription(s(R.string.monitored_overview)), required = false)) {
            tour.waitGone(hasContentDescription(s(R.string.a11y_loading)), 25_000)
            tour.shot("monitored")
            tour.tap(hasContentDescription(s(R.string.back)), required = false)
        }

        // Primary destinations.
        listOf(
            R.string.nav_terminal to "terminal", R.string.nav_monitoring to "surveillance",
            R.string.nav_deploy to "deploy", R.string.nav_more to "more"
        ).forEach { (label, name) ->
            tour.tap(hasText(s(label)) and hasClickAction())
            tour.waitGone(hasContentDescription(s(R.string.a11y_loading)), 20_000)
            tour.shot(name)
        }
        if (deep) {
            listOf(
                R.string.providers to "providers", R.string.account to "account", R.string.scripts_tab to "scripts",
                R.string.status_pages_tab to "status-pages", R.string.webserver_templates_tab to "templates",
                R.string.projects_tab to "projects"
            ).forEach { (label, name) ->
                tour.tapText(s(label))
                tour.waitGone(hasContentDescription(s(R.string.a11y_loading)), 20_000)
                tour.shot(name)
                tour.tap(hasContentDescription(s(R.string.back)))
            }
        }
        tour.tapText(s(R.string.settings_tab))
        tour.shot("settings")
        if (deep) {
            tour.scrollTo(hasText(s(R.string.settings_security)))
            tour.shot("settings-security")
            tour.scrollTo(hasText(s(R.string.settings_about)))
            tour.shot("settings-about")
        }
        // Dark theme pass on the densest screens.
        tour.scrollTo(hasText(s(R.string.settings_theme_dark)))
        tour.tapText(s(R.string.settings_theme_dark))
        tour.tap(hasText(s(R.string.nav_servers)) and hasClickAction())
        waitServers(tour)
        tour.shot("dark-servers")
        tour.tapText("scell-io")
        tour.waitFor(hasText(s(R.string.server_category_overview)))
        tour.shot("dark-server-hub")
        openSection(tour, R.string.server_section_monitoring_description, "dark-server-monitoring", s(R.string.metric_cpu))
        // Lock screen.
        tour.tap(hasContentDescription(s(R.string.lock_now)))
        tour.waitFor(hasText(s(R.string.unlock_title)))
        tour.shot("dark-unlock")
        File(out, "$device-index.txt").writeText(tour.shots.joinToString("\n"))
    }

    @Test @Config(qualifiers = "fr-rFR-w411dp-h891dp-port-xhdpi")
    fun phone() = fullTour("phone", deep = true)

    @Test @Config(qualifiers = "fr-rFR-w344dp-h882dp-port-xhdpi")
    fun foldFolded() = fullTour("fold-folded", deep = false)

    @Test @Config(qualifiers = "fr-rFR-w690dp-h830dp-port-xhdpi")
    fun foldUnfolded() = fullTour("fold-unfolded", deep = false)

    @Test @Config(qualifiers = "fr-rFR-w1280dp-h800dp-land-mdpi")
    fun tabletLandscape() = fullTour("tablet", deep = false)
}
