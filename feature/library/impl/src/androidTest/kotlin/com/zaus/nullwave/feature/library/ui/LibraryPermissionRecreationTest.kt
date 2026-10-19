package com.zaus.nullwave.feature.library.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.LibraryQuery
import com.zaus.nullwave.feature.library.api.model.LibrarySort
import com.zaus.nullwave.feature.library.api.model.LibraryTrack
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.TrackId
import com.zaus.nullwave.feature.library.api.repository.LibraryRepository
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.platform.AudioPermission
import com.zaus.nullwave.feature.library.presentation.LibraryIntent
import com.zaus.nullwave.feature.library.presentation.LibraryViewModel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Real Activity/Compose/SavedStateRegistry recreation; only the external OS operation is faked. */
@RunWith(Parameterized::class)
class LibraryPermissionRecreationTest(private val settings: Boolean, private val grant: Boolean) {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: PermissionActivityFixture
    private var scenario: ActivityScenario<LibraryPermissionTestActivity>? = null

    @After
    fun close() {
        scenario?.close()
        LibraryPermissionTestActivity.fixture = null
    }

    @Test
    fun pendingRequestSurvivesActivityRecreationAndAcceptsRawResult() {
        val activity = launchRequest()
        lateinit var oldActivity: LibraryPermissionTestActivity
        lateinit var oldRegistry: ActivityResultRegistry
        lateinit var oldVm: LibraryViewModel
        activity.onActivity {
            oldActivity = it
            oldRegistry = it.results
            oldVm = it.model
            it.model.onIntent(LibraryIntent.SortChanged(LibrarySort.Artist, SortDirection.Descending))
        }
        activity.recreate()
        compose.onNodeWithTag("library_access_action").assertIsNotEnabled()
        activity.onActivity {
            assertNotSame(oldActivity, it)
            assertNotSame(oldRegistry, it.results)
            assertSame("Activity recreation retains its ViewModelStore", oldVm, it.model)
            assertEquals(LibrarySort.Artist, it.model.state.value.query.sort)
            assertEquals(SortDirection.Descending, it.model.state.value.query.direction)
            assertEquals(expectedLaunches, fixture.requests.size)
            fixture.granted = grant
            fixture.deliverTo(it.results)
        }
        assertRecovered()
    }

    @Test
    fun rawResultBufferedWithoutCompositionSurvivesRegistryBundleRestoration() {
        val activity = launchRequest()
        // Simulate an entry leaving composition: preserve its saveable state, unregister launchers.
        activity.onActivity { it.showRoot = false }
        compose.waitForIdle()
        activity.onActivity {
            fixture.granted = grant
            fixture.deliverTo(it.results) // Raw result now lives in the old registry's pending Bundle.
        }
        activity.recreate() // New registry, restored Bundle, then Root registers and consumes the result.
        activity.onActivity { assertEquals(expectedLaunches, fixture.requests.size) }
        assertRecovered()
    }

    private val expectedLaunches get() = if (settings) 2 else 1

    private fun launchRequest(): ActivityScenario<LibraryPermissionTestActivity> {
        fixture = PermissionActivityFixture()
        LibraryPermissionTestActivity.fixture = fixture
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activity = ActivityScenario.launch<LibraryPermissionTestActivity>(
            Intent(context, LibraryPermissionTestActivity::class.java),
        ).also { scenario = it }
        compose.onNodeWithTag("library_access_action").performClick()
        if (settings) {
            activity.onActivity { fixture.deliverTo(it.results) } // Denial -> settings suggestion.
            compose.onNodeWithText(context.getString(R.string.library_open_settings)).performClick()
        }
        compose.onNodeWithTag("library_access_action").assertIsNotEnabled()
        activity.onActivity { assertEquals(expectedLaunches, fixture.requests.size) }
        return activity
    }

