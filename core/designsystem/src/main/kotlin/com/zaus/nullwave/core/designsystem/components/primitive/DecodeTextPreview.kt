package com.zaus.nullwave.core.designsystem.components.primitive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.zaus.nullwave.core.designsystem.components.control.chip.NullWaveChip
import com.zaus.nullwave.core.designsystem.preview.NullWavePreviewSurface
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Decode text - Yellow", group = "Decode text", widthDp = 360, heightDp = 720, locale = "ru")
@Composable
private fun YellowDecodeTextPreview() = DecodeTextSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Decode text - Crimson", group = "Decode text", widthDp = 360, heightDp = 720)
@Composable
private fun CrimsonDecodeTextPreview() = DecodeTextSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Decode text - Dive", group = "Decode text", widthDp = 360, heightDp = 720)
@Composable
private fun DiveDecodeTextPreview() = DecodeTextSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Decode text - RTL", group = "Decode text", widthDp = 360, heightDp = 720)
@Composable
private fun RtlDecodeTextPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        DecodeTextSheet(NullWaveColorVariant.Cyberpunk)
    }
}

@Preview(name = "Decode text - Large text", group = "Decode text", widthDp = 320, heightDp = 900, fontScale = 2f)
@Composable
private fun LargeDecodeTextPreview() = DecodeTextSheet(NullWaveColorVariant.Cyberpunk)

/** Static preview shows the first frame; Interactive Preview plays it and the chips replay it. */
@Composable
private fun DecodeTextSheet(variant: NullWaveColorVariant) {
    var replay by remember { mutableIntStateOf(0) }
    var reduceMotion by remember { mutableStateOf(false) }
    NullWaveTheme(variant = variant) {
        val typography = NullWaveTheme.typography
        val spacing = NullWaveTheme.spacing
        NullWavePreviewSurface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                NullWaveSectionHeader(
                    title = "Decode text / ${variant.name}",
                    supportingText = "Размер берётся из итогового текста; озвучивается только он",
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    NullWaveChip("REPLAY", selected = false, onClick = { replay++ })
                    NullWaveChip("REDUCE MOTION", selected = reduceMotion, onClick = { reduceMotion = !reduceMotion })
                }
                NullWaveDecodeText("БИБЛИОТЕКА", style = typography.display, replayKey = replay, reduceMotion = reduceMotion)
                NullWaveDecodeText("LIBRARY", style = typography.h2, replayKey = replay, reduceMotion = reduceMotion)
                NullWaveDecodeText(
                    "Static Pilgrim · Nullwave EP",
                    style = typography.body,
                    color = NullWaveTheme.colors.textPrimary,
                    replayKey = replay,
                    reduceMotion = reduceMotion,
                )
                NullWaveDecodeText(
                    "AUD/LOCAL/READ_OK · SYS.0x4F2A",
                    style = typography.micro,
                    color = NullWaveTheme.colors.textSecondary,
                    scrambleColor = NullWaveTheme.colors.primary,
                    replayKey = replay,
                    reduceMotion = reduceMotion,
                )
                NullWaveDecodeText(
                    "Одна строка: длинная подпись обрезается многоточием после расшифровки",
                    style = typography.caption,
                    color = NullWaveTheme.colors.textSecondary,
                    replayKey = replay,
                    reduceMotion = reduceMotion,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                NullWaveDecodeText("Ёж · І · Ў · 🎵 · 日本語", style = typography.body, replayKey = replay, reduceMotion = reduceMotion)
            }
        }
    }
}
