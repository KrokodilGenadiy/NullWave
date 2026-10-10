package com.zaus.nullwave.core.designsystem.components.control

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Controlled two-state icon button. The caller owns [checked] and accepts/rejects changes.
 * [contentDescription] is a non-blank localized feature name (e.g. "Favourite"), not its state;
 * Material supplies Checkbox role, checked state and disabled semantics.
 * [checkedIcon] defaults to [icon] for features such as shuffle with a single glyph.
 * Selection changes the glyph/tint only, never the container; press feedback uses Material ripple.
 * Parent constraints must allow a target of at least 48 x 48 dp. A supplied [interactionSource]
 * remains owned by the caller; the component only remembers transient interactions.
 */
@Composable
fun NullWaveIconToggleButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes checkedIcon: Int = icon,
    interactionSource: MutableInteractionSource? = null,
) {
    require(contentDescription.isNotBlank()) { "An icon toggle needs a non-blank feature description." }
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens
    val shape = NullWaveTheme.shapes.node
    val focusedHighlighted = enabled && focused
    val contentColor = when {
        !enabled -> colors.textSecondary
        checked -> colors.primary
        else -> colors.textPrimary
    }

    IconToggleButton(
        checked = checked,
        // Keep the same direct-semantics-action protection as the other controls.
        onCheckedChange = { if (enabled) onCheckedChange(it) },
        modifier = modifier
            .defaultMinSize(minWidth = dimens.touchTarget, minHeight = dimens.touchTarget)
            .then(
                if (focusedHighlighted) {
                    Modifier.border(dimens.focusBorder, contentColor, shape)
                } else Modifier,
            )
            // Preserve Material's toggleable semantics; the child icon is decorative.
            .semantics { this.contentDescription = contentDescription },
        enabled = enabled,
        shape = shape,
        colors = IconButtonDefaults.iconToggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.textPrimary,
            checkedContainerColor = Color.Transparent,
            checkedContentColor = colors.primary,
            // Disabled retains checkedIcon/semantics while using a neutral tint and no backdrop.
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.textSecondary,
        ),
        interactionSource = interactions,
    ) {
        NullWaveIcon(icon = if (checked) checkedIcon else icon, contentDescription = null)
    }
}
