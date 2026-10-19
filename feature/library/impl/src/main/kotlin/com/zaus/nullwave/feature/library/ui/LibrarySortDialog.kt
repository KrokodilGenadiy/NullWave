package com.zaus.nullwave.feature.library.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.radioButton.NullWaveRadioButton
import com.zaus.nullwave.core.designsystem.components.overlay.NullWaveDialog
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveDivider
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.feature.library.api.model.LibraryQuery
import com.zaus.nullwave.feature.library.api.model.LibrarySort
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.presentation.LibraryIntent

@StringRes
internal fun LibrarySort.label(): Int = when (this) {
    LibrarySort.Title -> R.string.library_sort_title
    LibrarySort.Artist -> R.string.library_sort_artist
    LibrarySort.RecentlyAdded -> R.string.library_sort_added
}

@Composable
internal fun LibrarySortDialog(query: LibraryQuery, onIntent: (LibraryIntent) -> Unit, onDismiss: () -> Unit) {
    NullWaveDialog(
        title = stringResource(R.string.library_sort),
        onDismissRequest = onDismiss,
        actions = { NullWaveButton(stringResource(R.string.library_done), onDismiss) },
    ) {
        Column(Modifier.selectableGroup()) {
            LibrarySort.entries.forEach { sort ->
                SortOption(stringResource(sort.label()), query.sort == sort) {
                    onIntent(LibraryIntent.SortChanged(sort, query.direction))
                }
            }
        }
        NullWaveDivider()
        Column(Modifier.selectableGroup()) {
            SortDirection.entries.forEach { direction ->
                SortOption(stringResource(if (direction == SortDirection.Ascending) R.string.library_ascending
                    else R.string.library_descending), query.direction == direction) {
                    onIntent(LibraryIntent.SortChanged(query.sort, direction))
                }
            }
        }
    }
}

@Composable
private fun SortOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        NullWaveRadioButton(selected, onClick = null)
        Text(label, style = NullWaveTheme.typography.body)
    }
}
