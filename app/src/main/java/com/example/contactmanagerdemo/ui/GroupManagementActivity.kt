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
import com.example.contactmanagerdemo.data.ContactPrefsStorage

class GroupManagementActivity : AppCompatActivity() {

    private lateinit var storage: ContactPrefsStorage
    private lateinit var listGroups: ListView
    private var groups: List<Pair<String, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_management)
        storage = ContactPrefsStorage(this)
        listGroups = findViewById(R.id.listGroups)

        findViewById<ImageButton>(R.id.btnGroupsBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnGroupsAdd).setOnClickListener { showCreateGroupDialog() }
        listGroups.setOnItemClickListener { _, _, position, _ ->
            val entry = groups.getOrNull(position) ?: return@setOnItemClickListener
            showGroupActionsDialog(entry.first, entry.second)
        }

        refreshGroups()
    }

    private fun refreshGroups() {
        groups = storage.getGroupsForManage()
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
        AlertDialog.Builder(this)
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
            .show()
    }

    private fun showGroupActionsDialog(code: String, title: String) {
        val options = arrayOf(getString(R.string.group_manage_rename), getString(R.string.group_manage_delete))
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameGroupDialog(code, title)
                    1 -> showDeleteGroupDialog(code, title)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
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
        AlertDialog.Builder(this)
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
            .show()
    }

    private fun showDeleteGroupDialog(code: String, title: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.group_manage_delete_title)
            .setMessage(getString(R.string.group_manage_delete_message, title))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                val reassigned = storage.deleteGroup(code)
                Toast.makeText(this, getString(R.string.group_manage_delete_done, reassigned), Toast.LENGTH_SHORT).show()
                refreshGroups()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