    private fun assertRecovered() {
        if (grant) {
            compose.onNodeWithText("Restored orbit").assertIsDisplayed()
            compose.onNodeWithTag("library_access").assertDoesNotExist()
        } else {
            compose.onNodeWithTag("library_access_action").assertIsEnabled()
            compose.onNodeWithText("Restored orbit").assertDoesNotExist()
            // A completed denial/settings visit releases the guard for a new explicit request.
            compose.onNodeWithTag("library_access_action").performClick()
            compose.runOnIdle { assertEquals(expectedLaunches + 1, fixture.requests.size) }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "settings={0}, grant={1}")
        fun cases() = listOf(arrayOf(false, false), arrayOf(false, true), arrayOf(true, false), arrayOf(true, true))
    }
}

/** Declared only in the test APK. No production Activity/graph changes or real permission dialogs. */
class LibraryPermissionTestActivity : ComponentActivity() {
    internal lateinit var results: ActivityResultRegistry
    internal lateinit var model: LibraryViewModel
    internal var showRoot by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val testFixture = checkNotNull(fixture)
        results = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                testFixture.requests += PermissionActivityFixture.Request(requestCode, input)
            }
        }
        // Force serialization as well: no accidental dependence on objects in the old Bundle.
        results.onRestoreInstanceState(savedStateRegistry.consumeRestoredStateForKey(RESULTS_KEY)?.parcelCopy())
        savedStateRegistry.registerSavedStateProvider(RESULTS_KEY) {
            Bundle().also(results::onSaveInstanceState)
        }
        model = ViewModelProvider(this, viewModelFactory {
            initializer { LibraryViewModel(testFixture.repository, createSavedStateHandle(), "en") }
        })[LibraryViewModel::class.java]
        val registryOwner = object : ActivityResultRegistryOwner { override val activityResultRegistry = results }
        setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
                    val holder = rememberSaveableStateHolder()
                    if (showRoot) holder.SaveableStateProvider("library") {
                        LibraryRoot({}, model, testFixture)
                    }
                }
            }
        }
    }

    companion object {
        internal var fixture: PermissionActivityFixture? = null
        private const val RESULTS_KEY = "test.library.activity.results"
    }
}

private fun Bundle.parcelCopy(): Bundle {
    val parcel = Parcel.obtain()
    return try {
        parcel.writeBundle(this)
        parcel.setDataPosition(0)
        checkNotNull(parcel.readBundle(LibraryPermissionTestActivity::class.java.classLoader))
    } finally {
        parcel.recycle()
    }
}

internal class PermissionActivityFixture : AudioPermission {
    data class Request(val code: Int, val input: Any?)
    val requests = mutableListOf<Request>()
    var granted = false
    override val name = "android.permission.READ_MEDIA_AUDIO"
    override fun isGranted() = granted
    override fun shouldExplain() = false
    val repository = object : LibraryRepository {
        override fun observeTracks(query: LibraryQuery) = flow<AppResult<List<LibraryTrack>, DataError>> {
            if (granted) {
                emit(AppResult.Success(listOf(LibraryTrack(TrackId("test:restored"), "Restored orbit", null, null, null))))
                awaitCancellation()
            } else emit(AppResult.Failure(DataError.AccessDenied))
        }
    }

    fun deliverTo(registry: ActivityResultRegistry) {
        val request = requests.last()
        val data = if (request.input is String) Intent()
            .putExtra(ActivityResultContracts.RequestMultiplePermissions.EXTRA_PERMISSIONS, arrayOf(name))
            .putExtra(ActivityResultContracts.RequestMultiplePermissions.EXTRA_PERMISSION_GRANT_RESULTS,
                intArrayOf(if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED))
        else null
        // Settings always returns CANCELED here, even when Android now grants access.
        assertTrue("Restored registry must recognize the original request code",
            registry.dispatchResult(request.code, if (request.input is String) Activity.RESULT_OK else Activity.RESULT_CANCELED, data))
    }
}
