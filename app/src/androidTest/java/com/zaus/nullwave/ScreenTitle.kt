package com.zaus.nullwave

import android.content.Context
import androidx.annotation.StringRes

internal fun Context.screenTitle(@StringRes title: Int): String =
    getString(title).uppercase(resources.configuration.locales[0])
