package com.qrcommunication.ploipanel.ssh

import com.qrcommunication.ploipanel.ProfilePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private class BookmarkPrefs : ProfilePrefs {
    val map = mutableMapOf<String, String>()
    override fun read(key: String): String? = map[key]
    override fun write(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

class SshBookmarkStoreTest {
    @Test fun `remember stores most recent first and deduplicates destination`() {
        val store = SshBookmarkStore(BookmarkPrefs(), "p1")
        store.remember("web", "10.0.0.1", 22, "ploi", null, 7)
        store.remember("db", "10.0.0.2", 22, "ploi", "k1", null)
        val again = store.remember("web renamed", "10.0.0.1", 22, "ploi", "k2", null)
        val list = store.list()
        assertEquals(2, list.size)
        assertEquals(again, list.first())
        assertEquals("web renamed", list.first().label)
        assertEquals(7L, list.first().serverId) // server link is kept on refresh
        assertEquals("k2", list.first().keyId)
    }

    @Test fun `bookmarks never contain secrets and are isolated per profile`() {
        val prefs = BookmarkPrefs()
        SshBookmarkStore(prefs, "p1").remember("web", "example.com", 2222, "deploy", null, null)
        assertTrue(SshBookmarkStore(prefs, "p2").list().isEmpty())
        val raw = prefs.map.getValue(SshBookmarkStore.key("p1"))
        assertTrue(!raw.contains("password", ignoreCase = true))
    }

    @Test fun `invalid destinations are rejected and corrupted storage is ignored`() {
        val prefs = BookmarkPrefs()
        val store = SshBookmarkStore(prefs, "p1")
        assertThrows(IllegalArgumentException::class.java) { store.remember("x", "bad host", 22, "ploi", null, null) }
        assertThrows(IllegalArgumentException::class.java) { store.remember("x", "h", 0, "ploi", null, null) }
        assertThrows(IllegalArgumentException::class.java) { store.remember("x", "h", 22, "root;rm", null, null) }
        prefs.map[SshBookmarkStore.key("p1")] = "{not json"
        assertTrue(store.list().isEmpty())
    }

    @Test fun `forgetting a key falls back to password and remove clears storage`() {
        val prefs = BookmarkPrefs()
        val store = SshBookmarkStore(prefs, "p1")
        val b = store.remember("", "h.example", 22, "ploi", "k1", null)
        assertEquals("ploi@h.example", b.label)
        store.forgetKey("k1")
        assertNull(store.list().single().keyId)
        store.remove(b.id)
        assertNull(prefs.map[SshBookmarkStore.key("p1")])
    }

    @Test fun `count is capped`() {
        val store = SshBookmarkStore(BookmarkPrefs(), "p1")
        repeat(SshBookmarkStore.MAX_BOOKMARKS + 5) { store.remember("h$it", "10.0.1.$it", 22, "ploi", null, null) }
        assertEquals(SshBookmarkStore.MAX_BOOKMARKS, store.list().size)
    }
}

class TerminalPaletteTest {
    @Test fun `xterm 256 colour cube and grayscale`() {
        assertEquals(0xFF000000.toInt(), TerminalPalette.indexed(16))
        assertEquals(0xFFFFFFFF.toInt(), TerminalPalette.indexed(231))
        assertEquals(0xFF080808.toInt(), TerminalPalette.indexed(232))
        assertEquals(0xFFEEEEEE.toInt(), TerminalPalette.indexed(255))
        assertEquals(0xFFFF5F00.toInt(), TerminalPalette.indexed(202))
    }

    @Test fun `bold brightens base colours and inverse swaps`() {
        val (boldFg, _) = TerminalPalette.resolve(CellStyle(fg = TermColor.Indexed(1), bold = true))
        assertEquals(TerminalPalette.indexed(9), boldFg)
        val (fg, bg) = TerminalPalette.resolve(CellStyle(inverse = true))
        assertEquals(TerminalPalette.DEFAULT_BACKGROUND, fg)
        assertEquals(TerminalPalette.DEFAULT_FOREGROUND, bg)
        assertEquals(0xFF123456.toInt(), TerminalPalette.resolve(CellStyle(fg = TermColor.Rgb(0x123456))).first)
    }
}
