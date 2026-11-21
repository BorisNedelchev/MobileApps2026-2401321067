package com.bagofholding.ui.common

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/** Stores the user's light / dark / follow-system choice. */
class ThemePreferences(context: Context) {

    enum class Mode(val nightMode: Int) {
        SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES),
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mode: Mode
        get() = Mode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: Mode.SYSTEM
        set(value) {
            prefs.edit { putString(KEY_MODE, value.name) }
            AppCompatDelegate.setDefaultNightMode(value.nightMode)
        }

    fun applySavedTheme() {
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_MODE = "theme_mode"
    }
}
