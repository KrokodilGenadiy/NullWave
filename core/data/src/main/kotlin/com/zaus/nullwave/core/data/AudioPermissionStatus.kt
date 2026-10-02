package com.zaus.nullwave.core.data

/**
 * Where the audio permission stands, as the UI needs to see it.
 *
 * Four states, mapping onto the design's artboards. Derived by [audioPermissionStatus] from three inputs,
 * no two of which are sufficient on their own.
 */
enum class AudioPermissionStatus {
    /** Readable. Scan. */
    Granted,

    /** Never asked, or asked and granted-then-revoked. Show the first-run prompt - artboard 41. */
    NeverAsked,

    /** Declined once, but the system will still show the dialog. Artboard 41, possibly with a rationale. */
    Declined,

    /**
     * The system will no longer show the dialog. Only a trip to system Settings fixes it - artboard 45.
     *
     * The expensive state to get wrong in either direction: claim it falsely and the user is told to go
     * to Settings when a prompt would have worked; miss it and they tap a button that does nothing.
     */
    PermanentlyDenied,
}

/**
 * Derives the permission status.
 *
 * A pure function, so the truth table below is executable rather than a comment in a KDoc - see
 * `AudioPermissionStatusTest`. The inputs come from three different places and all three are needed:
 *
 * | granted | asked before | rationale | status |
 * | --- | --- | --- | --- |
 * | yes | – | – | [Granted] |
 * | no | no | – | [NeverAsked] |
 * | no | yes | yes | [Declined] |
 * | no | yes | no | [PermanentlyDenied] |
 *
 * ## Why all three
 *
 * Android cannot distinguish **never asked** from **permanently denied**: both report not-granted with no
 * rationale. [hasBeenRequestedWithoutGrant] is the only thing that separates them, and it has to be
 * persisted because "permanently denied" must still be true tomorrow.
 *
 * ## Why the flag is "without grant"
 *
 * Revoking a permission through system Settings **resets the platform's deny counter**, so the dialog will
 * appear again. A flag that only recorded "we asked once" would keep reporting [PermanentlyDenied] for the
 * rest of the app's life after a grant-then-revoke cycle, sending the user to Settings when a prompt would
 * have worked. Clearing it whenever a grant is observed makes the flag mean *asked, and not granted since*,
 * which is the signal this function actually wants.
 *
 * @param isGranted from `checkSelfPermission` - the only source of truth for readability, and never
 *   substituted by the stored flag
 * @param hasBeenRequestedWithoutGrant from [AudioPermissionState], cleared on every observed grant
 * @param shouldShowRationale from `Activity.shouldShowRequestPermissionRationale`, which needs an
 *   Activity and so is passed in rather than read here
 */
fun audioPermissionStatus(
    isGranted: Boolean,
    hasBeenRequestedWithoutGrant: Boolean,
    shouldShowRationale: Boolean,
): AudioPermissionStatus = when {
    isGranted -> AudioPermissionStatus.Granted
    !hasBeenRequestedWithoutGrant -> AudioPermissionStatus.NeverAsked
    shouldShowRationale -> AudioPermissionStatus.Declined
    else -> AudioPermissionStatus.PermanentlyDenied
}
