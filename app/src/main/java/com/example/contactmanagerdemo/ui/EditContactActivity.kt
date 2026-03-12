package com.example.contactmanagerdemo.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.data.Contact
import com.example.contactmanagerdemo.data.ContactPrefsStorage

class EditContactActivity : AppCompatActivity() {

    private lateinit var storage: ContactPrefsStorage
    private var editingId: Long? = null

    private lateinit var editTitle: TextView
    private lateinit var inputFirstName: EditText
    private lateinit var inputLastName: EditText
    private lateinit var inputPhone: EditText
    private lateinit var inputEmail: EditText
    private lateinit var inputGroup: Spinner
    private lateinit var inputAddress: EditText
    private lateinit var inputBirthday: EditText
    private lateinit var btnDelete: Button

    private val groupCodes = listOf("family", "friends", "work", "other")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_contact)

        storage = ContactPrefsStorage(this)

        editTitle = findViewById(R.id.editTitle)
        inputFirstName = findViewById(R.id.inputFirstName)
        inputLastName = findViewById(R.id.inputLastName)
        inputPhone = findViewById(R.id.inputPhone)
        inputEmail = findViewById(R.id.inputEmail)
        inputGroup = findViewById(R.id.inputGroup)
        inputAddress = findViewById(R.id.inputAddress)
        inputBirthday = findViewById(R.id.inputBirthday)
        btnDelete = findViewById(R.id.btnDelete)

        val groupTitles = resources.getStringArray(R.array.contact_group_titles)
        inputGroup.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, groupTitles)

        findViewById<Button>(R.id.btnSave).setOnClickListener { saveContact() }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
        btnDelete.setOnClickListener { confirmDelete() }

        editingId = intent.getLongExtra(EXTRA_CONTACT_ID, -1).takeIf { it > 0 }
        if (editingId != null) {
            editTitle.text = "Редактировать"
            btnDelete.visibility = View.VISIBLE
            loadContact(editingId!!)
        }
    }

    private fun loadContact(id: Long) {
        val contact = storage.getContactById(id) ?: return
        inputFirstName.setText(contact.firstName)
        inputLastName.setText(contact.lastName)
        inputPhone.setText(contact.phone)
        inputEmail.setText(contact.email)
        inputAddress.setText(contact.address)
        inputBirthday.setText(contact.birthday)

        val groupIndex = groupCodes.indexOf(contact.group).takeIf { it >= 0 } ?: groupCodes.lastIndex
        inputGroup.setSelection(groupIndex)
    }

    private fun saveContact() {
        val firstName = inputFirstName.text.toString().trim()
        val lastName = inputLastName.text.toString().trim().ifBlank { null }
        val phone = inputPhone.text.toString().trim()
        val email = inputEmail.text.toString().trim().ifBlank { null }
        val group = groupCodes[inputGroup.selectedItemPosition]
        val address = inputAddress.text.toString().trim().ifBlank { null }
        val birthday = inputBirthday.text.toString().trim().ifBlank { null }

        if (firstName.isBlank() || phone.isBlank()) {
            return
        }

        val now = System.currentTimeMillis()
        val old = editingId?.let { storage.getContactById(it) }

        val contact = Contact(
            id = editingId ?: now,
            firstName = firstName,
            lastName = lastName,
            phone = phone,
            email = email,
            group = group,
            isWorkContact = group == "work",
            workTask = old?.workTask,
            address = address,
            birthday = birthday,
            imported = old?.imported ?: false,
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
        )

        storage.upsert(contact)
        finish()
    }

    private fun confirmDelete() {
        val id = editingId ?: return
        AlertDialog.Builder(this)
            .setTitle("Удалить контакт?")
            .setPositiveButton("Удалить") { _, _ ->
                storage.delete(id)
                finish()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    companion object {
        const val EXTRA_CONTACT_ID = "contact_id"
    }
}
