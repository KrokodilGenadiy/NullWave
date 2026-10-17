package com.zaus.nullwave.core.designsystem.components.content

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveDivider
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.preview.NullWavePreviewSurface
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Content - Yellow", group = "Library content", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun YellowContentPreview() = ContentSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Content - Crimson", group = "Library content", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun CrimsonContentPreview() = ContentSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Content - Dive", group = "Library content", widthDp = 412, heightDp = 1100, locale = "en")
@Composable
private fun DiveContentPreview() = ContentSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Content - Large text", group = "Library content", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LargeContentPreview() = ContentSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Content - RTL", group = "Library content", widthDp = 360, heightDp = 1100, locale = "ar")
@Composable
private fun RtlContentPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ContentSheet(NullWaveColorVariant.Cyberpunk)
    }
}

@Composable
private fun ContentSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        var clicks by remember { mutableIntStateOf(0) }
        var more by remember { mutableIntStateOf(0) }
        var longClicks by remember { mutableIntStateOf(0) }
        var selected by remember { mutableStateOf(false) }
        var artworkStep by remember { mutableIntStateOf(0) }
        var reduceTitleMotion by remember { mutableStateOf(false) }
        var focusResult by remember { mutableStateOf("Фокус не запрошен") }
        val focus = remember { FocusRequester() }
        val inputMode = LocalInputModeManager.current
        val painter = remember { PreviewSleevePainter() }
        NullWavePreviewSurface {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
            ) {
                NullWaveSectionHeader("CoverArt / ${variant.name}", Modifier.padding(NullWaveTheme.dimens.gutter))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = NullWaveTheme.dimens.gutter),
                    horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
                ) {
                    NullWaveCoverArt(monogram = "NW", contentDescription = "Обложка NW", size = 48.dp)
                    NullWaveCoverArt(monogram = "НВ", contentDescription = "Обложка НВ", size = 72.dp)
                    NullWaveCoverArt(painter = painter, contentDescription = "Геометрическая обложка", size = 120.dp)
                    NullWaveCoverArt(monogram = "МЯ", size = 160.dp)
                }
                Row(
                    Modifier.padding(horizontal = NullWaveTheme.dimens.gutter),
                    horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
                ) {
                    NullWaveCoverArt(contentDescription = "Обложка отсутствует")
                    NullWaveCoverArt(placeholder = NullWaveArtworkPlaceholder.Loading, contentDescription = "Обложка загружается")
                    NullWaveCoverArt(placeholder = NullWaveArtworkPlaceholder.Error, contentDescription = "Обложку не удалось загрузить")
                }
                NullWaveSectionHeader("TrackRow", Modifier.padding(horizontal = NullWaveTheme.dimens.gutter))
                Text("Click: $clicks · More: $more · Long: $longClicks", Modifier.padding(horizontal = NullWaveTheme.dimens.gutter), style = NullWaveTheme.typography.mono)
                NullWaveTrackRow(
                    "Deadzone Chorus", { clicks++ }, Modifier.focusRequester(focus),
                    subtitle = "Static Pilgrim · Nullwave EP", durationLabel = "3:41",
                    artwork = { NullWaveCoverArt(painter = painter) },
                    onMoreClick = { more++ }, onLongClick = { longClicks++ }, longClickLabel = "Выбрать трек",
                )
                NullWaveDivider()
                NullWaveTrackRow("Играющий трек", { clicks++ }, subtitle = "Ночной Вектор", durationLabel = "4:12",
                    playing = true, artwork = { NullWaveCoverArt(monogram = "НВ") }, onMoreClick = { more++ })
                NullWaveTrackRow("Выбор + воспроизведение", { selected = !selected }, subtitle = "Нажми строку для выбора", durationLabel = "2:58",
                    playing = true, selected = selected, selectionMode = true, onMoreClick = { more++ })
                NullWaveTrackRow("Выбранный трек", { clicks++ }, selected = true, durationLabel = "5:03")
                NullWaveButton("Reduce motion для длинного названия: ${if (reduceTitleMotion) "ON" else "OFF"}",
                    { reduceTitleMotion = !reduceTitleMotion }, Modifier.padding(horizontal = NullWaveTheme.dimens.gutter))
                NullWaveTrackRow("Очень длинное название композиции / A long title scrolling on a single line",
                    { clicks++ }, subtitle = "Исполнитель с длинным именем · Полное название альбома", durationLabel = "1:23:45",
                    artwork = { NullWaveCoverArt(monogram = "ДН") }, onMoreClick = { more++ }, reduceMotion = reduceTitleMotion)
                NullWaveTrackRow("Без метаданных и обложки", { clicks++ })
                NullWaveTrackRow("Файл недоступен", { clicks++ }, subtitle = "Исходная папка отключена", durationLabel = "3:10",
                    enabled = false, onMoreClick = { more++ }, onLongClick = { longClicks++ }, longClickLabel = "Выбрать трек",
                    artwork = { NullWaveCoverArt(placeholder = NullWaveArtworkPlaceholder.Error) })
                NullWaveTrackRow("Выбран и недоступен", { clicks++ }, enabled = false, selected = true, playing = true, onMoreClick = { more++ })
                NullWaveTrackRow("Внешнее состояние обложки: $artworkStep", { clicks++ },
                    artwork = {
                        NullWaveCoverArt(
                            painter = if (artworkStep == 2) painter else null,
                            monogram = "NW",
                            placeholder = when (artworkStep) {
                                1 -> NullWaveArtworkPlaceholder.Loading
                                3 -> NullWaveArtworkPlaceholder.Error
                                else -> NullWaveArtworkPlaceholder.Missing
                            },
                        )
                    })
                NullWaveButton("Обложка: missing → loading → image → error", { artworkStep = (artworkStep + 1) % 4 }, Modifier.padding(horizontal = NullWaveTheme.dimens.gutter))
                NullWaveButton("Фокус на первый трек", {
                    focusResult = if (inputMode.requestInputMode(InputMode.Keyboard) && focus.requestFocus()) "Запрос принят" else "Запрос отклонён"
                }, Modifier.padding(horizontal = NullWaveTheme.dimens.gutter))
                Text(focusResult, Modifier.padding(NullWaveTheme.dimens.gutter), style = NullWaveTheme.typography.mono)
            }
        }
    }
}

/** Wide, local artwork sample exercises ContentScale.Crop without networking or an image loader. */
private class PreviewSleevePainter : Painter() {
    override val intrinsicSize = Size(300f, 180f)
    override fun DrawScope.onDraw() {
        drawRect(Brush.linearGradient(listOf(Color(0xFF143948), Color(0xFF63375C))))
        drawCircle(Color(0xFFFFB84D), size.height * 0.32f, Offset(size.width * 0.5f, size.height * 0.45f))
        for (index in 0..4) {
            drawLine(Color(0xFF07131B), Offset(0f, size.height * (0.58f + index * 0.08f)), Offset(size.width, size.height * (0.42f + index * 0.08f)), size.height * 0.025f)
        }
    }
}
