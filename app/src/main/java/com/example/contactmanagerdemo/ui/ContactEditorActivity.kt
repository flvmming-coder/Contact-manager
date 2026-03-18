package com.example.contactmanagerdemo.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.core.AppEventLogger
import com.example.contactmanagerdemo.core.ContactQrCodec
import com.example.contactmanagerdemo.core.PhoneNumberFormatter
import com.example.contactmanagerdemo.core.QrAvatarCodec
import com.example.contactmanagerdemo.core.ThemeManager
import com.example.contactmanagerdemo.data.Contact
import com.example.contactmanagerdemo.data.ContactPrefsStorage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
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
    private lateinit var inputCustomGroupTitle: EditText
    private lateinit var spinnerGroup: Spinner
    private lateinit var imageAvatarPreview: ImageView
    private lateinit var textAvatarPreview: TextView
    private lateinit var btnEditorSave: Button
    private lateinit var btnEditorDelete: Button
    private lateinit var btnPickBirthday: Button
    private lateinit var btnPickAvatarPhoto: Button
    private lateinit var btnClearAvatarPhoto: Button
    private lateinit var btnScanQr: Button
    private lateinit var btnGenerateQr: Button
    private var readOnlyMode: Boolean = false
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

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchQrScanner()
            } else {
                Toast.makeText(this, R.string.qr_camera_permission_required, Toast.LENGTH_SHORT).show()
                AppEventLogger.warn("QR", "Camera permission denied in editor")
            }
        }

    private val qrScanLauncher =
        registerForActivityResult(ScanContract()) { result ->
            val payload = result.contents.orEmpty()
            if (payload.isBlank()) return@registerForActivityResult
            applyScannedQrPayload(payload)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_editor)
        storage = ContactPrefsStorage(this)
        readOnlyMode = intent.getBooleanExtra(EXTRA_READ_ONLY_MODE, false)

        val contactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)
        editingContact = if (readOnlyMode) {
            parseReadOnlyContact(intent.getStringExtra(EXTRA_READ_ONLY_CONTACT_JSON))
        } else {
            storage.getAllContacts().firstOrNull { it.id == contactId }
        }
        if (readOnlyMode && editingContact == null) {
            Toast.makeText(this, R.string.error_startup_title, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        avatarSeed = editingContact?.id ?: System.currentTimeMillis()
        selectedAvatarPhotoUri = editingContact?.avatarPhotoUri
        selectedAvatarColor = editingContact?.avatarColor

        val title = when {
            readOnlyMode -> getString(R.string.title_view_deleted_contact)
            editingContact == null -> getString(R.string.title_add_contact)
            else -> getString(R.string.title_edit_contact)
        }
        findViewById<TextView>(R.id.textEditorTitle).text = title

        findViewById<ImageButton>(R.id.btnEditorBack).setOnClickListener { finish() }
        btnEditorSave = findViewById<Button>(R.id.btnEditorSave).also {
            it.setOnClickListener { saveContact() }
        }
        btnEditorDelete = findViewById<Button>(R.id.btnEditorDelete).apply {
            visibility = if (editingContact == null || readOnlyMode) View.GONE else View.VISIBLE
            setOnClickListener { editingContact?.let { showDeleteDialog(it) } }
        }
        if (readOnlyMode) {
            btnEditorSave.visibility = View.GONE
        }

        inputName = findViewById(R.id.inputName)
        inputLastName = findViewById(R.id.inputLastName)
        inputPhone = findViewById(R.id.inputPhone)
        inputEmail = findViewById(R.id.inputEmail)
        inputAddress = findViewById(R.id.inputAddress)
        inputBirthday = findViewById(R.id.inputBirthday)
        inputComment = findViewById(R.id.inputComment)
        inputCustomGroupTitle = findViewById(R.id.inputCustomGroupTitle)
        spinnerGroup = findViewById(R.id.spinnerGroup)
        imageAvatarPreview = findViewById(R.id.imageAvatarPreview)
        textAvatarPreview = findViewById(R.id.textAvatarPreview)

        val showServiceGroup = devPrefs.getBoolean(KEY_SERVICE_GROUP_VISIBLE, false)
        editGroupCodes = storage.getEditableGroups().filter { code ->
            showServiceGroup || code != ContactPrefsStorage.GROUP_SERVICE
        }
        if (readOnlyMode) {
            val currentGroup = editingContact?.group
            if (!currentGroup.isNullOrBlank() && !editGroupCodes.contains(currentGroup)) {
                editGroupCodes = listOf(currentGroup) + editGroupCodes
            }
        }
        spinnerGroup.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            editGroupCodes.map { storage.getGroupTitle(it) },
        )
        spinnerGroup.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val code = editGroupCodes.getOrElse(position) { ContactPrefsStorage.GROUP_UNASSIGNED }
                val showCustomGroup = code == ContactPrefsStorage.GROUP_OTHER
                inputCustomGroupTitle.visibility = if (showCustomGroup) View.VISIBLE else View.GONE
                if (!showCustomGroup) {
                    inputCustomGroupTitle.text?.clear()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        btnPickBirthday = findViewById<Button>(R.id.btnPickBirthday).also {
            it.setOnClickListener { showBirthdayDatePicker() }
        }
        btnPickAvatarPhoto = findViewById<Button>(R.id.btnPickAvatarPhoto).also {
            it.setOnClickListener { pickAvatarPhotoLauncher.launch(arrayOf("image/*")) }
        }
        btnClearAvatarPhoto = findViewById<Button>(R.id.btnClearAvatarPhoto).also {
            it.setOnClickListener {
                selectedAvatarPhotoUri = null
                updateAvatarPreview()
            }
        }
        btnScanQr = findViewById<Button>(R.id.btnScanQr).also {
            it.visibility = if (!readOnlyMode && editingContact == null) View.VISIBLE else View.GONE
            it.setOnClickListener { startQrScanFlow() }
        }
        btnGenerateQr = findViewById<Button>(R.id.btnGenerateQr).also {
            it.visibility = if (!readOnlyMode && editingContact != null) View.VISIBLE else View.GONE
            it.setOnClickListener { showCurrentContactQr() }
        }
        findViewById<View>(R.id.layoutAvatarColorRow).visibility = View.GONE

        setupPhoneInputMask()
        setupBirthdayInputMask()
        fillExistingData()
        updateAvatarPreview()
        applyAccentUi()
        if (readOnlyMode) {
            enableReadOnlyState()
        }
    }

    override fun onResume() {
        super.onResume()
        applyAccentUi()
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
        val normalizedGroup = if (contact.group == ContactPrefsStorage.GROUP_OTHER) {
            ContactPrefsStorage.GROUP_UNASSIGNED
        } else {
            contact.group
        }
        val index = editGroupCodes.indexOf(normalizedGroup).takeIf { it >= 0 }
            ?: editGroupCodes.indexOf(ContactPrefsStorage.GROUP_UNASSIGNED).takeIf { it >= 0 }
            ?: 0
        spinnerGroup.setSelection(index)
    }

    private fun applyAccentUi() {
        ThemeManager.applyGradientBackground(btnEditorSave, cornerDp = 12f)
        ThemeManager.applyGradientBackground(btnEditorDelete, cornerDp = 12f)
        ThemeManager.applyGradientBackground(btnPickBirthday, cornerDp = 12f)
        ThemeManager.applyGradientBackground(btnPickAvatarPhoto, cornerDp = 12f)
        ThemeManager.applyGradientBackground(btnClearAvatarPhoto, cornerDp = 12f)
        ThemeManager.applyGradientBackground(btnScanQr, cornerDp = 12f)
        ThemeManager.applyGradientBackground(btnGenerateQr, cornerDp = 12f)
    }

    private fun enableReadOnlyState() {
        val fields = listOf(
            inputName,
            inputLastName,
            inputPhone,
            inputEmail,
            inputAddress,
            inputBirthday,
            inputComment,
            inputCustomGroupTitle,
        )
        fields.forEach {
            it.isEnabled = false
            it.isFocusable = false
            it.isFocusableInTouchMode = false
            it.isLongClickable = false
            it.isClickable = false
        }
        spinnerGroup.isEnabled = false
        btnPickBirthday.visibility = View.GONE
        btnPickAvatarPhoto.visibility = View.GONE
        btnClearAvatarPhoto.visibility = View.GONE
        btnScanQr.visibility = View.GONE
        btnGenerateQr.visibility = View.GONE
    }

    private fun saveContact() {
        if (readOnlyMode) return
        val name = inputName.text.toString().trim()
        val lastName = inputLastName.text.toString().trim().ifBlank { null }
        val rawPhone = inputPhone.text.toString().trim()
        val phone = PhoneNumberFormatter.normalizeForStorage(rawPhone)
        val email = inputEmail.text.toString().trim().ifBlank { null }
        val address = inputAddress.text.toString().trim().ifBlank { null }
        val birthdayRaw = inputBirthday.text.toString().trim()
        val comment = inputComment.text.toString().trim().ifBlank { null }
        var groupCode = editGroupCodes.getOrElse(spinnerGroup.selectedItemPosition.takeIf { it >= 0 } ?: 0) {
            ContactPrefsStorage.GROUP_UNASSIGNED
        }
        val customGroupTitle = inputCustomGroupTitle.text.toString().trim()

        var hasError = false
        if (groupCode == ContactPrefsStorage.GROUP_OTHER) {
            if (customGroupTitle.isBlank()) {
                inputCustomGroupTitle.error = getString(R.string.error_required)
                hasError = true
            } else {
                val created = storage.createGroup(customGroupTitle)
                if (created == null) {
                    inputCustomGroupTitle.error = getString(R.string.group_manage_name_invalid)
                    hasError = true
                } else {
                    groupCode = created
                }
            }
        }
        if (name.isBlank()) {
            inputName.error = getString(R.string.error_required)
            hasError = true
        }
        if (phone.isBlank()) {
            inputPhone.error = getString(R.string.error_required)
            hasError = true
        } else if (phone.startsWith("+7") && phone.filter { it.isDigit() }.length <= 1) {
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
            isFavorite = editingContact?.isFavorite ?: false,
            isImported = editingContact?.isImported ?: false,
        )
        storage.upsert(contact)
        AppEventLogger.info("DATA", "Saved contact from full-screen editor id=${contact.id}")
        setResult(RESULT_OK)
        finish()
    }

    private fun showDeleteDialog(contact: Contact) {
        val retentionDays = storage.getTrashRetentionDays()
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.title_delete_contact)
            .setMessage(getString(R.string.message_delete_contact, listOfNotNull(contact.name, contact.lastName).joinToString(" "), retentionDays))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                storage.moveContactToTrash(contact.id, retentionDays)
                AppEventLogger.info("DATA", "Deleted contact in editor id=${contact.id}")
                setResult(RESULT_OK)
                finish()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun updateAvatarPreview() {
        val uri = selectedAvatarPhotoUri?.trim().orEmpty()
        if (uri.isNotBlank()) {
            val shown = runCatching {
                imageAvatarPreview.setImageURI(Uri.parse(uri))
                ThemeManager.applyColorVisionFilter(imageAvatarPreview, this)
                true
            }.getOrDefault(false)
            if (shown) {
                imageAvatarPreview.visibility = View.VISIBLE
                textAvatarPreview.visibility = View.GONE
                return
            }
        }

        imageAvatarPreview.visibility = View.GONE
        imageAvatarPreview.colorFilter = null
        textAvatarPreview.visibility = View.VISIBLE
        textAvatarPreview.text = buildInitials(inputName.text.toString(), inputLastName.text.toString())
        val colorInt = AvatarColorPalette.resolveColorInt(selectedAvatarColor, avatarSeed)
        textAvatarPreview.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 14f * resources.displayMetrics.density
            setColor(colorInt)
        }
    }

    private fun startQrScanFlow() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchQrScanner()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchQrScanner() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(getString(R.string.qr_scan_prompt))
            .setBeepEnabled(false)
            .setOrientationLocked(true)
            .setCaptureActivity(QrPortraitCaptureActivity::class.java)
        qrScanLauncher.launch(options)
    }

    private fun applyScannedQrPayload(rawPayload: String) {
        val decoded = ContactQrCodec.decode(rawPayload)
        if (decoded == null) {
            Toast.makeText(this, R.string.error_qr_invalid, Toast.LENGTH_SHORT).show()
            AppEventLogger.warn("QR", "Invalid QR payload scanned in editor")
            return
        }

        inputName.setText(decoded.name)
        inputLastName.setText(decoded.lastName.orEmpty())
        inputPhone.setText(PhoneNumberFormatter.normalizeForStorage(decoded.phone))
        inputEmail.setText(decoded.email.orEmpty())
        inputAddress.setText(decoded.address.orEmpty())
        inputBirthday.setText(decoded.birthday.orEmpty())
        inputComment.setText(decoded.comment.orEmpty())
        selectedAvatarColor = AvatarColorPalette.normalizeHex(decoded.avatarColor)
        selectedAvatarPhotoUri = QrAvatarCodec.decodeAvatarToLocalUri(this, decoded.avatarPhotoBase64)

        val defaultGroupIndex = editGroupCodes.indexOf(ContactPrefsStorage.GROUP_UNASSIGNED).takeIf { it >= 0 } ?: 0
        spinnerGroup.setSelection(defaultGroupIndex)
        updateAvatarPreview()

        Toast.makeText(this, R.string.qr_contact_prefilled, Toast.LENGTH_SHORT).show()
        AppEventLogger.info("QR", "QR payload applied in contact editor")
    }

    private fun showCurrentContactQr() {
        val name = inputName.text.toString().trim()
        val phone = PhoneNumberFormatter.normalizeForStorage(inputPhone.text.toString().trim())
        if (name.isBlank()) {
            inputName.error = getString(R.string.error_required)
            return
        }
        if (phone.isBlank() || (phone.startsWith("+7") && phone.filter { it.isDigit() }.length <= 1)) {
            inputPhone.error = getString(R.string.error_required)
            return
        }

        val payload = ContactQrCodec.encode(
            name = name,
            lastName = inputLastName.text.toString().trim().ifBlank { null },
            phone = phone,
            email = inputEmail.text.toString().trim().ifBlank { null },
            address = inputAddress.text.toString().trim().ifBlank { null },
            birthday = normalizeBirthday(inputBirthday.text.toString().trim()).orEmpty().ifBlank { null },
            comment = inputComment.text.toString().trim().ifBlank { null },
            avatarColor = selectedAvatarColor,
            avatarPhotoBase64 = QrAvatarCodec.encodeAvatarFromUri(this, selectedAvatarPhotoUri),
        )
        val qrBitmap = ContactQrCodec.generateBitmap(payload, dp(240))
        if (qrBitmap == null) {
            Toast.makeText(this, R.string.error_qr_generation, Toast.LENGTH_SHORT).show()
            AppEventLogger.warn("QR", "Failed to generate QR in editor")
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_contact_qr, null)
        dialogView.findViewById<ImageView>(R.id.imageQrCode).setImageBitmap(qrBitmap)
        val displayName = listOfNotNull(name, inputLastName.text.toString().trim().ifBlank { null }).joinToString(" ")
        dialogView.findViewById<TextView>(R.id.textQrInfo).text =
            getString(R.string.message_contact_qr, displayName.ifBlank { phone })

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.title_contact_qr)
            .setView(dialogView)
            .setPositiveButton(R.string.action_ok, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
        AppEventLogger.info("QR", "Contact QR displayed from editor")
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
            private var deleteAcrossSeparator = false
            private var deleteDigitIndex = -1

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (editing) return
                val current = s?.toString().orEmpty()
                if (count <= 0 || after > 0) {
                    deleteAcrossSeparator = false
                    deleteDigitIndex = -1
                    return
                }
                val end = (start + count).coerceAtMost(current.length)
                val removed = if (start in 0 until end) current.substring(start, end) else ""
                val removedOnlyMaskChars = removed.isNotEmpty() && removed.none { it.isDigit() }
                if (!removedOnlyMaskChars) {
                    deleteAcrossSeparator = false
                    deleteDigitIndex = -1
                    return
                }
                val digitsBefore = localDigitsBeforeCursor(current, start)
                deleteAcrossSeparator = true
                deleteDigitIndex = (digitsBefore - 1).coerceAtLeast(0)
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (editing) return
                val raw = s?.toString().orEmpty()
                if (raw.contains('*') || raw.contains('#')) return
                if (raw.any { it.isLetter() }) return
                if (raw.startsWith("+") && !raw.startsWith("+7")) return
                editing = true
                val sourceCursor = inputPhone.selectionStart.coerceAtLeast(0)
                val localCursorDigitsBefore = localDigitsBeforeCursor(raw, sourceCursor)

                var digitsOnly = extractRuLocalDigits(raw)
                if (deleteAcrossSeparator && deleteDigitIndex in digitsOnly.indices) {
                    digitsOnly = digitsOnly.removeRange(deleteDigitIndex, deleteDigitIndex + 1)
                }
                val formatted = formatRuMaskFromDigits(digitsOnly)
                s?.replace(0, s.length, formatted)
                val targetLocalDigits = if (deleteAcrossSeparator) {
                    deleteDigitIndex.coerceAtLeast(0)
                } else {
                    localCursorDigitsBefore.coerceIn(0, digitsOnly.length)
                }
                val targetCursor = cursorForLocalDigits(formatted, targetLocalDigits)
                inputPhone.setSelection(targetCursor.coerceIn(0, inputPhone.text.length))

                deleteAcrossSeparator = false
                deleteDigitIndex = -1
                editing = false
            }
        })
        if (inputPhone.text.isNullOrBlank()) {
            inputPhone.setText("+7")
            inputPhone.setSelection(inputPhone.text.length)
        }
    }

    private fun formatRuMaskFromDigits(digits: String): String {
        if (digits.isBlank()) return "+7"
        val builder = StringBuilder("+7 (")
        builder.append(digits.take(3))
        if (digits.length >= 3) builder.append(")")
        if (digits.length > 3) {
            builder.append(" ")
            builder.append(digits.substring(3, minOf(6, digits.length)))
        }
        if (digits.length > 6) {
            builder.append("-")
            builder.append(digits.substring(6, minOf(8, digits.length)))
        }
        if (digits.length > 8) {
            builder.append("-")
            builder.append(digits.substring(8, minOf(10, digits.length)))
        }
        return builder.toString()
    }

    private fun extractRuLocalDigits(raw: String): String {
        val digitsRaw = raw.filter { it.isDigit() }
        var digits = digitsRaw
        if (raw.startsWith("+7") && digits.startsWith("7")) {
            digits = digits.drop(1)
        } else if (digits.length > 10 && (digits.startsWith("7") || digits.startsWith("8"))) {
            digits = digits.drop(1)
        }
        return digits.take(10)
    }

    private fun localDigitsBeforeCursor(text: String, cursor: Int): Int {
        val safeCursor = cursor.coerceIn(0, text.length)
        val prefix = text.take(safeCursor)
        val digitsBefore = prefix.count { it.isDigit() }
        val startsWithCountryCode = text.startsWith("+7")
        return if (startsWithCountryCode) (digitsBefore - 1).coerceAtLeast(0) else digitsBefore
    }

    private fun cursorForLocalDigits(formatted: String, localDigits: Int): Int {
        if (localDigits <= 0) return "+7".length.coerceAtMost(formatted.length)
        var localSeen = 0
        var countrySkipped = false
        val expectCountry = formatted.startsWith("+7")
        formatted.forEachIndexed { index, ch ->
            if (!ch.isDigit()) return@forEachIndexed
            if (expectCountry && !countrySkipped) {
                countrySkipped = true
                return@forEachIndexed
            }
            localSeen += 1
            if (localSeen >= localDigits) {
                return (index + 1).coerceAtMost(formatted.length)
            }
        }
        return formatted.length
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

    private fun parseReadOnlyContact(rawJson: String?): Contact? {
        val json = rawJson?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val obj = JSONObject(json)
            Contact(
                id = obj.optLong("id", System.currentTimeMillis()),
                name = obj.optString("name"),
                lastName = obj.optString("lastName").ifBlank { null },
                phone = obj.optString("phone"),
                email = obj.optString("email").ifBlank { null },
                address = obj.optString("address").ifBlank { null },
                birthday = obj.optString("birthday").ifBlank { null },
                comment = obj.optString("comment").ifBlank { null },
                avatarColor = obj.optString("avatarColor").ifBlank { null },
                avatarPhotoUri = obj.optString("avatarPhotoUri").ifBlank { null },
                group = obj.optString("group", ContactPrefsStorage.GROUP_UNASSIGNED),
                isFavorite = obj.optBoolean("isFavorite", false),
                isImported = obj.optBoolean("isImported", false),
            )
        }.getOrNull()
    }

    companion object {
        const val EXTRA_CONTACT_ID = "extra_contact_id"
        private const val EXTRA_READ_ONLY_MODE = "extra_read_only_mode"
        private const val EXTRA_READ_ONLY_CONTACT_JSON = "extra_read_only_contact_json"
        private const val COMMENT_MAX_LENGTH = 512
        private const val DEV_PREFS_NAME = "contact_manager_dev_settings"
        private const val KEY_SERVICE_GROUP_VISIBLE = "service_group_visible"

        fun buildReadOnlyIntent(context: Context, contact: Contact): Intent {
            val payload = JSONObject().apply {
                put("id", contact.id)
                put("name", contact.name)
                put("lastName", contact.lastName)
                put("phone", contact.phone)
                put("email", contact.email)
                put("address", contact.address)
                put("birthday", contact.birthday)
                put("comment", contact.comment)
                put("avatarColor", contact.avatarColor)
                put("avatarPhotoUri", contact.avatarPhotoUri)
                put("group", contact.group)
                put("isFavorite", contact.isFavorite)
                put("isImported", contact.isImported)
            }.toString()
            return Intent(context, ContactEditorActivity::class.java).apply {
                putExtra(EXTRA_READ_ONLY_MODE, true)
                putExtra(EXTRA_READ_ONLY_CONTACT_JSON, payload)
            }
        }
    }
}
