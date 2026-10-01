package com.qrcommunication.ploipanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedOptionPickerTest {
    @Test fun cursorDoesNotCrossBoundariesAndKeepsOnlyOnePage() {
        val cursor = PickerCursor()
        cursor.previous()
        assertEquals(1, cursor.page)
        val first = PickerOptions(listOf(1L, 2L), 1, 3)
        cursor.next(first)
        assertEquals(2, cursor.page)
        cursor.next(first) // Old page must not advance current cursor.
        assertEquals(2, cursor.page)
        val second = PickerOptions(listOf(51L), 2, 3)
        cursor.next(second)
        assertEquals(3, cursor.page)
        val last = PickerOptions(emptyList<Long>(), 3, 3)
        assertFalse(last.hasNext)
        cursor.next(last)
        assertEquals(3, cursor.page)
        cursor.previous()
        assertEquals(2, cursor.page)
    }

    @Test fun invalidPageMetadataIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { PickerOptions(emptyList<Long>(), 0, 1) }
        assertThrows(IllegalArgumentException::class.java) { PickerOptions(emptyList<Long>(), 2, 1) }
    }

    @Test fun selectionPersistsAcrossPagesAndCanBeRemoved() {
        var selected = ""
        selected = toggleProjectId(selected, 52, true)
        selected = toggleProjectId(selected, 1, true)
        selected = toggleProjectId(selected, 52, true)
        assertEquals(listOf(52L, 1L), parseProjectIds(selected))
        selected = toggleProjectId(selected, 1, false)
        assertEquals(listOf(52L), parseProjectIds(selected))
        assertTrue(52L in parseProjectIds(selected).orEmpty())
    }

    @Test fun malformedManualAssociationsCannotBeSilentlyDiscarded() {
        assertEquals(null, parseProjectIds("7,999999999999999999999999"))
        assertEquals(null, parseProjectIds("0"))
        assertEquals("7,bad", toggleProjectId("7,bad", 52, true))
        assertEquals(listOf(7L), parseProjectIds("7, ,7"))
    }
}
