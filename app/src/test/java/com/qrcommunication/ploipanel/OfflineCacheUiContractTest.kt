package com.qrcommunication.ploipanel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.TimeZone

/**
 * Source-level guardrails for the offline read-only mode. These assert the wiring, not a rendered
 * screen: no device or emulator runs here, so nothing below proves the banner is legible.
 */
class OfflineCacheUiContractTest {
    private fun source(path: String): String {
        var root: File? = File(System.getProperty("user.dir") ?: ".")
        while (root != null) {
            val file = File(root, "app/src/main/java/com/qrcommunication/ploipanel/$path.kt")
            if (file.isFile) return file.readText()
            root = root.parentFile
        }
        error("Missing source: $path")
    }

    private fun strings(qualifier: String): String {
        var root: File? = File(System.getProperty("user.dir") ?: ".")
        while (root != null) {
            val file = File(root, "app/src/main/res/$qualifier/strings.xml")
            if (file.isFile) return file.readText()
            root = root.parentFile
        }
        error("Missing strings: $qualifier")
    }

    @Test fun cacheOnlyAnswersAConnectivityFailure() {
        assertTrue(shouldServeCache(PloiOfflineException(IOException("no route"))))
        // A reachable API told us something; hiding it behind cached rows would mask the account.
        assertFalse(shouldServeCache(PloiHttpException(401, null)))
        assertFalse(shouldServeCache(PloiHttpException(403, null)))
        assertFalse(shouldServeCache(PloiHttpException(429, "30")))
        assertFalse(shouldServeCache(PloiHttpException(500, null)))
        assertFalse(shouldServeCache(PloiMalformedPayloadException(IllegalStateException("garbage"))))
        assertFalse(shouldServeCache(IllegalArgumentException("bad input")))
    }

    @Test fun serverListWritesFreshPagesAndFallsBackOnlyWhenAllowed() {
        val activity = source("MainActivity")
        assertTrue("A successful read must refresh the cache",
            activity.contains("offlineCache.saveServers(profileId, page, fresh)"))
        assertTrue("The fallback must be gated by the offline policy",
            activity.contains("shouldServeCache(failure)"))
        assertTrue("The cached page must carry its fetch instant",
            activity.contains("serversCachedAt = cached?.fetchedAt"))
        assertTrue("A cache write failure must not break a successful read",
            activity.contains("runCatching { offlineCache.saveServers(profileId, page, fresh) }"))
        assertTrue("The API error must remain visible next to cached rows",
            activity.contains("error = failure"))
        assertTrue("Cached reads use their own Keystore alias",
            activity.contains("KeystoreTokenCipher(OFFLINE_CACHE_ALIAS)"))
    }

    @Test fun cachedRowsDisableEveryActionThatNeedsTheApi() {
        val activity = source("MainActivity")
        assertTrue(activity.contains("val offline = cachedAt != null"))
        assertTrue("Creating a server must be blocked while offline (FAB hidden, empty-state action disabled)",
            activity.contains("if (!offline) ExtendedFloatingActionButton(") &&
                activity.contains("actionEnabled = !offline, onAction = onCreate"))
        val dashboard = source("ServerDashboard")
        assertTrue("The monitored overview needs a live call",
            dashboard.contains("IconButton(onClick = onMonitored, enabled = !offline)") &&
                activity.contains("offline = offline,"))
        assertTrue("Opening a cached server would need live detail calls",
            activity.contains("ServerItemCard(server, enabled = !offline,"))
        assertTrue("Reload must stay available so the user can leave offline mode",
            dashboard.contains("IconButton(onClick = onRefresh, enabled = !refreshing)") &&
                activity.contains("onRetry = onRefresh") && activity.contains("PullToRefreshBox("))
        assertTrue(activity.contains("OfflineCacheBanner(cachedAt)"))
    }

    @Test fun bothServerListCallSitesPassTheCacheState() {
        val activity = source("MainActivity")
        val callSites = Regex("ServerList\\(servers, loading, error, page, serversCachedAt")
            .findAll(activity).count()
        assertTrue("Compact and expanded layouts must both report cached state, found $callSites",
            callSites == 2)
        assertFalse("No call site may drop the cache argument",
            activity.contains("ServerList(servers, loading, error, page, onPage"))
    }

    @Test fun theBannerIsSpokenAsOneSentenceAndNotOnlyColoured() {
        val dashboard = source("ServerDashboard")
        assertTrue(dashboard.contains("clearAndSetSemantics { contentDescription = message }"))
        assertTrue(dashboard.contains("R.string.offline_cache_banner"))
        assertTrue(dashboard.contains("R.string.offline_cache_title"))
        assertTrue(dashboard.contains("R.string.offline_cache_body"))
    }

    @Test fun offlineStringsExistInBothLocalesAndCarryTheTimestamp() {
        listOf("values", "values-en").forEach { qualifier ->
            val xml = strings(qualifier)
            listOf("offline_cache_title", "offline_cache_body", "offline_cache_banner").forEach { name ->
                assertTrue("$name missing from $qualifier", xml.contains("name=\"$name\""))
            }
            val body = Regex("name=\"offline_cache_body\">(.*?)</string>").find(xml)!!.groupValues[1]
            val banner = Regex("name=\"offline_cache_banner\">(.*?)</string>").find(xml)!!.groupValues[1]
            assertTrue("$qualifier body must state when the data was read", body.contains("%1\$s"))
            assertTrue("$qualifier banner must state when the data was read", banner.contains("%1\$s"))
        }
    }

    @Test fun profileRemovalWipesTheCacheAlongsideOtherProfileMaterial() {
        val store = source("ProfileStore")
        assertTrue(store.contains("prefs.write(OfflineCache.key(id), null)"))
    }

    @Test fun timestampIsAbsoluteAndLocalised() {
        val utc = TimeZone.getTimeZone("UTC")
        val english = formatCacheTimestamp(0L, Locale.ENGLISH, utc)
        val french = formatCacheTimestamp(0L, Locale.FRENCH, utc)
        assertTrue("Expected a 1970 date, got $english", english.contains("70"))
        assertTrue("Expected a 1970 date, got $french", french.contains("70"))
        // An absolute instant, never a rounded "x hours ago" that could understate staleness.
        assertFalse(english.contains("ago"))
    }
}
