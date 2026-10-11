package com.zaus.nullwave.core.designsystem.components.control.textField

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.modifier.nullWaveBorder
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Controlled text input. [label] is a non-blank localized name, also available after text is entered.
 * The caller must feed [onValueChange] back immediately; debounce side effects, not input state.
 * Validation and persistence belong to the caller. No trimming, case conversion or length limits.
 * BasicTextField owns cursor, selection and IME composition; this wrapper adds decoration only.
 *
 * [readOnly] allows focus/selection/copy without editing; disabled prevents user interaction.
 * [supportingText] describes the error when [isError], otherwise it is a hint. A generic localized
 * error is announced if no message is supplied. The label/placeholder do not duplicate semantics.
 * [showLabel] hides only the visible label, never the accessible name (used by SearchField).
 * A trailing slot owns its action descriptions and must honour enabled/readOnly as appropriate.
 * Focus uses a closed border regardless of [borderStyle]. Parent constraints must allow 48 dp.
 */
@Composable
fun NullWaveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    @DrawableRes leadingIcon: Int? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    showLabel: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.Closed,
) {
    require(label.isNotBlank()) { "A text field needs a non-blank label." }
    require(minLines in 1..maxLines) { "Line limits must satisfy 1 <= minLines <= maxLines." }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val colors = NullWaveTheme.colors
    val spacing = NullWaveTheme.spacing
    val dimens = NullWaveTheme.dimens
    val shape = NullWaveTheme.shapes.small
    val focusedHighlighted = enabled && focused
    val borderColor = when {
        !enabled -> colors.outline
        isError -> colors.danger
        focusedHighlighted -> colors.secondary
        else -> colors.outline
    }
    val errorDescription = if (isError) {
        supportingText?.takeIf { it.isNotBlank() } ?: stringResource(R.string.nw_text_field_error)
    } else null

    BasicTextField(
        value = value,
        onValueChange = { if (enabled && !readOnly) onValueChange(it) },
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = label
            if (errorDescription != null) error(errorDescription)
        },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = NullWaveTheme.typography.body.copy(color = if (enabled) colors.textPrimary else colors.textSecondary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        interactionSource = source,
        cursorBrush = SolidColor(if (isError) colors.danger else colors.primary),
        decorationBox = { innerTextField ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                if (showLabel) {
                    Text(
                        label, modifier = Modifier.clearAndSetSemantics {},
                        style = NullWaveTheme.typography.caption,
                        color = if (isError && enabled) colors.danger else colors.textSecondary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = dimens.touchTarget)
                        .clip(shape).background(if (enabled) colors.surface else colors.surfaceRaised)
                        .nullWaveBorder(
                            BorderStroke(if (focusedHighlighted) dimens.focusBorder else dimens.hairline, borderColor),
                            shape, if (focusedHighlighted) NullWaveBorderStyle.Closed else borderStyle,
                        )
                        .padding(start = spacing.md, end = if (trailingContent == null) spacing.md else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) NullWaveIcon(leadingIcon, null, tint = if (focusedHighlighted) borderColor else colors.textSecondary)
                    Box(
                        modifier = Modifier.weight(1f).padding(vertical = spacing.sm),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                placeholder, modifier = Modifier.clearAndSetSemantics {},
                                style = NullWaveTheme.typography.body, color = colors.textSecondary,
                                maxLines = if (singleLine) 1 else maxLines,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                    trailingContent?.invoke()
                }
                if (!supportingText.isNullOrBlank()) {
                    Text(
                        supportingText,
                        // Errors are already attached to the editable node, not a second announcement.
                        modifier = if (isError) Modifier.clearAndSetSemantics {} else Modifier,
                        style = NullWaveTheme.typography.caption,
                        color = if (isError && enabled) colors.danger else colors.textSecondary,
                    )
                }
            }
        },
    )
}
