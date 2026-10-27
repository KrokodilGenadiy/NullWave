package com.zaus.nullwave.feature.library.ui

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContextWrapper
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ColorImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.ImageLoader
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import coil3.size.Size
import coil3.Uri
import com.zaus.nullwave.core.designsystem.theme.NullWaveColors
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.media.model.ArtworkRef
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.artwork.artworkRequest
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.artwork.mediaStoreUri
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real Coil and Compose with isolated fetchers/provider: no device media or permissions required. */
class LibraryArtworkInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val track = MediaTrack(TrackId("test:1"), "Low Orbit", "Nullwave", "Transmission", 203_000)

    @Test
    fun volumeAndRevisionSelectTheRightThumbnailAndReuseMemoryCache() = runBlocking {
        val reference = ArtworkRef("external_primary:5000000001", "v1")
        assertEquals("content://media/external_primary/audio/albums/5000000001", reference.mediaStoreUri().toString())
        assertNotEquals(reference.mediaStoreUri(), reference.copy(key = "card:5000000001").mediaStoreUri())
        assertNull(ArtworkRef("unknown", "v1").mediaStoreUri())
        val reads = AtomicInteger()
        val loader = loader { data ->
            assertNotSame(Looper.getMainLooper(), Looper.myLooper())
            assertEquals(reference.mediaStoreUri().toString(), data.toString())
            reads.incrementAndGet()
            ImageFetchResult(ColorImage(Color.Red.toArgb(), 64, 64, 64 * 64 * 4L), false, DataSource.DISK)
        }
        try {
            val request = checkNotNull(artworkRequest(context, reference, 64))
            assertEquals(Size(64, 64), request.sizeResolver.size())
            assertTrue(loader.execute(request) is SuccessResult)
            val cached = loader.execute(request) as SuccessResult
            assertEquals(DataSource.MEMORY_CACHE, cached.dataSource)
            assertEquals(1, reads.get())
            assertTrue(loader.execute(checkNotNull(artworkRequest(context, reference.copy(revision = "v2"), 64))) is SuccessResult)
            assertEquals(2, reads.get())
        } finally { loader.shutdown() }
    }

    @Test
    fun unavailableArtworkUsesMonogramsAndReplacingTheRequestCanRecover() {
        val current = mutableStateOf(track.copy(artwork = ArtworkRef("external_primary:1", "v1")))
        val failures = listOf(
            FileNotFoundException("No album thumbnail"),
            IllegalStateException("No thumbnail descriptor"),
            IOException("Provider failed"),
            IOException("Wrapped provider failure", FileNotFoundException("No album thumbnail")),
        )
        val entered = List(failures.size) { CompletableDeferred<Unit>() }
        val release = List(failures.size) { CompletableDeferred<Unit>() }
        val loader = loader { data ->
            val index = data.toString().substringAfterLast('/').toInt() - 1
            if (index in failures.indices) {
                entered[index].complete(Unit)
                release[index].await()
                throw failures[index]
            }
            ImageFetchResult(ColorImage(Color.Red.toArgb(), 256, 256), false, DataSource.DISK)
        }
        val service = LibraryArtworkLoader(loader)
        try {
            compose.setContent {
                NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
                    LibraryArtwork(current.value, service, Modifier.testTag("artwork"))
                }
            }
            for (index in failures.indices) {
                compose.runOnIdle {
                    current.value = track.copy(artwork = ArtworkRef("external_primary:${index + 1}", "v1"))
                }
                compose.waitUntil(5_000) { entered[index].isCompleted }
                // Observe each new request before completing it, so an old monogram cannot pass.
                awaitTopColor(NullWaveColors.Cyberpunk.surfaceRaised)
                release[index].complete(Unit)
                awaitTopColor(Color(0xFFEDE8DC))
            }
            compose.runOnIdle {
                current.value = track.copy(artwork = ArtworkRef("external_primary:${failures.size + 1}", "v1"))
            }
            awaitTopColor(Color.Red)
        } finally { loader.shutdown() }
    }

    @Test
    fun nullThumbnailFromProviderUsesMonogramThroughBuiltInCoilFetcher() = runBlocking {
        val provider = NullThumbnailProvider()
        val resolver = ContentResolver.wrap(provider)
        val isolatedContext = object : ContextWrapper(context) {
            override fun getApplicationContext() = this
            override fun getContentResolver() = resolver
        }
        val loader = ImageLoader.Builder(isolatedContext).diskCache(null).build()
        val reference = ArtworkRef("external_primary:1", "v1")
        try {
            // This exercises Coil's real ContentUriFetcher, including its null-descriptor branch.
            val result = loader.execute(checkNotNull(artworkRequest(isolatedContext, reference, 64)))
            assertTrue(result is ErrorResult && result.throwable is IllegalStateException)
            assertEquals(1, provider.reads.get())
            compose.setContent {
                CompositionLocalProvider(LocalContext provides isolatedContext) {
                    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
                        LibraryArtwork(track.copy(artwork = reference), LibraryArtworkLoader(loader), Modifier.testTag("artwork"))
                    }
                }
            }
            awaitTopColor(Color(0xFFEDE8DC))
            assertEquals(2, provider.reads.get())
        } finally { loader.shutdown() }
    }

    @Test
    fun removingTheRowCancelsItsPendingArtworkRequest() {
        val visible = mutableStateOf(true)
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val loader = loader {
            entered.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }
        val service = LibraryArtworkLoader(loader)
        try {
            compose.setContent {
                NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
                    if (visible.value) LibraryArtwork(track.copy(artwork = ArtworkRef("external_primary:1", "v1")), service)
                }
            }
            compose.waitUntil(5_000) { entered.isCompleted }
            compose.runOnIdle { visible.value = false }
            compose.waitUntil(5_000) { cancelled.isCompleted }
        } finally { loader.shutdown() }
    }

    private fun loader(fetch: suspend (Uri) -> ImageFetchResult): ImageLoader = ImageLoader.Builder(context)
        .diskCache(null)
        .components { add(Fetcher.Factory<Uri> { data, _, _ -> Fetcher { fetch(data) } }) }
        .build()

    private fun awaitTopColor(expected: Color, verticalFraction: Float = 0.125f) {
        compose.waitUntil(5_000) {
            val image = compose.onNodeWithTag("artwork").captureToImage()
            image.toPixelMap()[image.width / 2, (image.height * verticalFraction).toInt()].toArgb() == expected.toArgb()
        }
    }
}

/** Only the thumbnail endpoint is available; any accidental catalog query fails the test. */
private class NullThumbnailProvider : ContentProvider() {
    val reads = AtomicInteger()
    override fun onCreate() = true
    override fun getType(uri: android.net.Uri) = "image/jpeg"
    override fun openTypedAssetFile(
        uri: android.net.Uri,
        mimeTypeFilter: String,
        opts: Bundle?,
        signal: CancellationSignal?,
    ): AssetFileDescriptor? {
        assertEquals("content://media/external_primary/audio/albums/1", uri.toString())
        assertEquals("image/*", mimeTypeFilter)
        reads.incrementAndGet()
        return null
    }
    override fun query(uri: android.net.Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor = error("Unexpected query")
    override fun insert(uri: android.net.Uri, values: ContentValues?): android.net.Uri = error("Unexpected insert")
    override fun update(uri: android.net.Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?): Int = error("Unexpected update")
    override fun delete(uri: android.net.Uri, selection: String?,
        selectionArgs: Array<out String>?): Int = error("Unexpected delete")
}
