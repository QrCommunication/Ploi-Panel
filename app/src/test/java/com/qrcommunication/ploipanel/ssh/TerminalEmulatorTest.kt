package com.qrcommunication.ploipanel.ssh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class TerminalEmulatorTest {
    private fun term(cols: Int = 10, rows: Int = 4, replies: MutableList<String>? = null) =
        TerminalEmulator(cols, rows, maxScrollback = 50) { bytes -> replies?.add(String(bytes, StandardCharsets.US_ASCII)) }

    @Test fun `prints text and handles CR LF`() {
        val t = term()
        t.feed("hello\r\nworld")
        assertEquals("hello", t.lineText(0))
        assertEquals("world", t.lineText(1))
        assertEquals(1, t.cursorRow)
        assertEquals(5, t.cursorCol)
    }

    @Test fun `wraps at the right margin with deferred wrap`() {
        val t = term(cols = 5)
        t.feed("abcde")
        // xterm keeps the cursor on the last column until the next printable character.
        assertEquals(0, t.cursorRow)
        assertEquals(4, t.cursorCol)
        t.feed("f")
        assertEquals("abcde", t.lineText(0))
        assertEquals("f", t.lineText(1))
    }

    @Test fun `CR after a full line does not create an empty line`() {
        val t = term(cols = 5)
        t.feed("abcde\r\nx")
        assertEquals("abcde", t.lineText(0))
        assertEquals("x", t.lineText(1))
    }

    @Test fun `scrolling pushes lines to bounded scrollback`() {
        val t = TerminalEmulator(10, 2, maxScrollback = 3)
        t.feed("1\r\n2\r\n3\r\n4\r\n5\r\n6")
        assertEquals("5", t.lineText(0))
        assertEquals("6", t.lineText(1))
        assertEquals(3, t.scrollbackSize())
        assertEquals(listOf("2", "3", "4"), t.snapshot().scrollback.map { row -> row.joinToString("") { it.ch.toString() }.trim() })
    }

    @Test fun `cursor positioning and erase in line`() {
        val t = term()
        t.feed("0123456789")
        t.feed("\u001b[1;4H\u001b[K")
        assertEquals("012", t.lineText(0))
        t.feed("\u001b[2;1Hxyz\u001b[1;2H\u001b[1K")
        assertEquals("2", t.lineText(0).trim())
        assertEquals("xyz", t.lineText(1))
    }

    @Test fun `erase display clears screen`() {
        val t = term()
        t.feed("a\r\nb\r\nc\u001b[2J")
        (0 until 4).forEach { assertEquals("", t.lineText(it)) }
    }

    @Test fun `SGR colours and attributes`() {
        val t = term()
        t.feed("\u001b[1;31mR\u001b[0m\u001b[38;5;202mO\u001b[48;2;1;2;3mB\u001b[39;49;22mN")
        val row = t.snapshot().rows[0]
        assertTrue(row[0].style.bold)
        assertEquals(TermColor.Indexed(1), row[0].style.fg)
        assertEquals(CellStyle.DEFAULT, row[1].style.copy(fg = TermColor.Default))
        assertEquals(TermColor.Indexed(202), row[1].style.fg)
        assertEquals(TermColor.Rgb(0x010203), row[2].style.bg)
        assertEquals(CellStyle.DEFAULT, row[3].style)
    }

    @Test fun `bright colours map to indexes 8 to 15`() {
        val t = term()
        t.feed("\u001b[92;104mx")
        val style = t.snapshot().rows[0][0].style
        assertEquals(TermColor.Indexed(10), style.fg)
        assertEquals(TermColor.Indexed(12), style.bg)
    }

    @Test fun `alternate screen preserves primary content`() {
        val t = term()
        t.feed("shell$ ")
        t.feed("\u001b[?1049h\u001b[H\u001b[2Jvim buffer")
        assertTrue(t.usingAlternateScreen)
        assertEquals("vim buffer", t.lineText(0))
        t.feed("\u001b[?1049l")
        assertFalse(t.usingAlternateScreen)
        assertEquals("shell$", t.lineText(0))
        assertEquals(7, t.cursorCol)
    }

    @Test fun `alternate screen scrolling never pollutes scrollback`() {
        val t = TerminalEmulator(10, 2, maxScrollback = 10)
        t.feed("\u001b[?1049h1\r\n2\r\n3\r\n4")
        assertEquals(0, t.scrollbackSize())
    }

    @Test fun `scroll region keeps status line fixed`() {
        val t = term(rows = 4)
        t.feed("\u001b[4;1Hstatus\u001b[1;3r\u001b[1;1H")
        t.feed("a\r\nb\r\nc\r\nd")
        assertEquals("b", t.lineText(0))
        assertEquals("c", t.lineText(1))
        assertEquals("d", t.lineText(2))
        assertEquals("status", t.lineText(3))
    }

    @Test fun `insert and delete characters and lines`() {
        val t = term()
        t.feed("abcdef\u001b[1;2H\u001b[2P")
        assertEquals("adef", t.lineText(0))
        t.feed("\u001b[1;2H\u001b[2@")
        assertEquals("a  def", t.lineText(0))
        t.feed("\u001b[2;1Hline2\u001b[3;1Hline3\u001b[2;1H\u001b[M")
        assertEquals("line3", t.lineText(1))
        t.feed("\u001b[L")
        assertEquals("", t.lineText(1))
        assertEquals("line3", t.lineText(2))
    }

    @Test fun `tabs advance to multiples of eight`() {
        val t = term(cols = 20)
        t.feed("a\tb")
        assertEquals(9, t.cursorCol)
        assertEquals('b', t.snapshot().rows[0][8].ch)
    }

    @Test fun `backspace moves left without erasing`() {
        val t = term()
        t.feed("ab\bc")
        assertEquals("ac", t.lineText(0))
    }

    @Test fun `UTF-8 split across feeds is decoded`() {
        val t = term()
        val bytes = "é✓".toByteArray(StandardCharsets.UTF_8)
        t.feed(bytes, 0, 1)
        t.feed(bytes, 1, bytes.size - 1)
        assertEquals("é✓", t.lineText(0))
    }

    @Test fun `DEC line drawing charset`() {
        val t = term()
        t.feed("\u001b(0lqk\u001b(Bx")
        assertEquals("┌─┐x", t.lineText(0))
    }

    @Test fun `cursor position report and device attributes reply`() {
        val replies = mutableListOf<String>()
        val t = term(replies = replies)
        t.feed("\u001b[2;3H\u001b[6n\u001b[c\u001b[5n")
        assertEquals(listOf("\u001b[2;3R", "\u001b[?62;22c", "\u001b[0n"), replies)
    }

    @Test fun `private modes toggle cursor keys, paste and cursor visibility`() {
        val t = term()
        t.feed("\u001b[?1h\u001b[?2004h\u001b[?25l")
        assertTrue(t.applicationCursorKeys)
        assertTrue(t.bracketedPaste)
        assertFalse(t.cursorVisible)
        t.feed("\u001b[?1l\u001b[?2004l\u001b[?25h")
        assertFalse(t.applicationCursorKeys)
        assertFalse(t.bracketedPaste)
        assertTrue(t.cursorVisible)
    }

    @Test fun `OSC title is honoured and clipboard OSC 52 is ignored`() {
        val t = term()
        t.feed("\u001b]0;deploy@web-1: ~\u0007\u001b]52;c;c2VjcmV0\u001b\\ok")
        assertEquals("deploy@web-1: ~", t.title)
        assertEquals("ok", t.lineText(0))
    }

    @Test fun `unknown and DCS sequences never print garbage`() {
        val t = term()
        t.feed("\u001bP1\$r0m\u001b\\\u001b[?1000h\u001b[>4;1m\u001b[3 qok\u001b_apc\u001b\\")
        assertEquals("ok", t.lineText(0))
    }

    @Test fun `hostile huge parameters are clamped`() {
        val t = term()
        t.feed("\u001b[999999999;999999999H\u001b[99999999999999999999Sx")
        assertEquals(3, t.cursorRow)
        assertEquals(9, t.cursorCol)
        assertEquals("x", t.lineText(3).trim())
    }

    @Test fun `resize keeps content and clamps cursor`() {
        val t = term(cols = 10, rows = 4)
        t.feed("a\r\nb\r\nc\r\nd")
        t.resize(5, 2)
        assertEquals(5, t.columns)
        assertEquals(2, t.rows)
        assertEquals("c", t.lineText(0))
        assertEquals("d", t.lineText(1))
        assertEquals(1, t.cursorRow)
        t.resize(20, 6)
        assertEquals("c", t.lineText(0))
    }

    @Test fun `save and restore cursor`() {
        val t = term()
        t.feed("\u001b[2;3H\u001b7\u001b[4;1H\u001b8x")
        assertEquals("  x", t.lineText(1))
    }

    @Test fun `origin mode positions relative to region`() {
        val t = term(rows = 4)
        t.feed("\u001b[2;3r\u001b[?6h\u001b[1;1Hx")
        assertEquals("x", t.lineText(1))
    }

    @Test fun `erase keeps current background colour`() {
        val t = term()
        t.feed("\u001b[44m\u001b[2K")
        assertEquals(TermColor.Indexed(4), t.snapshot().rows[0][5].style.bg)
    }

    @Test fun `reverse index at top scrolls down`() {
        val t = term()
        t.feed("top\u001b[1;1H\u001bM")
        assertEquals("", t.lineText(0))
        assertEquals("top", t.lineText(1))
    }

    @Test fun `full reset clears everything`() {
        val t = term()
        t.feed("\u001b[?1h\u001b]0;t\u0007abc\u001bc")
        assertEquals("", t.lineText(0))
        assertFalse(t.applicationCursorKeys)
        assertEquals("", t.title)
    }

    @Test fun `C1 controls and DEL are not printed`() {
        val t = term()
        t.feed("a\u007fb\u0085c")
        assertEquals("abc", t.lineText(0))
    }

    @Test fun `insert mode shifts characters right`() {
        val t = term()
        t.feed("abc\u001b[1;1H\u001b[4hX\u001b[4l")
        assertEquals("Xabc", t.lineText(0))
    }

    @Test fun `repeat last character`() {
        val t = term()
        t.feed("-\u001b[4b")
        assertEquals("-----", t.lineText(0))
    }
}

