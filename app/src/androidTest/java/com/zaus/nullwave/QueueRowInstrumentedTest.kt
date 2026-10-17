package com.zaus.nullwave

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.components.queue.NullWaveQueueRow
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.zaus.nullwave.core.designsystem.R as DesignSystemR

/** Queue action policy and measurement regressions; run on a device separately from JVM tests. */
class QueueRowInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun unavailableTrackBlocksPlaybackButCanStillBeRemoved() {
        var playCalls = 0
        var removeCalls = 0
        val title = "Unavailable track"
        compose.setContent {
            NullWaveTheme {
                NullWaveQueueRow(
                    title = title,
                    onClick = { playCalls++ },
                    available = false,
                    onRemove = { removeCalls++ },
                    reduceMotion = true,
                )
            }
        }

        val playback = compose.onNodeWithText(title)
        playback.assertIsNotEnabled().performTouchInput { click() }
        invokeClickIfExposed(playback)
        handle(title).assertIsEnabled().performClick()
        compose.onNodeWithText(label(DesignSystemR.string.nw_queue_remove)).performClick()

        compose.runOnIdle {
            assertEquals("Unavailable media must not start playback", 0, playCalls)
            assertEquals("Queue management must remain available", 1, removeCalls)
        }
    }

    @Test
    fun disablingQueueClosesActionsAndBlocksBothTargets() {
        val enabled = mutableStateOf(true)
        var playCalls = 0
        var removeCalls = 0
        val title = "Temporarily locked queue"
        compose.setContent {
            NullWaveTheme {
                NullWaveQueueRow(
                    title = title,
                    onClick = { playCalls++ },
                    enabled = enabled.value,
                    onRemove = { removeCalls++ },
                    reduceMotion = true,
                )
            }
        }

        handle(title).performClick()
        compose.onNodeWithText(label(DesignSystemR.string.nw_queue_remove)).assertExists()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithText(label(DesignSystemR.string.nw_queue_remove)).assertDoesNotExist()

        val playback = compose.onNodeWithText(title)
        val actions = handle(title)
        playback.assertIsNotEnabled().performTouchInput { click() }
        actions.assertIsNotEnabled().performTouchInput { click() }
        // Some Foundation versions retain OnClick semantics even on disabled targets.
        invokeClickIfExposed(playback)
        invokeClickIfExposed(actions)
        val semantics = actions.fetchSemanticsNode().config
        assertTrue(
            "Disabled queue must not expose executable management actions",
            !semantics.contains(SemanticsActions.CustomActions) ||
                semantics[SemanticsActions.CustomActions].isEmpty(),
        )
        compose.onNodeWithText(label(DesignSystemR.string.nw_queue_remove)).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, playCalls)
            assertEquals(0, removeCalls)
        }
    }

    @Test
    fun firstItemOnlyExposesAllowedMoveInMenuAndAccessibility() {
        var moveDownCalls = 0
        val title = "First queued track"
        val moveDown = label(DesignSystemR.string.nw_queue_move_down)
        compose.setContent {
            NullWaveTheme {
                NullWaveQueueRow(
                    title = title,
                    onClick = {},
                    onMoveDown = { moveDownCalls++ },
                    reduceMotion = true,
                )
            }
        }

        val customActions = handle(title).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf(moveDown), customActions.map { it.label })
        compose.runOnUiThread { assertTrue(customActions.single().action()) }
        compose.runOnIdle { assertEquals(1, moveDownCalls) }

        handle(title).performClick()
        compose.onNodeWithText(label(DesignSystemR.string.nw_queue_move_up)).assertDoesNotExist()
        compose.onNodeWithText(label(DesignSystemR.string.nw_queue_remove)).assertDoesNotExist()
        compose.onNodeWithText(moveDown).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, moveDownCalls) }
    }

    @Test
    fun largeTextKeepsLongTagsCompactAndHandleTouchableInNarrowRow() {
        val shortTitle = "Track"
        val shortSubtitle = "Artist"
        val longTitle = "Очень длинное название композиции, которое не должно увеличивать строку очереди"
        val longSubtitle = "Исполнитель с очень длинным именем и дополнительными участниками"
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f),
            ) {
                NullWaveTheme {
                    Column(Modifier.width(280.dp)) {
                        for ((tag, title, subtitle) in listOf(
                            Triple("short", shortTitle, shortSubtitle),
                            Triple("long", longTitle, longSubtitle),
                        )) {
                            NullWaveQueueRow(
                                title = title,
                                onClick = {},
                                modifier = Modifier.testTag(tag),
                                subtitle = subtitle,
                                durationLabel = "3:45",
                                onMoveDown = {},
                                reduceMotion = true,
                            )
                        }
                    }
                }
            }
        }

        handle(longTitle).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        for (text in listOf(longTitle, longSubtitle)) {
            val layout = textLayout("long", text)
            assertEquals("Long metadata must stay on one line", 1, layout.lineCount)
        }
        val shortHeight = compose.onNodeWithTag("short").fetchSemanticsNode().size.height
        val longHeight = compose.onNodeWithTag("long").fetchSemanticsNode().size.height
        assertEquals("Tag length must not increase row height", shortHeight, longHeight)
    }

    private fun handle(title: String) = compose.onNodeWithContentDescription(
        InstrumentationRegistry.getInstrumentation().targetContext.getString(
            DesignSystemR.string.nw_queue_actions, title,
        ),
    )

    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun invokeClickIfExposed(node: SemanticsNodeInteraction) {
        val semantics = node.fetchSemanticsNode().config
        if (semantics.contains(SemanticsActions.OnClick)) {
            compose.runOnUiThread { semantics[SemanticsActions.OnClick].action?.invoke() }
        }
    }

    private fun textLayout(tag: String, text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(results))
            }
        return results.single()
    }
}
