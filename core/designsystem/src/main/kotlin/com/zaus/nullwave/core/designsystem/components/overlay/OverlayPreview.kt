package com.zaus.nullwave.core.designsystem.components.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.control.textField.NullWaveTextField
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveDivider
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSurface
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.preview.NullWavePreviewSurface
import com.zaus.nullwave.core.designsystem.preview.rememberPreviewFocus
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import kotlinx.coroutines.launch

@Preview(name = "Overlays - Yellow", group = "Overlays", widthDp = 360, heightDp = 1000, locale = "ru")
@Composable
private fun YellowOverlaysPreview() = OverlaySheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Overlays - Crimson", group = "Overlays", widthDp = 360, heightDp = 1000, locale = "ru")
@Composable
private fun CrimsonOverlaysPreview() = OverlaySheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Overlays - Dive", group = "Overlays", widthDp = 360, heightDp = 1000, locale = "en")
@Composable
private fun DiveOverlaysPreview() = OverlaySheet(NullWaveColorVariant.Braindance)

@Preview(name = "Overlays - Large text", group = "Overlays", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LargeOverlaysPreview() = OverlaySheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Overlays - RTL", group = "Overlays", widthDp = 360, heightDp = 1000, locale = "ar")
@Composable
private fun RtlOverlaysPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        OverlaySheet(NullWaveColorVariant.Cyberpunk)
    }
}

/** Static surfaces always render. Use the buttons in Interactive/Run Preview for real modal windows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverlaySheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        var dialogVisible by remember { mutableStateOf(false) }
        var sheetVisible by remember { mutableStateOf(false) }
        var dismissAllowed by remember { mutableStateOf(true) }
        var name by remember { mutableStateOf("") }
        var actions by remember { mutableIntStateOf(0) }
        var dismisses by remember { mutableIntStateOf(0) }
        val menuFocus = rememberPreviewFocus()
        val spacing = NullWaveTheme.spacing
        NullWavePreviewSurface {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                NullWaveSectionHeader("Overlays / ${variant.name}")
                Text("Action: $actions · Dismiss: $dismisses", style = NullWaveTheme.typography.mono)
                NullWaveButton("Открыть диалог с формой", { dismissAllowed = true; dialogVisible = true })
                NullWaveButton("Диалог: закрытие только кнопкой", { dismissAllowed = false; dialogVisible = true })
                NullWaveButton("Открыть панель", { sheetVisible = true })
                NullWaveSectionHeader("Статичные образцы", supportingText = "Не модальные окна; прокрутите витрину")
                DialogContent(
                    title = "Длинное название диалога / Long dialog title",
                    modifier = Modifier.heightIn(max = 420.dp),
                    borderStyle = NullWaveBorderStyle.ClippedCorners,
                    actions = {
                        NullWaveButton("Подтвердить выбранное действие", { actions++ }, Modifier.fillMaxWidth())
                        NullWaveButton("Отмена", { dismisses++ }, Modifier.fillMaxWidth(), variant = NullWaveButtonVariant.Neutral)
                    },
                ) {
                    Text("Длинное пояснение переносится. В коротком окне действия доступны прокруткой всей карточки.", style = NullWaveTheme.typography.body)
                }
                NullWaveSurface {
                    Column {
                        NullWaveSheetHeader(
                            "Заголовок панели / Mixed title 🎵",
                            supportingText = "Подзаголовок переносится и не перекрывает крестик.",
                            onClose = { dismisses++ },
                        )
                        NullWaveDivider()
                        NullWaveMenuItem(
                            "Обычное действие", { actions++ }, menuFocus.modifier,
                            leadingIcon = NullWaveIcons.Play,
                        )
                        NullWaveMenuItem(
                            "Длинное действие без иконки с дополнительным пояснением",
                            { actions++ }, supportingText = "One target / Один callback на всю строку",
                        )
                        NullWaveMenuItem("Недоступное действие", { actions++ }, enabled = false, leadingIcon = NullWaveIcons.Share)
                        NullWaveMenuItem(
                            "Опасное действие: только счётчик", { actions++ },
                            leadingIcon = NullWaveIcons.Delete, danger = true,
                            borderStyle = NullWaveBorderStyle.ClippedCorners,
                        )
                        NullWaveMenuItem("Опасное, но disabled", { actions++ }, danger = true, enabled = false)
                    }
                }
                NullWaveButton("Фокус пункта меню", menuFocus::requestFocus)
                NullWaveMicroLabel(menuFocus.status)
            }
        }
        if (dialogVisible) {
            NullWaveDialog(
                title = "Название плейлиста / Playlist name",
                onDismissRequest = { dismisses++; dialogVisible = false },
                properties = DialogProperties(
                    dismissOnBackPress = dismissAllowed,
                    dismissOnClickOutside = dismissAllowed,
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
                borderStyle = NullWaveBorderStyle.ClippedCorners,
                actions = {
                    NullWaveButton("Подтвердить", { actions++; dialogVisible = false }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
                    NullWaveButton("Отмена", { dismisses++; dialogVisible = false }, Modifier.fillMaxWidth(), variant = NullWaveButtonVariant.Neutral)
                },
            ) {
                NullWaveTextField(name, { name = it }, "Название")
                repeat(4) {
                    Text("Пояснение ${it + 1}: содержимое и кнопки должны оставаться достижимыми при клавиатуре и крупном шрифте.", style = NullWaveTheme.typography.body)
                }
            }
        }
        if (sheetVisible) {
            val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val scope = rememberCoroutineScope()
            val close: () -> Unit = {
                scope.launch {
                    state.hide()
                    if (!state.isVisible && sheetVisible) {
                        dismisses++
                        sheetVisible = false
                    }
                }
            }
            NullWaveModalSheet(
                onDismissRequest = { dismisses++; sheetVisible = false },
                sheetState = state,
                borderStyle = NullWaveBorderStyle.ClippedCorners,
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NullWaveSheetHeader("Действия / Actions", supportingText = "Крестик скрывает панель с анимацией", onClose = close)
                    NullWaveTextField(name, { name = it }, "Текст для проверки IME", Modifier.padding(horizontal = spacing.md))
                    repeat(12) { index ->
                        NullWaveMenuItem(
                            "Действие ${index + 1}", { actions++ },
                            supportingText = "Увеличивает счётчик, не закрывает панель",
                            enabled = index != 2,
                            leadingIcon = NullWaveIcons.Info,
                        )
                    }
                    NullWaveMenuItem("Опасное действие / Danger", { actions++ }, danger = true, leadingIcon = NullWaveIcons.Delete)
                    NullWaveMenuItem("Закрыть панель", close, leadingIcon = NullWaveIcons.Close)
                }
            }
        }
    }
}
