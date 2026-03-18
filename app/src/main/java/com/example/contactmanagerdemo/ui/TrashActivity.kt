package com.example.contactmanagerdemo.ui

import android.os.Bundle
import android.os.SystemClock
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.core.AppEventLogger
import com.example.contactmanagerdemo.core.ThemeManager
import com.example.contactmanagerdemo.data.ContactPrefsStorage
import com.example.contactmanagerdemo.data.DeletedContactEntry

class TrashActivity : AppCompatActivity() {

    private lateinit var storage: ContactPrefsStorage
    private lateinit var listTrash: ListView
    private lateinit var btnTrashClear: Button
    private var entries: List<DeletedContactEntry> = emptyList()
    private var lastTappedContactId: Long = -1L
    private var lastTapAtMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)
        storage = ContactPrefsStorage(this)

        listTrash = findViewById(R.id.listTrash)
        findViewById<ImageButton>(R.id.btnTrashBack).setOnClickListener { finish() }
        btnTrashClear = findViewById<Button>(R.id.btnTrashClear).also {
            it.setOnClickListener { confirmClearTrash() }
        }

        listTrash.setOnItemClickListener { _, _, position, _ ->
            val entry = entries.getOrNull(position) ?: return@setOnItemClickListener
            val now = SystemClock.elapsedRealtime()
            if (lastTappedContactId == entry.contact.id && now - lastTapAtMs <= DOUBLE_TAP_WINDOW_MS) {
                lastTappedContactId = -1L
                lastTapAtMs = 0L
                openReadOnlyContact(entry)
            } else {
                lastTappedContactId = entry.contact.id
                lastTapAtMs = now
            }
        }
        listTrash.setOnItemLongClickListener { _, _, position, _ ->
            val entry = entries.getOrNull(position) ?: return@setOnItemLongClickListener true
            showTrashEntryDetails(entry)
            true
        }
        applyAccentUi()
    }

    override fun onResume() {
        super.onResume()
        applyAccentUi()
        refreshTrashList()
    }

    private fun applyAccentUi() {
        ThemeManager.applyGradientBackground(btnTrashClear, cornerDp = 12f)
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

    private fun showTrashEntryDetails(entry: DeletedContactEntry) {
        val title = listOfNotNull(entry.contact.name, entry.contact.lastName).joinToString(" ").trim()
        val details = buildString {
            appendLine(getString(R.string.trash_details_phone, entry.contact.phone))
            appendLine(getString(R.string.trash_details_email, entry.contact.email.orEmpty().ifBlank { "—" }))
            appendLine(getString(R.string.trash_details_address, entry.contact.address.orEmpty().ifBlank { "—" }))
            appendLine(getString(R.string.trash_details_birthday, entry.contact.birthday.orEmpty().ifBlank { "—" }))
            appendLine(getString(R.string.trash_details_group, storage.getGroupTitle(entry.contact.group)))
            appendLine(getString(R.string.trash_details_comment, entry.contact.comment.orEmpty().ifBlank { "—" }))
            appendLine(getString(R.string.trash_details_edit_hint))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(details.trim())
            .setPositiveButton(R.string.trash_action_restore) { _, _ ->
                if (storage.restoreFromTrash(entry.contact.id)) {
                    AppEventLogger.info("TRASH", "Contact restored id=${entry.contact.id}")
                    Toast.makeText(this, R.string.trash_restore_done, Toast.LENGTH_SHORT).show()
                    refreshTrashList()
                }
            }
            .setNeutralButton(R.string.trash_action_delete_forever) { _, _ ->
                if (storage.deleteFromTrash(entry.contact.id)) {
                    AppEventLogger.info("TRASH", "Contact deleted forever id=${entry.contact.id}")
                    Toast.makeText(this, R.string.trash_delete_forever_done, Toast.LENGTH_SHORT).show()
                    refreshTrashList()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun confirmClearTrash() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.trash_clear_title)
            .setMessage(R.string.trash_clear_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                storage.clearTrash()
                AppEventLogger.warn("TRASH", "Trash cleared by user")
                Toast.makeText(this, R.string.trash_clear_done, Toast.LENGTH_SHORT).show()
                refreshTrashList()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun openReadOnlyContact(entry: DeletedContactEntry) {
        val intent = ContactEditorActivity.buildReadOnlyIntent(this, entry.contact)
        startActivity(intent)
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
        private const val DOUBLE_TAP_WINDOW_MS = 1_000L
    }
}
