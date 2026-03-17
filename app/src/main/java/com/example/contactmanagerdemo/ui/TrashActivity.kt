package com.example.contactmanagerdemo.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.data.ContactPrefsStorage
import com.example.contactmanagerdemo.data.DeletedContactEntry

class TrashActivity : AppCompatActivity() {

    private lateinit var storage: ContactPrefsStorage
    private lateinit var listTrash: ListView
    private var entries: List<DeletedContactEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)
        storage = ContactPrefsStorage(this)

        listTrash = findViewById(R.id.listTrash)
        findViewById<ImageButton>(R.id.btnTrashBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnTrashRetention).setOnClickListener { showTrashRetentionDialog() }
        findViewById<Button>(R.id.btnTrashClear).setOnClickListener { confirmClearTrash() }

        listTrash.setOnItemClickListener { _, _, position, _ ->
            val entry = entries.getOrNull(position) ?: return@setOnItemClickListener
            showTrashEntryActions(entry)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTrashList()
    }

    private fun refreshTrashList() {
        entries = storage.getTrashContacts()
        val now = System.currentTimeMillis()
        val rows = entries.map { entry ->
            val title = listOfNotNull(entry.contact.name, entry.contact.lastName).joinToString(" ").trim()
            getString(R.string.trash_contact_item, title, entry.remainingDays(now))
        }
        listTrash.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            rows,
        )
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
                refreshTrashList()
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
                refreshTrashList()
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
                refreshTrashList()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
