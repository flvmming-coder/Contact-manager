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
import com.example.contactmanagerdemo.data.DeletedContactEntry

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
        findViewById<Button>(R.id.btnSettingsTrash).setOnClickListener { showTrashDialog() }
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

    private fun showTrashDialog() {
        val entries = storage.getTrashContacts()
        val items = mutableListOf<String>()
        items.add(getString(R.string.trash_action_retention))
        items.add(getString(R.string.trash_action_clear))
        entries.forEach { entry ->
            val title = listOfNotNull(entry.contact.name, entry.contact.lastName).joinToString(" ").trim()
            items.add(getString(R.string.trash_contact_item, title, entry.remainingDays(System.currentTimeMillis())))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_trash)
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which == 0 -> showTrashRetentionDialog()
                    which == 1 -> confirmClearTrash()
                    else -> {
                        val entry = entries.getOrNull(which - 2) ?: return@setItems
                        showTrashEntryActions(entry)
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showTrashRetentionDialog() {
        val options = arrayOf(getString(R.string.trash_retention_7_days), getString(R.string.trash_retention_30_days))
        val selected = if (storage.getTrashRetentionDays() <= 7) 0 else 1
        AlertDialog.Builder(this)
            .setTitle(R.string.trash_retention_title)
            .setSingleChoiceItems(options, selected) { dialog, which ->
                storage.setTrashRetentionDays(if (which == 0) 7 else 30)
                Toast.makeText(this, R.string.trash_retention_saved, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showTrashEntryActions(entry: DeletedContactEntry) {
        val title = listOfNotNull(entry.contact.name, entry.contact.lastName).joinToString(" ").trim()
        val actions = arrayOf(getString(R.string.trash_action_restore), getString(R.string.trash_action_delete_forever))
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        if (storage.restoreFromTrash(entry.contact.id)) {
                            Toast.makeText(this, R.string.trash_restore_done, Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> {
                        if (storage.deleteFromTrash(entry.contact.id)) {
                            Toast.makeText(this, R.string.trash_delete_forever_done, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmClearTrash() {
        AlertDialog.Builder(this)
            .setTitle(R.string.trash_clear_title)
            .setMessage(R.string.trash_clear_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                storage.clearTrash()
                Toast.makeText(this, R.string.trash_clear_done, Toast.LENGTH_SHORT).show()
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
