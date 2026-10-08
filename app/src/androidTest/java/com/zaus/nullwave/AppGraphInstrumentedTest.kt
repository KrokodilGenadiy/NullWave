package com.zaus.nullwave

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppGraphInstrumentedTest {
    @Test
    fun applicationStartupCreatesGraphWithApplicationContext() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as NullWaveApplication
        val graph = application.appGraph

        assertSame(graph, application.appGraph)
        assertSame(application, graph.application)
        assertSame(application, graph.applicationContext)
    }
}
