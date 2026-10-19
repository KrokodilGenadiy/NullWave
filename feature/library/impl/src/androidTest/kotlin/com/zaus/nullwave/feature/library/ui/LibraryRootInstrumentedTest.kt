package com.zaus.nullwave.feature.library.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.LibraryQuery
import com.zaus.nullwave.feature.library.api.model.LibraryTrack
import com.zaus.nullwave.feature.library.api.model.TrackId
import com.zaus.nullwave.feature.library.api.repository.LibraryRepository
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.platform.AudioPermission
import com.zaus.nullwave.feature.library.presentation.LibraryViewModel
import java.util.Locale
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real Root/VM/Base plus a fake registry and permission check. Never opens an OS dialog. */
class LibraryRootInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val permission = FakeAudioPermission()
    private val registry = RecordingRegistry()
    private val store = ViewModelStore()
    private lateinit var owner: ScreenOwner
    private lateinit var viewModel: LibraryViewModel
    private var subscriptions = 0
    private val configuration = mutableStateOf(Configuration().apply { setLocale(Locale.ENGLISH) })

    @After
    fun clear() { compose.runOnUiThread { store.clear() } }

    @Test
    fun entryNeverPromptsAutomaticallyAndGrantReadsTracksAfterOneLaunch() {
        show()
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        compose.runOnIdle { assertTrue(registry.requests.isEmpty()) }
        clickAccess()
        clickAccess()
        compose.runOnIdle {
            assertEquals(1, registry.requests.size)
            assertEquals(permission.name, registry.requests.single().input)
            permission.granted = true
            registry.reply(true)
        }
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
    }

    @Test
    fun freshGrantBeforeClickAvoidsAnUnnecessaryPrompt() {
        show()
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        compose.runOnIdle { permission.granted = true }
        clickAccess()
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle { assertTrue(registry.requests.isEmpty()) }
    }

    @Test
    fun callbackFlagCannotOverrideTheFreshAndroidCheck() {
        show()
        clickAccess()
        compose.runOnIdle { registry.reply(true) } // Platform seam still reports denied.
        compose.onNodeWithText(text(R.string.library_open_settings)).assertIsDisplayed()
        compose.onNodeWithText("Low Orbit").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.library_ask_again)).performClick()
        compose.runOnIdle { assertEquals(permission.name, registry.requests.last().input) }
    }

    @Test
    fun rationaleCanBeCancelledAndOnlyConfirmationLaunches() {
        permission.rationale = true
        show()
        clickAccess()
        compose.onNodeWithText(text(R.string.library_cancel)).performClick()
        compose.runOnIdle { assertTrue(registry.requests.isEmpty()) }
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        clickAccess()
        compose.onNodeWithText(text(R.string.library_continue)).performClick()
        compose.runOnIdle { assertEquals(1, registry.requests.size) }
    }

    @Test
    fun deniedResultSuggestsSettingsAndCancelledSettingsResultStillRechecksGrant() {
        show()
        clickAccess()
        compose.runOnIdle { registry.reply(false) }
        compose.onNodeWithText(text(R.string.library_open_settings)).performClick()
        compose.runOnIdle {
            val intent = registry.requests.last().input as Intent
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
            assertEquals("package:${InstrumentationRegistry.getInstrumentation().targetContext.packageName}", intent.dataString)
            owner.registry.currentState = Lifecycle.State.CREATED
            permission.granted = true
            registry.reply(ActivityResult(0, null))
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
    }

    @Test
    fun resultBeforeResumeWaitsForResumeAndRefreshesOnlyOnce() {
        show()
        clickAccess()
        val before = compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            subscriptions
        }
        compose.runOnIdle {
            permission.granted = true
            registry.reply(true)
        }
        compose.runOnIdle {
            assertEquals("A stopped result must not restart the source during its grace period", before, subscriptions)
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle { assertEquals(before + 1, subscriptions) }
    }

    @Test
    fun resumeBeforeSettingsResultRefreshesOnlyOnceButLaterResumeStillChecks() {
        show()
        clickAccess()
        compose.runOnIdle { registry.reply(false) }
        compose.onNodeWithText(text(R.string.library_open_settings)).performClick()
        val before = compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            subscriptions
        }
        compose.runOnIdle {
            permission.granted = true
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle { registry.reply(ActivityResult(0, null)) }
        compose.runOnIdle {
            assertEquals(before + 1, subscriptions)
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals("A separate return must still invalidate the snapshot", before + 2, subscriptions) }
    }

    @Test
    fun lateCallbackWithChangedGrantIsNotSuppressedByResume() {
        show()
        clickAccess()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        val before = compose.runOnIdle { subscriptions }
        compose.runOnIdle {
            permission.granted = true
            registry.reply(true)
        }
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle { assertEquals(before + 1, subscriptions) }
    }

    @Test
    fun returningWithRevokedPermissionRemovesThePreviouslyVisibleTracks() {
        permission.granted = true
        show()
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            permission.granted = false
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        compose.onNodeWithText("Low Orbit").assertDoesNotExist()
        compose.runOnIdle { assertTrue(registry.requests.isEmpty()) }
    }

    @Test
    fun pendingPermissionSurvivesCompositionRestorationWithoutRelaunching() {
        val restoration = show()
        clickAccess()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("library_access_action").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, registry.requests.size)
            permission.granted = true
            registry.reply(true)
        }
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
    }

    @Test
    fun missingSystemActivityShowsRecoverableMessageAndReleasesLaunchGuard() {
        registry.failLaunch = true
        show()
        clickAccess()
        compose.onNodeWithText(text(R.string.library_access_launch_failed)).assertIsDisplayed()
        compose.runOnIdle {
            registry.failLaunch = false
            owner.registry.currentState = Lifecycle.State.STARTED
            permission.granted = true
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            permission.granted = false
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText(text(R.string.library_access_launch_failed)).assertDoesNotExist()
        clickAccess()
        compose.runOnIdle { assertEquals(2, registry.requests.size) }
    }

    @Test
    fun configurationLocaleReachesTheQueryWithoutPersistingAStaleLocale() {
        permission.granted = true
        show()
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle {
            configuration.value = Configuration(configuration.value).apply { setLocale(Locale.forLanguageTag("ru-RU")) }
        }
        compose.runOnIdle { assertEquals("ru-RU", viewModel.state.value.query.localeTag) }
    }

    private fun clickAccess() { compose.onNodeWithTag("library_access_action").performClick() }
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(): StateRestorationTester {
        compose.runOnUiThread {
            owner = ScreenOwner()
            val repository = object : LibraryRepository {
                override fun observeTracks(query: LibraryQuery) = flow<AppResult<List<LibraryTrack>, DataError>> {
                    subscriptions++
                    if (permission.granted) {
                        emit(AppResult.Success(listOf(LibraryTrack(TrackId("test:1"), "Low Orbit", null, null, 203_000))))
                        awaitCancellation()
                    } else emit(AppResult.Failure(DataError.AccessDenied))
                }
            }
            viewModel = LibraryViewModel(repository, SavedStateHandle(), "en")
            store.put("library", viewModel)
            configuration.value = Configuration(InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration)
        }
        val registryOwner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        return StateRestorationTester(compose).also { restoration ->
            restoration.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner,
                    LocalLifecycleOwner provides owner, LocalConfiguration provides configuration.value) {
                    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
                        LibraryRoot({}, viewModel, permission)
                    }
                }
            }
        }
    }
}

private class ScreenOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
}

private class FakeAudioPermission : AudioPermission {
    override val name = "android.permission.READ_MEDIA_AUDIO"
    var granted = false
    var rationale = false
    override fun isGranted() = granted
    override fun shouldExplain() = rationale
}

private class RecordingRegistry : ActivityResultRegistry() {
    data class Request(val code: Int, val input: Any?)
    val requests = mutableListOf<Request>()
    var failLaunch = false
    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        requests += Request(requestCode, input)
        if (failLaunch) throw ActivityNotFoundException("Test system activity unavailable")
    }
    fun reply(value: Any?) { dispatchResult(requests.last().code, value) }
}
