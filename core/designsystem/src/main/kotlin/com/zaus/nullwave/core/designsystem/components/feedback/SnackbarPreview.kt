package com.zaus.nullwave.core.designsystem.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Preview(name = "Snackbar - Yellow", group = "Snackbar", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun YellowSnackbarPreview() = SnackbarSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Snackbar - Crimson", group = "Snackbar", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun CrimsonSnackbarPreview() = SnackbarSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Snackbar - Dive", group = "Snackbar", widthDp = 360, heightDp = 1100, locale = "en")
@Composable
private fun DiveSnackbarPreview() = SnackbarSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Snackbar - Large text", group = "Snackbar", widthDp = 320, heightDp = 1100, fontScale = 2f, locale = "ru")
@Composable
private fun LargeSnackbarPreview() = SnackbarSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Snackbar - RTL", group = "Snackbar", widthDp = 360, heightDp = 1100, locale = "ar")
@Composable
private fun RtlSnackbarPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        SnackbarSheet(NullWaveColorVariant.Cyberpunk)
    }
}

/** Static examples always render; Interactive Preview exercises the real Material host below. */
@Composable
private fun SnackbarSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        val host = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var result by remember { mutableStateOf("Пока нет результата") }
        var queuedJob by remember { mutableStateOf<Job?>(null) }
        val spacing = NullWaveTheme.spacing
        Surface(color = NullWaveTheme.colors.bg) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                NullWaveSectionHeader("Snackbar host / ${variant.name}")
                Text(result, style = NullWaveTheme.typography.mono)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    NullWaveButton("Short", {
                        scope.launch { result = "Short: ${host.showSnackbar("Короткое сообщение / Short")}" }
                    })
                    NullWaveButton("Long", {
                        scope.launch {
                            result = "Long: ${host.showSnackbar("Длинное сообщение / Long", duration = SnackbarDuration.Long)}"
                        }
                    })
                    NullWaveButton("С действием", {
                        scope.launch {
                            result = "Action: ${host.showSnackbar("Демо: действие не изменяет файлы.", actionLabel = "Проверить результат") }"
                        }
                    })
                    NullWaveButton("Без таймера", {
                        scope.launch {
                            result = "Indefinite: ${host.showSnackbar("Закрывается крестиком без действия.", duration = SnackbarDuration.Indefinite)}"
                        }
                    })
                    NullWaveButton("В очередь", {
                        queuedJob = scope.launch {
                            try {
                                result = "Queued: ${host.showSnackbar("Сообщение из очереди", withDismissAction = true)}"
                            } finally {
                                queuedJob = null
                            }
                        }
                    }, enabled = queuedJob == null)
                    NullWaveButton("Отменить запрос", {
                        queuedJob?.cancel()
                        result = "Queued request cancelled"
                    }, enabled = queuedJob != null, variant = NullWaveButtonVariant.Neutral)
                }
                NullWaveSnackbarHost(host) { data ->
                    NullWaveSnackbar(data, borderStyle = NullWaveBorderStyle.ClippedCorners)
                }
                NullWaveSectionHeader("Статичные образцы", supportingText = "Ниже только visuals, без таймера и live region")
                NullWaveSnackbar(
                    PreviewSnackbarData("Короткое сообщение без действий", onAction = {}, onDismiss = {}),
                )
                NullWaveSnackbar(
                    PreviewSnackbarData(
                        message = "Не удалось завершить операцию. Длинный текст переносится, действие расположено отдельной строкой.",
                        actionLabel = "Повторить обработку выбранных файлов",
                        withDismissAction = true,
                        onAction = { result = "Static: ActionPerformed" },
                        onDismiss = { result = "Static: Dismissed" },
                    ),
                    variant = NullWaveMessageVariant.Error,
                    borderStyle = NullWaveBorderStyle.ClippedCorners,
                )
                NullWaveSnackbar(
                    PreviewSnackbarData(
                        "Готово / Complete", duration = SnackbarDuration.Indefinite,
                        onAction = {}, onDismiss = { result = "Static indefinite: Dismissed" },
                    ),
                    variant = NullWaveMessageVariant.Success,
                )
            }
        }
    }
}

private class PreviewSnackbarData(
    message: String,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    duration: SnackbarDuration = SnackbarDuration.Short,
    private val onAction: () -> Unit,
    private val onDismiss: () -> Unit,
) : SnackbarData {
    override val visuals: SnackbarVisuals = object : SnackbarVisuals {
        override val message = message
        override val actionLabel = actionLabel
        override val withDismissAction = withDismissAction
        override val duration = duration
    }
    override fun performAction() = onAction()
    override fun dismiss() = onDismiss()
}
