package com.zaus.nullwave

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.player.NullWavePlayPauseButton
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Theme wiring and rendered decoration regressions; device execution is a separate manual step. */
class EffectsPolicyInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nestedThemesFollowLiveParentRestrictionsAndCannotRelaxThem() {
        val parent = mutableStateOf(NullWaveEffects.Default)
        val restricted = NullWaveEffects(reduceMotion = true, glowEnabled = false)
        val observed = mutableMapOf<String, NullWaveEffects>()
        compose.setContent {
            NullWaveTheme(effects = parent.value) {
                NullWaveTheme { EffectsProbe("defaults", observed) }
                NullWaveTheme(effects = NullWaveEffects(reduceMotion = false, glowEnabled = true)) {
                    EffectsProbe("explicit", observed)
                }
                NullWaveTheme(effects = restricted) {
                    NullWaveTheme(effects = NullWaveEffects.Default) { EffectsProbe("strict", observed) }
                }
            }
        }

        for (policy in listOf(NullWaveEffects.Default, restricted, NullWaveEffects.Default)) {
            compose.runOnIdle { parent.value = policy }
            compose.runOnIdle {
                assertEquals(mapOf("defaults" to policy, "explicit" to policy, "strict" to restricted), observed)
            }
        }
    }

    @Test
    fun liveGlowPolicyChangesRenderedHaloWithoutChangingThePlaybackAction() {
        val parent = mutableStateOf(NullWaveEffects.Default)
        val localGlow = mutableStateOf(true)
        var clicks = 0
        compose.setContent {
            NullWaveTheme(effects = parent.value) {
                // An explicitly permissive nested theme must not undo the host's choice.
                NullWaveTheme(effects = NullWaveEffects.Default) {
                    HaloFixture {
                        NullWavePlayPauseButton(false, { clicks++ }, Modifier.testTag("play"),
                            glowEnabled = localGlow.value)
                    }
                }
            }
        }

        val action = compose.onNodeWithTag("play")
        val before = action.fetchSemanticsNode()
        val lit = haloBrightness()
        action.assertIsEnabled().assertHasClickAction().performClick()

        compose.runOnIdle { parent.value = NullWaveEffects(glowEnabled = false) }
        val unlit = haloBrightness()
        assertTrue("Global OFF must remove the rendered halo even with local ON", lit > unlit + 0.01f)
        val after = action.assertIsEnabled().assertHasClickAction().fetchSemanticsNode()
        assertEquals(before.boundsInRoot, after.boundsInRoot)
        assertEquals(before.config[SemanticsProperties.Role], after.config[SemanticsProperties.Role])
        assertEquals(before.config[SemanticsProperties.ContentDescription], after.config[SemanticsProperties.ContentDescription])
        action.performClick()

        compose.runOnIdle {
            parent.value = NullWaveEffects.Default
            localGlow.value = false
        }
        assertEquals("Local OFF must remain effective under permissive global policy", unlit, haloBrightness(), 0.002f)
        action.performClick()
        compose.runOnIdle {
            assertEquals("Changing decoration must not invoke or block playback", 3, clicks)
            localGlow.value = true
        }
        assertTrue("Re-enabling effects must update the existing composition", haloBrightness() > unlit + 0.01f)
    }

    private fun fixtureImage() = compose.onNodeWithTag("halo-fixture").captureToImage()

    private fun haloBrightness(): Float {
        val pixels = fixtureImage().toPixelMap()
        var sum = 0f
        var count = 0
        // The 76 dp button starts at y=42 dp in this 160 dp fixture. Sample 37–40 dp:
        // outside its top edge/border, but close enough to measure the restrained hero halo.
        for (y in (pixels.height * (37f / 160f)).toInt()..(pixels.height * (40f / 160f)).toInt()) {
            for (x in (pixels.width * 0.4f).toInt()..(pixels.width * 0.6f).toInt()) {
                val color = pixels[x, y]
                sum += (color.red + color.green + color.blue) / 3f
                count++
            }
        }
        return sum / count
    }
}

@Composable
private fun EffectsProbe(name: String, observed: MutableMap<String, NullWaveEffects>) {
    val effects = NullWaveTheme.effects
    SideEffect { observed[name] = effects }
}

@Composable
private fun HaloFixture(content: @Composable () -> Unit) {
    Box(Modifier.size(160.dp).background(NullWaveTheme.colors.bg).testTag("halo-fixture"),
        contentAlignment = Alignment.Center) { content() }
}
