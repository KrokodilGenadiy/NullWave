package com.zaus.nullwave.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The permission truth table, executed rather than described.
 *
 * Worth testing despite being four lines: every branch maps to a different screen, and two of the inputs
 * are indistinguishable to the platform. Getting it wrong either tells a user to visit system Settings
 * when a prompt would have worked, or hands them a button that silently does nothing.
 */
class AudioPermissionStatusTest {

    private fun status(
        granted: Boolean = false,
        askedWithoutGrant: Boolean = false,
        rationale: Boolean = false,
    ) = audioPermissionStatus(granted, askedWithoutGrant, rationale)

    @Test
    fun `granted wins regardless of the other inputs`() {
        assertEquals(AudioPermissionStatus.Granted, status(granted = true))
        assertEquals(
            AudioPermissionStatus.Granted,
            status(granted = true, askedWithoutGrant = true, rationale = true),
        )
    }

    @Test
    fun `first run`() {
        assertEquals(AudioPermissionStatus.NeverAsked, status())
    }

    @Test
    fun `declined once, the dialog will still show`() {
        assertEquals(
            AudioPermissionStatus.Declined,
            status(askedWithoutGrant = true, rationale = true),
        )
    }

    @Test
    fun `permanently denied`() {
        assertEquals(
            AudioPermissionStatus.PermanentlyDenied,
            status(askedWithoutGrant = true, rationale = false),
        )
    }

    @Test
    fun `never asked and permanently denied differ only by the stored flag`() {
        // The whole reason the flag is persisted at all. Both of these report not-granted with no
        // rationale, and Android offers nothing else to tell them apart - but one shows artboard 41 and
        // the other artboard 45.
        assertEquals(AudioPermissionStatus.NeverAsked, status(askedWithoutGrant = false))
        assertEquals(AudioPermissionStatus.PermanentlyDenied, status(askedWithoutGrant = true))
    }

    @Test
    fun `granted then revoked in Settings reads as never asked, not as a denial`() {
        // The bug this table was rewritten for. Revoking through system Settings resets the platform's
        // deny counter, so the dialog WILL appear again - and `syncWithSystem` cleared the flag while the
        // permission was granted, so we arrive here with askedWithoutGrant = false.
        //
        // Had the flag merely recorded "we asked once", this would be PermanentlyDenied forever after,
        // and the app would send the user to Settings for a permission a prompt could have obtained.
        val afterRevoke = status(granted = false, askedWithoutGrant = false, rationale = false)

        assertEquals(AudioPermissionStatus.NeverAsked, afterRevoke)
    }

    @Test
    fun `asked, denied, granted via Settings, then revoked - still not a permanent denial`() {
        // The long way round to the same place: deny permanently, go to Settings and allow, then revoke.
        // The grant clears the flag, so the app recovers instead of staying stuck on artboard 45.
        assertEquals(
            AudioPermissionStatus.PermanentlyDenied,
            status(askedWithoutGrant = true),
        )
        // ... user grants in Settings; syncWithSystem clears the flag ...
        assertEquals(AudioPermissionStatus.Granted, status(granted = true))
        // ... user revokes again ...
        assertEquals(AudioPermissionStatus.NeverAsked, status(askedWithoutGrant = false))
    }
}
