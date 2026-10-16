package com.zaus.nullwave.core.designsystem.components.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.zaus.nullwave.core.designsystem.components.content.NullWaveCoverArt
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

@Preview(name = "Glow - Yellow", group = "Player glow", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun YellowPlayerGlowPreview() = PlayerGlowSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Glow - Crimson", group = "Player glow", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun CrimsonPlayerGlowPreview() = PlayerGlowSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Glow - Dive", group = "Player glow", widthDp = 412, heightDp = 1100, locale = "en")
@Composable
private fun DivePlayerGlowPreview() = PlayerGlowSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Glow - Large text", group = "Player glow", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LargePlayerGlowPreview() = PlayerGlowSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Glow - RTL", group = "Player glow", widthDp = 360, heightDp = 1100, locale = "ar")
@Composable
private fun RtlPlayerGlowPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        PlayerGlowSheet(NullWaveColorVariant.Cyberpunk)
    }
}

@Composable
private fun PlayerGlowSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        var playing by remember { mutableStateOf(true) }
        var buffering by remember { mutableStateOf(false) }
        var enabled by remember { mutableStateOf(true) }
        var effects by remember { mutableStateOf(true) }
        var buttonEffects by remember { mutableStateOf(true) }
        var lineEffects by remember { mutableStateOf(true) }
        var reduced by remember { mutableStateOf(false) }
        var durationKnown by remember { mutableStateOf(true) }
        var media by remember { mutableIntStateOf(1) }
        var position by remember { mutableLongStateOf(84_000L) }
        var seeks by remember { mutableIntStateOf(0) }
        var plays by remember { mutableIntStateOf(0) }
        var expands by remember { mutableIntStateOf(0) }
        var shuffled by remember { mutableStateOf(false) }
        var repeat by remember { mutableStateOf(NullWaveRepeatMode.Off) }
        var changeScheduled by remember { mutableStateOf(false) }
        var focusResult by remember { mutableStateOf("Фокус не запрошен") }
        val duration = if (durationKnown) 241_000L else null
        // Match SeekBar's gesture lifetime: a new item, duration or availability cancels the old one.
        val source = remember(media, duration, enabled) { MutableInteractionSource() }
        val dragged by key(source) { source.collectIsDraggedAsState() }
        var scrub by remember(media, duration, enabled) { mutableStateOf<Long?>(null) }
        // Drag begins before a value changes; scrub also covers changes made without a drag.
        val seeking = enabled && duration != null && (dragged || scrub != null)
        val playGlow = effects && buttonEffects && !seeking
        val displayPosition = scrub ?: position
        val progress = duration?.let { (displayPosition.toDouble() / it).toFloat().coerceIn(0f, 1f) }
        val scope = rememberCoroutineScope()
        val focus = remember { FocusRequester() }
        val inputMode = LocalInputModeManager.current
        val spacing = NullWaveTheme.spacing
        val gutter = NullWaveTheme.dimens.gutter
        // Synthetic peaks are a preview fixture, never a substitute for real audio analysis.
        val peaks = remember { List(96) { 0.15f + abs(sin(it * 0.47f)) * 0.8f } }
        val play: () -> Unit = { playing = !playing; plays++ }
        val changeMedia: () -> Unit = { media++; position = 0L }

        Surface(color = NullWaveTheme.colors.bg, contentColor = NullWaveTheme.colors.textPrimary) {
            Column(
                Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    NullWaveSectionHeader("Full player / перенос акцента")
                    Text("Линии имеют мягкое свечение и в покое. Потяни waveform: основной акцент переходит " +
                        "с Play/Pause на seek и возвращается после отпускания или отмены. " +
                        "Тонкий playhead остаётся на точной позиции и во время drag — меняется только свечение.",
                        style = NullWaveTheme.typography.caption)
                    NullWaveButton(
                        label = if (changeScheduled) "Смена трека запланирована" else "Сменить трек через 2 с",
                        onClick = {
                            changeScheduled = true
                            scope.launch {
                                delay(2000)
                                changeMedia()
                                changeScheduled = false
                            }
                        },
                        enabled = !changeScheduled,
                    )
                    NullWaveSeekBar(
                        mediaKey = "glow-preview-$media",
                        positionMs = position,
                        durationMs = duration,
                        onSeek = { position = it; seeks++ },
                        formatTime = ::glowPreviewTime,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        waveform = peaks,
                        onScrubPositionChange = { scrub = it },
                        interactionSource = source,
                        glowEnabled = effects && lineEffects,
                    )
                    // Space around the controls is intentional: the halo draws beyond their bounds.
                    NullWaveTransportControls(
                        isPlaying = playing,
                        onPlayPause = play,
                        onPrevious = changeMedia,
                        onNext = changeMedia,
                        modifier = Modifier.padding(vertical = spacing.md),
                        buffering = buffering,
                        enabled = enabled,
                        shuffle = shuffled,
                        onShuffleChange = { shuffled = it },
                        repeatMode = repeat,
                        onRepeatClick = {
                            repeat = when (repeat) {
                                NullWaveRepeatMode.Off -> NullWaveRepeatMode.All
                                NullWaveRepeatMode.All -> NullWaveRepeatMode.One
                                NullWaveRepeatMode.One -> NullWaveRepeatMode.Off
                            }
                        },
                        reduceMotion = reduced,
                        playPauseGlowEnabled = playGlow,
                    )
                    Text("Основной акцент: ${when {
                        !effects || !enabled -> "нет"
                        seeking && lineEffects -> "seek"
                        playGlow -> "Play/Pause"
                        else -> "нет"
                    }}\n" +
                        "Трек: $media · Seek commits: $seeks · Drag: $dragged\n" +
                        "Позиция: ${glowPreviewTime(position)} · Scrub: ${scrub?.let(::glowPreviewTime) ?: "—"}",
                        style = NullWaveTheme.typography.mono)
                    NullWaveSectionHeader("MiniPlayer / отдельный образец")
                }
                NullWaveMiniPlayer(
                    title = "Deadzone Chorus / Ночной город",
                    isPlaying = playing,
                    onExpand = { expands++ },
                    onPlayPause = play,
                    onNext = changeMedia,
                    subtitle = "Static Pilgrim — Nullwave EP",
                    artwork = { NullWaveCoverArt(monogram = "NW") },
                    progress = progress,
                    buffering = buffering,
                    playbackEnabled = enabled,
                    reduceMotion = reduced,
                    playPauseGlowEnabled = playGlow,
                    progressGlowEnabled = effects && lineEffects,
                )
                Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text("Образцы используют один scrub: свечение обеих кнопок гаснет на время перемотки. " +
                        "В реальном экране виден один transport. Play: $plays · Expand: $expands",
                        style = NullWaveTheme.typography.caption)
                    NullWaveSectionHeader("Условия проверки")
                    NullWaveButton("Glow: ${if (effects) "ON" else "OFF"}", { effects = !effects })
                    NullWaveButton("Button glow: ${if (buttonEffects) "ON" else "OFF"}", { buttonEffects = !buttonEffects })
                    NullWaveButton("Line glow: ${if (lineEffects) "ON" else "OFF"}", { lineEffects = !lineEffects })
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        NullWaveButton("0%", { position = 0L }, enabled = durationKnown)
                        NullWaveButton("0,5%", { duration?.let { position = it / 200L } }, enabled = durationKnown)
                        NullWaveButton("50%", { duration?.let { position = it / 2L } }, enabled = durationKnown)
                        NullWaveButton("100%", { duration?.let { position = it } }, enabled = durationKnown)
                    }
                    NullWaveButton("Reduce motion: $reduced", { reduced = !reduced })
                    NullWaveButton("Buffering: $buffering", { buffering = !buffering })
                    NullWaveButton("Playback enabled: $enabled", { enabled = !enabled })
                    NullWaveButton("Duration: ${duration?.let(::glowPreviewTime) ?: "unknown"}", { durationKnown = !durationKnown })
                    Text("После запуска таймера начни drag и удерживай. При смене трека scrub исчезает, " +
                        "акцент возвращается к Play/Pause, счётчик Seek не увеличивается.",
                        style = NullWaveTheme.typography.caption)
                    NullWaveSectionHeader("Play/Pause / glow, без glow, disabled")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(spacing.lg),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        NullWavePlayPauseButton(
                            isPlaying = playing, onClick = play, modifier = Modifier.focusRequester(focus),
                            buffering = buffering, enabled = enabled, reduceMotion = reduced, glowEnabled = playGlow,
                        )
                        NullWavePlayPauseButton(
                            isPlaying = playing, onClick = play, size = NullWavePlayPauseButtonSize.Compact,
                            buffering = buffering, enabled = enabled, reduceMotion = reduced, glowEnabled = false,
                        )
                        NullWavePlayPauseButton(
                            isPlaying = true, onClick = { plays++ }, size = NullWavePlayPauseButtonSize.Compact,
                            buffering = true, enabled = false, reduceMotion = reduced, glowEnabled = true,
                        )
                    }
                    NullWaveButton("Фокус на Hero в сравнении", {
                        focusResult = if (inputMode.requestInputMode(InputMode.Keyboard) && focus.requestFocus())
                            "Запрос принят" else "Запрос отклонён"
                    })
                    Text(focusResult, style = NullWaveTheme.typography.mono)
                    Text("Glow статичен: Reduce motion останавливает scanner буферизации, а свечение остаётся. " +
                        "Выключение Glow не убирает focus border и тонкий playhead.",
                        style = NullWaveTheme.typography.caption)
                }
            }
        }
    }
}

