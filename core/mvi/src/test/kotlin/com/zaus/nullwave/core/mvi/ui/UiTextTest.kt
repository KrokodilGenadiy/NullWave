package com.zaus.nullwave.core.mvi.ui

import com.zaus.nullwave.core.mvi.R
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UiTextTest {
    @Test
    fun `resource values compare their typed arguments including nested text`() {
        val first = UiText.Resource(
            1,
            UiText.Argument.Text(UiText.Resource(2)),
            UiText.Argument.Count(3),
            UiText.Argument.Decimal(1.5),
        )
        val equal = UiText.Resource(
            1,
            UiText.Argument.Text(UiText.Resource(2)),
            UiText.Argument.Count(3L),
            UiText.Argument.Decimal(1.5),
        )

        assertEquals(first, equal)
        assertEquals(first.hashCode(), equal.hashCode())
        assertNotEquals(first, UiText.Resource(1, UiText.Argument.Text(UiText.Resource(3))))
        assertNotEquals(UiText.Resource(1), UiText.Resource(2))
        assertEquals(UiText.Literal("track"), UiText.Literal("track"))
    }

    @Test
    fun `constructor arguments cannot change an existing resource value`() {
        val arguments = arrayOf<UiText.Argument>(UiText.Argument.Text("original"))
        val value = UiText.Resource(1, *arguments)
        val initialHash = value.hashCode()

        arguments[0] = UiText.Argument.Text("changed")

        assertEquals(UiText.Resource(1, UiText.Argument.Text("original")), value)
        assertEquals(initialHash, value.hashCode())
    }

    @Test
    fun `each shared data error maps to a localized resource without exposing diagnostics`() {
        val expected = mapOf(
            DataError.AccessDenied to R.string.mvi_error_access_denied,
            DataError.NotFound to R.string.mvi_error_not_found,
            DataError.StorageFull to R.string.mvi_error_storage_full,
            DataError.Io to R.string.mvi_error_io,
        )

        assertEquals(DataError.entries.toSet(), expected.keys)
        expected.forEach { (error, resource) ->
            val failure = AppResult.Failure(error, IllegalStateException("internal file path"))
            assertEquals(UiText.Resource(resource), failure.error.toUiText())
        }
    }
}
