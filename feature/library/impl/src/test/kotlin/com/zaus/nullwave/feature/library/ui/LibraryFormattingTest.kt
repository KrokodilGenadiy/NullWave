package com.zaus.nullwave.feature.library.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LibraryFormattingTest {
    @Test
    fun `unknown duration differs from a known zero`() {
        assertNull(libraryDuration(null))
        assertEquals("0:00", libraryDuration(0))
    }

    @Test
    fun `duration floors milliseconds and pads seconds and minutes`() {
        assertEquals("0:59", libraryDuration(59_999))
        assertEquals("1:00", libraryDuration(60_000))
        assertEquals("59:59", libraryDuration(3_599_999))
        assertEquals("1:00:01", libraryDuration(3_601_000))
    }

    @Test
    fun `long recordings do not wrap at a day or overflow`() {
        assertEquals("25:01:01", libraryDuration(90_061_000))
        assertEquals("2562047788015:12:55", libraryDuration(Long.MAX_VALUE))
    }

    @Test
    fun `negative known duration is a contract violation`() {
        assertThrows(IllegalArgumentException::class.java) { libraryDuration(-1) }
    }
}