@Preview(name = "Lines only - ON vs OFF", group = "Player glow", widthDp = 360, heightDp = 680, locale = "ru")
@Composable
private fun PlayerLineGlowComparisonPreview() {
    NullWaveTheme {
        val spacing = NullWaveTheme.spacing
        val peaks = remember { List(96) { 0.15f + abs(sin(it * 0.47f)) * 0.8f } }
        Surface(color = NullWaveTheme.colors.bg, contentColor = NullWaveTheme.colors.textPrimary) {
            Column(
                Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())
                    .padding(vertical = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                Text(
                    "Свечение линий / 50%\nСвечение кнопок отключено в обоих образцах.",
                    modifier = Modifier.padding(horizontal = NullWaveTheme.dimens.gutter),
                    style = NullWaveTheme.typography.caption,
                )
                listOf(true, false).forEach { glow ->
                    Text(
                        "Line glow: ${if (glow) "ON" else "OFF"}",
                        modifier = Modifier.padding(horizontal = NullWaveTheme.dimens.gutter),
                        style = NullWaveTheme.typography.mono,
                    )
                    NullWaveMiniPlayer(
                        title = "Deadzone Chorus",
                        subtitle = "Static Pilgrim",
                        isPlaying = true,
                        onExpand = {},
                        onPlayPause = {},
                        onNext = {},
                        artwork = { NullWaveCoverArt(monogram = "NW") },
                        progress = 0.5f,
                        reduceMotion = true,
                        playPauseGlowEnabled = false,
                        progressGlowEnabled = glow,
                    )
                    NullWaveSeekBar(
                        mediaKey = "line-comparison-$glow",
                        positionMs = 120_500L,
                        durationMs = 241_000L,
                        onSeek = {},
                        formatTime = ::glowPreviewTime,
                        modifier = Modifier.padding(horizontal = NullWaveTheme.dimens.gutter),
                        waveform = peaks,
                        glowEnabled = glow,
                    )
                }
            }
        }
    }
}

