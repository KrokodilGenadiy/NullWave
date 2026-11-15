package com.zaus.nullwave

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.mvi.ui.collectMviUiWithLifecycle
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.navigation.navigateBack
import com.zaus.nullwave.core.navigation.navigateTo
import com.zaus.nullwave.di.RestorationGraph
import com.zaus.nullwave.di.RestorationIntent
import com.zaus.nullwave.di.RestorationState
import com.zaus.nullwave.di.RestorationVm
import com.zaus.nullwave.feature.search.api.SearchKey
import com.zaus.nullwave.feature.settings.api.SettingsKey
import com.zaus.nullwave.feature.settings.api.R as SettingsR
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.feature.library.api.LibraryKey
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.metroViewModel
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/** Exercises the production host/decorator order with test-only screen content and a generated graph. */
class NavigationViewModelInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun returningToLibraryThroughTheMenuRetainsItsVmAndScrolledList() {
        val graph = createGraph<RestorationGraph>()
        lateinit var root: RestorationVm
        val entries: Set<EntryProviderInstaller> = setOf({ menu ->
            entry<LibraryKey> {
                val vm = metroViewModel<RestorationVm>()
                SideEffect { root = vm }
                Column {
                    menu(null)
                    LazyColumn(Modifier.testTag("items")) {
                        items(100) { Text("Track $it") }
                    }
                }
            }
            entry<SettingsKey> { menu(null) }
        })
        compose.setContent {
            NullWaveTheme {
                CompositionLocalProvider(LocalMetroViewModelFactory provides graph.metroViewModelFactory) {
                    NullWaveNavigation(entries, rememberNullWaveNavigationScope())
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val openMenu = compose.onNodeWithContentDescription(context.getString(R.string.menu_open))
        val vm = compose.runOnIdle { root }
        compose.onNodeWithTag("items").performScrollToIndex(75)
        val before = compose.onNodeWithText("Track 75").fetchSemanticsNode().boundsInRoot.top
        openMenu.performClick()
        compose.onNodeWithText(context.getString(SettingsR.string.settings_title)).performClick()
        compose.onNodeWithTag("items").assertDoesNotExist()
        openMenu.performClick()
        compose.onNodeWithText(context.getString(R.string.menu_library)).performClick()
        assertEquals(before, compose.onNodeWithText("Track 75").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle {
            assertSame(vm, root)
            assertEquals(0, vm.clears)
        }
    }

    @Test
    fun entryOwnsVmAcrossRecompositionAndCoveringButPopClearsIt() {
        val graph = createGraph<RestorationGraph>()
        lateinit var root: RestorationVm
        lateinit var detail: RestorationVm
        lateinit var detailIntents: (RestorationIntent) -> Unit
        val entries: Set<EntryProviderInstaller> = setOf({
            entry<LibraryKey> {
                val vm = metroViewModel<RestorationVm>()
                val ui = vm.collectMviUiWithLifecycle()
                SideEffect { root = vm }
                ProbeScreen("root", ui.state, ui.onIntent, "Open detail") { navigateTo(SearchKey) }
            }
            entry<SearchKey> {
                val vm = metroViewModel<RestorationVm>()
                val ui = vm.collectMviUiWithLifecycle()
                SideEffect { detail = vm; detailIntents = ui.onIntent }
                ProbeScreen("detail", ui.state, ui.onIntent, "Back") { navigateBack(SearchKey) }
            }
        })
        compose.setContent {
            NullWaveTheme {
                CompositionLocalProvider(LocalMetroViewModelFactory provides graph.metroViewModelFactory) {
                    NullWaveNavigation(entries, rememberNullWaveNavigationScope())
                }
            }
        }
        val originalRoot = compose.runOnIdle { root }
        compose.onNodeWithText("Edit root").performClick()
        compose.onNodeWithText("root:edited").assertIsDisplayed()
        compose.runOnIdle {
            assertSame(originalRoot, root)
            assertEquals(1, graph.dependency.created)
        }

        compose.onNodeWithText("Open detail").performClick()
        compose.onNodeWithText("Edit detail").performClick()
        compose.onNodeWithText("detail:edited").assertIsDisplayed()
        val popped = compose.runOnIdle {
            assertNotSame(root, detail)
            assertSame(root.dependency, detail.dependency)
            assertEquals(0, root.clears)
            detailIntents(RestorationIntent.Start)
            detail
        }
        val poppedIntents = compose.runOnIdle { detailIntents }
        compose.runOnIdle { assertEquals(1, popped.workStarts) }

        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("root:edited").assertIsDisplayed()
        compose.runOnIdle {
            assertSame(originalRoot, root)
            assertEquals(0, root.clears)
            assertEquals(1, popped.clears)
            assertEquals(1, popped.workStops)
            val frozen = popped.snapshot.value.state
            poppedIntents(RestorationIntent.Edit("late callback"))
            assertEquals(frozen, popped.snapshot.value.state)
        }

        compose.onNodeWithText("Open detail").performClick()
        compose.onNodeWithText("detail:").assertIsDisplayed()
        compose.runOnIdle {
            assertNotSame(popped, detail)
            assertEquals(0, detail.workStarts)
            assertEquals(3, graph.dependency.created)
        }
    }
}

@Composable
private fun ProbeScreen(
    name: String,
    state: RestorationState,
    onIntent: (RestorationIntent) -> Unit,
    action: String,
    onNavigate: () -> Unit,
) {
    Column {
        Text("$name:${state.query}")
        Button(onClick = { onIntent(RestorationIntent.Edit("edited")) }) { Text("Edit $name") }
        Button(onClick = onNavigate) { Text(action) }
    }
}
