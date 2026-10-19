package com.zaus.nullwave.core.mvi.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.mvi.test.R as TestR
import com.zaus.nullwave.core.result.DataError
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Resource resolution and locale invalidation need real Android resources, not JVM stubs. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class UiTextInstrumentedTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun contextResolvesBothLocalesAndTypedFormattingArguments() {
        val fixtureContext = InstrumentationRegistry.getInstrumentation().context
        val value = formattedText()

        assertEquals("Tracks · 3 · 1.5", value.asString(fixtureContext.inLocale(Locale.ENGLISH)))
        assertEquals("Треки · 3 · 1,5", value.asString(fixtureContext.inLocale(Locale.forLanguageTag("ru"))))
        assertEquals("100%", UiText.Resource(TestR.string.mvi_test_text_percent).asString(fixtureContext))
        assertEquals("Value: 100%", wrappedPercent().asString(fixtureContext.inLocale(Locale.ENGLISH)))

        val target = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(
            "There is not enough free storage.",
            DataError.StorageFull.toUiText().asString(target.inLocale(Locale.ENGLISH)),
        )
        assertEquals(
            "Недостаточно свободного места.",
            DataError.StorageFull.toUiText().asString(target.inLocale(Locale.forLanguageTag("ru"))),
        )
    }

    @Test
    fun composeReresolvesTheSameTextValueAfterLocaleChange() = runTest(dispatcher) {
        val fixtureContext = InstrumentationRegistry.getInstrumentation().context
        val currentContext = mutableStateOf(fixtureContext.inLocale(Locale.ENGLISH))
        val value = formattedText()
        var resolved = ""
        var percent = ""
        val frames = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + frames)
        val composition = Composition(EmptyApplier(), recomposer)
        val runner = backgroundScope.launch(frames) { recomposer.runRecomposeAndApplyChanges() }

        try {
            composition.setContent {
                val context = currentContext.value
                CompositionLocalProvider(
                    LocalContext provides context,
                    LocalConfiguration provides context.resources.configuration,
                ) {
                    resolved = value.asString()
                    percent = wrappedPercent().asString()
                }
            }
            runCurrent()
            assertEquals("Tracks · 3 · 1.5", resolved)
            assertEquals("Value: 100%", percent)

            currentContext.value = fixtureContext.inLocale(Locale.forLanguageTag("ru"))
            Snapshot.sendApplyNotifications()
            runCurrent()
            frames.sendFrame(1L)
            runCurrent()

            assertEquals("Треки · 3 · 1,5", resolved)
            assertEquals("Значение: 100%", percent)
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.cancel()
        }
    }

    private fun formattedText() = UiText.Resource(
        TestR.string.mvi_test_text_format,
        UiText.Argument.Text(UiText.Resource(TestR.string.mvi_test_text_nested)),
        UiText.Argument.Count(3),
        UiText.Argument.Decimal(1.5),
    )

    private fun wrappedPercent() = UiText.Resource(
        TestR.string.mvi_test_text_wrapped,
        UiText.Argument.Text(UiText.Resource(TestR.string.mvi_test_text_percent)),
    )

    private fun Context.inLocale(locale: Locale): Context = createConfigurationContext(
        Configuration(resources.configuration).apply { setLocale(locale) },
    )

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun onClear() = Unit
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
    }
}
