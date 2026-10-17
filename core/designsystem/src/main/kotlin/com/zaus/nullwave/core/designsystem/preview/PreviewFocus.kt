package com.zaus.nullwave.core.designsystem.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager

/** Real focus for interactive previews; static interaction fixtures use a separate source. */
@Composable
internal fun rememberPreviewFocus(): PreviewFocus {
    val inputModeManager = LocalInputModeManager.current
    return remember(inputModeManager) { PreviewFocus(inputModeManager) }
}

internal class PreviewFocus(private val inputModeManager: InputModeManager) {
    private val requester = FocusRequester()
    private var requestStatus by mutableStateOf("—")
    var isFocused by mutableStateOf(false)
        private set

    // Must precede the target's focusable/clickable modifier, as with FocusRequester itself.
    val modifier: Modifier = Modifier.focusRequester(requester).onFocusChanged {
        isFocused = it.isFocused
    }

    val status: String
        get() = "Запрос: $requestStatus · Фокус: $isFocused"

    fun requestFocus() {
        // SystemDefined controls reject programmatic focus while the host stays in touch mode.
        requestStatus = when {
            !inputModeManager.requestInputMode(InputMode.Keyboard) -> "режим клавиатуры недоступен"
            requester.requestFocus() -> "принят"
            else -> "отклонён"
        }
    }
}
