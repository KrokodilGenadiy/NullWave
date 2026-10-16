package com.zaus.nullwave.core.designsystem.components.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.zaus.nullwave.core.designsystem.components.control.rockerSwitch.NullWaveRockerSwitch
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle

/**
 * Controlled binary setting. The entire row owns Switch semantics, focus and the single callback;
 * the rocker is decorative, so tapping its halves or the label requests the same toggle once.
 * [checked] changes only when the caller accepts the request. No preference is stored here.
 * Long labels wrap; at narrow widths/large text the rocker moves below them without shrinking.
 * [borderStyle] affects only the rocker body; the row's focus outline always stays closed.
 */
@Composable
fun NullWaveSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.Closed,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    SettingsRowContent(
        label = label, supportingText = supportingText, leadingIcon = leadingIcon,
        enabled = enabled, source = source, modifier = modifier, adaptiveTrailing = true,
        action = Modifier.toggleable(
            value = checked, enabled = enabled, role = Role.Switch,
            interactionSource = source, indication = ripple(),
            onValueChange = { if (enabled) onCheckedChange(it) },
        ),
        trailing = {
            NullWaveRockerSwitch(checked, onCheckedChange = null, enabled = enabled, borderStyle = borderStyle)
        },
    )
}
