package com.zaus.nullwave.core.designsystem.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.preview.NullWavePreviewSurface
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Messages - Yellow", group = "Messages", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun YellowMessagesPreview() = MessageSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Messages - Crimson", group = "Messages", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun CrimsonMessagesPreview() = MessageSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Messages - Dive", group = "Messages", widthDp = 360, heightDp = 1100, locale = "en")
@Composable
private fun DiveMessagesPreview() = MessageSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Messages - Large text", group = "Messages", widthDp = 320, heightDp = 1100, fontScale = 2f, locale = "ru")
@Composable
private fun LargeMessagesPreview() = MessageSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Messages - RTL", group = "Messages", widthDp = 360, heightDp = 1100, locale = "ar")
@Composable
private fun RtlMessagesPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MessageSheet(NullWaveColorVariant.Cyberpunk)
    }
}

@Composable
private fun MessageSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        var actionCount by remember { mutableIntStateOf(0) }
        var dismissCount by remember { mutableIntStateOf(0) }
        var visible by remember { mutableStateOf(true) }
        NullWavePreviewSurface {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
            ) {
                NullWaveSectionHeader("Inline messages / ${variant.name}")
                Text("Action: $actionCount · Dismiss: $dismissCount", style = NullWaveTheme.typography.mono)
                if (visible) {
                    NullWaveInlineMessage(
                        message = "Не удалось прочитать часть файлов. Повторите попытку или проверьте доступ к папке.",
                        variant = NullWaveMessageVariant.Error,
                        action = NullWaveMessageAction("Повторить чтение файлов", { actionCount++ }),
                        onDismiss = { dismissCount++; visible = false },
                        borderStyle = NullWaveBorderStyle.ClippedCorners,
                    )
                }
                NullWaveButton("Показать снова", { visible = true }, enabled = !visible)
                NullWaveInlineMessage("Нейтральное сообщение без действий / No action")
                NullWaveInlineMessage(
                    "Информация: новые файлы появятся после обновления списка.",
                    variant = NullWaveMessageVariant.Info,
                    action = NullWaveMessageAction("Обновить", { actionCount++ }),
                )
                NullWaveInlineMessage(
                    "Готово: настройки сохранены / Settings saved 🎵",
                    variant = NullWaveMessageVariant.Success,
                )
                NullWaveInlineMessage(
                    "Ошибка: файл недоступен. Кнопка ниже отключена.",
                    variant = NullWaveMessageVariant.Error,
                    action = NullWaveMessageAction("Недоступное действие", { actionCount++ }, enabled = false),
                )
                NullWaveInlineMessage(
                    "Dismiss без action: callback намеренно только считает нажатия, видимость контролирует caller.",
                    onDismiss = { dismissCount++ },
                )
            }
        }
    }
}
