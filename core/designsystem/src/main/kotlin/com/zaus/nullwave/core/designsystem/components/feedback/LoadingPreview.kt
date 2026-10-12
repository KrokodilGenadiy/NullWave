 package com.zaus.nullwave.core.designsystem.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
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
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveDivider
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Loading - Yellow", group = "Loading", widthDp = 360, heightDp = 1000, locale = "ru")
@Composable
private fun YellowLoadingPreview() = LoadingSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Loading - Crimson", group = "Loading", widthDp = 360, heightDp = 1000, locale = "ru")
@Composable
private fun CrimsonLoadingPreview() = LoadingSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Loading - Dive", group = "Loading", widthDp = 360, heightDp = 1000, locale = "en")
@Composable
private fun DiveLoadingPreview() = LoadingSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Loading - Large text", group = "Loading", widthDp = 320, heightDp = 1000, fontScale = 2f, locale = "ru")
@Composable
private fun LargeLoadingPreview() = LoadingSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Loading - RTL", group = "Loading", widthDp = 360, heightDp = 1000, locale = "ar")
@Composable
private fun RtlLoadingPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        LoadingSheet(NullWaveColorVariant.Cyberpunk)
    }
}

/** Static previews show the initial fraction; Interactive Preview drives it without timers. */
@Composable
private fun LoadingSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        var percent by remember { mutableIntStateOf(62) }
        val spacing = NullWaveTheme.spacing
        Surface(color = NullWaveTheme.colors.bg) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                NullWaveSectionHeader("Загрузка / ${variant.name}", supportingText = "Static marks · no shimmer")
                NullWaveLoadingIndicator()
                NullWaveLoadingIndicator(label = "Подготавливаем локальную медиатеку / Preparing the library")
                NullWaveDivider()
                NullWaveProgress(percent / 100f, "Обработка файлов / Processing files")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    NullWaveButton("−10%", { percent = (percent - 10).coerceAtLeast(0) })
                    NullWaveButton("+10%", { percent = (percent + 10).coerceAtMost(100) })
                    NullWaveButton("Сброс", { percent = 0 }, variant = NullWaveButtonVariant.Neutral)
                }
                NullWaveProgress(0f, "Начало / Start")
                NullWaveProgress(1f, "Завершено / Complete")
                NullWaveProgress(-0.2f, "Ниже диапазона → 0%")
                NullWaveProgress(1.2f, "Выше диапазона → 100%")
                NullWaveDivider()
                NullWaveSectionHeader("Skeleton rows", supportingText = "Один loading на весь список; строки декоративны")
                Column {
                    NullWaveLoadingIndicator(Modifier.padding(horizontal = NullWaveTheme.dimens.gutter))
                    NullWaveSkeletonRow()
                    NullWaveSkeletonRow(showTrailing = false)
                    NullWaveSkeletonRow(showArtwork = false)
                    NullWaveSkeletonRow(showArtwork = false, showTrailing = false)
                }
            }
        }
    }
}
