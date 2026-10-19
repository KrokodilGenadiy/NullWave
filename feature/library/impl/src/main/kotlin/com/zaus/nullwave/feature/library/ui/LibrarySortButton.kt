package com.zaus.nullwave.feature.library.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonSize
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.feature.library.api.model.LibraryQuery
import com.zaus.nullwave.feature.library.api.model.LibrarySort
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.impl.R

@Composable
internal fun LibrarySortButton(query: LibraryQuery, onClick: () -> Unit) {
    val ascending = query.direction == SortDirection.Ascending
    val sortLabel = stringResource(when (query.sort) {
        LibrarySort.Title -> R.string.library_sort_title_short
        LibrarySort.Artist -> R.string.library_sort_artist_short
        LibrarySort.RecentlyAdded -> R.string.library_sort_added_short
    })
    val actionDescription = stringResource(R.string.library_sort)
    val selectionDescription = stringResource(R.string.library_sort_selection,
        stringResource(query.sort.label()),
        stringResource(if (ascending) R.string.library_ascending else R.string.library_descending))
    NullWaveButton(
        label = stringResource(R.string.library_sort_current, sortLabel, if (ascending) "▲" else "▼"),
        onClick = onClick,
        modifier = Modifier.testTag("library_sort").semantics {
            contentDescription = actionDescription
            stateDescription = selectionDescription
        },
        variant = NullWaveButtonVariant.Neutral,
        size = NullWaveButtonSize.Compact,
    )
}
