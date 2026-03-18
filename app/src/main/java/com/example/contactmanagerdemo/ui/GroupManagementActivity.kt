package com.example.contactmanagerdemo.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.core.AppEventLogger
import com.example.contactmanagerdemo.core.ThemeManager
import com.example.contactmanagerdemo.data.ContactPrefsStorage

class GroupManagementActivity : AppCompatActivity() {

    private lateinit var storage: ContactPrefsStorage
    private lateinit var listGroups: ListView
    private lateinit var btnGroupsAdd: Button
    private var groups: List<Pair<String, String>> = emptyList()
    private val devPrefs by lazy { getSharedPreferences(DEV_PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_management)
        storage = ContactPrefsStorage(this)
        listGroups = findViewById(R.id.listGroups)

        findViewById<ImageButton>(R.id.btnGroupsBack).setOnClickListener { finish() }
        btnGroupsAdd = findViewById<Button>(R.id.btnGroupsAdd).also {
            it.setOnClickListener { showCreateGroupDialog() }
        }
        listGroups.setOnItemClickListener { _, _, position, _ ->
            val entry = groups.getOrNull(position) ?: return@setOnItemClickListener
            showGroupActionsDialog(entry.first, entry.second)
        }

        applyAccentUi()
        refreshGroups()
    }

    override fun onResume() {
        super.onResume()
        applyAccentUi()
    }

    private fun refreshGroups() {
        val showService = devPrefs.getBoolean(KEY_SERVICE_GROUP_VISIBLE, false)
        groups = storage.getGroupsForManage().filter { (code, _) ->
            showService || code != ContactPrefsStorage.GROUP_SERVICE
        }
        listGroups.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            groups.map { it.second },
        )
    }

    private fun showCreateGroupDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.group_name_hint)
            setTextColor(ContextCompat.getColor(this@GroupManagementActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@GroupManagementActivity, R.color.text_secondary))
            background = ContextCompat.getDrawable(this@GroupManagementActivity, R.drawable.bg_dialog_input)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.group_manage_create_title)
            .setView(input)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val code = storage.createGroup(input.text.toString())
                if (code == null) {
                    Toast.makeText(this, R.string.group_manage_name_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    AppEventLogger.info("GROUP", "Created group in full-screen manager code=$code")
                    refreshGroups()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showGroupActionsDialog(code: String, title: String) {
        if (code == ContactPrefsStorage.GROUP_SERVICE || code == ContactPrefsStorage.GROUP_FAVORITES) {
            Toast.makeText(this, R.string.group_manage_name_locked, Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf(getString(R.string.group_manage_rename), getString(R.string.group_manage_delete))
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameGroupDialog(code, title)
                    1 -> showDeleteGroupDialog(code, title)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showRenameGroupDialog(code: String, title: String) {
        val input = EditText(this).apply {
            hint = getString(R.string.group_name_hint)
            setText(title)
            setSelection(text.length)
            setTextColor(ContextCompat.getColor(this@GroupManagementActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@GroupManagementActivity, R.color.text_secondary))
            background = ContextCompat.getDrawable(this@GroupManagementActivity, R.drawable.bg_dialog_input)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.group_manage_rename_title)
            .setView(input)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val ok = storage.renameGroup(code, input.text.toString())
                if (!ok) {
                    Toast.makeText(this, R.string.group_manage_name_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    refreshGroups()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showDeleteGroupDialog(code: String, title: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.group_manage_delete_title)
            .setMessage(getString(R.string.group_manage_delete_message, title))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                val reassigned = storage.deleteGroup(code)
                Toast.makeText(this, getString(R.string.group_manage_delete_done, reassigned), Toast.LENGTH_SHORT).show()
                refreshGroups()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun applyAccentUi() {
        ThemeManager.applyGradientBackground(btnGroupsAdd, cornerDp = 12f)
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
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val DEV_PREFS_NAME = "contact_manager_dev_settings"
        private const val KEY_SERVICE_GROUP_VISIBLE = "service_group_visible"
    }
}
