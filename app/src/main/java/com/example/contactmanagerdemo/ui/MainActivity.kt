package com.example.contactmanagerdemo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.data.Contact
import com.example.contactmanagerdemo.data.ContactPrefsStorage
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var storage: ContactPrefsStorage
    private lateinit var adapter: ContactAdapter

    private lateinit var textStats: TextView
    private lateinit var inputSearch: EditText
    private lateinit var spinnerFilter: Spinner
    private lateinit var textEmpty: TextView

    private var allContacts: MutableList<Contact> = mutableListOf()
    private lateinit var filterGroupCodes: List<String>
    private lateinit var editGroupCodes: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage = ContactPrefsStorage(this)

        textStats = findViewById(R.id.textStats)
        inputSearch = findViewById(R.id.inputSearch)
        spinnerFilter = findViewById(R.id.spinnerFilter)
        textEmpty = findViewById(R.id.textEmpty)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerContacts)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = ContactAdapter(
            onEdit = { contact -> showContactDialog(contact) },
            onDelete = { contact -> showDeleteDialog(contact) },
            mapGroupLabel = { mapGroupLabel(it) },
        )
        recyclerView.adapter = adapter

        setupFilterSpinner()

        findViewById<Button>(R.id.btnAddContact).setOnClickListener {
            showContactDialog(null)
        }
        inputSearch.doAfterTextChanged { renderContacts() }

        migrateContactsIfNeeded()
        loadContactsAndRender()
    }

    override fun onResume() {
        super.onResume()
        loadContactsAndRender()
    }

    private fun setupFilterSpinner() {
        filterGroupCodes = storage.getFilterGroups()
        val labels = filterGroupCodes.map { mapGroupLabel(it) }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinnerFilter.adapter = spinnerAdapter
        spinnerFilter.setSelection(0)
        spinnerFilter.onItemSelectedListener = SimpleItemSelectedListener {
            renderContacts()
        }

        editGroupCodes = storage.getAvailableGroups()
    }

    private fun loadContactsAndRender() {
        allContacts = storage.getAllContacts()
        updateStats()
        renderContacts()
    }

    private fun renderContacts() {
        val selectedIndex = spinnerFilter.selectedItemPosition.takeIf { it >= 0 } ?: 0
        val selectedGroupCode = filterGroupCodes.getOrElse(selectedIndex) { ContactPrefsStorage.GROUP_ALL }
        val query = inputSearch.text.toString().trim().lowercase(Locale.getDefault())

        val list = allContacts
            .filter { selectedGroupCode == ContactPrefsStorage.GROUP_ALL || it.group == selectedGroupCode }
            .filter {
                query.isBlank() ||
                    it.name.lowercase(Locale.getDefault()).contains(query) ||
                    it.phone.lowercase(Locale.getDefault()).contains(query)
            }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }

        adapter.submitList(list)
        textEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateStats() {
        val total = allContacts.size
        val imported = allContacts.count { it.isImported }
        textStats.text = getString(R.string.stats_contacts, total, imported)
    }

    private fun showContactDialog(contact: Contact?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_contact, null)
        val inputName = dialogView.findViewById<EditText>(R.id.inputName)
        val inputPhone = dialogView.findViewById<EditText>(R.id.inputPhone)
        val spinnerGroup = dialogView.findViewById<Spinner>(R.id.spinnerGroup)

        val groupLabels = editGroupCodes.map { mapGroupLabel(it) }
        spinnerGroup.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, groupLabels)

        if (contact != null) {
            inputName.setText(contact.name)
            inputPhone.setText(contact.phone)
            val index = editGroupCodes.indexOf(contact.group).takeIf { it >= 0 } ?: 0
            spinnerGroup.setSelection(index)
        }

        val title = if (contact == null) getString(R.string.title_add_contact) else getString(R.string.title_edit_contact)

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .apply {
                if (contact != null) {
                    setNeutralButton(R.string.action_delete, null)
                }
            }
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val name = inputName.text.toString().trim()
                val phone = inputPhone.text.toString().trim()
                val groupIndex = spinnerGroup.selectedItemPosition.takeIf { it >= 0 } ?: 0
                val group = editGroupCodes.getOrElse(groupIndex) { ContactPrefsStorage.GROUP_OTHER }

                var hasError = false
                if (name.isBlank()) {
                    inputName.error = getString(R.string.error_required)
                    hasError = true
                }
                if (phone.isBlank()) {
                    inputPhone.error = getString(R.string.error_required)
                    hasError = true
                }

                if (hasError) return@setOnClickListener

                val newContact = Contact(
                    id = contact?.id ?: System.currentTimeMillis(),
                    name = name,
                    phone = phone,
                    group = group,
                    isImported = contact?.isImported ?: false,
                )
                storage.upsert(newContact)
                loadContactsAndRender()
                dialog.dismiss()
            }

            if (contact != null) {
                val neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                neutral.setOnClickListener {
                    showDeleteDialog(contact)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showDeleteDialog(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle(R.string.title_delete_contact)
            .setMessage(getString(R.string.message_delete_contact, contact.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                storage.delete(contact.id)
                loadContactsAndRender()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun mapGroupLabel(code: String): String {
        return when (code) {
            ContactPrefsStorage.GROUP_ALL -> getString(R.string.group_all)
            ContactPrefsStorage.GROUP_FAMILY -> getString(R.string.group_family)
            ContactPrefsStorage.GROUP_FRIENDS -> getString(R.string.group_friends)
            ContactPrefsStorage.GROUP_WORK -> getString(R.string.group_work)
            else -> getString(R.string.group_other)
        }
    }

    private fun migrateContactsIfNeeded() {
        val contacts = storage.getAllContacts()
        val repaired = contacts.map {
            it.copy(
                name = repairMojibake(it.name).ifBlank { it.name },
                phone = repairMojibake(it.phone).ifBlank { it.phone },
            )
        }
        if (repaired != contacts) {
            storage.saveAllContacts(repaired)
            Toast.makeText(this, R.string.message_encoding_repaired, Toast.LENGTH_SHORT).show()
        }
    }

    private fun repairMojibake(value: String): String {
        if (value.isBlank()) return value
        val looksBroken = value.contains('\u00D0') || value.contains('\u00D1') || value.contains('\u00C3') || value.contains('\uFFFD')
        if (!looksBroken) return value

        return try {
            String(value.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        } catch (_: Exception) {
            value
        }
    }
}
