package com.example.contactmanagerdemo.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.core.AppEventLogger
import com.example.contactmanagerdemo.core.ThemeManager
import com.example.contactmanagerdemo.data.ContactPrefsStorage

class SettingsActivity : AppCompatActivity() {

    private lateinit var storage: ContactPrefsStorage
    private lateinit var btnSettingsImport: Button
    private lateinit var btnSettingsExport: Button
    private lateinit var btnSettingsTheme: Button
    private lateinit var btnSettingsTrash: Button
    private lateinit var btnSettingsClearAllInfo: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        storage = ContactPrefsStorage(this)

        findViewById<ImageButton>(R.id.btnSettingsBack).setOnClickListener { finish() }
        btnSettingsImport = findViewById<Button>(R.id.btnSettingsImport).also {
            it.setOnClickListener { returnAction(ACTION_IMPORT) }
        }
        btnSettingsExport = findViewById<Button>(R.id.btnSettingsExport).also {
            it.setOnClickListener { returnAction(ACTION_EXPORT) }
        }
        btnSettingsTheme = findViewById<Button>(R.id.btnSettingsTheme).also {
            it.setOnClickListener { showThemeSelectorDialog() }
        }
        btnSettingsTrash = findViewById<Button>(R.id.btnSettingsTrash).also {
            it.setOnClickListener {
                startActivity(Intent(this, TrashActivity::class.java))
            }
        }
        btnSettingsClearAllInfo = findViewById<Button>(R.id.btnSettingsClearAllInfo).also {
            it.setOnClickListener { confirmClearAllInfo() }
        }

        applyAccentButtons()
    }

    override fun onResume() {
        super.onResume()
        applyAccentButtons()
    }

    private fun returnAction(action: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_ACTION, action))
        finish()
    }

    private fun showThemeSelectorDialog() {
        val options = arrayOf(
            getString(R.string.settings_theme_mode_branch),
            getString(R.string.settings_theme_accent_branch),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showThemeModeDialog()
                    1 -> showAccentThemeDialog()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showThemeModeDialog() {
        val options = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system),
        )
        val currentIndex = when (ThemeManager.getThemeMode(this)) {
            ThemeManager.ThemeMode.LIGHT -> 0
            ThemeManager.ThemeMode.DARK -> 1
            ThemeManager.ThemeMode.SYSTEM -> 2
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme_mode_branch)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val selected = when (which) {
                    1 -> ThemeManager.ThemeMode.DARK
                    2 -> ThemeManager.ThemeMode.SYSTEM
                    else -> ThemeManager.ThemeMode.LIGHT
                }
                ThemeManager.setThemeMode(this, selected)
                AppEventLogger.info("THEME", "Theme mode changed to ${selected.storageValue}")
                Toast.makeText(this, R.string.settings_theme_saved, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showAccentThemeDialog() {
        val presets = listOf(
            ThemeManager.AccentPreset.YELLOW to getString(R.string.theme_accent_yellow),
            ThemeManager.AccentPreset.LIGHT_GRAY to getString(R.string.theme_accent_light_gray),
            ThemeManager.AccentPreset.LILAC to getString(R.string.theme_accent_lilac),
            ThemeManager.AccentPreset.BLUE_CYAN to getString(R.string.theme_accent_blue_cyan),
            ThemeManager.AccentPreset.CUSTOM to getString(R.string.theme_accent_custom),
        )
        val current = ThemeManager.getAccentPreset(this)
        val selectedIndex = presets.indexOfFirst { it.first == current }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme_accent_branch)
            .setSingleChoiceItems(presets.map { it.second }.toTypedArray(), selectedIndex) { dialog, which ->
                val preset = presets.getOrNull(which)?.first ?: ThemeManager.AccentPreset.YELLOW
                if (preset == ThemeManager.AccentPreset.CUSTOM) {
                    dialog.dismiss()
                    showCustomAccentDialog()
                    return@setSingleChoiceItems
                }
                ThemeManager.setAccentPreset(this, preset)
                AppEventLogger.info("THEME", "Accent preset changed to ${preset.storageValue}")
                Toast.makeText(this, R.string.settings_theme_saved, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                applyAccentButtons()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showCustomAccentDialog() {
        val (currentStart, currentEnd) = ThemeManager.getAccentGradient(this)
        val inputStart = EditText(this).apply {
            hint = "#F59E0B"
            setText(colorToHex(currentStart))
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_secondary))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_dialog_input)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val inputEnd = EditText(this).apply {
            hint = "#FBBF24"
            setText(colorToHex(currentEnd))
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_secondary))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_dialog_input)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(inputStart)
            addView(inputEnd)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.theme_accent_custom_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val startRaw = inputStart.text.toString().trim()
                val endRaw = inputEnd.text.toString().trim()
                val start = runCatching { Color.parseColor(startRaw) }.getOrNull()
                val end = runCatching { Color.parseColor(endRaw) }.getOrNull()
                if (start == null || end == null) {
                    Toast.makeText(this, R.string.theme_accent_custom_error, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                ThemeManager.setCustomAccent(this, start, end)
                AppEventLogger.info("THEME", "Accent preset changed to custom")
                Toast.makeText(this, R.string.settings_theme_saved, Toast.LENGTH_SHORT).show()
                applyAccentButtons()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmClearAllInfo() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_clear_all_info_title)
            .setMessage(R.string.settings_clear_all_info_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                val contactsBefore = storage.getAllContacts().size
                val groupsBefore = storage.getAvailableGroups().size
                AppEventLogger.warn(
                    "DATA",
                    "Full reset requested by user; contactsBefore=$contactsBefore; groupsBefore=$groupsBefore",
                )
                storage.clearAllInfo()
                getSharedPreferences(DEV_PREFS_NAME, MODE_PRIVATE).edit().clear().apply()
                AppEventLogger.info("DATA", "Full reset completed; logs preserved")
                Toast.makeText(this, R.string.settings_clear_all_info_done, Toast.LENGTH_SHORT).show()
                returnAction(ACTION_DATA_CLEARED)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun applyAccentButtons() {
        listOf(
            btnSettingsImport,
            btnSettingsExport,
            btnSettingsTheme,
            btnSettingsTrash,
            btnSettingsClearAllInfo,
        ).forEach { button ->
            ThemeManager.applyGradientBackground(button, cornerDp = 12f)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun colorToHex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

    companion object {
        const val EXTRA_ACTION = "extra_action"
        const val ACTION_IMPORT = "action_import"
        const val ACTION_EXPORT = "action_export"
        const val ACTION_DATA_CLEARED = "action_data_cleared"
        private const val DEV_PREFS_NAME = "contact_manager_dev_settings"
    }
}
