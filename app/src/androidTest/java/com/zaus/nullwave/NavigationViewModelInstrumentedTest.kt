package com.zaus.nullwave

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.mvi.ui.collectMviStateWithLifecycle
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.navigation.navigateBack
import com.zaus.nullwave.core.navigation.navigateTo
import com.zaus.nullwave.di.RestorationGraph
import com.zaus.nullwave.di.RestorationIntent
import com.zaus.nullwave.di.RestorationVm
import com.zaus.nullwave.feature.about.api.AboutKey
import com.zaus.nullwave.feature.library.api.LibraryKey
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.metroViewModel
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
    fun entryOwnsVmAcrossRecompositionAndCoveringButPopClearsIt() {
        val graph = createGraph<RestorationGraph>()
        lateinit var root: RestorationVm
        lateinit var detail: RestorationVm
        val entries: Set<EntryProviderInstaller> = setOf({
            entry<LibraryKey> {
                val vm = metroViewModel<RestorationVm>()
                SideEffect { root = vm }
                ProbeScreen("root", vm, "Open detail") { navigateTo(AboutKey) }
            }
            entry<AboutKey> {
                val vm = metroViewModel<RestorationVm>()
                SideEffect { detail = vm }
                ProbeScreen("detail", vm, "Back") { navigateBack() }
            }
        })
        compose.setContent {
            NullWaveTheme { NullWaveNavigation(entries, graph.metroViewModelFactory) }
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
            detail.onIntent(RestorationIntent.Start)
            detail
        }
        compose.runOnIdle { assertEquals(1, popped.workStarts) }

        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("root:edited").assertIsDisplayed()
        compose.runOnIdle {
            assertSame(originalRoot, root)
            assertEquals(0, root.clears)
            assertEquals(1, popped.clears)
            assertEquals(1, popped.workStops)
            val frozen = popped.state.value
            popped.onIntent(RestorationIntent.Edit("late callback"))
            assertEquals(frozen, popped.state.value)
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
private fun ProbeScreen(name: String, vm: RestorationVm, action: String, onNavigate: () -> Unit) {
    val state by vm.collectMviStateWithLifecycle()
    Column {
        Text("$name:${state.query}")
        Button(onClick = { vm.onIntent(RestorationIntent.Edit("edited")) }) { Text("Edit $name") }
        Button(onClick = onNavigate) { Text(action) }
    }
}
