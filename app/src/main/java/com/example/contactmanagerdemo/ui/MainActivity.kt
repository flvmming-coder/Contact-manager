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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.data.Contact
import com.example.contactmanagerdemo.data.ContactPrefsStorage
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var storage: ContactPrefsStorage
    private lateinit var adapter: ContactAdapter

    private lateinit var spinnerFilter: Spinner
    private lateinit var textEmpty: TextView

    private var allContacts: MutableList<Contact> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage = ContactPrefsStorage(this)

        spinnerFilter = findViewById(R.id.spinnerFilter)
        textEmpty = findViewById(R.id.textEmpty)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerContacts)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = ContactAdapter(
            onEdit = { contact -> showContactDialog(contact) },
            onDelete = { contact -> showDeleteDialog(contact) },
        )
        recyclerView.adapter = adapter

        setupFilterSpinner()

        findViewById<Button>(R.id.btnAddContact).setOnClickListener {
            showContactDialog(null)
        }

        loadContactsAndRender()
    }

    override fun onResume() {
        super.onResume()
        loadContactsAndRender()
    }

    private fun setupFilterSpinner() {
        val groups = storage.getFilterGroups()
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, groups)
        spinnerFilter.adapter = spinnerAdapter
        spinnerFilter.setSelection(0)
        spinnerFilter.onItemSelectedListener = SimpleItemSelectedListener {
            renderContacts()
        }
    }

    private fun loadContactsAndRender() {
        allContacts = storage.getAllContacts()
        renderContacts()
    }

    private fun renderContacts() {
        val selectedGroup = spinnerFilter.selectedItem?.toString() ?: ContactPrefsStorage.GROUP_ALL
        val list = allContacts
            .filter { selectedGroup == ContactPrefsStorage.GROUP_ALL || it.group == selectedGroup }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }

        adapter.submitList(list)
        textEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showContactDialog(contact: Contact?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_contact, null)
        val inputName = dialogView.findViewById<EditText>(R.id.inputName)
        val inputPhone = dialogView.findViewById<EditText>(R.id.inputPhone)
        val spinnerGroup = dialogView.findViewById<Spinner>(R.id.spinnerGroup)

        val groups = storage.getAvailableGroups()
        spinnerGroup.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, groups)

        if (contact != null) {
            inputName.setText(contact.name)
            inputPhone.setText(contact.phone)
            val index = groups.indexOf(contact.group).takeIf { it >= 0 } ?: 0
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
                val group = spinnerGroup.selectedItem?.toString().orEmpty()

                var hasError = false
                if (name.isBlank()) {
                    inputName.error = getString(R.string.error_required)
                    hasError = true
                }
                if (phone.isBlank()) {
                    inputPhone.error = getString(R.string.error_required)
                    hasError = true
                }
                if (group.isBlank()) {
                    Toast.makeText(this, R.string.error_group, Toast.LENGTH_SHORT).show()
                    hasError = true
                }

                if (hasError) return@setOnClickListener

                val newContact = Contact(
                    id = contact?.id ?: System.currentTimeMillis(),
                    name = name,
                    phone = phone,
                    group = group,
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
}
