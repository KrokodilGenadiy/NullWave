package com.zaus.nullwave.feature.library.api.model.search

import java.text.Normalizer

/**
 * Shared comparison text for query input and searchable metadata. Empty input stays empty.
 *
 * Applies NFC, invariant lowercase, explicit Russian ё -> е, whitespace collapse/trim and NFC
 * again. Preserves punctuation, other diacritics and different scripts; this is not full Unicode
 * case folding. No truncation, transliteration or query operators are applied.
 *
 * Keep the original text for display: normalized offsets cannot be used to highlight it directly.
 * Metadata callers must exclude missing/provider-unknown values before using this function.
 */
fun normalizeLibrarySearchText(text: String): String {
    val lowercase = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase().replace('ё', 'е')
    val collapsed = buildString(lowercase.length) {
        var pendingSpace = false
        for (character in lowercase) {
            if (character.isWhitespace()) {
                pendingSpace = isNotEmpty()
            } else {
                if (pendingSpace) append(' ')
                append(character)
                pendingSpace = false
            }
        }
    }
    return Normalizer.normalize(collapsed, Normalizer.Form.NFC)
}

/** Search explanations contain cleaned, original metadata, never a provider sentinel. */
internal fun String.isKnownSearchText(): Boolean =
    isNotBlank() && this == trim() && !equals("<unknown>", ignoreCase = true)
