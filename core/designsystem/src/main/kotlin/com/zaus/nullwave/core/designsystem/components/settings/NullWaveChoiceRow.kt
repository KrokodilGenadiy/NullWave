package com.zaus.nullwave.core.designsystem.components.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * A setting's current [value], not a radio option. [onClick] asks the caller to open its chooser;
 * options, dialog/sheet and persistence belong to that caller. The row is one Button target.
 * Value is primary mono, read once as merged text. It moves below the label when it cannot fit
 * alongside it, and wraps without ellipsis. Disabled preserves the value with a neutral color.
 */
@Composable
fun NullWaveChoiceRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    require(value.isNotBlank()) { "Setting value must not be blank" }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    SettingsRowContent(
        label = label, supportingText = supportingText, leadingIcon = leadingIcon,
        enabled = enabled, source = source, modifier = modifier,
        adaptiveTrailing = true, value = value,
        action = Modifier.clickable(
            interactionSource = source, indication = ripple(), enabled = enabled, role = Role.Button,
            onClick = { if (enabled) onClick() },
        ),
        trailing = {
            Text(value, color = if (enabled) NullWaveTheme.colors.primary else NullWaveTheme.colors.textSecondary,
                style = NullWaveTheme.typography.mono)
        },
    )
}
