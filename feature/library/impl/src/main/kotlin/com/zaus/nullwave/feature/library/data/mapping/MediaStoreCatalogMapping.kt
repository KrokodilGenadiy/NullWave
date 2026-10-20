package com.zaus.nullwave.feature.library.data.mapping

import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import java.io.IOException
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal fun String?.metadataOrNull(): String? = this?.trim()?.takeIf {
    it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true)
}

/** Validates both the audio row ID and its volume, returning the volume for catalog keys. */
internal fun MediaStoreAudioRow.validateAudioIdentityAndGetVolume(): String {
    val value = volume?.takeIf { it.isNotBlank() }
        ?: throw IOException("MediaStore audio row has no volume")
    if (id < 0) throw IOException("MediaStore audio row has an invalid identity")
    return value
}

/** Missing membership omits only that group; malformed audio identity is still a provider failure. */
internal suspend fun List<MediaStoreAudioRow>.catalogGroups(
    entityId: (MediaStoreAudioRow) -> Long?,
): Map<String, List<MediaStoreAudioRow>> {
    val context = currentCoroutineContext()
    val groups = linkedMapOf<String, MutableList<MediaStoreAudioRow>>()
    for (row in this) {
        context.ensureActive()
        val volume = row.validateAudioIdentityAndGetVolume()
        val id = entityId(row)?.takeIf { it >= 0 } ?: continue
        groups.getOrPut("$volume:$id") { mutableListOf() }.add(row)
    }
    return groups
}

/** Prefer a known label from the lowest track ID, independent of cursor order and UI locale. */
internal suspend fun List<MediaStoreAudioRow>.catalogLabel(
    label: (MediaStoreAudioRow) -> String?,
): String? {
    val context = currentCoroutineContext()
    var selectedId: Long? = null
    var selectedLabel: String? = null
    for (row in this) {
        context.ensureActive()
        val value = label(row).metadataOrNull() ?: continue
        if (selectedId == null || row.id < selectedId) {
            selectedId = row.id
            selectedLabel = value
        }
    }
    return selectedLabel
}

/** Collator belongs to this snapshot; mutable collators must not be shared by subscriptions. */
internal suspend fun <T> List<T>.sortedCatalog(
    localeTag: String,
    direction: SortDirection,
    label: (T) -> String?,
    identity: (T) -> String,
): List<T> {
    val context = currentCoroutineContext()
    val collator = Collator.getInstance(Locale.forLanguageTag(localeTag))
    val ascending = direction == SortDirection.Ascending
    return sortedWith { first, second ->
        context.ensureActive()
        val order = compareMissingLast(label(first), label(second), ascending, collator::compare)
        if (order != 0) order else identity(first).compareTo(identity(second))
    }
}

internal fun <T : Any> compareMissingLast(first: T?, second: T?, ascending: Boolean, compare: (T, T) -> Int): Int = when {
    first == null && second == null -> 0
    first == null -> 1
    second == null -> -1
    ascending -> compare(first, second)
    else -> compare(second, first)
}
