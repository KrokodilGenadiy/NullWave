package com.zaus.nullwave.feature.library.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess

@SuppressLint("InlinedApi") // Pure SDK selector; production callers pass Build.VERSION.SDK_INT.
internal fun audioReadPermission(sdk: Int): String =
    if (sdk >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

internal fun Context.hasAudioAccess(): Boolean =
    checkSelfPermission(audioReadPermission(Build.VERSION.SDK_INT)) == PackageManager.PERMISSION_GRANTED

/** A fresh platform observation, never a persisted grant. The seam also avoids OS dialogs in UI tests. */
internal interface AudioPermission {
    val name: String
    fun isGranted(): Boolean
    fun shouldExplain(): Boolean
}

internal class AndroidAudioPermission(private val activity: Activity) : AudioPermission {
    override val name: String get() = audioReadPermission(Build.VERSION.SDK_INT)
    override fun isGranted(): Boolean = activity.hasAudioAccess()
    override fun shouldExplain(): Boolean = activity.shouldShowRequestPermissionRationale(name)
}

/** A denied callback without rationale suggests settings; it does not prove permanent denial. */
internal fun libraryAccess(granted: Boolean, rationale: Boolean, deniedResult: Boolean): LibraryAccess = when {
    granted -> LibraryAccess.Granted
    rationale -> LibraryAccess.Required(AccessAction.ExplainThenRequest)
    deniedResult -> LibraryAccess.Required(AccessAction.SettingsSuggested)
    else -> LibraryAccess.Required(AccessAction.Request)
}
