package com.example.contactmanagerdemo.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.provider.ContactsContract
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.core.AppEventLogger
import com.example.contactmanagerdemo.core.UpdateChecker
import com.example.contactmanagerdemo.data.Contact
import com.example.contactmanagerdemo.data.ContactPrefsStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var storage: ContactPrefsStorage
    private lateinit var adapter: ContactAdapter

    private lateinit var textStats: TextView
    private lateinit var inputSearch: EditText
    private lateinit var groupContainer: LinearLayout
    private lateinit var textEmpty: TextView

    private var allContacts: MutableList<Contact> = mutableListOf()
    private lateinit var filterGroupCodes: List<String>
    private lateinit var editGroupCodes: List<String>
    private var selectedGroupCode: String = ContactPrefsStorage.GROUP_ALL
    private val groupViews: MutableMap<String, TextView> = linkedMapOf()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var adminHoldTriggered = false
    private val adminHoldRunnable = Runnable {
        adminHoldTriggered = true
        AppEventLogger.info("ADMIN", "Developer panel requested by long press")
        showAdminPanel()
    }
    private val requestContactsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                importContactsFromDevice()
            } else {
                AppEventLogger.warn("IMPORT", "Contacts permission denied")
                Toast.makeText(this, R.string.import_contacts_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
    private val requestNotificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                AppEventLogger.warn("UPDATE", "Notifications permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppEventLogger.info("APP", "MainActivity onCreate started")
        try {
            setContentView(R.layout.activity_main)

            storage = ContactPrefsStorage(this)

            textStats = findViewById(R.id.textStats)
            inputSearch = findViewById(R.id.inputSearch)
            groupContainer = findViewById(R.id.groupContainer)
            textEmpty = findViewById(R.id.textEmpty)

            val recyclerView = findViewById<RecyclerView>(R.id.recyclerContacts)
            recyclerView.layoutManager = LinearLayoutManager(this)

            adapter = ContactAdapter(
                onEdit = { contact -> showContactDialog(contact) },
            )
            recyclerView.adapter = adapter

            setupFilterGroups()

            findViewById<View>(R.id.btnAddContact).setOnClickListener {
                AppEventLogger.info("UI", "Add contact button clicked")
                showContactDialog(null)
            }
            findViewById<ImageButton>(R.id.btnImportDeviceContacts).setOnClickListener {
                showImportContactsConfirmDialog()
            }
            setupAdminPanelLongPress()
            inputSearch.doAfterTextChanged { renderContacts() }
            ensureNotificationsPermissionIfNeeded()

            migrateContactsIfNeeded()
            loadContactsAndRender()
            AppEventLogger.info("APP", "MainActivity onCreate completed")
        } catch (t: Throwable) {
            AppEventLogger.error("CRASH", "MainActivity initialization failed", t)
            showStartupFallback()
        }
    }

    override fun onResume() {
        super.onResume()
        AppEventLogger.info("APP", "MainActivity onResume")
        loadContactsAndRender()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(adminHoldRunnable)
        super.onDestroy()
    }

    private fun setupFilterGroups() {
        filterGroupCodes = storage.getFilterGroups()
        editGroupCodes = storage.getAvailableGroups()
        selectedGroupCode = ContactPrefsStorage.GROUP_ALL

        groupContainer.removeAllViews()
        groupViews.clear()
        filterGroupCodes.forEach { code ->
            val tabView = TextView(this).apply {
                text = mapGroupLabel(code)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener {
                    selectedGroupCode = code
                    updateGroupTabsSelection()
                    renderContacts()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginEnd = dp(8)
            }
            tabView.layoutParams = lp
            groupContainer.addView(tabView)
            groupViews[code] = tabView
        }
        updateGroupTabsSelection()
    }

    private fun loadContactsAndRender() {
        allContacts = storage.getAllContacts()
        AppEventLogger.info("DATA", "Loaded contacts: ${allContacts.size}")
        updateStats()
        renderContacts()
    }

    private fun renderContacts() {
        val query = inputSearch.text.toString().trim().lowercase(Locale.getDefault())

        val list = allContacts
            .filter { selectedGroupCode == ContactPrefsStorage.GROUP_ALL || it.group == selectedGroupCode }
            .filter {
                val fullName = listOfNotNull(it.name, it.lastName).joinToString(" ").lowercase(Locale.getDefault())
                query.isBlank() ||
                    fullName.contains(query) ||
                    it.phone.lowercase(Locale.getDefault()).contains(query)
            }
            .sortedWith(compareBy<Contact> { it.name.lowercase(Locale.getDefault()) }.thenBy { it.lastName.orEmpty().lowercase(Locale.getDefault()) })

        adapter.submitList(list)
        AppEventLogger.info("UI", "Rendered contacts: ${list.size}, group=$selectedGroupCode, query='$query'")
        textEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateGroupTabsSelection() {
        groupViews.forEach { (code, view) ->
            val selected = code == selectedGroupCode
            view.background = ContextCompat.getDrawable(
                this,
                if (selected) R.drawable.bg_group_selected_gradient else R.drawable.bg_group_unselected,
            )
            view.setTextColor(
                if (selected) {
                    0xFFFFFFFF.toInt()
                } else {
                    0xFF1E293B.toInt()
                },
            )
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun updateStats() {
        val total = allContacts.size
        val imported = allContacts.count { it.isImported }
        textStats.text = getString(R.string.stats_contacts, total, imported)
    }

    private fun showContactDialog(contact: Contact?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_contact, null)
        val inputName = dialogView.findViewById<EditText>(R.id.inputName)
        val inputLastName = dialogView.findViewById<EditText>(R.id.inputLastName)
        val inputPhone = dialogView.findViewById<EditText>(R.id.inputPhone)
        val inputEmail = dialogView.findViewById<EditText>(R.id.inputEmail)
        val inputAddress = dialogView.findViewById<EditText>(R.id.inputAddress)
        val inputBirthday = dialogView.findViewById<EditText>(R.id.inputBirthday)
        val inputComment = dialogView.findViewById<EditText>(R.id.inputComment)
        val btnPickBirthday = dialogView.findViewById<Button>(R.id.btnPickBirthday)
        val btnPickAvatarColor = dialogView.findViewById<Button>(R.id.btnPickAvatarColor)
        val viewAvatarColorPreview = dialogView.findViewById<View>(R.id.viewAvatarColorPreview)
        val spinnerGroup = dialogView.findViewById<Spinner>(R.id.spinnerGroup)
        var selectedAvatarColor = contact?.avatarColor
        val avatarSeed = contact?.id ?: System.currentTimeMillis()

        setupBirthdayInputMask(inputBirthday)
        btnPickBirthday.setOnClickListener {
            showBirthdayDatePicker(inputBirthday)
        }
        btnPickAvatarColor.setOnClickListener {
            showAvatarColorPicker(selectedAvatarColor) { selectedHex ->
                selectedAvatarColor = selectedHex
                updateAvatarColorPreview(viewAvatarColorPreview, selectedAvatarColor, avatarSeed)
            }
        }
        updateAvatarColorPreview(viewAvatarColorPreview, selectedAvatarColor, avatarSeed)

        val groupLabels = editGroupCodes.map { mapGroupLabel(it) }
        spinnerGroup.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, groupLabels)

        if (contact != null) {
            inputName.setText(contact.name)
            inputLastName.setText(contact.lastName.orEmpty())
            inputPhone.setText(contact.phone)
            inputEmail.setText(contact.email.orEmpty())
            inputAddress.setText(contact.address.orEmpty())
            inputBirthday.setText(contact.birthday.orEmpty())
            inputComment.setText(contact.comment.orEmpty())
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
                val lastName = inputLastName.text.toString().trim().ifBlank { null }
                val phone = inputPhone.text.toString().trim()
                val email = inputEmail.text.toString().trim().ifBlank { null }
                val address = inputAddress.text.toString().trim().ifBlank { null }
                val birthdayRaw = inputBirthday.text.toString().trim()
                val comment = inputComment.text.toString().trim().ifBlank { null }
                val groupIndex = spinnerGroup.selectedItemPosition.takeIf { it >= 0 } ?: 0
                val group = editGroupCodes.getOrElse(groupIndex) { ContactPrefsStorage.GROUP_OTHER }

                var hasError = false
                if (name.isBlank()) {
                    inputName.error = getString(R.string.error_required)
                    AppEventLogger.warn("VALIDATION", "Name is empty")
                    hasError = true
                }
                if (phone.isBlank()) {
                    inputPhone.error = getString(R.string.error_required)
                    AppEventLogger.warn("VALIDATION", "Phone is empty")
                    hasError = true
                }
                if (!email.isNullOrBlank() && !email.contains("@")) {
                    inputEmail.error = getString(R.string.error_email_format)
                    AppEventLogger.warn("VALIDATION", "Invalid email: $email")
                    hasError = true
                }
                if (!comment.isNullOrBlank() && comment.length > COMMENT_MAX_LENGTH) {
                    inputComment.error = getString(R.string.error_comment_too_long)
                    AppEventLogger.warn("VALIDATION", "Comment too long: ${comment.length}")
                    hasError = true
                }

                val birthday = if (birthdayRaw.isBlank()) {
                    null
                } else {
                    normalizeBirthday(birthdayRaw)?.also {
                        inputBirthday.setText(it)
                    } ?: run {
                        inputBirthday.error = getString(R.string.error_birthday_format)
                        AppEventLogger.warn("VALIDATION", "Invalid birthday: $birthdayRaw")
                        hasError = true
                        null
                    }
                }

                if (hasError) return@setOnClickListener

                val newContact = Contact(
                    id = contact?.id ?: System.currentTimeMillis(),
                    name = name,
                    lastName = lastName,
                    phone = phone,
                    email = email,
                    address = address,
                    birthday = birthday,
                    comment = comment,
                    avatarColor = selectedAvatarColor,
                    group = group,
                    isImported = contact?.isImported ?: false,
                )
                storage.upsert(newContact)
                AppEventLogger.info("DATA", "Upsert contact id=${newContact.id}")
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

    private fun showAvatarColorPicker(
        currentColorHex: String?,
        onSelected: (String?) -> Unit,
    ) {
        val colors = AvatarColorPalette.allHexColors()
        val options = mutableListOf(getString(R.string.avatar_color_random)).apply {
            addAll(colors.map { it.uppercase() })
        }

        val normalizedCurrent = AvatarColorPalette.normalizeHex(currentColorHex)
        val selectedIndex = normalizedCurrent
            ?.let { hex -> colors.indexOfFirst { it.equals(hex, ignoreCase = true) } }
            ?.takeIf { it >= 0 }
            ?.plus(1)
            ?: 0

        AlertDialog.Builder(this)
            .setTitle(R.string.action_pick_avatar_color)
            .setSingleChoiceItems(options.toTypedArray(), selectedIndex) { dialog, which ->
                if (which == 0) {
                    onSelected(null)
                } else {
                    onSelected(colors[which - 1].uppercase())
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun updateAvatarColorPreview(previewView: View, colorHex: String?, seed: Long) {
        val colorInt = AvatarColorPalette.resolveColorInt(colorHex, seed)
        previewView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colorInt)
            setStroke(dp(1), 0xFFE2E8F0.toInt())
        }
    }

    private fun setupBirthdayInputMask(inputBirthday: EditText) {
        var isUpdating = false
        inputBirthday.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                val current = s?.toString().orEmpty()
                val formatted = formatBirthdayTyping(current)
                if (formatted != current) {
                    isUpdating = true
                    inputBirthday.setText(formatted)
                    inputBirthday.setSelection(formatted.length)
                    isUpdating = false
                }
            }
        })
    }

    private fun formatBirthdayTyping(text: String): String {
        val digits = text.filter { it.isDigit() }.take(8)
        if (digits.isEmpty()) return ""

        var index = 0
        val dayLen = chooseDatePartLength(digits.substring(index), 31)
        val day = digits.substring(index, index + dayLen)
        index += dayLen

        if (index >= digits.length) return day

        val monthLen = chooseDatePartLength(digits.substring(index), 12)
        val month = digits.substring(index, index + monthLen)
        index += monthLen

        if (index >= digits.length) return "$day/$month"

        val year = digits.substring(index)
        return "$day/$month/$year"
    }

    private fun chooseDatePartLength(source: String, maxValue: Int): Int {
        if (source.isEmpty()) return 0
        if (source.length == 1) return 1
        val twoDigitsValue = source.substring(0, 2).toIntOrNull() ?: return 1
        return if (twoDigitsValue in 1..maxValue) 2 else 1
    }

    private fun showBirthdayDatePicker(inputBirthday: EditText) {
        val calendar = Calendar.getInstance()
        parseBirthdayToDate(inputBirthday.text.toString().trim())?.let {
            calendar.time = it
        }

        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val formatted = String.format(Locale.US, "%02d/%02d/%04d", dayOfMonth, month + 1, year)
                inputBirthday.setText(formatted)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        )
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun normalizeBirthday(rawValue: String): String? {
        val supportedFormats = listOf("dd/MM/yyyy", "d/M/yyyy", "dd/M/yyyy", "d/MM/yyyy")
        val parsedDate = supportedFormats.asSequence()
            .mapNotNull { pattern ->
                runCatching {
                    val parser = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                    parser.parse(rawValue)
                }.getOrNull()
            }
            .firstOrNull()
            ?: return null

        return SimpleDateFormat("dd/MM/yyyy", Locale.US).format(parsedDate)
    }

    private fun parseBirthdayToDate(rawValue: String): Date? {
        val normalized = normalizeBirthday(rawValue) ?: return null
        return runCatching {
            SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { isLenient = false }.parse(normalized)
        }.getOrNull()
    }

    private fun showDeleteDialog(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle(R.string.title_delete_contact)
            .setMessage(getString(R.string.message_delete_contact, listOfNotNull(contact.name, contact.lastName).joinToString(" ")))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                storage.delete(contact.id)
                AppEventLogger.info("DATA", "Deleted contact id=${contact.id}")
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

    private fun setupAdminPanelLongPress() {
        val headerArea = findViewById<View>(R.id.headerTriggerArea)
        headerArea.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    adminHoldTriggered = false
                    mainHandler.postDelayed(adminHoldRunnable, ADMIN_HOLD_DURATION_MS)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(adminHoldRunnable)
                    if (adminHoldTriggered) {
                        adminHoldTriggered = false
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun showImportContactsConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.import_contacts_title)
            .setMessage(R.string.import_contacts_message)
            .setPositiveButton(R.string.import_contacts_action) { _, _ ->
                requestContactsPermissionAndImport()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun requestContactsPermissionAndImport() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            importContactsFromDevice()
        } else {
            requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun importContactsFromDevice() {
        AppEventLogger.info("IMPORT", "Contacts import started")
        Thread {
            val result = runCatching { performDeviceContactsImport() }
            runOnUiThread {
                result.onSuccess { importedCount ->
                    if (importedCount > 0) {
                        AppEventLogger.info("IMPORT", "Imported contacts count=$importedCount")
                        Toast.makeText(this, getString(R.string.import_contacts_result, importedCount), Toast.LENGTH_SHORT).show()
                    } else {
                        AppEventLogger.info("IMPORT", "No contacts imported")
                        Toast.makeText(this, R.string.import_contacts_none, Toast.LENGTH_SHORT).show()
                    }
                    loadContactsAndRender()
                }.onFailure { error ->
                    AppEventLogger.error("IMPORT", "Contacts import failed", error)
                    Toast.makeText(this, R.string.import_contacts_error, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun performDeviceContactsImport(): Int {
        val existing = storage.getAllContacts().toMutableList()
        val dedupKeys = existing.mapTo(mutableSetOf()) { contactKey(it.name, it.lastName, it.phone) }

        var imported = 0
        var nextIdSeed = System.currentTimeMillis()
        val contactsCursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ),
            null,
            null,
            null,
        ) ?: return 0

        contactsCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val contactId = cursor.getLongOrNull(ContactsContract.Contacts._ID) ?: continue
                val displayName = cursor.getStringOrNull(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY).orEmpty()

                val details = loadDeviceContactDetails(contactId, displayName) ?: continue
                val key = contactKey(details.name, details.lastName, details.phone)
                if (!dedupKeys.add(key)) continue

                nextIdSeed += 1
                existing.add(
                    Contact(
                        id = nextIdSeed,
                        name = details.name,
                        lastName = details.lastName,
                        phone = details.phone,
                        email = details.email,
                        address = details.address,
                        birthday = details.birthday,
                        comment = details.comment,
                        avatarColor = null,
                        group = ContactPrefsStorage.GROUP_OTHER,
                        isImported = true,
                    ),
                )
                imported += 1
            }
        }

        if (imported > 0) {
            storage.saveAllContacts(existing)
        }
        return imported
    }

    private fun loadDeviceContactDetails(contactId: Long, displayName: String): ImportedContactDetails? {
        val phone = queryPhone(contactId).orEmpty()
        if (phone.isBlank()) return null

        val (name, lastName) = queryNameParts(contactId, displayName)
        if (name.isBlank()) return null

        return ImportedContactDetails(
            name = name,
            lastName = lastName,
            phone = phone,
            email = queryEmail(contactId),
            address = queryAddress(contactId),
            birthday = normalizeImportedBirthday(queryBirthday(contactId)),
            comment = queryNote(contactId),
        )
    }

    private fun queryNameParts(contactId: Long, fallbackDisplayName: String): Pair<String, String?> {
        val result = queryDataByMimeType(
            contactId = contactId,
            mimeType = ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            projection = arrayOf(
                ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
            ),
        ) { cursor ->
            val givenName = cursor.getStringOrNull(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME).orEmpty().trim()
            val familyName = cursor.getStringOrNull(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME)?.trim().orEmpty()
            val display = cursor.getStringOrNull(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME).orEmpty().trim()
            Triple(givenName, familyName.ifBlank { null }, display)
        }

        if (result != null) {
            val (givenName, familyName, display) = result
            val baseName = when {
                givenName.isNotBlank() -> givenName
                display.isNotBlank() -> display.substringBefore(" ").trim()
                else -> fallbackDisplayName.substringBefore(" ").trim()
            }
            val baseLastName = familyName ?: parseLastNameFromDisplay(display.ifBlank { fallbackDisplayName })
            return baseName to baseLastName
        }

        val fallbackName = fallbackDisplayName.substringBefore(" ").trim()
        val fallbackLast = parseLastNameFromDisplay(fallbackDisplayName)
        return fallbackName to fallbackLast
    }

    private fun queryPhone(contactId: Long): String? {
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
            arrayOf(contactId.toString()),
            "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
        ) ?: return null

        cursor.use {
            while (it.moveToNext()) {
                val raw = it.getStringOrNull(ContactsContract.CommonDataKinds.Phone.NUMBER).orEmpty().trim()
                if (raw.isNotBlank()) {
                    return raw
                }
            }
        }
        return null
    }

    private fun queryEmail(contactId: Long): String? {
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID}=?",
            arrayOf(contactId.toString()),
            "${ContactsContract.CommonDataKinds.Email.IS_PRIMARY} DESC",
        ) ?: return null

        cursor.use {
            while (it.moveToNext()) {
                val email = it.getStringOrNull(ContactsContract.CommonDataKinds.Email.ADDRESS).orEmpty().trim()
                if (email.isNotBlank()) return email
            }
        }
        return null
    }

    private fun queryAddress(contactId: Long): String? {
        return queryDataByMimeType(
            contactId = contactId,
            mimeType = ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
            projection = arrayOf(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS),
        ) { cursor ->
            cursor.getStringOrNull(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
                ?.trim()
                ?.ifBlank { null }
        }
    }

    private fun queryBirthday(contactId: Long): String? {
        val cursor = contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Event.START_DATE),
            "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Event.TYPE}=?",
            arrayOf(
                contactId.toString(),
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString(),
            ),
            null,
        ) ?: return null

        cursor.use {
            while (it.moveToNext()) {
                val date = it.getStringOrNull(ContactsContract.CommonDataKinds.Event.START_DATE).orEmpty().trim()
                if (date.isNotBlank()) return date
            }
        }
        return null
    }

    private fun queryNote(contactId: Long): String? {
        return queryDataByMimeType(
            contactId = contactId,
            mimeType = ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
            projection = arrayOf(ContactsContract.CommonDataKinds.Note.NOTE),
        ) { cursor ->
            cursor.getStringOrNull(ContactsContract.CommonDataKinds.Note.NOTE)
                ?.trim()
                ?.take(COMMENT_MAX_LENGTH)
                ?.ifBlank { null }
        }
    }

    private fun normalizeImportedBirthday(rawValue: String?): String? {
        val raw = rawValue?.trim().orEmpty()
        if (raw.isBlank()) return null

        val normalized = when {
            raw.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> {
                val parts = raw.split("-")
                "${parts[2]}/${parts[1]}/${parts[0]}"
            }

            raw.matches(Regex("\\d{2}/\\d{2}/\\d{4}")) -> raw
            else -> null
        }

        return normalized?.let { normalizeBirthday(it) }
    }

    private fun parseLastNameFromDisplay(displayName: String): String? {
        val parts = displayName.trim().split(" ").filter { it.isNotBlank() }
        return parts.drop(1).joinToString(" ").ifBlank { null }
    }

    private fun contactKey(name: String, lastName: String?, phone: String): String {
        val fullName = listOfNotNull(name, lastName).joinToString(" ").trim().lowercase(Locale.getDefault())
        val normalizedPhone = phone.filter { it.isDigit() || it == '+' }
        return "$fullName|$normalizedPhone"
    }

    private fun <T> queryDataByMimeType(
        contactId: Long,
        mimeType: String,
        projection: Array<String>,
        mapper: (Cursor) -> T?,
    ): T? {
        val cursor = contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
            arrayOf(contactId.toString(), mimeType),
            null,
        ) ?: return null

        cursor.use {
            while (it.moveToNext()) {
                val mapped = mapper(it)
                if (mapped != null) return mapped
            }
        }
        return null
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.getLongOrNull(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    private fun showAdminPanel() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_panel, null)
        val switchLogs = dialogView.findViewById<SwitchCompat>(R.id.switchLogs)
        val textVersionValue = dialogView.findViewById<TextView>(R.id.textVersionValue)
        val textLogPathValue = dialogView.findViewById<TextView>(R.id.textLogPathValue)
        val btnCheckUpdates = dialogView.findViewById<Button>(R.id.btnCheckUpdates)
        val textUpdateResult = dialogView.findViewById<TextView>(R.id.textUpdateResult)

        switchLogs.isChecked = AppEventLogger.isLoggingEnabled()
        textVersionValue.text = getString(
            R.string.admin_version_value,
            getAppVersionName(),
            resources.getInteger(R.integer.update_sequence),
        )
        textLogPathValue.text = getString(R.string.admin_log_path_value, AppEventLogger.getCurrentLogDirectory(this))

        switchLogs.setOnCheckedChangeListener { _, enabled ->
            AppEventLogger.setLoggingEnabled(this, enabled)
            Toast.makeText(
                this,
                if (enabled) getString(R.string.admin_logs_enabled) else getString(R.string.admin_logs_disabled),
                Toast.LENGTH_SHORT,
            ).show()
        }

        btnCheckUpdates.setOnClickListener {
            if (!hasInternetConnection()) {
                showNoInternetDialog { btnCheckUpdates.performClick() }
                return@setOnClickListener
            }
            textUpdateResult.text = getString(R.string.admin_checking_updates)
            AppEventLogger.info("ADMIN", "Checking updates from GitHub")
            checkForUpdatesFromGithub(textUpdateResult)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.admin_panel_title)
            .setView(dialogView)
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showNoInternetDialog(onRetry: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.admin_no_internet_title)
            .setMessage(R.string.admin_no_internet_message)
            .setPositiveButton(R.string.admin_action_try_again) { _, _ -> onRetry() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun ensureNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasInternetConnection(): Boolean {
        @Suppress("DEPRECATION")
        val cm = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    private fun checkForUpdatesFromGithub(textUpdateResult: TextView) {
        Thread {
            val result = runCatching { UpdateChecker.fetchLatestReleaseOrPrerelease() }
            runOnUiThread {
                result.onSuccess { latest ->
                    val latestNormalized = UpdateChecker.normalizeVersionTag(latest.tagName)
                    val currentNormalized = UpdateChecker.normalizeVersionTag(getAppVersionName())
                    val hasUpdate = UpdateChecker.compareVersions(latestNormalized, currentNormalized) > 0
                    if (hasUpdate) {
                        textUpdateResult.text = if (latest.isPrerelease) {
                            getString(R.string.admin_update_available_prerelease, latestNormalized)
                        } else {
                            getString(R.string.admin_update_available, latestNormalized)
                        }
                        AppEventLogger.info("ADMIN", "Update available: $latestNormalized")
                        if (latest.htmlUrl.isNotBlank()) {
                            AlertDialog.Builder(this)
                                .setTitle(R.string.admin_panel_title)
                                .setMessage(
                                    if (latest.isPrerelease) {
                                        getString(R.string.admin_update_available_prerelease, latestNormalized)
                                    } else {
                                        getString(R.string.admin_update_available, latestNormalized)
                                    },
                                )
                                .setPositiveButton(R.string.admin_open_release_page) { _, _ ->
                                    runCatching {
                                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.htmlUrl)))
                                    }
                                }
                                .setNegativeButton(R.string.action_cancel, null)
                                .show()
                        }
                    } else {
                        textUpdateResult.text = getString(R.string.admin_update_latest, currentNormalized)
                        AppEventLogger.info("ADMIN", "No update found, current=$currentNormalized")
                    }
                }.onFailure { error ->
                    textUpdateResult.text = getString(R.string.admin_update_error)
                    AppEventLogger.error("ADMIN", "Failed to check updates", error)
                }
            }
        }.start()
    }

    private fun getAppVersionName(): String {
        return runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrDefault("unknown")
    }

    private fun showStartupFallback() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        val title = TextView(this).apply {
            text = getString(R.string.error_startup_title)
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0xFF111827.toInt())
        }
        val subtitle = TextView(this).apply {
            text = getString(R.string.error_startup_subtitle)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
            setTextColor(0xFF374151.toInt())
        }
        val retry = Button(this).apply {
            text = getString(R.string.action_retry)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { recreate() }
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(14)
        }
        container.addView(title)
        container.addView(subtitle)
        container.addView(retry, lp)
        setContentView(container)
    }

    private fun migrateContactsIfNeeded() {
        val contacts = storage.getAllContacts()
        val repaired = contacts.map {
            it.copy(
                name = repairMojibake(it.name).ifBlank { it.name },
                lastName = it.lastName?.let { value -> repairMojibake(value).ifBlank { null } },
                phone = repairMojibake(it.phone).ifBlank { it.phone },
                email = it.email?.let { value -> repairMojibake(value).ifBlank { null } },
                address = it.address?.let { value -> repairMojibake(value).ifBlank { null } },
                birthday = it.birthday?.let { value -> repairMojibake(value).ifBlank { null } },
                comment = it.comment?.let { value -> repairMojibake(value).ifBlank { null } },
                avatarColor = AvatarColorPalette.normalizeHex(it.avatarColor),
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

    private data class ImportedContactDetails(
        val name: String,
        val lastName: String?,
        val phone: String,
        val email: String?,
        val address: String?,
        val birthday: String?,
        val comment: String?,
    )

    companion object {
        private const val COMMENT_MAX_LENGTH = 512
        private const val ADMIN_HOLD_DURATION_MS = 5_000L
    }
}
