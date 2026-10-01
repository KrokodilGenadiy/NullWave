package com.zaus.nullwave.core.data

import android.Manifest
import android.os.Build

/**
 * Which runtime permission lets this app read the user's audio files.
 *
 * The permission was split at API 33 and `minSdk` here is 29, so there are two answers. This is the one
 * place that knows that: the scanner asks for [name], and so does whatever UI drives the request
 * launcher. A screen that branches on `Build.VERSION.SDK_INT` itself is a screen that has to be revisited
 * the next time the platform moves.
 *
 * There is no partial grant for audio - `READ_MEDIA_VISUAL_USER_SELECTED` covers images and video only -
 * so this is all-or-nothing and there is no "partially granted" state to model. The manifest says the
 * same thing at more length.
 */
object AudioPermission {

    /** The permission to check with `checkSelfPermission` and pass to a `RequestPermission` contract. */
    val name: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            // Capped at API 32 in the manifest, so on 33+ this branch is unreachable - and were it
            // reached, the request would be denied rather than shown.
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
}
