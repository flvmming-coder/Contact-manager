package com.example.contactmanagerdemo.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.core.AppEventLogger
import com.example.contactmanagerdemo.core.DevSecurityManager
import com.example.contactmanagerdemo.core.ThemeManager
import com.example.contactmanagerdemo.data.ContactPrefsStorage

class SettingsActivity : AppCompatActivity() {

    private lateinit var storage: ContactPrefsStorage
    private lateinit var btnSettingsImport: Button
    private lateinit var btnSettingsExport: Button
    private lateinit var btnSettingsTransferAll: Button
    private lateinit var btnSettingsTheme: Button
    private lateinit var btnSettingsTrash: Button
    private lateinit var btnSettingsClearAllInfo: Button
    private val accentPaletteColors by lazy {
        AvatarColorPalette.allHexColors()
            .mapNotNull { hex -> runCatching { Color.parseColor(hex) }.getOrNull() }
    }

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
        btnSettingsTransferAll = findViewById<Button>(R.id.btnSettingsTransferAll).also {
            it.setOnClickListener { returnAction(ACTION_TRANSFER_ALL) }
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
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showThemeModeDialog()
                    1 -> showAccentThemeDialog()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showThemeModeDialog() {
        val options = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system),
            getString(R.string.theme_color_deuteranomaly),
            getString(R.string.theme_color_protanomaly),
            getString(R.string.theme_color_tritanomaly),
            getString(R.string.theme_color_monochrome),
        )
        val currentVisionMode = ThemeManager.getColorVisionMode(this)
        val currentIndex = when (currentVisionMode) {
            ThemeManager.ColorVisionMode.DEUTERANOMALY -> 3
            ThemeManager.ColorVisionMode.PROTANOMALY -> 4
            ThemeManager.ColorVisionMode.TRITANOMALY -> 5
            ThemeManager.ColorVisionMode.MONOCHROME -> 6
            ThemeManager.ColorVisionMode.NORMAL -> when (ThemeManager.getThemeMode(this)) {
                ThemeManager.ThemeMode.LIGHT -> 0
                ThemeManager.ThemeMode.DARK -> 1
                ThemeManager.ThemeMode.SYSTEM -> 2
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme_mode_branch)
            .setSingleChoiceItems(options, currentIndex) { choiceDialog, which ->
                when (which) {
                    1 -> {
                        ThemeManager.setThemeMode(this, ThemeManager.ThemeMode.DARK)
                        ThemeManager.setColorVisionMode(this, ThemeManager.ColorVisionMode.NORMAL)
                        AppEventLogger.info("THEME", "Theme mode changed to dark")
                    }

                    2 -> {
                        ThemeManager.setThemeMode(this, ThemeManager.ThemeMode.SYSTEM)
                        ThemeManager.setColorVisionMode(this, ThemeManager.ColorVisionMode.NORMAL)
                        AppEventLogger.info("THEME", "Theme mode changed to system")
                    }

                    3 -> {
                        ThemeManager.setColorVisionMode(this, ThemeManager.ColorVisionMode.DEUTERANOMALY)
                        AppEventLogger.info("THEME", "Color vision mode changed to deuteranomaly")
                    }

                    4 -> {
                        ThemeManager.setColorVisionMode(this, ThemeManager.ColorVisionMode.PROTANOMALY)
                        AppEventLogger.info("THEME", "Color vision mode changed to protanomaly")
                    }

                    5 -> {
                        ThemeManager.setColorVisionMode(this, ThemeManager.ColorVisionMode.TRITANOMALY)
                        AppEventLogger.info("THEME", "Color vision mode changed to tritanomaly")
                    }

                    6 -> {
                        ThemeManager.setColorVisionMode(this, ThemeManager.ColorVisionMode.MONOCHROME)
                        AppEventLogger.info("THEME", "Color vision mode changed to monochrome")
                    }

                    else -> {
                        ThemeManager.setThemeMode(this, ThemeManager.ThemeMode.LIGHT)
                        ThemeManager.setColorVisionMode(this, ThemeManager.ColorVisionMode.NORMAL)
                        AppEventLogger.info("THEME", "Theme mode changed to light")
                    }
                }
                Toast.makeText(this, R.string.settings_theme_saved, Toast.LENGTH_SHORT).show()
                choiceDialog.dismiss()
                onThemeChanged()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
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

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme_accent_branch)
            .setSingleChoiceItems(presets.map { it.second }.toTypedArray(), selectedIndex) { choiceDialog, which ->
                val preset = presets.getOrNull(which)?.first ?: ThemeManager.AccentPreset.YELLOW
                if (preset == ThemeManager.AccentPreset.CUSTOM) {
                    choiceDialog.dismiss()
                    showCustomAccentDialog()
                    return@setSingleChoiceItems
                }
                ThemeManager.setAccentPreset(this, preset)
                AppEventLogger.info("THEME", "Accent preset changed to ${preset.storageValue}")
                Toast.makeText(this, R.string.settings_theme_saved, Toast.LENGTH_SHORT).show()
                choiceDialog.dismiss()
                onThemeChanged()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showCustomAccentDialog() {
        val (currentStart, currentEnd) = ThemeManager.getAccentGradient(this)
        var selectedStart = currentStart
        var selectedEnd = currentEnd

        val previewStart = createColorPreviewView(selectedStart)
        val previewEnd = createColorPreviewView(selectedEnd)
        val btnPickStart = Button(this).apply {
            text = getString(R.string.theme_accent_custom_pick_start)
            ThemeManager.applyGradientBackground(this, cornerDp = 10f)
            setOnClickListener {
                showAccentPaletteDialog(selectedStart) { color ->
                    selectedStart = color
                    previewStart.background = createColorPreviewDrawable(color)
                }
            }
        }
        val btnPickEnd = Button(this).apply {
            text = getString(R.string.theme_accent_custom_pick_end)
            ThemeManager.applyGradientBackground(this, cornerDp = 10f)
            setOnClickListener {
                showAccentPaletteDialog(selectedEnd) { color ->
                    selectedEnd = color
                    previewEnd.background = createColorPreviewDrawable(color)
                }
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            addView(
                TextView(this@SettingsActivity).apply {
                    text = getString(R.string.theme_accent_custom_preview_start)
                    setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_secondary))
                },
            )
            addView(previewStart, LinearLayout.LayoutParams(dp(46), dp(46)).apply { topMargin = dp(4) })
            addView(btnPickStart, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) })
            addView(
                TextView(this@SettingsActivity).apply {
                    text = getString(R.string.theme_accent_custom_preview_end)
                    setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_secondary))
                    setPadding(0, dp(12), 0, 0)
                },
            )
            addView(previewEnd, LinearLayout.LayoutParams(dp(46), dp(46)).apply { topMargin = dp(4) })
            addView(btnPickEnd, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) })
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.theme_accent_custom_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.action_save) { _, _ ->
                ThemeManager.setCustomAccent(this, selectedStart, selectedEnd)
                AppEventLogger.info("THEME", "Accent preset changed to custom palette")
                Toast.makeText(this, R.string.settings_theme_saved, Toast.LENGTH_SHORT).show()
                onThemeChanged()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showAccentPaletteDialog(
        selectedColor: Int,
        onColorSelected: (Int) -> Unit,
    ) {
        val colors = if (accentPaletteColors.isEmpty()) {
            listOf(Color.parseColor("#F59E0B"), Color.parseColor("#FBBF24"))
        } else {
            accentPaletteColors
        }
        val grid = GridLayout(this).apply {
            columnCount = 6
            rowCount = (colors.size + 5) / 6
        }
        colors.forEach { color ->
            val swatch = View(this).apply {
                background = createPaletteSwatchDrawable(color, color == selectedColor)
            }
            grid.addView(
                swatch,
                GridLayout.LayoutParams().apply {
                    width = dp(36)
                    height = dp(36)
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                },
            )
        }

        val scroll = ScrollView(this).apply {
            addView(
                grid,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.theme_accent_custom_palette_title)
            .setView(scroll)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        for (index in 0 until grid.childCount) {
            grid.getChildAt(index).setOnClickListener {
                val pickedColor = colors[index]
                onColorSelected(pickedColor)
                dialog.dismiss()
            }
        }

        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun createColorPreviewView(color: Int): View {
        return View(this).apply {
            background = createColorPreviewDrawable(color)
        }
    }

    private fun createColorPreviewDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(1), 0xFFE2E8F0.toInt())
        }
    }

    private fun createPaletteSwatchDrawable(color: Int, selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(
                dp(if (selected) 2 else 1),
                if (selected) ContextCompat.getColor(this@SettingsActivity, R.color.text_primary) else 0xFFE2E8F0.toInt(),
            )
        }
    }

    private fun onThemeChanged() {
        applyAccentButtons()
        if (isRestartBypassAuthorized()) {
            AppEventLogger.info("THEME", "Theme changed with restart bypass=ON; app restart skipped")
            recreate()
            return
        }
        AppEventLogger.info("THEME", "Theme changed with restart bypass=OFF; restart requested")
        returnAction(ACTION_THEME_RESTART_REQUIRED)
    }

    private fun isRestartBypassAuthorized(): Boolean {
        return DevSecurityManager.isRestartBypassAuthorized(this)
    }

    private fun confirmClearAllInfo() {
        val dialog = AlertDialog.Builder(this)
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
                DevSecurityManager.registerFullResetAttempt(this)
                AppEventLogger.info("DATA", "Full reset completed; logs preserved")
                Toast.makeText(this, R.string.settings_clear_all_info_done, Toast.LENGTH_SHORT).show()
                returnAction(ACTION_DATA_CLEARED)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun applyAccentButtons() {
        listOf(
            btnSettingsImport,
            btnSettingsExport,
            btnSettingsTransferAll,
            btnSettingsTheme,
            btnSettingsTrash,
            btnSettingsClearAllInfo,
        ).forEach { button ->
            ThemeManager.applyGradientBackground(button, cornerDp = 12f)
        }
    }

    private fun styleDialogButtons(dialog: AlertDialog) {
        listOf(
            AlertDialog.BUTTON_POSITIVE,
            AlertDialog.BUTTON_NEGATIVE,
            AlertDialog.BUTTON_NEUTRAL,
        ).forEach { id ->
            val button = dialog.getButton(id) ?: return@forEach
            ThemeManager.applyGradientBackground(button, cornerDp = 12f)
            button.isAllCaps = false
            button.textSize = 12f
            button.minHeight = dp(34)
            button.minimumHeight = dp(34)
            button.setPadding(dp(10), dp(6), dp(10), dp(6))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_ACTION = "extra_action"
        const val ACTION_IMPORT = "action_import"
        const val ACTION_EXPORT = "action_export"
        const val ACTION_TRANSFER_ALL = "action_transfer_all"
        const val ACTION_DATA_CLEARED = "action_data_cleared"
        const val ACTION_THEME_RESTART_REQUIRED = "action_theme_restart_required"
    }
}
