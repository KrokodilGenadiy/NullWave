package com.zaus.nullwave

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.components.player.NullWaveRepeatMode
import com.zaus.nullwave.core.designsystem.components.player.NullWaveTransportControls
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import com.zaus.nullwave.core.designsystem.R as DesignR

/** Independent mode availability must hold in both the inline and wrapped transport layouts. */
@RunWith(Parameterized::class)
class TransportModesInstrumentedTest(private val widthDp: Int) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun independentAvailabilityKeepsModeStateAndBlocksDisabledActions() {
        val enabled = mutableStateOf(true)
        val canShuffle = mutableStateOf(true)
        val canRepeat = mutableStateOf(true)
        val shuffleChanges = mutableListOf<Boolean>()
        var repeatClicks = 0
        compose.setContent {
            NullWaveTheme(effects = NullWaveEffects(reduceMotion = true, glowEnabled = false)) {
                Box(Modifier.width(widthDp.dp)) {
                    NullWaveTransportControls(
                        isPlaying = false, onPlayPause = {}, onPrevious = {}, onNext = {},
                        enabled = enabled.value,
                        shuffle = true, onShuffleChange = shuffleChanges::add,
                        repeatMode = NullWaveRepeatMode.One, onRepeatClick = { repeatClicks++ },
                        canShuffle = canShuffle.value, canRepeat = canRepeat.value,
                    )
                }
            }
        }

        val shuffle = compose.onNodeWithContentDescription(label(DesignR.string.nw_player_shuffle))
        val repeat = compose.onNodeWithContentDescription(label(DesignR.string.nw_player_repeat))
        val play = compose.onNodeWithContentDescription(label(DesignR.string.nw_player_play))
        val expectedChanges = mutableListOf<Boolean>()
        var expectedRepeats = 0
        for ((shuffleAllowed, repeatAllowed) in listOf(true to false, false to true, false to false, true to true)) {
            compose.runOnIdle {
                canShuffle.value = shuffleAllowed
                canRepeat.value = repeatAllowed
            }
            assertModes(shuffle, repeat)
            play.assertIsEnabled()
            if (shuffleAllowed) {
                shuffle.assertIsEnabled().performClick()
                expectedChanges += false
            } else rejectClick(shuffle)
            if (repeatAllowed) {
                repeat.assertIsEnabled().performClick()
                expectedRepeats++
            } else rejectClick(repeat)
            compose.runOnIdle {
                assertEquals(expectedChanges, shuffleChanges)
                assertEquals(expectedRepeats, repeatClicks)
            }
        }

        // The shared gate wins even when each individual mode capability remains available.
        compose.runOnIdle { enabled.value = false }
        assertModes(shuffle, repeat)
        play.assertIsNotEnabled()
        rejectClick(shuffle)
        rejectClick(repeat)
        compose.runOnIdle {
            assertEquals(expectedChanges, shuffleChanges)
            assertEquals(expectedRepeats, repeatClicks)
        }
    }

    private fun assertModes(shuffle: SemanticsNodeInteraction, repeat: SemanticsNodeInteraction) {
        shuffle.assertIsDisplayed().assertIsOn().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        repeat.assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label(DesignR.string.nw_player_repeat_one)))
    }

    private fun rejectClick(node: SemanticsNodeInteraction) {
        node.assertIsNotEnabled().performTouchInput { click() }
        // Some Foundation versions expose OnClick even when disabled; direct calls must also be gated.
        val semantics = node.fetchSemanticsNode().config
        if (semantics.contains(SemanticsActions.OnClick)) {
            compose.runOnUiThread { semantics[SemanticsActions.OnClick].action?.invoke() }
        }
    }

    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "width={0}dp")
        fun widths(): List<Array<Int>> = listOf(arrayOf(240), arrayOf(360))
    }
}
