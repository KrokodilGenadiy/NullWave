package com.zaus.nullwave

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.zaus.nullwave.feature.about.api.R as AboutR
import com.zaus.nullwave.feature.library.impl.R as LibraryR

/** Real app wiring: the host must create its Player through AppGraph in the Activity store. */
class PlayerHostOwnershipInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun playerSurvivesNavigationAndActivityRecreation() {
        compose.onNodeWithText(text(LibraryR.string.library_title)).assertIsDisplayed()
        val originalOwner = compose.activity
        val originalPlayer = compose.runOnIdle { existingPlayer(originalOwner) }

        compose.onNodeWithContentDescription(text(R.string.menu_open)).performClick()
        compose.onNodeWithText(text(AboutR.string.about_title)).performClick()
        compose.onNodeWithText(text(AboutR.string.about_title).uppercase()).assertIsDisplayed()
        compose.runOnIdle { assertSame(originalPlayer, existingPlayer(compose.activity)) }

        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(text(AboutR.string.about_title).uppercase()).assertIsDisplayed()
        compose.runOnIdle {
            assertNotSame(originalOwner, compose.activity)
            assertSame(originalPlayer, existingPlayer(compose.activity))
        }

        compose.onNodeWithContentDescription(text(R.string.menu_open)).performClick()
        compose.onNodeWithText(text(R.string.menu_library)).performClick()
        compose.onNodeWithText(text(LibraryR.string.library_title)).assertIsDisplayed()
        compose.runOnIdle { assertSame(originalPlayer, existingPlayer(compose.activity)) }
    }

    @Test
    fun finishingActivityCreatesANewPlayerInTheSameApplicationGraph() {
        compose.onNodeWithText(text(LibraryR.string.library_title)).assertIsDisplayed()
        val originalOwner = compose.activity
        val originalGraph = (originalOwner.application as NullWaveApplication).appGraph
        val originalPlayer = compose.runOnIdle { existingPlayer(originalOwner) }

        compose.activityRule.scenario.close()
        ActivityScenario.launch(MainActivity::class.java).use { replacement ->
            compose.onNodeWithText(text(LibraryR.string.library_title)).assertIsDisplayed()
            replacement.onActivity { owner ->
                assertNotSame(originalOwner, owner)
                assertSame(originalGraph, (owner.application as NullWaveApplication).appGraph)
                assertNotSame(originalPlayer, existingPlayer(owner))
            }
        }
    }

    /** Assert first: asking the factory to create a missing VM would hide a broken host owner. */
    private fun existingPlayer(owner: MainActivity): ViewModel {
        val key = "nullwave.player"
        assertTrue("PlayerHost must already own '$key' in MainActivity", key in owner.viewModelStore.keys())
        val graph = (owner.application as NullWaveApplication).appGraph
        return ViewModelProvider(owner, graph.metroViewModelFactory)[key, ViewModel::class.java]
    }

    private fun text(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
