package com.zaus.nullwave.feature.search.presentation

import com.zaus.nullwave.core.mvi.UiIntent

internal sealed interface SearchIntent : UiIntent {
    data class QueryChanged(val value: String) : SearchIntent
    data object ClearQuery : SearchIntent
    data object Retry : SearchIntent
    data class LocaleChanged(val localeTag: String) : SearchIntent
    /** Fresh OS observation from Root on resume/result; this does not request permission. */
    data class AccessChecked(val granted: Boolean) : SearchIntent
}