class TerminalInputTest {
    private fun s(bytes: ByteArray) = String(bytes, StandardCharsets.UTF_8)

    @Test fun `cursor keys honour application mode`() {
        assertEquals("\u001b[A", s(TerminalInput.key(TerminalKey.UP, applicationCursor = false)))
        assertEquals("\u001bOA", s(TerminalInput.key(TerminalKey.UP, applicationCursor = true)))
        assertEquals("\u001b\u001b[D", s(TerminalInput.key(TerminalKey.LEFT, applicationCursor = false, alt = true)))
    }

    @Test fun `special keys`() {
        assertEquals("\u001b[3~", s(TerminalInput.key(TerminalKey.DELETE, false)))
        assertEquals("\u001b[5~", s(TerminalInput.key(TerminalKey.PAGE_UP, false)))
        assertEquals("\u001bOP", s(TerminalInput.key(TerminalKey.F1, false)))
        assertEquals("\u001b[24~", s(TerminalInput.key(TerminalKey.F12, false)))
        assertEquals("\u007f", s(TerminalInput.key(TerminalKey.BACKSPACE, false)))
        assertEquals("\r", s(TerminalInput.key(TerminalKey.ENTER, false)))
        assertEquals("\u001b", s(TerminalInput.key(TerminalKey.ESCAPE, false, alt = true)))
    }

    @Test fun `ctrl and alt text encoding`() {
        assertArrayEquals(byteArrayOf(3), TerminalInput.text("c", ctrl = true))
        assertArrayEquals(byteArrayOf(3), TerminalInput.text("C", ctrl = true))
        assertArrayEquals(byteArrayOf(27), TerminalInput.text("[", ctrl = true))
        assertArrayEquals(byteArrayOf(0x1b, 'x'.code.toByte()), TerminalInput.text("x", alt = true))
        assertEquals("a\rb", s(TerminalInput.text("a\nb")))
        assertEquals("é", s(TerminalInput.text("é")))
    }

    @Test fun `bracketed paste wraps and strips injected end marker`() {
        assertEquals("ls\rpwd", s(TerminalInput.paste("ls\npwd", bracketed = false)))
        assertEquals(
            "\u001b[200~echo x; rm\u001b[201~",
            s(TerminalInput.paste("echo x\u001b[201~; rm", bracketed = true))
        )
    }
}
