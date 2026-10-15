package com.zaus.nullwave.core.designsystem.components.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Ready-to-display position and duration labels, at logical start/end. Wraps if they cannot fit.
 * For a remaining-time end label, also supply a corresponding [endDescription]. No clock/formatter.
 */
@Composable
fun NullWaveTimeLabels(
    positionLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
    positionDescription: String = stringResource(R.string.nw_time_position, positionLabel),
    endDescription: String = stringResource(R.string.nw_time_duration, endLabel),
) {
    require(positionLabel.isNotBlank() && endLabel.isNotBlank()) { "Time labels must not be blank" }
    require(positionDescription.isNotBlank() && endDescription.isNotBlank()) { "Time descriptions must not be blank" }
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xxs)) {
        Text(positionLabel, Modifier.padding(end = NullWaveTheme.spacing.sm).clearAndSetSemantics { contentDescription = positionDescription },
            color = NullWaveTheme.colors.textPrimary, style = NullWaveTheme.typography.mono)
        Text(endLabel, Modifier.clearAndSetSemantics { contentDescription = endDescription },
            color = NullWaveTheme.colors.textSecondary, style = NullWaveTheme.typography.mono)
    }
}
