package com.zaus.nullwave.feature.library.api.model.search

/**
 * Nonempty canonical search input. Create with [fromInput]; raw editable text belongs to the UI.
 *
 * Equality includes both [text] and [localeTag]. Locale controls result ordering, never text
 * normalization. Token order and repetition are retained because ranking can depend on the phrase.
 * A private constructor and no copy method prevent bypassing normalization or creating an empty
 * request. Locale is explicit and nonblank, as in the other Library query contracts.
 */
class LibrarySearchQuery private constructor(
    val text: String,
    val localeTag: String,
) {
    override fun equals(other: Any?): Boolean =
        other is LibrarySearchQuery && text == other.text && localeTag == other.localeTag

    override fun hashCode(): Int = 31 * text.hashCode() + localeTag.hashCode()

    override fun toString(): String = "LibrarySearchQuery(text=$text, localeTag=$localeTag)"

    companion object {
        /**
         * Returns null for whitespace-only input: no search, no catalog read or observer.
         * Even one non-whitespace character is a query. Invalid locale is a caller error,
         * including when [rawText] is empty; language tags are supplied by the active UI locale.
         */
        fun fromInput(rawText: String, localeTag: String): LibrarySearchQuery? {
            require(localeTag.isNotBlank()) { "Search locale must not be blank." }
            val text = normalizeLibrarySearchText(rawText)
            return if (text.isEmpty()) null else LibrarySearchQuery(text, localeTag)
        }
    }
}
