package com.zaus.nullwave.core.designsystem.components.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.zaus.nullwave.core.designsystem.components.content.NullWaveCoverArt
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.preview.NullWavePreviewSurface
import com.zaus.nullwave.core.designsystem.preview.PreviewInteractionSample
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Queue drag - Yellow", group = "Queue drag states", widthDp = 360, heightDp = 1000, locale = "ru")
@Composable
private fun YellowQueueDragPreview() = QueueDragSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Queue drag - Crimson", group = "Queue drag states", widthDp = 360, heightDp = 1000, locale = "ru")
@Composable
private fun CrimsonQueueDragPreview() = QueueDragSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Queue drag - Dive", group = "Queue drag states", widthDp = 412, heightDp = 1000, locale = "en")
@Composable
private fun DiveQueueDragPreview() = QueueDragSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Queue drag - Large text", group = "Queue drag states", widthDp = 320, heightDp = 1400, fontScale = 2f, locale = "ru")
@Composable
private fun LargeQueueDragPreview() = QueueDragSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Queue handle - Press to drag", group = "Queue drag states", widthDp = 360, heightDp = 240, locale = "ru")
@Composable
private fun QueueHandleFeedbackPreview() {
    NullWaveTheme {
        NullWavePreviewSurface {
            Column(
                Modifier.padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
            ) {
                for (dragging in listOf(false, true)) {
                    Row(
                        Modifier.fillMaxWidth().background(NullWaveTheme.colors.surfaceRaised)
                            .padding(NullWaveTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (dragging) "Удержание → перенос\nЕдиный фон строки" else "Короткое нажатие\nЖёлтый отклик",
                            Modifier.weight(1f), style = NullWaveTheme.typography.caption,
                        )
                        // Keep Press active to cover takeover before the first consumed drag move.
                        PreviewInteractionSample(pressed = true) { source ->
                            NullWaveDragHandle(
                                contentDescription = "Действия записи — образец",
                                onClick = {},
                                dragging = dragging,
                                interactionSource = source,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Static states make the row halo visible in a normal IDE render, without a pointer gesture. */
@Composable
private fun QueueDragSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        NullWavePreviewSurface {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(NullWaveTheme.spacing.lg),
                // The halo extends beyond the row; labels and neighbouring rows leave it space.
                verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.lg),
            ) {
                NullWaveSectionHeader("Перенос очереди", supportingText = "Статичные состояния · Reduce motion ON")
                QueueDragSample("Обычная строка")
                QueueDragSample("Перенос · акцент и свечение", dragging = true)
                QueueDragSample("Недоступный файл · перенос разрешён", dragging = true, available = false)
                QueueDragSample("Disabled · свечение отключено", dragging = true, enabled = false)
                Text("Отключённая строка не подсвечивается даже при dragging = true. " +
                    "Жесты, возврат и перестановка проверяются в интерактивном QueuePreview.",
                    style = NullWaveTheme.typography.caption)
            }
        }
    }
}

@Composable
private fun QueueDragSample(
    label: String,
    dragging: Boolean = false,
    available: Boolean = true,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md)) {
        Text(label, style = NullWaveTheme.typography.mono)
        NullWaveQueueRow(
            title = "Тихий маяк",
            onClick = {},
            subtitle = "Сигналы утра",
            durationLabel = "3:42",
            artwork = { NullWaveCoverArt(monogram = "ТМ") },
            onMoveUp = {},
            onMoveDown = {},
            onRemove = {},
            dragging = dragging,
            available = available,
            enabled = enabled,
            reduceMotion = true,
        )
    }
}
