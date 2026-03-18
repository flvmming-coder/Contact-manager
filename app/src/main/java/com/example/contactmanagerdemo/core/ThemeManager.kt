package com.example.contactmanagerdemo.core

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.contactmanagerdemo.R
import kotlin.math.roundToInt

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

    enum class ColorVisionMode(val storageValue: String) {
        NORMAL("normal"),
        DEUTERANOMALY("deuteranomaly"),
        PROTANOMALY("protanomaly"),
        TRITANOMALY("tritanomaly"),
        MONOCHROME("monochrome"),
    }

    fun applySavedTheme(context: Context) {
        val resolvedMode = runCatching { getThemeMode(context) }
            .getOrDefault(ThemeMode.LIGHT)
        AppCompatDelegate.setDefaultNightMode(resolvedMode.nightMode)
    }

    fun getThemeMode(context: Context): ThemeMode {
        val prefs = prefs(context)
        val value = prefs.all[KEY_THEME_MODE]
        val resolved = when (value) {
            is String -> ThemeMode.entries.firstOrNull { it.storageValue == value } ?: ThemeMode.LIGHT
            is Int -> fromLegacyNightMode(value)
            else -> ThemeMode.LIGHT
        }
        if (value !is String || value != resolved.storageValue) {
            // Migrate legacy/non-string value to the new stable format.
            prefs.edit().putString(KEY_THEME_MODE, resolved.storageValue).apply()
        }
        return resolved
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
        val value = prefs(context).all[KEY_ACCENT_PRESET]
        return if (value is String) {
            AccentPreset.entries.firstOrNull { it.storageValue == value } ?: AccentPreset.YELLOW
        } else {
            AccentPreset.YELLOW
        }
    }

    fun setAccentPreset(context: Context, preset: AccentPreset) {
        prefs(context).edit().putString(KEY_ACCENT_PRESET, preset.storageValue).apply()
    }

    fun getColorVisionMode(context: Context): ColorVisionMode {
        val value = prefs(context).getString(KEY_COLOR_VISION_MODE, ColorVisionMode.NORMAL.storageValue).orEmpty()
        return ColorVisionMode.entries.firstOrNull { it.storageValue == value } ?: ColorVisionMode.NORMAL
    }

    fun setColorVisionMode(context: Context, mode: ColorVisionMode) {
        prefs(context).edit().putString(KEY_COLOR_VISION_MODE, mode.storageValue).apply()
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
        val baseGradient = if (preset != AccentPreset.CUSTOM) {
            parseColorSafe(preset.fallbackStart, ContextCompat.getColor(context, R.color.primary)) to
                parseColorSafe(preset.fallbackEnd, ContextCompat.getColor(context, R.color.accent))
        } else {
            val startHex = prefs(context).getString(KEY_CUSTOM_ACCENT_START, preset.fallbackStart).orEmpty()
            val endHex = prefs(context).getString(KEY_CUSTOM_ACCENT_END, preset.fallbackEnd).orEmpty()
            parseColorSafe(startHex, ContextCompat.getColor(context, R.color.primary)) to
                parseColorSafe(endHex, ContextCompat.getColor(context, R.color.accent))
        }
        return applyColorVisionMode(baseGradient, getColorVisionMode(context))
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
            view.setTextColor(resolveReadableTextColor(start, end))
        }
    }

    private fun resolveReadableTextColor(start: Int, end: Int): Int {
        val mixed = Color.rgb(
            ((Color.red(start) + Color.red(end)) / 2f).roundToInt(),
            ((Color.green(start) + Color.green(end)) / 2f).roundToInt(),
            ((Color.blue(start) + Color.blue(end)) / 2f).roundToInt(),
        )
        val luminance = (0.299 * Color.red(mixed) + 0.587 * Color.green(mixed) + 0.114 * Color.blue(mixed)) / 255.0
        return if (luminance > 0.62) Color.BLACK else Color.WHITE
    }

    private fun applyColorVisionMode(gradient: Pair<Int, Int>, mode: ColorVisionMode): Pair<Int, Int> {
        return when (mode) {
            ColorVisionMode.NORMAL -> gradient
            ColorVisionMode.DEUTERANOMALY -> Color.parseColor("#2563EB") to Color.parseColor("#22D3EE")
            ColorVisionMode.PROTANOMALY -> Color.parseColor("#0F766E") to Color.parseColor("#14B8A6")
            ColorVisionMode.TRITANOMALY -> Color.parseColor("#B45309") to Color.parseColor("#F59E0B")
            ColorVisionMode.MONOCHROME -> Color.parseColor("#64748B") to Color.parseColor("#CBD5E1")
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

    private fun fromLegacyNightMode(value: Int): ThemeMode {
        return when (value) {
            AppCompatDelegate.MODE_NIGHT_YES -> ThemeMode.DARK
            AppCompatDelegate.MODE_NIGHT_NO -> ThemeMode.LIGHT
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> ThemeMode.SYSTEM
            AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY -> ThemeMode.SYSTEM
            else -> ThemeMode.LIGHT
        }
    }

    private const val PREFS_NAME = "contact_manager_theme"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT_PRESET = "accent_preset"
    private const val KEY_CUSTOM_ACCENT_START = "accent_custom_start"
    private const val KEY_CUSTOM_ACCENT_END = "accent_custom_end"
    private const val KEY_COLOR_VISION_MODE = "color_vision_mode"
}
