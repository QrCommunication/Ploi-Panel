package com.qrcommunication.ploipanel.ssh

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Colour of a cell: terminal default, one of the 256 indexed colours, or a 24-bit RGB value. */
internal sealed interface TermColor {
    data object Default : TermColor
    data class Indexed(val index: Int) : TermColor
    data class Rgb(val rgb: Int) : TermColor
}

/** Graphic rendition of a cell. Immutable so rows can be shared with the UI without copying. */
internal data class CellStyle(
    val fg: TermColor = TermColor.Default,
    val bg: TermColor = TermColor.Default,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
    val dim: Boolean = false,
) {
    companion object {
        val DEFAULT = CellStyle()
    }
}

internal data class TermCell(val ch: Char = ' ', val style: CellStyle = CellStyle.DEFAULT) {
    companion object {
        val BLANK = TermCell()
    }
}

/** Immutable view of the visible screen handed to the UI after each batch of output. */
internal data class TerminalSnapshot(
    val rows: List<List<TermCell>>,
    val scrollback: List<List<TermCell>>,
    val cursorRow: Int,
    val cursorCol: Int,
    val cursorVisible: Boolean,
    val columns: Int,
    val title: String,
)

/**
 * Minimal xterm-compatible terminal emulator, pure JVM so it is fully unit-testable.
 *
 * Scope is deliberately what interactive shells, editors (vi/nano), pagers (less), `top`/`htop`
 * and Laravel/Artisan output actually emit: UTF-8, C0 controls, cursor movement, erase/insert/
 * delete, scroll regions, SGR with 16/256/true colours, alternate screen, application cursor keys,
 * bracketed paste, DEC line drawing, DSR/DA replies, and OSC title. Unknown sequences are consumed
 * and ignored, never printed, so a server can never inject garbage into the parser state.
 *
 * [reply] carries answers the terminal must send back to the host (cursor position reports,
 * device attributes). It is invoked synchronously from [feed]; the caller forwards the bytes.
 *
 * Not thread-safe: feed and read from one thread, or guard externally.
 */
