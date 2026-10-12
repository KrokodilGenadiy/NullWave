package com.zaus.nullwave.core.designsystem.components.feedback

import androidx.compose.foundation.BorderStroke
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
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSurface
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

enum class NullWaveMessageVariant { Neutral, Info, Success, Error }

/** One labelled action; the caller owns its effect and whether the message remains visible. */
data class NullWaveMessageAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
) {
    init {
        require(label.isNotBlank()) { "Message action label must not be blank" }
    }
}

/**
 * Persistent message in normal layout flow. No timer, focus request or automatic announcement.
 * For an asynchronous update, the caller may add liveRegion semantics to [modifier].
 * [onDismiss] reports intent; the caller must remove the message. Action and dismiss are separate.
 * Explain the status in [message]: a decorative icon/colour is not the only status cue.
 */
@Composable
fun NullWaveInlineMessage(
    message: String,
    modifier: Modifier = Modifier,
    variant: NullWaveMessageVariant = NullWaveMessageVariant.Neutral,
    action: NullWaveMessageAction? = null,
    onDismiss: (() -> Unit)? = null,
    dismissDescription: String = stringResource(R.string.nw_message_dismiss),
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.Closed,
) {
    require(message.isNotBlank()) { "Message must not be blank" }
    val colors = NullWaveTheme.colors
    val spacing = NullWaveTheme.spacing
    val accent = when (variant) {
        NullWaveMessageVariant.Neutral -> colors.outline
        NullWaveMessageVariant.Info -> colors.secondary
        NullWaveMessageVariant.Success -> colors.success
        NullWaveMessageVariant.Error -> colors.danger
    }
    val icon = when (variant) {
        NullWaveMessageVariant.Neutral -> null
        NullWaveMessageVariant.Info -> NullWaveIcons.Info
        NullWaveMessageVariant.Success -> NullWaveIcons.Check
        NullWaveMessageVariant.Error -> NullWaveIcons.Warning
    }
    NullWaveSurface(
        modifier = modifier.fillMaxWidth(),
        shape = NullWaveTheme.shapes.small,
        contentColor = colors.textPrimary,
        border = BorderStroke(NullWaveTheme.dimens.hairline, accent),
        borderStyle = borderStyle,
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.Top,
            ) {
                if (icon != null) {
                    NullWaveIcon(icon, contentDescription = null, tint = accent)
                }
                Text(
                    text = message,
                    modifier = Modifier.weight(1f),
                    color = colors.textPrimary,
                    style = NullWaveTheme.typography.body,
                )
                if (onDismiss != null) {
                    NullWaveIconButton(NullWaveIcons.Close, dismissDescription, onDismiss)
                }
            }
            if (action != null) {
                NullWaveButton(
                    label = action.label,
                    onClick = action.onClick,
                    modifier = Modifier.align(Alignment.End),
                    variant = NullWaveButtonVariant.Secondary,
                    enabled = action.enabled,
                    borderStyle = borderStyle,
                )
            }
        }
    }
}
