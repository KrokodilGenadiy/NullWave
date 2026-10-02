package com.zaus.nullwave.core.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The audio permission, as far as this app can observe it.
 *
 * Holds one persisted bit - whether the dialog has been shown without a grant since - and reads the live
 * permission from the system. Feed both into [audioPermissionStatus] to get the state a screen should
 * render.
 *
 * ## The stored flag is never the source of truth
 *
 * [isGranted] asks `checkSelfPermission` every time. Nothing here caches granted-ness, because it can
 * change while the app is in the background: the user can revoke in system Settings, and Android can
 * auto-reset permissions for an app left unused. A cached copy would be wrong within minutes.
 *
 * The flag exists only to answer the one question the platform cannot: whether an un-granted permission
 * was ever asked for. Both **never asked** and **permanently denied** report not-granted with no
 * rationale, and they need opposite screens.
 *
 * ## Why the flag is cleared on a grant
 *
 * Because revoking through system Settings **resets the platform's deny counter** - the dialog will show
 * again. A flag that only ever recorded "we asked once" would report a permanent denial forever after a
 * grant-then-revoke cycle, so the app would send the user to Settings when a simple prompt would have
 * worked. [syncWithSystem] clears it whenever a grant is observed, which makes it mean *asked, and not
 * granted since*.
 */
@Inject
@SingleIn(AppScope::class)
class AudioPermissionState(
    private val context: Context,
    private val preferences: DataStore<Preferences>,
) {

    /**
     * Whether audio is readable right now.
     *
     * Read on every call, never cached, never inferred from the stored flag. Re-read it on `ON_RESUME`:
     * nothing notifies an app that its permission changed while it was backgrounded.
     */
    val isGranted: Boolean
        get() = context.checkSelfPermission(AudioPermission.name) == PackageManager.PERMISSION_GRANTED

    /**
     * True once the dialog has been shown, and false again from the moment a grant is observed.
     *
     * Pass to [audioPermissionStatus] together with [isGranted]; on its own it says nothing useful.
     */
    val hasBeenRequestedWithoutGrant: Flow<Boolean> =
        preferences.data.map { it[RequestedWithoutGrantKey] ?: false }

    /**
     * Record that the request has been shown.
     *
     * Call when the launcher is **fired**, not when the result arrives. A user who swipes the dialog away
     * without choosing has still been asked, and treating that as never-asked would re-show the first-run
     * screen on every launch.
     */
    suspend fun markRequested() {
        preferences.edit { it[RequestedWithoutGrantKey] = true }
    }

    /**
     * Reconcile the stored flag with reality. Call on every `ON_RESUME`, beside the [isGranted] re-check.
     *
     * Clears the flag while the permission is granted, so a later revoke in system Settings starts from
     * [AudioPermissionStatus.NeverAsked] and offers the prompt - which works, because the revoke reset the
     * platform's deny counter - instead of claiming a permanent denial and sending the user to Settings.
     *
     * Writes only when something needs changing, so it does not wake collectors of
     * [hasBeenRequestedWithoutGrant] on every resume.
     */
    suspend fun syncWithSystem() {
        if (!isGranted) return
        preferences.edit { stored ->
            if (stored[RequestedWithoutGrantKey] == true) stored.remove(RequestedWithoutGrantKey)
        }
    }

    private companion object {
        /**
         * Spelled "without grant" rather than "requested", because that is what it means - and because a
         * key named `audio_permission_requested` would invite someone to stop clearing it.
         */
        val RequestedWithoutGrantKey = booleanPreferencesKey("audio_permission_requested_without_grant")
    }
}
