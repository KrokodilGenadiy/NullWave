package com.zaus.nullwave.core.designsystem.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveDivider
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveOctagonFrame
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.preview.NullWavePreviewSurface
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "States - Yellow", group = "States", widthDp = 360, heightDp = 1200)
@Composable
private fun YellowStatesPreview() = StateSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "States - Crimson", group = "States", widthDp = 360, heightDp = 1200)
@Composable
private fun CrimsonStatesPreview() = StateSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "States - Dive", group = "States", widthDp = 360, heightDp = 1200)
@Composable
private fun DiveStatesPreview() = StateSheet(NullWaveColorVariant.Braindance)

@Preview(name = "States - Large text", group = "States", widthDp = 320, heightDp = 1200, fontScale = 2f)
@Composable
private fun LargeStatesPreview() = StateSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "States - RTL", group = "States", widthDp = 360, heightDp = 1200)
@Composable
private fun RtlStatesPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        StateSheet(NullWaveColorVariant.Cyberpunk)
    }
}

/** Scroll in Interactive Preview; callbacks only update local counters and do no real work. */
@Composable
private fun StateSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        var primaryCalls by remember { mutableIntStateOf(0) }
        var secondaryCalls by remember { mutableIntStateOf(0) }
        NullWavePreviewSurface {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
            ) {
                NullWaveSectionHeader("Состояния / ${variant.name}")
                Text("Primary: $primaryCalls · Secondary: $secondaryCalls", style = NullWaveTheme.typography.mono)
                NullWaveEmptyState(
                    title = "Ничего не найдено",
                    body = "Попробуйте изменить запрос или снять ограничения поиска. Название с кириллицей / Mixed title 🎵.",
                    illustration = {
                        NullWaveOctagonFrame(
                            modifier = Modifier.size(72.dp),
                            borderStyle = NullWaveBorderStyle.ClippedCorners,
                        ) {
                            NullWaveIcon(NullWaveIcons.Search, null, Modifier.size(32.dp))
                        }
                    },
                    primaryAction = {
                        NullWaveButton("Изменить запрос", { primaryCalls++ }, Modifier.fillMaxWidth())
                    },
                    secondaryAction = {
                        NullWaveButton(
                            "Сбросить ограничения поиска", { secondaryCalls++ }, Modifier.fillMaxWidth(),
                            variant = NullWaveButtonVariant.Neutral,
                            borderStyle = NullWaveBorderStyle.ClippedCorners,
                        )
                    },
                )
                NullWaveDivider()
                NullWaveErrorState(
                    title = "Не удалось прочитать файлы / Could not read files",
                    body = "Повторите попытку. Если файлы перемещены, выберите доступную папку. Этот текст и способы восстановления задаёт экран.",
                    primaryAction = {
                        NullWaveButton("Повторить", { primaryCalls++ }, Modifier.fillMaxWidth())
                    },
                    secondaryAction = {
                        NullWaveButton("Выбрать папку", { secondaryCalls++ }, Modifier.fillMaxWidth(), variant = NullWaveButtonVariant.Neutral)
                    },
                )
                NullWaveDivider()
                NullWaveEmptyState("Очередь пуста", "No illustration or actions. Тексты переносятся без ограничения числа строк.")
                NullWaveErrorState(
                    title = "Файл недоступен",
                    body = "Ошибка без автоматического retry. Нет доступного действия восстановления.",
                    illustration = null,
                )
                NullWaveEmptyState(
                    title = "Пока пусто",
                    body = "Только вторичное действие — без пустого места вместо primary.",
                    secondaryAction = {
                        NullWaveButton("Вернуться", { secondaryCalls++ }, variant = NullWaveButtonVariant.Neutral)
                    },
                )
            }
        }
    }
}