internal class TerminalEmulator(
    columns: Int = 80,
    rows: Int = 24,
    private val maxScrollback: Int = DEFAULT_SCROLLBACK,
    private val reply: (ByteArray) -> Unit = {},
) {
    companion object {
        const val DEFAULT_SCROLLBACK = 2_000
        const val MIN_SIZE = 2
        const val MAX_COLUMNS = 500
        const val MAX_ROWS = 300
        private const val MAX_PARAMS = 32
        private const val MAX_PARAM_VALUE = 9_999
        private const val MAX_OSC_LENGTH = 4_096
        private const val TAB_WIDTH = 8

        /** DEC Special Graphics set, used by `ESC ( 0` for box drawing (mc, dialog, tmux borders). */
        private val DEC_GRAPHICS: Map<Char, Char> = mapOf(
            '`' to '◆', 'a' to '▒', 'f' to '°', 'g' to '±', 'j' to '┘', 'k' to '┐', 'l' to '┌',
            'm' to '└', 'n' to '┼', 'o' to '⎺', 'p' to '⎻', 'q' to '─', 'r' to '⎼', 's' to '⎽',
            't' to '├', 'u' to '┤', 'v' to '┴', 'w' to '┬', 'x' to '│', 'y' to '≤', 'z' to '≥',
            '{' to 'π', '|' to '≠', '}' to '£', '~' to '·',
        )
    }

    var columns: Int = columns.coerceIn(MIN_SIZE, MAX_COLUMNS)
        private set
    var rows: Int = rows.coerceIn(MIN_SIZE, MAX_ROWS)
        private set

    private var primary = blankScreen(this.rows, this.columns)
    private var alternate = blankScreen(this.rows, this.columns)
    private var screen = primary
    private val scrollback = ArrayDeque<List<TermCell>>()

    var cursorRow = 0
        private set
    var cursorCol = 0
        private set

    /** xterm "pending wrap": the cursor sits past the last column until the next printable char. */
    private var wrapPending = false
    private var style = CellStyle.DEFAULT
    private var scrollTop = 0
    private var scrollBottom = this.rows - 1
    private var tabStops = defaultTabStops(this.columns)
    private var savedCursor: SavedCursor? = null
    private var altSavedCursor: SavedCursor? = null
    private var g0Graphics = false
    private var g1Graphics = false
    private var useG1 = false

    var applicationCursorKeys = false
        private set
    var bracketedPaste = false
        private set
    var cursorVisible = true
        private set
    var autoWrap = true
        private set
    var originMode = false
        private set
    var insertMode = false
        private set
    var usingAlternateScreen = false
        private set
    var title = ""
        private set

    private data class SavedCursor(
        val row: Int, val col: Int, val style: CellStyle, val g0: Boolean, val g1: Boolean,
        val useG1: Boolean, val origin: Boolean, val wrapPending: Boolean,
    )

    // ----- parser state -----
    private enum class State { GROUND, ESCAPE, ESCAPE_INTERMEDIATE, CSI, OSC, OSC_ESCAPE, STRING, STRING_ESCAPE }

    private var state = State.GROUND
    private val params = IntArray(MAX_PARAMS)
    private var paramCount = 0
    private var paramStarted = false
    private var privateMarker: Char? = null
    private var intermediate: Char? = null
    private val osc = StringBuilder()
    private val decoder: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pendingBytes = ByteArray(0)

    /** Feeds raw bytes received from the host. Incomplete UTF-8 sequences are kept for the next call. */
    fun feed(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size) { "Invalid range" }
        val input = if (pendingBytes.isEmpty()) {
            ByteBuffer.wrap(bytes, offset, length)
        } else {
            ByteBuffer.wrap(pendingBytes + bytes.copyOfRange(offset, offset + length))
        }
        val out = CharBuffer.allocate(input.remaining() + 1)
        decoder.decode(input, out, false)
        pendingBytes = if (input.hasRemaining()) ByteArray(input.remaining()).also(input::get) else ByteArray(0)
        out.flip()
        while (out.hasRemaining()) process(out.get())
    }

    fun feed(text: String) = feed(text.toByteArray(StandardCharsets.UTF_8))

    /** Resizes both screens, keeping the top-left content and clamping the cursor. */
    fun resize(newColumns: Int, newRows: Int) {
        val cols = newColumns.coerceIn(MIN_SIZE, MAX_COLUMNS)
        val lines = newRows.coerceIn(MIN_SIZE, MAX_ROWS)
        if (cols == columns && lines == rows) return
        // Keep the cursor line visible on the primary screen: push surplus top lines to history.
        if (!usingAlternateScreen && cursorRow >= lines) {
            val overflow = cursorRow - lines + 1
            repeat(overflow) { pushScrollback(primary[it].toList()) }
            primary = primary.drop(overflow).toMutableList()
            cursorRow -= overflow
        }
        primary = resized(primary, cols, lines)
        alternate = resized(alternate, cols, lines)
        screen = if (usingAlternateScreen) alternate else primary
        columns = cols
        rows = lines
        scrollTop = 0
        scrollBottom = rows - 1
        tabStops = defaultTabStops(columns)
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, columns - 1)
        wrapPending = false
    }

    fun snapshot(): TerminalSnapshot = TerminalSnapshot(
        rows = screen.map { it.toList() },
        scrollback = scrollback.toList(),
        cursorRow = cursorRow,
        cursorCol = cursorCol,
        cursorVisible = cursorVisible,
        columns = columns,
        title = title,
    )

    /** Plain text of a visible line, trailing blanks trimmed. Test and accessibility helper. */
    fun lineText(row: Int): String = screen[row].joinToString("") { it.ch.toString() }.trimEnd()

    fun scrollbackSize(): Int = scrollback.size

    // ----- dispatch -----

    private fun process(c: Char) {
        when (state) {
            State.GROUND -> ground(c)
            State.ESCAPE -> escape(c)
            State.ESCAPE_INTERMEDIATE -> escapeIntermediate(c)
            State.CSI -> csi(c)
            State.OSC -> oscChar(c)
            State.OSC_ESCAPE -> if (c == '\\') { finishOsc(); state = State.GROUND } else { state = State.OSC; oscChar(c) }
            State.STRING -> if (c == '\u001b') state = State.STRING_ESCAPE else if (c == '\u0007') state = State.GROUND
            State.STRING_ESCAPE -> state = if (c == '\\') State.GROUND else State.STRING
        }
    }

    private fun ground(c: Char) {
        when (c) {
            '\u001b' -> enterEscape()
            '\u0007' -> Unit // bell: no audible signal, and never a visible glyph
            '\b' -> { wrapPending = false; if (cursorCol > 0) cursorCol-- }
            '\t' -> tab()
            '\n', '\u000b', '\u000c' -> lineFeed()
            '\r' -> { cursorCol = 0; wrapPending = false }
            '\u000e' -> useG1 = true
            '\u000f' -> useG1 = false
            '\u009b' -> { enterEscape(); state = State.CSI }
            '\u009d' -> { osc.setLength(0); state = State.OSC }
            else -> if (c >= ' ' && c != '\u007f' && (c < '\u0080' || c > '\u009f')) print(c)
        }
    }

    private fun enterEscape() {
        state = State.ESCAPE
        paramCount = 0
        paramStarted = false
        params.fill(0)
        privateMarker = null
        intermediate = null
    }

    private fun escape(c: Char) {
        state = State.GROUND
        when (c) {
            '[' -> state = State.CSI
            ']' -> { osc.setLength(0); state = State.OSC }
            'P', 'X', '^', '_' -> state = State.STRING // DCS/SOS/PM/APC: consumed, never rendered
            '(', ')', '*', '+', '#', '%' -> { intermediate = c; state = State.ESCAPE_INTERMEDIATE }
            '7' -> saveCursor()
            '8' -> restoreCursor()
            'D' -> lineFeed()
            'E' -> { cursorCol = 0; lineFeed() }
            'M' -> reverseIndex()
            'H' -> tabStops[cursorCol] = true
            'c' -> fullReset()
            '=', '>' -> Unit // keypad modes: the soft keyboard has no keypad to switch
            '\u001b' -> enterEscape()
            else -> Unit
        }
    }

    private fun escapeIntermediate(c: Char) {
        state = State.GROUND
        when (intermediate) {
            '(' -> g0Graphics = c == '0'
            ')' -> g1Graphics = c == '0'
            '#' -> if (c == '8') fillWithE()
            else -> Unit
        }
    }

    private fun csi(c: Char) {
        when {
            c in '0'..'9' -> {
                if (paramCount == 0) paramCount = 1
                val index = paramCount - 1
                if (index < MAX_PARAMS) params[index] = (params[index] * 10 + (c - '0')).coerceAtMost(MAX_PARAM_VALUE)
                paramStarted = true
            }
            c == ';' || c == ':' -> {
                if (paramCount == 0) paramCount = 1
                if (paramCount < MAX_PARAMS) paramCount++
            }
            c in "?>=<" && paramCount == 0 && !paramStarted -> privateMarker = c
            c in ' '..'/' -> intermediate = c
            c in '@'..'~' -> { state = State.GROUND; dispatchCsi(c) }
            c == '\u001b' -> enterEscape()
            c < ' ' -> ground(c) // C0 controls execute even inside a sequence
            else -> state = State.GROUND
        }
    }

    private fun param(index: Int, default: Int): Int {
        if (index >= paramCount) return default
        val value = params[index]
        return if (value == 0) default else value
    }

    private fun rawParam(index: Int): Int = if (index < paramCount) params[index] else 0

    private fun dispatchCsi(c: Char) {
        val marker = privateMarker
        if (marker == '?') {
            when (c) {
                'h' -> for (i in 0 until maxOf(paramCount, 1)) setPrivateMode(rawParam(i), true)
                'l' -> for (i in 0 until maxOf(paramCount, 1)) setPrivateMode(rawParam(i), false)
            }
            return
        }
        if (marker == '>' || marker == '=' || marker == '<') {
            if (c == 'c' && marker == '>') reply("\u001b[>0;10;1c")
            return
        }
        if (intermediate != null) {
            // DECSCUSR (cursor shape) and similar: accepted, no effect on this renderer.
            return
        }
        when (c) {
            'A' -> moveCursor(cursorRow - param(0, 1), cursorCol, clampToRegion = true)
            'B', 'e' -> moveCursor(cursorRow + param(0, 1), cursorCol, clampToRegion = true)
            'C', 'a' -> moveCursor(cursorRow, cursorCol + param(0, 1))
            'D' -> moveCursor(cursorRow, cursorCol - param(0, 1))
            'E' -> moveCursor(cursorRow + param(0, 1), 0, clampToRegion = true)
            'F' -> moveCursor(cursorRow - param(0, 1), 0, clampToRegion = true)
            'G', '`' -> moveCursor(cursorRow, param(0, 1) - 1)
            'd' -> moveCursor(originRow(param(0, 1) - 1), cursorCol)
            'H', 'f' -> moveCursor(originRow(param(0, 1) - 1), param(1, 1) - 1)
            'J' -> eraseDisplay(rawParam(0))
            'K' -> eraseLine(rawParam(0))
            'L' -> insertLines(param(0, 1))
            'M' -> deleteLines(param(0, 1))
            'P' -> deleteChars(param(0, 1))
            '@' -> insertChars(param(0, 1))
            'X' -> eraseChars(param(0, 1))
            'S' -> repeat(param(0, 1).coerceAtMost(rows)) { scrollUp(scrollTop, scrollBottom) }
            'T' -> repeat(param(0, 1).coerceAtMost(rows)) { scrollDown(scrollTop, scrollBottom) }
            'I' -> repeat(param(0, 1).coerceAtMost(columns)) { tab() }
            'Z' -> repeat(param(0, 1).coerceAtMost(columns)) { backTab() }
            'b' -> repeatLast(param(0, 1))
            'g' -> when (rawParam(0)) { 0 -> tabStops[cursorCol] = false; 3 -> tabStops.fill(false) }
            'm' -> selectGraphicRendition()
            'r' -> setScrollRegion(param(0, 1) - 1, param(1, rows) - 1)
            's' -> saveCursor()
            'u' -> restoreCursor()
            'h' -> if (rawParam(0) == 4) insertMode = true
            'l' -> if (rawParam(0) == 4) insertMode = false
            'n' -> when (rawParam(0)) {
                5 -> reply("\u001b[0n")
                6 -> {
                    val reportedRow = if (originMode) cursorRow - scrollTop + 1 else cursorRow + 1
                    reply("\u001b[$reportedRow;${cursorCol + 1}R")
                }
            }
            'c' -> if (rawParam(0) == 0) reply("\u001b[?62;22c")
            't' -> Unit // window manipulation: never let a host resize or move the app
            else -> Unit
        }
    }

    private var lastPrinted: Char? = null

    private fun repeatLast(count: Int) {
        val ch = lastPrinted ?: return
        repeat(count.coerceAtMost(columns * rows)) { print(ch) }
    }

    private fun reply(text: String) = reply(text.toByteArray(StandardCharsets.US_ASCII))

    private fun setPrivateMode(mode: Int, enabled: Boolean) {
        when (mode) {
            1 -> applicationCursorKeys = enabled
            6 -> { originMode = enabled; moveCursor(if (enabled) scrollTop else 0, 0) }
            7 -> autoWrap = enabled
            25 -> cursorVisible = enabled
            47, 1047 -> switchScreen(enabled, saveCursor = false, clear = mode == 1047 && enabled)
            1048 -> if (enabled) saveCursor() else restoreCursor()
            1049 -> switchScreen(enabled, saveCursor = true, clear = true)
            2004 -> bracketedPaste = enabled
            else -> Unit // mouse reporting, focus events...: not supported, silently refused
        }
    }

    private fun switchScreen(toAlternate: Boolean, saveCursor: Boolean, clear: Boolean) {
        if (toAlternate == usingAlternateScreen) return
        if (toAlternate) {
            if (saveCursor) altSavedCursor = currentCursor()
            usingAlternateScreen = true
            screen = alternate
            if (clear) for (r in 0 until rows) clearRow(r)
        } else {
            usingAlternateScreen = false
            screen = primary
            if (saveCursor) altSavedCursor?.let(::applyCursor)
        }
        scrollTop = 0
        scrollBottom = rows - 1
        wrapPending = false
    }

    // ----- SGR -----

    private fun selectGraphicRendition() {
        if (paramCount == 0) {
            style = CellStyle.DEFAULT
            return
        }
        var i = 0
        while (i < paramCount) {
            val p = params[i]
            when (p) {
                0 -> style = CellStyle.DEFAULT
                1 -> style = style.copy(bold = true)
                2 -> style = style.copy(dim = true)
                3 -> style = style.copy(italic = true)
                4 -> style = style.copy(underline = true)
                7 -> style = style.copy(inverse = true)
                21, 22 -> style = style.copy(bold = false, dim = false)
                23 -> style = style.copy(italic = false)
                24 -> style = style.copy(underline = false)
                27 -> style = style.copy(inverse = false)
                in 30..37 -> style = style.copy(fg = TermColor.Indexed(p - 30))
                39 -> style = style.copy(fg = TermColor.Default)
                in 40..47 -> style = style.copy(bg = TermColor.Indexed(p - 40))
                49 -> style = style.copy(bg = TermColor.Default)
                in 90..97 -> style = style.copy(fg = TermColor.Indexed(p - 90 + 8))
                in 100..107 -> style = style.copy(bg = TermColor.Indexed(p - 100 + 8))
                38, 48 -> {
                    val (color, consumed) = extendedColor(i + 1)
                    if (color != null) {
                        style = if (p == 38) style.copy(fg = color) else style.copy(bg = color)
                    }
                    i += consumed
                }
                else -> Unit
            }
            i++
        }
    }

    /** Parses `5;n` or `2;r;g;b` after 38/48; returns the colour and the parameters consumed. */
    private fun extendedColor(start: Int): Pair<TermColor?, Int> {
        if (start >= paramCount) return null to 0
        return when (params[start]) {
            5 -> if (start + 1 < paramCount) {
                TermColor.Indexed(params[start + 1].coerceIn(0, 255)) to 2
            } else null to 1
            2 -> if (start + 3 < paramCount) {
                val r = params[start + 1].coerceIn(0, 255)
                val g = params[start + 2].coerceIn(0, 255)
                val b = params[start + 3].coerceIn(0, 255)
                TermColor.Rgb((r shl 16) or (g shl 8) or b) to 4
            } else null to (paramCount - start)
            else -> null to 1
        }
    }

    // ----- OSC -----

    private fun oscChar(c: Char) {
        when (c) {
            '\u0007' -> { finishOsc(); state = State.GROUND }
            '\u001b' -> state = State.OSC_ESCAPE
            '\u009c' -> { finishOsc(); state = State.GROUND }
            else -> if (osc.length < MAX_OSC_LENGTH) osc.append(c)
        }
    }

    private fun finishOsc() {
        val text = osc.toString()
        osc.setLength(0)
        val separator = text.indexOf(';')
        if (separator <= 0) return
        when (text.substring(0, separator)) {
            // Only the window title is honoured; clipboard (52), colours and hyperlinks are ignored
            // so a remote host can never read or write the phone clipboard.
            "0", "2" -> title = text.substring(separator + 1).filterNot(Char::isISOControl).take(120)
        }
    }

    // ----- printing and movement -----

    private fun print(raw: Char) {
        val graphics = if (useG1) g1Graphics else g0Graphics
        val ch = if (graphics) DEC_GRAPHICS[raw] ?: raw else raw
        if (wrapPending) {
            if (autoWrap) {
                cursorCol = 0
                lineFeed()
            }
            wrapPending = false
        }
        val row = screen[cursorRow]
        if (insertMode) {
            for (col in columns - 1 downTo cursorCol + 1) row[col] = row[col - 1]
        }
        row[cursorCol] = TermCell(ch, style)
        lastPrinted = ch
        if (cursorCol == columns - 1) wrapPending = true else cursorCol++
    }

    private fun tab() {
        wrapPending = false
        var col = cursorCol + 1
        while (col < columns - 1 && !tabStops[col]) col++
        cursorCol = col.coerceAtMost(columns - 1)
    }

    private fun backTab() {
        wrapPending = false
        var col = cursorCol - 1
        while (col > 0 && !tabStops[col]) col--
        cursorCol = col.coerceAtLeast(0)
    }

    private fun lineFeed() {
        wrapPending = false
        if (cursorRow == scrollBottom) scrollUp(scrollTop, scrollBottom)
        else if (cursorRow < rows - 1) cursorRow++
    }

    private fun reverseIndex() {
        wrapPending = false
        if (cursorRow == scrollTop) scrollDown(scrollTop, scrollBottom)
        else if (cursorRow > 0) cursorRow--
    }

    private fun originRow(row: Int): Int = if (originMode) row + scrollTop else row

    private fun moveCursor(row: Int, col: Int, clampToRegion: Boolean = false) {
        wrapPending = false
        val (top, bottom) = when {
            originMode -> scrollTop to scrollBottom
            clampToRegion && cursorRow in scrollTop..scrollBottom -> scrollTop to scrollBottom
            else -> 0 to rows - 1
        }
        cursorRow = row.coerceIn(top, bottom)
        cursorCol = col.coerceIn(0, columns - 1)
    }

    private fun setScrollRegion(top: Int, bottom: Int) {
        val t = top.coerceIn(0, rows - 1)
        val b = bottom.coerceIn(0, rows - 1)
        if (t >= b) return
        scrollTop = t
        scrollBottom = b
        moveCursor(if (originMode) scrollTop else 0, 0)
    }

    private fun scrollUp(top: Int, bottom: Int) {
        val removed = screen.removeAt(top)
        // Only lines leaving the whole primary screen become history; alt screen and inner
        // regions (vim splits, status bars) must not pollute the scrollback.
        if (screen === primary && top == 0) pushScrollback(removed.toList())
        screen.add(bottom, blankRow())
    }

    private fun scrollDown(top: Int, bottom: Int) {
        screen.removeAt(bottom)
        screen.add(top, blankRow())
    }

    private fun pushScrollback(line: List<TermCell>) {
        if (maxScrollback <= 0) return
        scrollback.addLast(line)
        while (scrollback.size > maxScrollback) scrollback.removeFirst()
    }

    private fun insertLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        repeat(count.coerceAtMost(scrollBottom - cursorRow + 1)) { scrollDown(cursorRow, scrollBottom) }
        cursorCol = 0
        wrapPending = false
    }

    private fun deleteLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        repeat(count.coerceAtMost(scrollBottom - cursorRow + 1)) {
            screen.removeAt(cursorRow)
            screen.add(scrollBottom, blankRow())
        }
        cursorCol = 0
        wrapPending = false
    }

    private fun deleteChars(count: Int) {
        val row = screen[cursorRow]
        val n = count.coerceAtMost(columns - cursorCol)
        for (col in cursorCol until columns) {
            row[col] = if (col + n < columns) row[col + n] else blankCell()
        }
        wrapPending = false
    }

    private fun insertChars(count: Int) {
        val row = screen[cursorRow]
        val n = count.coerceAtMost(columns - cursorCol)
        for (col in columns - 1 downTo cursorCol) {
            row[col] = if (col - n >= cursorCol) row[col - n] else blankCell()
        }
        wrapPending = false
    }

    private fun eraseChars(count: Int) {
        val row = screen[cursorRow]
        for (col in cursorCol until (cursorCol + count).coerceAtMost(columns)) row[col] = blankCell()
        wrapPending = false
    }

    private fun eraseLine(mode: Int) {
        val row = screen[cursorRow]
        val range = when (mode) {
            0 -> cursorCol until columns
            1 -> 0..cursorCol
            2 -> 0 until columns
            else -> return
        }
        for (col in range) row[col] = blankCell()
        wrapPending = false
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> { eraseLine(0); for (r in cursorRow + 1 until rows) clearRow(r) }
            1 -> { eraseLine(1); for (r in 0 until cursorRow) clearRow(r) }
            2 -> for (r in 0 until rows) clearRow(r)
            3 -> scrollback.clear()
        }
        wrapPending = false
    }

    private fun clearRow(row: Int) {
        val line = screen[row]
        for (col in 0 until columns) line[col] = blankCell()
    }

    /** Erased cells keep the current background, like xterm (coloured status bars). */
    private fun blankCell(): TermCell =
        if (style.bg == TermColor.Default) TermCell.BLANK else TermCell(' ', CellStyle(bg = style.bg))

    private fun blankRow(): MutableList<TermCell> = MutableList(columns) { blankCell() }

    private fun fillWithE() {
        for (r in 0 until rows) for (c in 0 until columns) screen[r][c] = TermCell('E')
        moveCursor(0, 0)
    }

    private fun currentCursor() = SavedCursor(
        cursorRow, cursorCol, style, g0Graphics, g1Graphics, useG1, originMode, wrapPending,
    )

    private fun applyCursor(saved: SavedCursor) {
        cursorRow = saved.row.coerceIn(0, rows - 1)
        cursorCol = saved.col.coerceIn(0, columns - 1)
        style = saved.style
        g0Graphics = saved.g0
        g1Graphics = saved.g1
        useG1 = saved.useG1
        originMode = saved.origin
        wrapPending = saved.wrapPending
    }

    private fun saveCursor() {
        savedCursor = currentCursor()
    }

    private fun restoreCursor() {
        applyCursor(savedCursor ?: SavedCursor(0, 0, CellStyle.DEFAULT, false, false, false, false, false))
    }

    private fun fullReset() {
        primary = blankScreen(rows, columns)
        alternate = blankScreen(rows, columns)
        screen = primary
        scrollback.clear()
        cursorRow = 0
        cursorCol = 0
        wrapPending = false
        style = CellStyle.DEFAULT
        scrollTop = 0
        scrollBottom = rows - 1
        tabStops = defaultTabStops(columns)
        savedCursor = null
        altSavedCursor = null
        g0Graphics = false
        g1Graphics = false
        useG1 = false
        applicationCursorKeys = false
        bracketedPaste = false
        cursorVisible = true
        autoWrap = true
        originMode = false
        insertMode = false
        usingAlternateScreen = false
        title = ""
    }

    private fun blankScreen(lines: Int, cols: Int): MutableList<MutableList<TermCell>> =
        MutableList(lines) { MutableList(cols) { TermCell.BLANK } }

    private fun resized(source: MutableList<MutableList<TermCell>>, cols: Int, lines: Int): MutableList<MutableList<TermCell>> =
        MutableList(lines) { r ->
            val old = source.getOrNull(r)
            MutableList(cols) { c -> old?.getOrNull(c) ?: TermCell.BLANK }
        }

    private fun defaultTabStops(cols: Int) = BooleanArray(cols) { it > 0 && it % TAB_WIDTH == 0 }
}

