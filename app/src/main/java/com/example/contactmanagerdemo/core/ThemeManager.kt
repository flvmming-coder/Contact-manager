package com.example.contactmanagerdemo.core

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    fun applySavedTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(getSavedMode(context))
    }

    fun setDarkThemeEnabled(context: Context, enabled: Boolean) {
        val mode = if (enabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME_MODE, mode)
            .apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun isDarkThemeEnabled(context: Context): Boolean {
        return getSavedMode(context) == AppCompatDelegate.MODE_NIGHT_YES
    }

    private fun getSavedMode(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_NO)
    }

    private const val PREFS_NAME = "contact_manager_theme"
    private const val KEY_THEME_MODE = "theme_mode"
}
