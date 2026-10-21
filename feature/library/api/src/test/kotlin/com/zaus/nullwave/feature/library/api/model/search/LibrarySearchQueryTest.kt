package com.zaus.nullwave.feature.library.api.model.search

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LibrarySearchQueryTest {
    @Test
    fun `empty or Unicode whitespace-only input does not create a search`() {
        val whitespace = " \t\n\r\u000B\u000C\u001C\u001D\u001E\u001F" +
            "\u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007" +
            "\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000"
        for (input in listOf("", whitespace) + whitespace.map(Char::toString)) {
            assertEquals("", normalizeLibrarySearchText(input))
            assertNull(LibrarySearchQuery.fromInput(input, "en-US"))
        }
        assertEquals("a b", query("${whitespace}A${whitespace}B$whitespace").text)
    }

    @Test
    fun `single characters including punctuation are valid queries`() {
        for (input in listOf("a", "я", "%", "_", "*", "'", "\"", "-", "🎵")) {
            assertEquals(input, query(input).text)
        }
        // Not Char.isWhitespace: do not silently discard formatting or invisible characters.
        assertEquals("\u200B", query("\u200B").text)
    }

    @Test
    fun `case whitespace and Russian yo share one canonical request`() {
        val first = query(" \tЕ\u0308ЛКА\u00A0\n Signal  ")
        val second = query("елка signal")
        assertEquals("елка signal", first.text)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(1, setOf(first, second, query("Ёлка SIGNAL")).size)
    }

    @Test
    fun `canonical composed and decomposed forms compare equally`() {
        assertEquals(query("CAFE\u0301"), query("Café"))
        assertEquals("café", query("CAFE\u0301").text)
        assertEquals(query("И\u0306ОД"), query("Йод"))
        assertEquals("йод", query("И\u0306ОД").text)
    }

    @Test
    fun `diacritics scripts and compatibility characters are not folded`() {
        for ((left, right) in listOf(
            "cafe" to "café",
            "иод" to "йод",
            "a" to "а",
            "strasse" to "Straße",
            "fi" to "ﬁ",
            "A" to "Ａ",
        )) {
            assertNotEquals(query(left), query(right))
        }
        assertEquals("straße", query("Straße").text)
    }

    @Test
    fun `normalization ignores ambient locale and ordering locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("i signal i\u0307", query("I SIGNAL İ").text)
            assertEquals(
                LibrarySearchQuery.fromInput("I SIGNAL İ", "en-US")?.text,
                LibrarySearchQuery.fromInput("I SIGNAL İ", "tr-TR")?.text,
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `locale participates in identity while input spelling does not`() {
        val english = LibrarySearchQuery.fromInput("Signal", "en-US")!!
        val russian = LibrarySearchQuery.fromInput("Signal", "ru-RU")!!
        assertEquals(english.text, russian.text)
        assertEquals("en-US", english.localeTag)
        assertEquals("ru-RU", russian.localeTag)
        assertNotEquals(english, russian)
        assertNotEquals(english, query("Noise"))
        assertNotEquals(english, null)
        assertNotEquals(english, english.text)
    }

    @Test
    fun `blank locale is rejected even for idle input`() {
        for (locale in listOf("", " ", "\t\n", "\u00A0")) {
            for (input in listOf("", "  ", "signal")) {
                assertThrows(IllegalArgumentException::class.java) {
                    LibrarySearchQuery.fromInput(input, locale)
                }
            }
        }
    }

    @Test
    fun `token order and repetition remain available for phrase ranking`() {
        assertEquals("alpha beta alpha", query(" Alpha  BETA alpha ").text)
        assertNotEquals(query("alpha beta"), query("beta alpha"))
        assertNotEquals(query("alpha beta"), query("alpha beta alpha"))
    }

    @Test
    fun `punctuation stays literal and is not interpreted as query syntax`() {
        val input = "  AC/DC  'Signal' \"Noise\" 100% _ * - 🎵  "
        assertEquals("ac/dc 'signal' \"noise\" 100% _ * - 🎵", query(input).text)
        assertEquals("<unknown>", query("<UNKNOWN>").text)
        assertNotEquals(query("ac-dc"), query("ac/dc"))
    }

    @Test
    fun `normalization and the query factory are idempotent`() {
        for (input in listOf("", " \n", "ЁЛКА\u00A0И\u0306од", "CAFE\u0301", "İ Σ Straße 🎵", "a  a %")) {
            val normalized = normalizeLibrarySearchText(input)
            assertEquals(normalized, normalizeLibrarySearchText(normalized))
            assertEquals(
                LibrarySearchQuery.fromInput(input, "ru-RU"),
                LibrarySearchQuery.fromInput(normalized, "ru-RU"),
            )
        }
    }

    @Test
    fun `long input is not silently truncated`() {
        val input = " Signal".repeat(2_000) + " FINAL"
        assertEquals("signal ".repeat(2_000) + "final", query(input).text)
    }

    private fun query(input: String): LibrarySearchQuery =
        checkNotNull(LibrarySearchQuery.fromInput(input, "en-US"))
}
