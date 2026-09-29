package com.qrcommunication.ploipanel

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Whether a failed read may be answered from the cache. Only a connectivity failure qualifies: an
 * HTTP answer means Ploi was reached and is telling us something (invalid token, missing scope,
 * rate limit), and covering that with stale rows would hide a real account problem.
 */
internal fun shouldServeCache(failure: Throwable): Boolean = failure is PloiOfflineException

/** Absolute local time of a cached read. Absolute, because a relative age invites rounding lies. */
internal fun formatCacheTimestamp(
    fetchedAt: Long,
    locale: Locale = Locale.getDefault(),
    zone: TimeZone = TimeZone.getDefault()
): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
    .apply { timeZone = zone }
    .format(Date(fetchedAt))

/**
 * A page that was read from the local cache instead of the network, with the instant it was
 * fetched. Callers must show that instant: cached rows are a past observation of the Ploi account,
 * never a live state, and the server may have changed since.
 */
internal data class CachedServerPage(val page: ServerPage, val fetchedAt: Long)

/** Dedicated Keystore alias: cached inventory is separate from tokens, templates and SSH keys. */
internal const val OFFLINE_CACHE_ALIAS = "ploi-panel.offline-cache"

/**
 * Read-only offline cache of Ploi collections, scoped per profile.
 *
 * Purpose is strictly to keep the app readable when the device is offline or the API is
 * unreachable. It is never authoritative: a cached page is only served to fill a *failed* read,
 * and every screen that shows one must label it as cached with its age.
 *
 * Contract:
 * - the whole container (server names, IPs, statuses) is encrypted through [cipher], the same
 *   Keystore-backed AES-GCM used for tokens, so account topology is not readable at rest;
 * - entries older than [MAX_AGE_MILLIS] are dropped rather than shown, and an entry stamped in the
 *   future beyond [MAX_CLOCK_SKEW_MILLIS] is dropped too (clock change or tampering);
 * - at most [MAX_PAGES] pages are kept per profile, evicting the oldest, so the cache cannot grow
 *   without bound on a large account;
 * - an unreadable or tampered container yields *no* cache rather than an exception or partial
 *   data, and the stored value is left alone so a transient key failure does not destroy it;
 * - removing a profile wipes its cache (see [ProfileStore.remove]).
 */
internal class OfflineCache(
    private val prefs: ProfilePrefs,
    private val cipher: TokenCipher,
    private val now: () -> Long = System::currentTimeMillis
) {
    companion object {
        const val VERSION = 1
        const val MAX_PAGES = 8
        /** Beyond a day, a cached inventory is more misleading than useful. */
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
        /** Tolerated forward skew before an entry is considered untrustworthy. */
        const val MAX_CLOCK_SKEW_MILLIS = 5L * 60 * 1000
        private const val PREFIX = "cache.servers."

        fun key(profileId: String): String {
            require(profileId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid profile ID" }
            return PREFIX + profileId
        }
    }

    /** Returns the cached page, or null when absent, expired, unreadable or tampered with. */
    fun servers(profileId: String, page: Int): CachedServerPage? {
        require(page >= 1) { "Page must be positive" }
        val entries = load(profileId) ?: return null
        val entry = entries[page] ?: return null
        return if (isFresh(entry.fetchedAt)) entry else null
    }

    fun saveServers(profileId: String, page: Int, value: ServerPage) {
        require(page >= 1) { "Page must be positive" }
        validate(value)
        val storageKey = key(profileId)
        val kept = (load(profileId) ?: emptyMap())
            .filterKeys { it != page }
            .filterValues { isFresh(it.fetchedAt) }
            .toMutableMap()
        kept[page] = CachedServerPage(value, now())
        val bounded = if (kept.size <= MAX_PAGES) kept else {
            // Drop the oldest fetch first; ties keep the lower page number for determinism.
            kept.entries.sortedWith(compareByDescending<Map.Entry<Int, CachedServerPage>> { it.value.fetchedAt }
                .thenBy { it.key })
                .take(MAX_PAGES).associate { it.key to it.value }
        }
        prefs.write(storageKey, encrypt(bounded))
    }

    fun clear(profileId: String) = prefs.write(key(profileId), null)

    private fun isFresh(fetchedAt: Long): Boolean {
        val current = now()
        if (fetchedAt - current > MAX_CLOCK_SKEW_MILLIS) return false
        return current - fetchedAt <= MAX_AGE_MILLIS
    }

    /** Refuses to cache a payload the API cannot have produced, so bad data never reaches a screen. */
    private fun validate(value: ServerPage) {
        require(value.currentPage >= 1 && value.lastPage >= 1 && value.currentPage <= value.lastPage) {
            "Invalid pagination metadata"
        }
        require(value.servers.size <= 50) { "Ploi returns at most 50 rows per page" }
        require(value.servers.map { it.id }.distinct().size == value.servers.size) { "Duplicate server ID" }
        require(value.servers.all { it.id > 0 }) { "Invalid server ID" }
    }

    private fun load(profileId: String): Map<Int, CachedServerPage>? {
        val stored = prefs.read(key(profileId)) ?: return null
        return try {
            val plain = cipher.decrypt(Base64.getDecoder().decode(stored))
            val raw = try { String(plain, StandardCharsets.UTF_8) } finally { plain.fill(0) }
            val root = JSONObject(raw)
            require(root.getInt("version") == VERSION) { "Unsupported cache version" }
            val pages = root.getJSONArray("pages")
            require(pages.length() <= MAX_PAGES) { "Oversized cache" }
            (0 until pages.length()).associate { index ->
                val entry = pages.getJSONObject(index)
                val number = entry.getInt("page")
                require(number >= 1) { "Invalid cached page number" }
                number to CachedServerPage(readPage(entry), entry.getLong("fetched"))
            }
        } catch (_: Exception) {
            // Wrong device key, corruption or tampering: behave as a cold cache. The stored value is
            // deliberately left in place; a transient Keystore failure must not delete user data.
            null
        }
    }

    private fun readPage(entry: JSONObject): ServerPage {
        val rows = entry.getJSONArray("servers")
        val servers = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            Server(row.getLong("id"), row.getString("name"), row.getString("status"), row.getString("ip"))
        }
        val page = ServerPage(servers, entry.getInt("current"), entry.getInt("last"))
        validate(page)
        return page
    }

    private fun encrypt(entries: Map<Int, CachedServerPage>): String {
        val pages = JSONArray()
        entries.toSortedMap().forEach { (number, cached) ->
            val rows = JSONArray()
            cached.page.servers.forEach { server ->
                rows.put(
                    JSONObject().put("id", server.id).put("name", server.name)
                        .put("status", server.status).put("ip", server.ipAddress)
                )
            }
            pages.put(
                JSONObject().put("page", number).put("fetched", cached.fetchedAt)
                    .put("current", cached.page.currentPage).put("last", cached.page.lastPage)
                    .put("servers", rows)
            )
        }
        val raw = JSONObject().put("version", VERSION).put("pages", pages).toString()
        return Base64.getEncoder().encodeToString(cipher.encrypt(raw.toByteArray(StandardCharsets.UTF_8)))
    }
}
