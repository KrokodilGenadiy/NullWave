package com.zaus.nullwave.core.designsystem.components.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * A settings action or destination, with one click/accessibility target for the whole row.
 * Icons are decorative; [trailingIcon] defaults to the RTL-aware forward arrow, but can be replaced
 * (e.g. Rescan) or omitted. Labels wrap and the row grows from 64 dp. Navigation, confirmation and
 * operation state belong to the caller; invoking [onClick] does not dismiss any enclosing surface.
 */
@Composable
fun NullWaveSettingRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = NullWaveIcons.Forward,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    SettingsRowContent(
        label = label, supportingText = supportingText, leadingIcon = leadingIcon,
        enabled = enabled, source = source, modifier = modifier,
        action = Modifier.clickable(
            interactionSource = source, indication = ripple(), enabled = enabled, role = Role.Button,
            onClick = { if (enabled) onClick() },
        ),
        trailing = trailingIcon?.let { icon ->
            { NullWaveIcon(icon, contentDescription = null, tint = NullWaveTheme.colors.textSecondary) }
        },
    )
}
