package com.zaus.nullwave.feature.library.platform

import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPermissionTest {
    @Test
    fun `permission name follows the Android audio boundary`() {
        for (sdk in listOf(29, 30, 32)) assertEquals("android.permission.READ_EXTERNAL_STORAGE", audioReadPermission(sdk))
        for (sdk in listOf(33, 36, 37)) assertEquals("android.permission.READ_MEDIA_AUDIO", audioReadPermission(sdk))
    }

    @Test
    fun `fresh grant overrides any previous denial or rationale`() {
        for (rationale in listOf(false, true)) for (denied in listOf(false, true)) {
            assertEquals(LibraryAccess.Granted, libraryAccess(true, rationale, denied))
        }
    }

    @Test
    fun `rationale takes priority over settings history`() {
        for (denied in listOf(false, true)) {
            assertEquals(LibraryAccess.Required(AccessAction.ExplainThenRequest), libraryAccess(false, true, denied))
        }
    }

    @Test
    fun `no rationale alone is not permanent denial`() {
        assertEquals(LibraryAccess.Required(AccessAction.Request), libraryAccess(false, false, false))
        assertEquals(LibraryAccess.Required(AccessAction.SettingsSuggested), libraryAccess(false, false, true))
    }
}
