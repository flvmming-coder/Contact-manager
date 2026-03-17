package com.example.contactmanagerdemo.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.core.AppEventLogger
import com.example.contactmanagerdemo.core.PhoneNumberFormatter
import com.example.contactmanagerdemo.data.Contact
import com.example.contactmanagerdemo.data.ContactPrefsStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ContactEditorActivity : AppCompatActivity() {

    private lateinit var storage: ContactPrefsStorage
    private var editingContact: Contact? = null
    private lateinit var editGroupCodes: List<String>
    private var selectedAvatarPhotoUri: String? = null
    private var selectedAvatarColor: String? = null
    private var avatarSeed: Long = System.currentTimeMillis()

    private lateinit var inputName: EditText
    private lateinit var inputLastName: EditText
    private lateinit var inputPhone: EditText
    private lateinit var inputEmail: EditText
    private lateinit var inputAddress: EditText
    private lateinit var inputBirthday: EditText
    private lateinit var inputComment: EditText
    private lateinit var spinnerGroup: Spinner
    private lateinit var imageAvatarPreview: ImageView
    private lateinit var textAvatarPreview: TextView
    private val devPrefs by lazy { getSharedPreferences(DEV_PREFS_NAME, MODE_PRIVATE) }

    private val pickAvatarPhotoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            selectedAvatarPhotoUri = uri.toString()
            updateAvatarPreview()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_editor)
        storage = ContactPrefsStorage(this)

        val contactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)
        editingContact = storage.getAllContacts().firstOrNull { it.id == contactId }
        avatarSeed = editingContact?.id ?: System.currentTimeMillis()
        selectedAvatarPhotoUri = editingContact?.avatarPhotoUri
        selectedAvatarColor = editingContact?.avatarColor

        val title = if (editingContact == null) getString(R.string.title_add_contact) else getString(R.string.title_edit_contact)
        findViewById<TextView>(R.id.textEditorTitle).text = title

        findViewById<ImageButton>(R.id.btnEditorBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnEditorSave).setOnClickListener { saveContact() }
        findViewById<Button>(R.id.btnEditorDelete).apply {
            visibility = if (editingContact == null) View.GONE else View.VISIBLE
            setOnClickListener { editingContact?.let { showDeleteDialog(it) } }
        }

        inputName = findViewById(R.id.inputName)
        inputLastName = findViewById(R.id.inputLastName)
        inputPhone = findViewById(R.id.inputPhone)
        inputEmail = findViewById(R.id.inputEmail)
        inputAddress = findViewById(R.id.inputAddress)
        inputBirthday = findViewById(R.id.inputBirthday)
        inputComment = findViewById(R.id.inputComment)
        spinnerGroup = findViewById(R.id.spinnerGroup)
        imageAvatarPreview = findViewById(R.id.imageAvatarPreview)
        textAvatarPreview = findViewById(R.id.textAvatarPreview)

        val showServiceGroup = devPrefs.getBoolean(KEY_SERVICE_GROUP_VISIBLE, false)
        editGroupCodes = storage.getEditableGroups().filter { code ->
            showServiceGroup || code != ContactPrefsStorage.GROUP_SERVICE
        }
        spinnerGroup.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            editGroupCodes.map { storage.getGroupTitle(it) },
        )

        findViewById<Button>(R.id.btnPickBirthday).setOnClickListener { showBirthdayDatePicker() }
        findViewById<Button>(R.id.btnPickAvatarPhoto).setOnClickListener { pickAvatarPhotoLauncher.launch(arrayOf("image/*")) }
        findViewById<Button>(R.id.btnClearAvatarPhoto).setOnClickListener {
            selectedAvatarPhotoUri = null
            updateAvatarPreview()
        }
        findViewById<View>(R.id.layoutAvatarColorRow).visibility = View.GONE

        setupPhoneInputMask()
        setupBirthdayInputMask()
        fillExistingData()
        updateAvatarPreview()
    }

    private fun fillExistingData() {
        val contact = editingContact ?: return
        inputName.setText(contact.name)
        inputLastName.setText(contact.lastName.orEmpty())
        inputPhone.setText(contact.phone)
        inputEmail.setText(contact.email.orEmpty())
        inputAddress.setText(contact.address.orEmpty())
        inputBirthday.setText(contact.birthday.orEmpty())
        inputComment.setText(contact.comment.orEmpty())
        val index = editGroupCodes.indexOf(contact.group).takeIf { it >= 0 }
            ?: editGroupCodes.indexOf(ContactPrefsStorage.GROUP_UNASSIGNED).takeIf { it >= 0 }
            ?: 0
        spinnerGroup.setSelection(index)
    }

    private fun saveContact() {
        val name = inputName.text.toString().trim()
        val lastName = inputLastName.text.toString().trim().ifBlank { null }
        val rawPhone = inputPhone.text.toString().trim()
        val phone = PhoneNumberFormatter.normalizeForStorage(rawPhone)
        val email = inputEmail.text.toString().trim().ifBlank { null }
        val address = inputAddress.text.toString().trim().ifBlank { null }
        val birthdayRaw = inputBirthday.text.toString().trim()
        val comment = inputComment.text.toString().trim().ifBlank { null }
        val groupCode = editGroupCodes.getOrElse(spinnerGroup.selectedItemPosition.takeIf { it >= 0 } ?: 0) {
            ContactPrefsStorage.GROUP_UNASSIGNED
        }

        var hasError = false
        if (name.isBlank()) {
            inputName.error = getString(R.string.error_required)
            hasError = true
        }
        if (phone.isBlank()) {
            inputPhone.error = getString(R.string.error_required)
            hasError = true
        } else if (phone != rawPhone) {
            inputPhone.setText(phone)
            inputPhone.setSelection(phone.length)
        }
        if (!email.isNullOrBlank() && !email.contains("@")) {
            inputEmail.error = getString(R.string.error_email_format)
            hasError = true
        }
        val birthday = if (birthdayRaw.isBlank()) {
            null
        } else {
            normalizeBirthday(birthdayRaw)?.also { inputBirthday.setText(it) } ?: run {
                inputBirthday.error = getString(R.string.error_birthday_format)
                hasError = true
                null
            }
        }
        if (!comment.isNullOrBlank() && comment.length > COMMENT_MAX_LENGTH) {
            inputComment.error = getString(R.string.error_comment_too_long)
            hasError = true
        }
        if (hasError) return

        val contact = Contact(
            id = editingContact?.id ?: System.currentTimeMillis(),
            name = name,
            lastName = lastName,
            phone = phone,
            email = email,
            address = address,
            birthday = birthday,
            comment = comment,
            avatarColor = selectedAvatarColor,
            avatarPhotoUri = selectedAvatarPhotoUri,
            group = groupCode,
            isImported = editingContact?.isImported ?: false,
        )
        storage.upsert(contact)
        AppEventLogger.info("DATA", "Saved contact from full-screen editor id=${contact.id}")
        setResult(RESULT_OK)
        finish()
    }

    private fun showDeleteDialog(contact: Contact) {
        val retentionDays = storage.getTrashRetentionDays()
        AlertDialog.Builder(this)
            .setTitle(R.string.title_delete_contact)
            .setMessage(getString(R.string.message_delete_contact, listOfNotNull(contact.name, contact.lastName).joinToString(" "), retentionDays))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                storage.moveContactToTrash(contact.id, retentionDays)
                AppEventLogger.info("DATA", "Deleted contact in editor id=${contact.id}")
                setResult(RESULT_OK)
                finish()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun updateAvatarPreview() {
        val uri = selectedAvatarPhotoUri?.trim().orEmpty()
        if (uri.isNotBlank()) {
            val shown = runCatching {
                imageAvatarPreview.setImageURI(Uri.parse(uri))
                true
            }.getOrDefault(false)
            if (shown) {
                imageAvatarPreview.visibility = View.VISIBLE
                textAvatarPreview.visibility = View.GONE
                return
            }
        }

        imageAvatarPreview.visibility = View.GONE
        textAvatarPreview.visibility = View.VISIBLE
        textAvatarPreview.text = buildInitials(inputName.text.toString(), inputLastName.text.toString())
        val colorInt = AvatarColorPalette.resolveColorInt(selectedAvatarColor, avatarSeed)
        textAvatarPreview.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 14f * resources.displayMetrics.density
            setColor(colorInt)
        }
    }

    private fun buildInitials(name: String, lastName: String?): String {
        val first = name.trim()
        val last = lastName?.trim().orEmpty()
        if (first.isBlank() && last.isBlank()) return "?"
        if (last.isNotBlank()) {
            return "${first.firstOrNull { !it.isWhitespace() } ?: '?'}${last.firstOrNull { !it.isWhitespace() } ?: '?'}"
                .uppercase(Locale.getDefault())
        }
        val chars = first.filter { !it.isWhitespace() }.take(2)
        return if (chars.isBlank()) "?" else chars.uppercase(Locale.getDefault())
    }

    private fun showBirthdayDatePicker() {
        val calendar = Calendar.getInstance()
        parseBirthdayToDate(inputBirthday.text.toString())?.let { calendar.time = it }
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val dateText = String.format(Locale.US, "%02d/%02d/%04d", dayOfMonth, month + 1, year)
                inputBirthday.setText(dateText)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun setupPhoneInputMask() {
        inputPhone.addTextChangedListener(object : TextWatcher {
            private var editing = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (editing) return
                val raw = s?.toString().orEmpty()
                if (raw.isBlank()) return
                if (PhoneNumberFormatter.isServiceNumber(raw)) return
                if (PhoneNumberFormatter.isForeignInternational(raw)) return

                val formatted = PhoneNumberFormatter.formatRuKzMask(raw)
                if (formatted == raw) return

                editing = true
                s?.replace(0, s.length, formatted)
                inputPhone.setSelection(formatted.length)
                editing = false
            }
        })
    }

    private fun setupBirthdayInputMask() {
        inputBirthday.addTextChangedListener(object : TextWatcher {
            private var editing = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (editing) return
                editing = true
                val digits = s?.toString().orEmpty().filter { it.isDigit() }.take(8)
                val builder = StringBuilder()
                digits.forEachIndexed { index, ch ->
                    builder.append(ch)
                    if (index == 1 || index == 3) builder.append('/')
                }
                val formatted = builder.toString()
                if (formatted != s?.toString().orEmpty()) {
                    s?.replace(0, s.length, formatted)
                }
                editing = false
            }
        })
    }

    private fun normalizeBirthday(rawValue: String): String? {
        val supportedFormats = listOf("dd/MM/yyyy", "yyyy-MM-dd")
        val parsedDate = supportedFormats.asSequence()
            .mapNotNull { pattern ->
                runCatching {
                    val parser = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                    parser.parse(rawValue)
                }.getOrNull()
            }
            .firstOrNull() ?: return null
        return SimpleDateFormat("dd/MM/yyyy", Locale.US).format(parsedDate)
    }

    private fun parseBirthdayToDate(rawValue: String): Date? {
        val normalized = normalizeBirthday(rawValue) ?: return null
        return runCatching {
            SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { isLenient = false }.parse(normalized)
        }.getOrNull()
    }

    companion object {
        const val EXTRA_CONTACT_ID = "extra_contact_id"
        private const val COMMENT_MAX_LENGTH = 512
        private const val DEV_PREFS_NAME = "contact_manager_dev_settings"
        private const val KEY_SERVICE_GROUP_VISIBLE = "service_group_visible"
    }
}