/** Keys that have no printable character, encoded to the bytes a VT/xterm host expects. */
internal enum class TerminalKey { UP, DOWN, RIGHT, LEFT, HOME, END, PAGE_UP, PAGE_DOWN, INSERT, DELETE, TAB, BACK_TAB, ESCAPE, ENTER, BACKSPACE, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12 }

internal object TerminalInput {
    private const val ESC = "\u001b"

    fun key(key: TerminalKey, applicationCursor: Boolean, alt: Boolean = false): ByteArray {
        val arrowPrefix = if (applicationCursor) "${ESC}O" else "$ESC["
        val sequence = when (key) {
            TerminalKey.UP -> "${arrowPrefix}A"
            TerminalKey.DOWN -> "${arrowPrefix}B"
            TerminalKey.RIGHT -> "${arrowPrefix}C"
            TerminalKey.LEFT -> "${arrowPrefix}D"
            TerminalKey.HOME -> "${arrowPrefix}H"
            TerminalKey.END -> "${arrowPrefix}F"
            TerminalKey.PAGE_UP -> "$ESC[5~"
            TerminalKey.PAGE_DOWN -> "$ESC[6~"
            TerminalKey.INSERT -> "$ESC[2~"
            TerminalKey.DELETE -> "$ESC[3~"
            TerminalKey.TAB -> "\t"
            TerminalKey.BACK_TAB -> "$ESC[Z"
            TerminalKey.ESCAPE -> ESC
            TerminalKey.ENTER -> "\r"
            TerminalKey.BACKSPACE -> "\u007f"
            TerminalKey.F1 -> "${ESC}OP"
            TerminalKey.F2 -> "${ESC}OQ"
            TerminalKey.F3 -> "${ESC}OR"
            TerminalKey.F4 -> "${ESC}OS"
            TerminalKey.F5 -> "$ESC[15~"
            TerminalKey.F6 -> "$ESC[17~"
            TerminalKey.F7 -> "$ESC[18~"
            TerminalKey.F8 -> "$ESC[19~"
            TerminalKey.F9 -> "$ESC[20~"
            TerminalKey.F10 -> "$ESC[21~"
            TerminalKey.F11 -> "$ESC[23~"
            TerminalKey.F12 -> "$ESC[24~"
        }
        val bytes = sequence.toByteArray(StandardCharsets.US_ASCII)
        return if (alt && key != TerminalKey.ESCAPE) byteArrayOf(0x1b) + bytes else bytes
    }

