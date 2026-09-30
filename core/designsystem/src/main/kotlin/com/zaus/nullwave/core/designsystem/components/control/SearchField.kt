package com.zaus.nullwave.core.designsystem.components.control

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.chamferBorder
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.theme.chamfer

/**
 * The search field. 48dp, `surface` fill, chamfered, hairline border that switches to `secondary`
 * while focused.
 *
 * `BasicTextField` with a hand-written decoration box rather than Material's `TextField`, which
 * brings a rounded container and an indicator line of its own.
 *
 * The caret is `primary` via `cursorBrush` - Material would otherwise draw it in its own accent,
 * which is the one bit of the text field that cannot be restyled by theming alone.
 *
 * The clear action is a full [NullWaveIconButton], not a bare icon with a `clickable`: the design's
 * 48dp touch-target floor applies to it like anything else. The field is exactly 48dp tall, so the
 * button fills its height and takes only the trailing 48dp of its width. The leading magnifier
 * stays a plain `Icon` - decoration, not a control, so it gets no hit area at all.
 *
 * The row's end padding is dropped while the button is showing, so its hit area reaches the field's
 * inner edge rather than stopping 14dp short. The glyph stays optically inset regardless, because a
 * 24dp icon centred in 48dp already carries 12dp of its own.
 */
@Composable
fun NullWaveSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onSearch: () -> Unit = {},
) {
    val colors = NullWaveTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = if (isFocused) colors.secondary else colors.outline

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(FieldHeight)
            .clip(FieldShape)
            .background(colors.surface)
            .chamferBorder(borderColor, FieldShape),
        textStyle = NullWaveTheme.typography.body.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.primary),
        singleLine = true,
        interactionSource = interactionSource,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = FieldPadding,
                        end = if (value.isEmpty()) FieldPadding else 0.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(NullWaveIcons.Search),
                    contentDescription = null,
                    tint = if (isFocused) colors.secondary else colors.textTertiary,
                    modifier = Modifier.size(NullWaveTheme.dimens.icon),
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = NullWaveTheme.typography.body,
                            color = colors.textTertiary,
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    NullWaveIconButton(
                        icon = NullWaveIcons.Close,
                        contentDescription = "Clear search",
                        onClick = { onValueChange("") },
                        tint = colors.textSecondary,
                    )
                }
            }
        },
    )
}

private val FieldHeight = 48.dp
private val FieldPadding = 14.dp
private val FieldShape = chamfer(10.dp)

@Preview(name = "Search field", showBackground = true)
@Composable
private fun SearchFieldPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        Column(
            modifier = Modifier
                .background(colors.bg)
                .padding(NullWaveTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
        ) {
            NullWaveSearchField(value = "", onValueChange = {})
            NullWaveSearchField(value = "deadzone", onValueChange = {})
        }
    }
}
