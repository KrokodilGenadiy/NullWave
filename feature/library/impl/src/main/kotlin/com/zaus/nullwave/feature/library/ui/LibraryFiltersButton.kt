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
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.impl.R

@Composable
internal fun LibraryFiltersButton(query: LibraryTrackQuery, onClick: () -> Unit) {
    val ascending = query.direction == SortDirection.Ascending
    val actionDescription = stringResource(R.string.library_filters)
    val selectionDescription = stringResource(R.string.library_sort_selection,
        stringResource(query.sort.label()),
        stringResource(if (ascending) R.string.library_ascending else R.string.library_descending))
    NullWaveButton(
        label = stringResource(R.string.library_filters_action),
        onClick = onClick,
        modifier = Modifier.testTag("library_filters").semantics {
            contentDescription = actionDescription
            stateDescription = selectionDescription
        },
        variant = NullWaveButtonVariant.Secondary,
        borderStyle = NullWaveBorderStyle.ClippedCorners,
        size = NullWaveButtonSize.Compact,
    )
}