    /**
     * Encodes typed text. With [ctrl], letters and `@[\]^_ ?` become C0 control codes (Ctrl+C =
     * 0x03); with [alt], each character is prefixed by ESC (meta key convention of xterm).
     */
    fun text(text: String, ctrl: Boolean = false, alt: Boolean = false): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (ch in text) {
            val mapped = when (ch) {
                '\n' -> "\r"
                else -> if (ctrl) controlOf(ch)?.toString() ?: ch.toString() else ch.toString()
            }
            if (alt) out.write(0x1b)
            out.write(mapped.toByteArray(StandardCharsets.UTF_8))
        }
        return out.toByteArray()
    }

    /** Wraps a paste in bracketed-paste markers when the host asked for them, stripping any embedded end marker. */
    fun paste(text: String, bracketed: Boolean): ByteArray {
        val normalized = text.replace("\r\n", "\r").replace('\n', '\r')
        if (!bracketed) return normalized.toByteArray(StandardCharsets.UTF_8)
        val safe = normalized.replace("$ESC[201~", "")
        return "$ESC[200~$safe$ESC[201~".toByteArray(StandardCharsets.UTF_8)
    }

    fun controlOf(ch: Char): Char? = when (ch) {
        in 'a'..'z' -> (ch - 'a' + 1).toChar()
        in 'A'..'Z' -> (ch - 'A' + 1).toChar()
        '@', ' ', '2' -> 0.toChar()
        '[', '3' -> 27.toChar()
        '\\', '4' -> 28.toChar()
        ']', '5' -> 29.toChar()
        '^', '6' -> 30.toChar()
        '_', '7', '/' -> 31.toChar()
        '?', '8' -> 127.toChar()
        else -> null
    }
}
