package com.example.contactmanagerdemo.core

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.contactmanagerdemo.R

object ThemeManager {

    enum class ThemeMode(val storageValue: String, val nightMode: Int) {
        LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
        DARK("dark", AppCompatDelegate.MODE_NIGHT_YES),
        SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    }

    enum class AccentPreset(val storageValue: String, val fallbackStart: String, val fallbackEnd: String) {
        YELLOW("yellow", "#F59E0B", "#FBBF24"),
        LIGHT_GRAY("light_gray", "#94A3B8", "#CBD5E1"),
        LILAC("lilac", "#A855F7", "#C084FC"),
        BLUE_CYAN("blue_cyan", "#0EA5E9", "#22D3EE"),
        CUSTOM("custom", "#F59E0B", "#FBBF24"),
    }

    fun applySavedTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(getThemeMode(context).nightMode)
    }

    fun getThemeMode(context: Context): ThemeMode {
        val raw = prefs(context).getString(KEY_THEME_MODE, ThemeMode.LIGHT.storageValue).orEmpty()
        return ThemeMode.entries.firstOrNull { it.storageValue == raw } ?: ThemeMode.LIGHT
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit()
            .putString(KEY_THEME_MODE, mode.storageValue)
            .apply()
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    fun setDarkThemeEnabled(context: Context, enabled: Boolean) {
        setThemeMode(context, if (enabled) ThemeMode.DARK else ThemeMode.LIGHT)
    }

    fun isDarkThemeEnabled(context: Context): Boolean {
        return getThemeMode(context) == ThemeMode.DARK
    }

    fun getAccentPreset(context: Context): AccentPreset {
        val raw = prefs(context).getString(KEY_ACCENT_PRESET, AccentPreset.YELLOW.storageValue).orEmpty()
        return AccentPreset.entries.firstOrNull { it.storageValue == raw } ?: AccentPreset.YELLOW
    }

    fun setAccentPreset(context: Context, preset: AccentPreset) {
        prefs(context).edit().putString(KEY_ACCENT_PRESET, preset.storageValue).apply()
    }

    fun setCustomAccent(context: Context, startColor: Int, endColor: Int) {
        prefs(context).edit()
            .putString(KEY_CUSTOM_ACCENT_START, colorToHex(startColor))
            .putString(KEY_CUSTOM_ACCENT_END, colorToHex(endColor))
            .putString(KEY_ACCENT_PRESET, AccentPreset.CUSTOM.storageValue)
            .apply()
    }

    fun getAccentGradient(context: Context): Pair<Int, Int> {
        val preset = getAccentPreset(context)
        if (preset != AccentPreset.CUSTOM) {
            return parseColorSafe(preset.fallbackStart, ContextCompat.getColor(context, R.color.primary)) to
                parseColorSafe(preset.fallbackEnd, ContextCompat.getColor(context, R.color.accent))
        }
        val startHex = prefs(context).getString(KEY_CUSTOM_ACCENT_START, preset.fallbackStart).orEmpty()
        val endHex = prefs(context).getString(KEY_CUSTOM_ACCENT_END, preset.fallbackEnd).orEmpty()
        return parseColorSafe(startHex, ContextCompat.getColor(context, R.color.primary)) to
            parseColorSafe(endHex, ContextCompat.getColor(context, R.color.accent))
    }

    fun applyGradientBackground(view: View, cornerDp: Float = 12f) {
        val (start, end) = getAccentGradient(view.context)
        val density = view.resources.displayMetrics.density
        view.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(start, end),
        ).apply {
            cornerRadius = cornerDp * density
        }
        if (view is TextView) {
            view.setTextColor(Color.WHITE)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun parseColorSafe(value: String, fallback: Int): Int {
        return runCatching { Color.parseColor(value) }.getOrDefault(fallback)
    }

    private fun colorToHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }

    private const val PREFS_NAME = "contact_manager_theme"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT_PRESET = "accent_preset"
    private const val KEY_CUSTOM_ACCENT_START = "accent_custom_start"
    private const val KEY_CUSTOM_ACCENT_END = "accent_custom_end"
}
