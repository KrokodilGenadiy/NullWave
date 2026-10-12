package com.zaus.nullwave.core.designsystem.components.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/** Wrapping title/supporting text and optional explicit close. No drag or dismiss policy here. */
@Composable
fun NullWaveSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    onClose: (() -> Unit)? = null,
    closeDescription: String = stringResource(R.string.nw_sheet_close),
) {
    require(title.isNotBlank()) { "Sheet title must not be blank" }
    val spacing = NullWaveTheme.spacing
    Row(
        modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(
                title, Modifier.semantics { heading() },
                color = NullWaveTheme.colors.textPrimary, style = NullWaveTheme.typography.h2,
            )
            if (!supportingText.isNullOrBlank()) {
                Text(supportingText, color = NullWaveTheme.colors.textSecondary, style = NullWaveTheme.typography.caption)
            }
        }
        if (onClose != null) {
            NullWaveIconButton(NullWaveIcons.Close, closeDescription, onClose)
        }
    }
}
