package com.zaus.nullwave.feature.player.ui

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.feature.player.impl.R
import com.zaus.nullwave.feature.player.presentation.PlayerViewModel
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real Activity, navigation entry owners and Metro host; never connects to a playback service. */
class PlayerHostInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val fixture = PlayerHostActivityFixture()
    private var scenario: ActivityScenario<PlayerHostTestActivity>? = null

    @After
    fun close() {
        try {
            scenario?.close()
        } finally {
            PlayerHostTestActivity.fixture = null
        }
    }

    @Test
    fun navigationKeepsActivityPlayerAndDismissalOutsideEntryOwners() {
        val activity = launch()
        awaitSubscriptions(1)
        compose.onNodeWithTag("player_notice").assertIsDisplayed()
        val original = model(activity)
        dismissNotice()

        compose.onNodeWithTag("host_open_search").performClick()
        compose.onNodeWithTag("host_search").assertIsDisplayed()
        compose.onNodeWithTag("player_notice").assertDoesNotExist()
        activity.onActivity {
            assertSame(original, it.playerViewModel())
            assertNotNull(original.snapshot.value.state.dismissedProblem)
            val libraryOwner = fixture.entryOwners.getValue(HostDestination.Library)
            val searchOwner = fixture.entryOwners.getValue(HostDestination.Search)
            assertNotSame(libraryOwner.viewModelStore, searchOwner.viewModelStore)
            fixture.entryOwners.values.forEach { owner ->
                assertNotSame(it.viewModelStore, owner.viewModelStore)
                assertTrue(PlayerViewModelKey !in owner.viewModelStore.keys())
            }
        }
        compose.onNodeWithTag("host_back_library").performClick()
        compose.onNodeWithTag("host_library").assertIsDisplayed()
        compose.onNodeWithTag("player_notice").assertDoesNotExist()
        activity.onActivity {
            assertSame(original, it.playerViewModel())
            assertEquals(1, fixture.playback.stateStarts.get())
            assertEquals(1, fixture.playback.progressStarts.get())
            assertNoCommands()
        }
    }

    @Test
    fun recreationRetainsPlayerButFinishingOwnerClearsItAndNextActivityStartsFresh() {
        val activity = launch()
        awaitSubscriptions(1)
        val original = model(activity)
        val clears = AtomicInteger()
        lateinit var firstActivity: PlayerHostTestActivity
        activity.onActivity {
            firstActivity = it
            original.addCloseable { clears.incrementAndGet() }
        }
        dismissNotice()

        activity.recreate()
        awaitSubscriptions(1)
        compose.onNodeWithTag("host_library").assertIsDisplayed()
        compose.onNodeWithTag("player_notice").assertDoesNotExist()
        activity.onActivity {
            assertNotSame(firstActivity, it)
            assertSame(original, it.playerViewModel())
            assertNotNull(original.snapshot.value.state.dismissedProblem)
            assertEquals(0, clears.get())
            assertNoCommands()
        }

        activity.close()
        scenario = null
        awaitSubscriptions(0)
        assertEquals(1, clears.get())
        val replacement = launch()
        awaitSubscriptions(1)
        compose.onNodeWithTag("player_notice").assertIsDisplayed()
        replacement.onActivity {
            assertNotSame(original, it.playerViewModel())
            assertNull(it.playerViewModel().snapshot.value.state.dismissedProblem)
            assertNoCommands()
        }
    }

    @Test
    fun startedObservesWhileStopAndCompositionRemovalImmediatelyReleaseBothLeases() {
        val activity = launch()
        awaitSubscriptions(1)
        val original = model(activity)
        activity.moveToState(Lifecycle.State.STARTED)
        awaitSubscriptions(1)
        activity.onActivity {
            assertEquals(Lifecycle.State.STARTED, it.lifecycle.currentState)
            assertEquals(1, fixture.playback.stateStarts.get())
            assertEquals(1, fixture.playback.progressStarts.get())
        }

        activity.moveToState(Lifecycle.State.CREATED)
        awaitSubscriptions(0)
        activity.moveToState(Lifecycle.State.STARTED)
        awaitSubscriptions(1)
        activity.onActivity {
            assertSame(original, it.playerViewModel())
            assertEquals(2, fixture.playback.stateStarts.get())
            assertEquals(2, fixture.playback.progressStarts.get())
            fixture.hostVisible.value = false
        }
        awaitSubscriptions(0)
        activity.onActivity { fixture.hostVisible.value = true }
        awaitSubscriptions(1)
        activity.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("player_notice").assertIsDisplayed()
        activity.onActivity {
            assertSame(original, it.playerViewModel())
            assertEquals(3, fixture.playback.stateStarts.get())
            assertEquals(3, fixture.playback.progressStarts.get())
            assertNoCommands()
        }
    }

    @Test
    fun globalNoticePrioritizesCommandFailureAndAccessActionOnlyRequestsLibraryNavigation() {
        val problem = fixture.playback.connection.value as PlaybackConnection.Connected
        fixture.playback.commandReply = AppResult.Failure(PlaybackCommandError.CommandUnavailable)
        val activity = launch()
        awaitSubscriptions(1)
        compose.onNodeWithText(text(R.string.player_decoder_failed)).assertIsDisplayed()
        activity.onActivity { assertNoCommands() }
        compose.onNodeWithText(text(R.string.player_retry)).performClick()
        compose.onNodeWithText(text(R.string.player_command_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.player_decoder_failed)).assertDoesNotExist()
        activity.onActivity {
            assertEquals(
                listOf(PlaybackCommand.Retry(problem.snapshot.queue.current!!, problem.snapshot.transport.problem!!.id)),
                fixture.playback.commands,
            )
        }
        dismissNotice()
        compose.onNodeWithText(text(R.string.player_decoder_failed)).assertIsDisplayed()

        activity.onActivity {
            fixture.playback.connection.value = hostProblem(PlaybackProblemCode.AccessDenied, "access-problem")
        }
        compose.onNodeWithText(text(R.string.player_access_denied)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.player_open_library)).performClick()
        activity.onActivity {
            assertEquals(1, fixture.libraryRequests)
            assertEquals(1, fixture.playback.commands.size)
            assertEquals(0, fixture.playback.reconnects)
            fixture.playback.connection.value = PlaybackConnection.Unavailable(PlaybackConnectionError.ConnectionFailed)
        }
        compose.onNodeWithText(text(R.string.player_reconnect)).performClick()
        compose.onNodeWithContentDescription(text(R.string.player_dismiss_notice)).assertDoesNotExist()
        activity.onActivity {
            assertEquals(1, fixture.playback.reconnects)
            assertEquals(1, fixture.playback.commands.size)
        }
    }

    private fun launch(): ActivityScenario<PlayerHostTestActivity> {
        PlayerHostTestActivity.fixture = fixture
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return ActivityScenario.launch<PlayerHostTestActivity>(Intent(context, PlayerHostTestActivity::class.java))
            .also { scenario = it }
    }

    private fun model(activity: ActivityScenario<PlayerHostTestActivity>): PlayerViewModel {
        lateinit var model: PlayerViewModel
        activity.onActivity { model = it.playerViewModel() }
        return model
    }

    private fun awaitSubscriptions(expected: Int) {
        compose.waitUntil(timeoutMillis = 5_000) {
            fixture.playback.activeStates.get() == expected && fixture.playback.activeProgress.get() == expected
        }
    }

    private fun dismissNotice() {
        compose.onNodeWithContentDescription(text(R.string.player_dismiss_notice)).performClick()
    }

    private fun text(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /** Called on Main; the fake has no transport callbacks outside this thread. */
    private fun assertNoCommands() {
        assertTrue(fixture.playback.commands.isEmpty())
        assertEquals(0, fixture.playback.reconnects)
    }
}
