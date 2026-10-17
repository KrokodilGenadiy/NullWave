package com.zaus.nullwave.feature.lyrics.impl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.layout.NullWaveScaffold
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTopBar
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

@Preview(name = "Lyrics - Yellow", group = "Lyrics", widthDp = 360, heightDp = 900, locale = "ru")
@Composable
private fun YellowLyricsPreview() = LyricsSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Lyrics - Crimson", group = "Lyrics", widthDp = 360, heightDp = 900, locale = "ru")
@Composable
private fun CrimsonLyricsPreview() = LyricsSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Lyrics - Dive", group = "Lyrics", widthDp = 412, heightDp = 900, locale = "en")
@Composable
private fun DiveLyricsPreview() = LyricsSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Lyrics - Large text", group = "Lyrics", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LargeLyricsPreview() = LyricsSheet(NullWaveColorVariant.Cyberpunk, longLineFirst = true)

@Preview(name = "Lyrics - RTL", group = "Lyrics", widthDp = 360, heightDp = 900, locale = "ar")
@Composable
private fun RtlLyricsPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        LyricsSheet(NullWaveColorVariant.Cyberpunk, arabicFixture = true)
    }
}

/** All lines below are original preview fixtures, not an imported song or parsed lyrics. */
@Composable
private fun LyricsSheet(variant: NullWaveColorVariant, arabicFixture: Boolean = false, longLineFirst: Boolean = false) {
    NullWaveTheme(variant = variant) {
        val lines = if (arabicFixture) ArabicLyricsFixture else LyricsFixture
        val firstCurrent = if (longLineFirst) 3 else 2
        var current by remember { mutableIntStateOf(firstCurrent) }
        var synced by remember { mutableStateOf(true) }
        var acceptCurrent by remember { mutableStateOf(true) }
        var followEnabled by remember { mutableStateOf(true) }
        var glowEnabled by remember { mutableStateOf(true) }
        var currentRequests by remember { mutableIntStateOf(0) }
        var follows by remember { mutableIntStateOf(0) }
        var externalChanges by remember { mutableIntStateOf(0) }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (longLineFirst) firstCurrent else 0)
        val scope = rememberCoroutineScope()
        val spacing = NullWaveTheme.spacing
        val gutter = NullWaveTheme.dimens.gutter
        val changeCurrent: (Int) -> Unit = {
            currentRequests++
            if (acceptCurrent) current = it
        }

        NullWaveScaffold(
            topBar = { NullWaveTopBar(if (synced) "Lyrics · synced" else "Lyrics · unsynced") },
            bottomBar = {
                // Bounded, independently scrollable controls leave room for the lyric list at 2x text.
                Column(
                    Modifier.fillMaxWidth().background(NullWaveTheme.colors.surface)
                        .heightIn(max = LyricsControlsMaxHeight).verticalScroll(rememberScrollState()).padding(gutter),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    if (synced) LyricsFollowButton(
                        onClick = {
                            follows++
                            val target = current
                            scope.launch { listState.scrollToItem(target) }
                        },
                        enabled = followEnabled,
                    )
                    Text("Current: ${if (synced) "${current + 1}/${lines.size}" else "—"} · Requests: $currentRequests\n" +
                        "Follow: $follows · External: $externalChanges", style = NullWaveTheme.typography.mono)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        NullWaveButton("Предыдущая", { changeCurrent(current - 1) }, enabled = synced && current > 0)
                        NullWaveButton("Следующая", { changeCurrent(current + 1) }, enabled = synced && current < lines.lastIndex)
                        NullWaveButton("Synced: $synced", { synced = !synced })
                        NullWaveButton("Glow: $glowEnabled", { glowEnabled = !glowEnabled })
                        NullWaveButton("Принимать current: $acceptCurrent", { acceptCurrent = !acceptCurrent })
                        NullWaveButton("Follow enabled: $followEnabled", { followEnabled = !followEnabled })
                        NullWaveButton("Сменить current извне", {
                            current = (current + 5) % lines.size
                            externalChanges++
                        })
                    }
                    Text("Текст можно прокручивать вручную. Только Follow возвращает к текущей строке; переход мгновенный. " +
                        "Смена current не прокручивает список. При отказе растёт Requests, но акцент остаётся. " +
                        "Unsynced не содержит текущей строки и скрывает Follow. Здесь нет часов, seek или playback.",
                        style = NullWaveTheme.typography.caption)
                }
            },
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                // The fixed fixture never reorders: the stable position is its identity.
                itemsIndexed(lines, key = { index, _ -> "lyric-fixture-$index" }) { index, text ->
                    LyricsLine(
                        text = text,
                        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
                        emphasis = when {
                            !synced -> LyricsLineEmphasis.Neighbor
                            index == current -> LyricsLineEmphasis.Current
                            abs(index - current) == 1 -> LyricsLineEmphasis.Neighbor
                            else -> LyricsLineEmphasis.Distant
                        },
                        glowEnabled = glowEnabled,
                    )
                }
            }
        }
    }
}

private val LyricsControlsMaxHeight = 240.dp

private val LyricsFixture = listOf(
    "На крыше гудит пустой маяк.",
    "Между антеннами прячется утро.",
    "Я собираю свет по проводам.",
    "Мы оставили на запотевшем стекле длинное сообщение о том, как тихий город учится встречать рассвет без единого сигнала.",
    "A paper satellite drifts above the sleeping avenue, carrying our small unfinished map beyond the last illuminated window.",
    "Синий трамвай считает мосты.",
    "На каждой остановке слышен дождь.",
    "No clocks are listening to this street.",
    "The morning folds its silver coat.",
    "И мы находим новую дорогу.",
    "فوق السطح ضوء صغير ينتظر الصباح",
    "Я собираю свет по проводам.",
)

private val ArabicLyricsFixture = listOf(
    "فوق السطح ضوء صغير",
    "والشارع ينتظر الصباح",
    "نجمع الضوء بين النوافذ",
    "نكتب على زجاج النافذة رسالة طويلة عن مدينة هادئة تتعلم استقبال الصباح من دون أن توقظ البيوت النائمة",
    "تسير الغيمة ببطء فوق الجسر",
    "ثم تفتح الريح باب النهار",
    "A small paper lantern follows the river beyond the quiet bridge and returns with a different shade of morning.",
    "Это длинная кириллическая строка внутри RTL-компоновки: направление самого текста должно оставаться естественным.",
    "نترك عند الباب خريطة صغيرة",
    "ويحمل الطريق أسماء جديدة",
    "يصل الضوء إلى آخر نافذة",
    "نجمع الضوء بين النوافذ",
)
