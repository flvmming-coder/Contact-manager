package com.example.contactmanagerdemo.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.core.AppEventLogger
import com.example.contactmanagerdemo.core.ThemeManager
import com.example.contactmanagerdemo.data.ContactPrefsStorage

class SettingsActivity : AppCompatActivity() {

    private lateinit var storage: ContactPrefsStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        storage = ContactPrefsStorage(this)

        findViewById<ImageButton>(R.id.btnSettingsBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSettingsImport).setOnClickListener { returnAction(ACTION_IMPORT) }
        findViewById<Button>(R.id.btnSettingsExport).setOnClickListener { returnAction(ACTION_EXPORT) }
        findViewById<Button>(R.id.btnSettingsTheme).setOnClickListener { showThemeSelectorDialog() }
        findViewById<Button>(R.id.btnSettingsTrash).setOnClickListener {
            startActivity(Intent(this, TrashActivity::class.java))
        }
        findViewById<Button>(R.id.btnSettingsClearAllInfo).setOnClickListener { confirmClearAllInfo() }
    }

    private fun returnAction(action: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_ACTION, action))
        finish()
    }

    private fun showThemeSelectorDialog() {
        val options = arrayOf(getString(R.string.theme_light), getString(R.string.theme_dark))
        val currentIndex = if (ThemeManager.isDarkThemeEnabled(this)) 1 else 0
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme_title)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                ThemeManager.setDarkThemeEnabled(this, which == 1)
                Toast.makeText(this, R.string.settings_theme_saved, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmClearAllInfo() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_clear_all_info_title)
            .setMessage(R.string.settings_clear_all_info_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                storage.clearAllInfo()
                AppEventLogger.clearAllLogs(this)
                getSharedPreferences(DEV_PREFS_NAME, MODE_PRIVATE).edit().clear().apply()
                Toast.makeText(this, R.string.settings_clear_all_info_done, Toast.LENGTH_SHORT).show()
                returnAction(ACTION_DATA_CLEARED)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_ACTION = "extra_action"
        const val ACTION_IMPORT = "action_import"
        const val ACTION_EXPORT = "action_export"
        const val ACTION_DATA_CLEARED = "action_data_cleared"
        private const val DEV_PREFS_NAME = "contact_manager_dev_settings"
    }
}
