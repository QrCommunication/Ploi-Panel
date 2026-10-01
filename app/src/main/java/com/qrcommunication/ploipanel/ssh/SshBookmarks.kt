package com.qrcommunication.ploipanel.ssh

import com.qrcommunication.ploipanel.ProfilePrefs
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A saved SSH destination. Contains no secret: the password is never stored, keys stay in the vault. */
internal data class SshBookmark(
    val id: String,
    val label: String,
    val host: String,
    val port: Int,
    val username: String,
    /** Vault key used for public-key auth, or null for password auth. */
    val keyId: String?,
    /** Ploi server this destination belongs to, when opened from a server screen. */
    val serverId: Long? = null,
)

/** Per-profile list of saved SSH destinations, most recently used first. */
internal class SshBookmarkStore(private val prefs: ProfilePrefs, private val profileId: String) {
    companion object {
        const val MAX_BOOKMARKS = 50
        const val MAX_LABEL_LENGTH = 60
        private const val PREFIX = "ssh.bookmarks."
        private val PROFILE_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")

        fun key(profileId: String): String {
            require(profileId.matches(PROFILE_ID_PATTERN)) { "Invalid profile ID" }
            return PREFIX + profileId
        }
    }

    fun list(): List<SshBookmark> {
        val raw = prefs.read(key(profileId)) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until minOf(array.length(), MAX_BOOKMARKS)).mapNotNull { index ->
                val row = array.getJSONObject(index)
                runCatching {
                    validated(
                        SshBookmark(
                            id = row.getString("id"),
                            label = row.getString("label"),
                            host = row.getString("host"),
                            port = row.getInt("port"),
                            username = row.getString("username"),
                            keyId = row.optString("keyId").takeIf { it.isNotEmpty() },
                            serverId = if (row.has("serverId")) row.getLong("serverId") else null,
                        )
                    )
                }.getOrNull()
            }
        } catch (corrupted: Exception) {
            // Bookmarks are a convenience: a corrupted list is dropped rather than blocking SSH.
            emptyList()
        }
    }

    /**
     * Saves or refreshes the destination (same host/port/user replaces the old entry) and moves it
     * to the top. Returns the stored bookmark.
     */
    fun remember(label: String, host: String, port: Int, username: String, keyId: String?, serverId: Long?): SshBookmark {
        val existing = list()
        val cleanHost = SshHostTrustStore.validateHost(host)
        val previous = existing.firstOrNull { it.host == cleanHost && it.port == port && it.username == username }
        val bookmark = validated(
            SshBookmark(previous?.id ?: UUID.randomUUID().toString(), label.trim().ifEmpty { "$username@$cleanHost" },
                cleanHost, port, username, keyId, serverId ?: previous?.serverId)
        )
        persist((listOf(bookmark) + existing.filterNot { it.id == bookmark.id }).take(MAX_BOOKMARKS))
        return bookmark
    }

    fun remove(id: String) = persist(list().filterNot { it.id == id })

    /** Drops the key reference of bookmarks pointing at a deleted vault key. */
    fun forgetKey(keyId: String) = persist(list().map { if (it.keyId == keyId) it.copy(keyId = null) else it })

    fun clear() = prefs.write(key(profileId), null)

    private fun validated(bookmark: SshBookmark): SshBookmark {
        SshHostTrustStore.validateHost(bookmark.host)
        SshHostTrustStore.validatePort(bookmark.port)
        require(SshConnectRequest.isValidUsername(bookmark.username)) { "Invalid username" }
        require(bookmark.label.isNotBlank() && bookmark.label.length <= MAX_LABEL_LENGTH && bookmark.label.none(Char::isISOControl)) {
            "Invalid label"
        }
        return bookmark
    }

    private fun persist(bookmarks: List<SshBookmark>) {
        if (bookmarks.isEmpty()) {
            prefs.write(key(profileId), null)
            return
        }
        val array = JSONArray()
        bookmarks.forEach { b ->
            array.put(
                JSONObject().put("id", b.id).put("label", b.label).put("host", b.host).put("port", b.port)
                    .put("username", b.username).put("keyId", b.keyId ?: "")
                    .apply { b.serverId?.let { put("serverId", it) } }
            )
        }
        prefs.write(key(profileId), array.toString())
    }
}

/** xterm colour palette, as ARGB ints, independent of Compose so it is unit-testable. */
internal object TerminalPalette {
    const val DEFAULT_FOREGROUND = 0xFFD8DEE9.toInt()
    const val DEFAULT_BACKGROUND = 0xFF15181E.toInt()

    private val BASE16 = intArrayOf(
        0xFF15181E.toInt(), 0xFFE06C75.toInt(), 0xFF98C379.toInt(), 0xFFE5C07B.toInt(),
        0xFF61AFEF.toInt(), 0xFFC678DD.toInt(), 0xFF56B6C2.toInt(), 0xFFD8DEE9.toInt(),
        0xFF5C6370.toInt(), 0xFFFF7A85.toInt(), 0xFFB5E890.toInt(), 0xFFFFD68A.toInt(),
        0xFF7FC4FF.toInt(), 0xFFDE9CF2.toInt(), 0xFF7FD8E3.toInt(), 0xFFFFFFFF.toInt(),
    )

    fun indexed(index: Int): Int {
        val i = index.coerceIn(0, 255)
        if (i < 16) return BASE16[i]
        if (i < 232) {
            val n = i - 16
            val levels = intArrayOf(0, 95, 135, 175, 215, 255)
            val r = levels[n / 36]
            val g = levels[(n / 6) % 6]
            val b = levels[n % 6]
            return argb(r, g, b)
        }
        val gray = 8 + (i - 232) * 10
        return argb(gray, gray, gray)
    }

    /** Resolves a cell to (foreground, background), applying bold-bright and inverse like xterm. */
    fun resolve(style: CellStyle): Pair<Int, Int> {
        var fg = when (val c = style.fg) {
            TermColor.Default -> DEFAULT_FOREGROUND
            is TermColor.Indexed -> indexed(if (style.bold && c.index < 8) c.index + 8 else c.index)
            is TermColor.Rgb -> 0xFF000000.toInt() or c.rgb
        }
        var bg = when (val c = style.bg) {
            TermColor.Default -> DEFAULT_BACKGROUND
            is TermColor.Indexed -> indexed(c.index)
            is TermColor.Rgb -> 0xFF000000.toInt() or c.rgb
        }
        if (style.dim) fg = blend(fg, bg)
        if (style.inverse) {
            val swap = fg
            fg = bg
            bg = swap
        }
        return fg to bg
    }

    private fun blend(a: Int, b: Int): Int = argb(
        (((a shr 16) and 0xFF) + ((b shr 16) and 0xFF)) / 2,
        (((a shr 8) and 0xFF) + ((b shr 8) and 0xFF)) / 2,
        ((a and 0xFF) + (b and 0xFF)) / 2,
    )

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
