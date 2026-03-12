package com.example.contactmanagerdemo.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.data.Contact
import com.example.contactmanagerdemo.data.ContactRepository

class EditContactActivity : AppCompatActivity() {

    private lateinit var repo: ContactRepository
    private var editingId: Long? = null

    private lateinit var inputFirstName: EditText
    private lateinit var inputLastName: EditText
    private lateinit var inputPhone: EditText
    private lateinit var inputEmail: EditText
    private lateinit var inputGroup: EditText
    private lateinit var checkWork: CheckBox
    private lateinit var inputWorkTask: EditText
    private lateinit var inputAddress: EditText
    private lateinit var inputBirthday: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_contact)

        repo = ContactRepository(this)

        inputFirstName = findViewById(R.id.inputFirstName)
        inputLastName = findViewById(R.id.inputLastName)
        inputPhone = findViewById(R.id.inputPhone)
        inputEmail = findViewById(R.id.inputEmail)
        inputGroup = findViewById(R.id.inputGroup)
        checkWork = findViewById(R.id.checkWork)
        inputWorkTask = findViewById(R.id.inputWorkTask)
        inputAddress = findViewById(R.id.inputAddress)
        inputBirthday = findViewById(R.id.inputBirthday)

        findViewById<Button>(R.id.btnSave).setOnClickListener { saveContact() }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }

        checkWork.setOnCheckedChangeListener { _, isChecked ->
            inputWorkTask.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        editingId = intent.getLongExtra(EXTRA_CONTACT_ID, -1).takeIf { it > 0 }
        if (editingId != null) {
            loadContact(editingId!!)
        } else {
            inputWorkTask.visibility = View.GONE
        }
    }

    private fun loadContact(id: Long) {
        val contact = repo.getContactById(id) ?: return
        inputFirstName.setText(contact.firstName)
        inputLastName.setText(contact.lastName)
        inputPhone.setText(contact.phone)
        inputEmail.setText(contact.email)
        inputGroup.setText(contact.group)
        checkWork.isChecked = contact.isWorkContact
        inputWorkTask.setText(contact.workTask)
        inputAddress.setText(contact.address)
        inputBirthday.setText(contact.birthday)
        inputWorkTask.visibility = if (contact.isWorkContact) View.VISIBLE else View.GONE
    }

    private fun saveContact() {
        val firstName = inputFirstName.text.toString().trim()
        val lastName = inputLastName.text.toString().trim().ifEmpty { null }
        val phone = inputPhone.text.toString().trim()
        val email = inputEmail.text.toString().trim().ifEmpty { null }
        val group = inputGroup.text.toString().trim().ifEmpty { "other" }
        val isWork = checkWork.isChecked
        val workTask = inputWorkTask.text.toString().trim().ifEmpty { null }
        val address = inputAddress.text.toString().trim().ifEmpty { null }
        val birthday = inputBirthday.text.toString().trim().ifEmpty { null }

        if (firstName.isEmpty() || phone.isEmpty()) {
            // Minimal validation: name and phone
            return
        }

        val now = System.currentTimeMillis()
        val existing = editingId?.let { repo.getContactById(it) }
        val createdAt = existing?.createdAt ?: now

        val contact = Contact(
            id = editingId ?: 0,
            firstName = firstName,
            lastName = lastName,
            phone = phone,
            email = email,
            group = group,
            isWorkContact = isWork,
            workTask = if (isWork) workTask else null,
            address = address,
            birthday = birthday,
            imported = existing?.imported ?: false,
            createdAt = createdAt,
            updatedAt = now
        )

        if (editingId == null) {
            repo.insertContact(contact)
        } else {
            repo.updateContact(contact)
        }

        finish()
    }

    companion object {
        const val EXTRA_CONTACT_ID = "contact_id"
    }
}
