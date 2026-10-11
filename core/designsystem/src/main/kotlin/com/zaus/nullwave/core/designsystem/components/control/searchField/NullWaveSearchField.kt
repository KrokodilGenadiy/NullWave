package com.zaus.nullwave.core.designsystem.components.control.searchField

import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.components.control.textField.NullWaveTextField
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle

/** Optional independent action, for example opening filters. Its meaning belongs to the feature. */
data class NullWaveSearchAction(
    @DrawableRes val icon: Int,
    val contentDescription: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/**
 * Single-line controlled query, without search execution, debounce, trimming or blank-query policy.
 * [onSearch] receives the current query on the IME Search action; it does not hide the keyboard.
 * The default [onClear] requests an empty query exactly once. A supplied callback replaces it and
 * must update the query itself. Clear is shown only for non-empty editable input.
 * Clearing returns focus to the text input, including when the disappearing button had key focus.
 * Disabled/readOnly block both clear and the optional action; readOnly still permits selection/copy.
 * [label] remains the accessible name even when the placeholder is hidden by an entered query.
 */
@Composable
fun NullWaveSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClear: () -> Unit = { onQueryChange("") },
    label: String = stringResource(R.string.nw_search_label),
    placeholder: String = stringResource(R.string.nw_search_placeholder),
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    trailingAction: NullWaveSearchAction? = null,
    interactionSource: MutableInteractionSource? = null,
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.Closed,
) {
    val editable = enabled && !readOnly
    val showClear = query.isNotEmpty() && editable
    val clearDescription = stringResource(R.string.nw_search_clear)
    val inputFocus = remember { FocusRequester() }
    NullWaveTextField(
        value = query,
        onValueChange = onQueryChange,
        label = label,
        modifier = modifier.focusRequester(inputFocus),
        placeholder = placeholder,
        supportingText = supportingText,
        isError = isError,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (editable) onSearch(query) }),
        leadingIcon = NullWaveIcons.Search,
        trailingContent = if (showClear || trailingAction != null) {
            {
                Row {
                    if (showClear) {
                        NullWaveIconButton(NullWaveIcons.Close, clearDescription, {
                            onClear()
                            inputFocus.requestFocus()
                        })
                    }
                    if (trailingAction != null) {
                        NullWaveIconButton(
                            icon = trailingAction.icon,
                            contentDescription = trailingAction.contentDescription,
                            onClick = trailingAction.onClick,
                            enabled = editable && trailingAction.enabled,
                        )
                    }
                }
            }
        } else null,
        showLabel = false,
        interactionSource = interactionSource,
        borderStyle = borderStyle,
    )
}