@Preview(name = "Seek precision - Rest vs drag", group = "Player glow", widthDp = 360, heightDp = 800, locale = "ru")
@Composable
private fun SeekPrecisionPreview() = SeekPrecisionSheet()

@Preview(name = "Seek precision - RTL", group = "Player glow", widthDp = 360, heightDp = 800, locale = "ar")
@Composable
private fun RtlSeekPrecisionPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        SeekPrecisionSheet()
    }
}

/** Static drawing comparison; scrubbing here does not synthesize gestures or seek commits. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekPrecisionSheet() {
    NullWaveTheme {
        val spacing = NullWaveTheme.spacing
        val peaks = remember { List(96) { 0.15f + abs(sin(it * 0.47f)) * 0.8f } }
        Surface(color = NullWaveTheme.colors.bg, contentColor = NullWaveTheme.colors.textPrimary) {
            Column(
                Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = NullWaveTheme.dimens.gutter, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                NullWaveSectionHeader("Точная позиция / playhead")
                Text(
                    "Статичное сравнение одной позиции в покое и при drag. " +
                        "Центр линии не смещается; glow не обрывается у границы прогресса. " +
                        "Жест и Seek commits проверяются в интерактивном Glow preview.",
                    style = NullWaveTheme.typography.caption,
                )
                listOf(0f, 0.5f, 1f).forEach { fraction ->
                    val state = remember(fraction) { SliderState(value = fraction) }
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text("${(fraction * 100).toInt()}%", style = NullWaveTheme.typography.mono)
                        listOf(false, true).forEach { scrubbing ->
                            Text(if (scrubbing) "Drag" else "Покой", style = NullWaveTheme.typography.caption)
                            Column(Modifier.padding(vertical = spacing.xs)) {
                                SeekTrack(state, enabled = true, waveform = peaks, scrubbing = scrubbing, glowEnabled = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun glowPreviewTime(milliseconds: Long): String {
    val seconds = milliseconds / 1000L
    return "${(seconds / 60L).toString().padStart(2, '0')}:${(seconds % 60L).toString().padStart(2, '0')}"
}
