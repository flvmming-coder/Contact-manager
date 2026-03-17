package com.example.contactmanagerdemo.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ScrollView
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.core.AppEventLogger
import com.example.contactmanagerdemo.core.ThemeManager
import com.example.contactmanagerdemo.core.UpdateChecker
import com.example.contactmanagerdemo.data.Contact
import com.example.contactmanagerdemo.data.ContactPrefsStorage
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.text.TextPaint

class MainActivity : AppCompatActivity() {

    private lateinit var storage: ContactPrefsStorage
    private lateinit var adapter: ContactAdapter

    private lateinit var textStats: TextView
    private lateinit var inputSearch: EditText
    private lateinit var groupContainer: LinearLayout
    private lateinit var textEmpty: TextView
    private lateinit var selectionActionsBar: LinearLayout
    private lateinit var textSelectionCount: TextView
    private lateinit var btnAddContact: View
    private val selectedContactIds = linkedSetOf<Long>()

    private var allContacts: MutableList<Contact> = mutableListOf()
    private lateinit var filterGroupCodes: List<String>
    private lateinit var editGroupCodes: List<String>
    private var selectedGroupCode: String = ContactPrefsStorage.GROUP_ALL
    private val groupViews: MutableMap<String, TextView> = linkedMapOf()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var adminHoldTriggered = false
    private val devPrefs by lazy { getSharedPreferences(DEV_PREFS_NAME, Context.MODE_PRIVATE) }
    private var pendingAvatarPhotoResult: ((String?) -> Unit)? = null
    private var pendingVcfPayload: String? = null
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
    private val pickAvatarPhotoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            pendingAvatarPhotoResult?.invoke(uri.toString())
            pendingAvatarPhotoResult = null
        }
    private val exportVcfLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
            val payload = pendingVcfPayload
            pendingVcfPayload = null
            if (uri == null || payload.isNullOrBlank()) return@registerForActivityResult

            val success = runCatching {
                contentResolver.openOutputStream(uri)?.use { output ->
                    OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                        writer.write(payload)
                    }
                } ?: error("Unable to open output stream")
            }.isSuccess

            if (success) {
                Toast.makeText(this, R.string.settings_export_done, Toast.LENGTH_SHORT).show()
                AppEventLogger.info("SETTINGS", "VCF exported: $uri")
            } else {
                Toast.makeText(this, R.string.settings_export_error, Toast.LENGTH_SHORT).show()
                AppEventLogger.warn("SETTINGS", "VCF export failed")
            }
        }
    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            runCatching { task.getResult(ApiException::class.java) }
                .onSuccess { account ->
                    val email = account.email.orEmpty()
                    devPrefs.edit()
                        .putBoolean(KEY_NETWORK_MODE_ENABLED, true)
                        .putString(KEY_NETWORK_ACCOUNT, email)
                        .apply()
                    Toast.makeText(this, getString(R.string.google_mode_connected, email), Toast.LENGTH_SHORT).show()
                    AppEventLogger.info("GOOGLE", "Network mode enabled for account=$email")
                }
                .onFailure { error ->
                    Toast.makeText(this, R.string.google_mode_failed, Toast.LENGTH_SHORT).show()
                    AppEventLogger.error("GOOGLE", "Google sign-in failed", error)
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
            selectionActionsBar = findViewById(R.id.selectionActionsBar)
            textSelectionCount = findViewById(R.id.textSelectionCount)

            val recyclerView = findViewById<RecyclerView>(R.id.recyclerContacts)
            recyclerView.layoutManager = LinearLayoutManager(this)

            adapter = ContactAdapter(
                onEdit = { contact -> showContactDialog(contact) },
                onSelectStarted = { contact -> beginSelectionMode(contact) },
                onSelectionToggle = { contact -> toggleContactSelection(contact) },
                isSelectionMode = { isSelectionModeActive() },
            )
            recyclerView.adapter = adapter

            findViewById<Button>(R.id.btnSelectionGroup).setOnClickListener { showBulkAssignGroupDialog() }
            findViewById<Button>(R.id.btnSelectionDelete).setOnClickListener { showBulkDeleteDialog() }
            findViewById<Button>(R.id.btnSelectionCancel).setOnClickListener { clearSelectionMode() }
            findViewById<ImageButton>(R.id.btnGroupSettings).setOnClickListener { showGroupManagementDialog() }

            setupFilterGroups()

            btnAddContact = findViewById(R.id.btnAddContact)
            btnAddContact.setOnClickListener {
                AppEventLogger.info("UI", "Add contact button clicked")
                showContactDialog(null)
            }
            findViewById<ImageButton>(R.id.btnSettings).setOnClickListener { showSettingsMenu() }
            findViewById<ImageButton>(R.id.btnGoogleMode).setOnClickListener { showGoogleModeDialog() }
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
        val previousGroupCode = selectedGroupCode
        filterGroupCodes = storage.getFilterGroups()
        editGroupCodes = storage.getEditableGroups()
        selectedGroupCode = if (filterGroupCodes.contains(previousGroupCode)) {
            previousGroupCode
        } else {
            ContactPrefsStorage.GROUP_ALL
        }

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
        val allIds = allContacts.map { it.id }.toSet()
        selectedContactIds.removeAll { it !in allIds }

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
        adapter.setSelectedIds(selectedContactIds)
        updateSelectionUi()
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
                    ContextCompat.getColor(this, R.color.group_tab_text)
                },
            )
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun styleDialogButtons(dialog: AlertDialog) {
        val buttonIds = listOf(
            AlertDialog.BUTTON_POSITIVE,
            AlertDialog.BUTTON_NEGATIVE,
            AlertDialog.BUTTON_NEUTRAL,
        )
        buttonIds.forEach { id ->
            val button = dialog.getButton(id) ?: return@forEach
            button.background = ContextCompat.getDrawable(this, R.drawable.bg_button_gradient_rect)
            button.setTextColor(0xFF0F172A.toInt())
            button.isAllCaps = false
            button.textSize = 12f
            button.minHeight = dp(34)
            button.minimumHeight = dp(34)
            button.setPadding(dp(10), dp(6), dp(10), dp(6))
            (button.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.marginStart = dp(4)
                lp.marginEnd = dp(4)
                button.layoutParams = lp
            }
        }
    }

    private fun updateStats() {
        val total = allContacts.size
        val imported = allContacts.count { it.isImported }
        textStats.text = getString(R.string.stats_contacts, total, imported)
    }

    private fun isSelectionModeActive(): Boolean = selectedContactIds.isNotEmpty()

    private fun beginSelectionMode(contact: Contact) {
        selectedContactIds.add(contact.id)
        AppEventLogger.info("UI", "Selection started for id=${contact.id}")
        updateSelectionUi()
        adapter.setSelectedIds(selectedContactIds)
    }

    private fun toggleContactSelection(contact: Contact) {
        if (selectedContactIds.contains(contact.id)) {
            selectedContactIds.remove(contact.id)
        } else {
            selectedContactIds.add(contact.id)
        }
        updateSelectionUi()
        adapter.setSelectedIds(selectedContactIds)
    }

    private fun clearSelectionMode() {
        if (selectedContactIds.isEmpty()) return
        selectedContactIds.clear()
        updateSelectionUi()
        adapter.setSelectedIds(emptySet())
    }

    private fun updateSelectionUi() {
        val count = selectedContactIds.size
        selectionActionsBar.visibility = if (count > 0) View.VISIBLE else View.GONE
        btnAddContact.visibility = if (count > 0) View.GONE else View.VISIBLE
        textSelectionCount.text = getString(R.string.selection_count, count)
    }

    private fun showBulkAssignGroupDialog() {
        if (selectedContactIds.isEmpty()) return
        val groupLabels = editGroupCodes.map { mapGroupLabel(it) }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.bulk_assign_group_title)
            .setItems(groupLabels.toTypedArray()) { _, which ->
                val targetGroup = editGroupCodes.getOrElse(which) { ContactPrefsStorage.GROUP_UNASSIGNED }
                val updated = allContacts.map { contact ->
                    if (selectedContactIds.contains(contact.id)) contact.copy(group = targetGroup) else contact
                }
                storage.saveAllContacts(updated)
                AppEventLogger.info("DATA", "Bulk group assign for ${selectedContactIds.size} contacts to $targetGroup")
                clearSelectionMode()
                loadContactsAndRender()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showBulkDeleteDialog() {
        if (selectedContactIds.isEmpty()) return
        val idsToDelete = selectedContactIds.toSet()
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.bulk_delete_title)
            .setMessage(getString(R.string.bulk_delete_message, idsToDelete.size))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                val updated = storage.getAllContacts().filterNot { idsToDelete.contains(it.id) }
                storage.saveAllContacts(updated)
                AppEventLogger.info("DATA", "Bulk delete count=${idsToDelete.size}")
                clearSelectionMode()
                loadContactsAndRender()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
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
        val btnPickAvatarPhoto = dialogView.findViewById<Button>(R.id.btnPickAvatarPhoto)
        val btnClearAvatarPhoto = dialogView.findViewById<Button>(R.id.btnClearAvatarPhoto)
        val layoutAvatarColorRow = dialogView.findViewById<View>(R.id.layoutAvatarColorRow)
        val imageAvatarPreview = dialogView.findViewById<ImageView>(R.id.imageAvatarPreview)
        val textAvatarPreview = dialogView.findViewById<TextView>(R.id.textAvatarPreview)
        val viewAvatarColorPreview = dialogView.findViewById<View>(R.id.viewAvatarColorPreview)
        val spinnerGroup = dialogView.findViewById<Spinner>(R.id.spinnerGroup)
        val colorEditorEnabled = isAvatarColorEditorEnabled()
        var selectedAvatarColor = if (colorEditorEnabled) contact?.avatarColor else null
        var selectedAvatarPhotoUri = contact?.avatarPhotoUri
        val avatarSeed = contact?.id ?: System.currentTimeMillis()

        layoutAvatarColorRow.visibility = if (colorEditorEnabled) View.VISIBLE else View.GONE

        setupBirthdayInputMask(inputBirthday)
        btnPickBirthday.setOnClickListener {
            showBirthdayDatePicker(inputBirthday)
        }
        btnPickAvatarColor.setOnClickListener {
            showAvatarColorPicker(selectedAvatarColor) { selectedHex ->
                selectedAvatarColor = selectedHex
                updateAvatarColorPreview(viewAvatarColorPreview, selectedAvatarColor, avatarSeed)
                updateAvatarDialogPreview(
                    imageAvatarPreview = imageAvatarPreview,
                    textAvatarPreview = textAvatarPreview,
                    avatarUri = selectedAvatarPhotoUri,
                    name = inputName.text.toString(),
                    lastName = inputLastName.text.toString(),
                    colorHex = selectedAvatarColor,
                    avatarSeed = avatarSeed,
                )
            }
        }
        viewAvatarColorPreview.setOnClickListener { btnPickAvatarColor.performClick() }
        btnPickAvatarPhoto.setOnClickListener {
            pendingAvatarPhotoResult = { uriString ->
                selectedAvatarPhotoUri = uriString
                updateAvatarDialogPreview(
                    imageAvatarPreview = imageAvatarPreview,
                    textAvatarPreview = textAvatarPreview,
                    avatarUri = selectedAvatarPhotoUri,
                    name = inputName.text.toString(),
                    lastName = inputLastName.text.toString(),
                    colorHex = selectedAvatarColor,
                    avatarSeed = avatarSeed,
                )
            }
            pickAvatarPhotoLauncher.launch(arrayOf("image/*"))
        }
        btnClearAvatarPhoto.setOnClickListener {
            selectedAvatarPhotoUri = null
            updateAvatarDialogPreview(
                imageAvatarPreview = imageAvatarPreview,
                textAvatarPreview = textAvatarPreview,
                avatarUri = null,
                name = inputName.text.toString(),
                lastName = inputLastName.text.toString(),
                colorHex = selectedAvatarColor,
                avatarSeed = avatarSeed,
            )
        }
        inputName.doAfterTextChanged {
            updateAvatarDialogPreview(
                imageAvatarPreview = imageAvatarPreview,
                textAvatarPreview = textAvatarPreview,
                avatarUri = selectedAvatarPhotoUri,
                name = inputName.text.toString(),
                lastName = inputLastName.text.toString(),
                colorHex = selectedAvatarColor,
                avatarSeed = avatarSeed,
            )
        }
        inputLastName.doAfterTextChanged {
            updateAvatarDialogPreview(
                imageAvatarPreview = imageAvatarPreview,
                textAvatarPreview = textAvatarPreview,
                avatarUri = selectedAvatarPhotoUri,
                name = inputName.text.toString(),
                lastName = inputLastName.text.toString(),
                colorHex = selectedAvatarColor,
                avatarSeed = avatarSeed,
            )
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
            val index = editGroupCodes.indexOf(contact.group).takeIf { it >= 0 }
                ?: editGroupCodes.indexOf(ContactPrefsStorage.GROUP_UNASSIGNED).takeIf { it >= 0 }
                ?: 0
            spinnerGroup.setSelection(index)
        }
        updateAvatarDialogPreview(
            imageAvatarPreview = imageAvatarPreview,
            textAvatarPreview = textAvatarPreview,
            avatarUri = selectedAvatarPhotoUri,
            name = inputName.text.toString(),
            lastName = inputLastName.text.toString(),
            colorHex = selectedAvatarColor,
            avatarSeed = avatarSeed,
        )

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
                val group = editGroupCodes.getOrElse(groupIndex) { ContactPrefsStorage.GROUP_UNASSIGNED }

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
                    avatarColor = if (colorEditorEnabled) selectedAvatarColor else null,
                    avatarPhotoUri = selectedAvatarPhotoUri,
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

        dialog.setOnDismissListener {
            pendingAvatarPhotoResult = null
        }
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showAvatarColorPicker(
        currentColorHex: String?,
        onSelected: (String?) -> Unit,
    ) {
        val options = arrayOf(
            getString(R.string.avatar_color_random),
            getString(R.string.avatar_color_from_palette),
            getString(R.string.avatar_color_custom),
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.action_pick_avatar_color)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onSelected(null)
                    1 -> showPaletteColorPicker(currentColorHex, onSelected)
                    2 -> showCustomColorPicker(currentColorHex, onSelected)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showPaletteColorPicker(
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

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.avatar_color_from_palette)
            .setSingleChoiceItems(options.toTypedArray(), selectedIndex) { dialog, which ->
                if (which == 0) {
                    onSelected(null)
                } else {
                    onSelected(colors[which - 1].uppercase())
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showCustomColorPicker(
        currentColorHex: String?,
        onSelected: (String?) -> Unit,
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_custom_color, null)
        val preview = dialogView.findViewById<View>(R.id.viewColorPreview)
        val textHex = dialogView.findViewById<TextView>(R.id.textColorHex)
        val seekRed = dialogView.findViewById<SeekBar>(R.id.seekRed)
        val seekGreen = dialogView.findViewById<SeekBar>(R.id.seekGreen)
        val seekBlue = dialogView.findViewById<SeekBar>(R.id.seekBlue)

        val initialColor = runCatching {
            Color.parseColor(AvatarColorPalette.normalizeHex(currentColorHex) ?: "#64748B")
        }.getOrDefault(Color.parseColor("#64748B"))

        seekRed.progress = Color.red(initialColor)
        seekGreen.progress = Color.green(initialColor)
        seekBlue.progress = Color.blue(initialColor)

        fun refreshPreview() {
            val color = Color.rgb(seekRed.progress, seekGreen.progress, seekBlue.progress)
            val hex = String.format(Locale.US, "#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color))
            textHex.text = hex
            preview.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(color)
                setStroke(dp(1), 0xFFE2E8F0.toInt())
            }
        }

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = refreshPreview()
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        seekRed.setOnSeekBarChangeListener(listener)
        seekGreen.setOnSeekBarChangeListener(listener)
        seekBlue.setOnSeekBarChangeListener(listener)
        refreshPreview()

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.avatar_color_custom)
            .setView(dialogView)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val hex = String.format(
                    Locale.US,
                    "#%02X%02X%02X",
                    seekRed.progress,
                    seekGreen.progress,
                    seekBlue.progress,
                )
                onSelected(hex)
            }
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun updateAvatarColorPreview(previewView: View, colorHex: String?, seed: Long) {
        val colorInt = AvatarColorPalette.resolveColorInt(colorHex, seed)
        previewView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colorInt)
            setStroke(dp(1), 0xFFE2E8F0.toInt())
        }
    }

    private fun updateAvatarDialogPreview(
        imageAvatarPreview: ImageView,
        textAvatarPreview: TextView,
        avatarUri: String?,
        name: String,
        lastName: String,
        colorHex: String?,
        avatarSeed: Long,
    ) {
        val normalizedUri = avatarUri?.trim().orEmpty()
        if (normalizedUri.isNotEmpty()) {
            val shown = runCatching {
                imageAvatarPreview.setImageURI(Uri.parse(normalizedUri))
                true
            }.getOrElse { false }
            if (shown) {
                imageAvatarPreview.visibility = View.VISIBLE
                textAvatarPreview.visibility = View.GONE
                return
            }
        }

        imageAvatarPreview.visibility = View.GONE
        textAvatarPreview.visibility = View.VISIBLE
        textAvatarPreview.text = buildInitials(name, lastName)
        textAvatarPreview.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(AvatarColorPalette.resolveColorInt(colorHex, avatarSeed))
        }
    }

    private fun buildInitials(name: String, lastName: String): String {
        val firstName = name.trim()
        val secondName = lastName.trim()
        if (firstName.isBlank() && secondName.isBlank()) return "?"

        if (secondName.isNotBlank()) {
            val first = firstName.firstOrNull { !it.isWhitespace() }?.toString().orEmpty()
            val second = secondName.firstOrNull { !it.isWhitespace() }?.toString().orEmpty()
            return (first + second).ifBlank { "?" }.uppercase(Locale.getDefault())
        }

        val chars = firstName.filter { !it.isWhitespace() }.take(2)
        return if (chars.isBlank()) "?" else chars.uppercase(Locale.getDefault())
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
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.title_delete_contact)
            .setMessage(getString(R.string.message_delete_contact, listOfNotNull(contact.name, contact.lastName).joinToString(" ")))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                storage.delete(contact.id)
                AppEventLogger.info("DATA", "Deleted contact id=${contact.id}")
                loadContactsAndRender()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun mapGroupLabel(code: String): String {
        return storage.getGroupTitle(code)
    }

    private fun showGroupManagementDialog() {
        val groups = storage.getGroupsForManage()
        val items = mutableListOf<String>()
        items.add(getString(R.string.group_manage_create))
        items.addAll(groups.map { (_, title) -> title })

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.group_manage_title)
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) {
                    showCreateGroupDialog()
                } else {
                    val selected = groups.getOrNull(which - 1) ?: return@setItems
                    showGroupActionsDialog(selected.first, selected.second)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showCreateGroupDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.group_name_hint)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_dialog_input)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.group_manage_create_title)
            .setView(input)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val created = storage.createGroup(input.text.toString())
                if (created == null) {
                    Toast.makeText(this, R.string.group_manage_name_invalid, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                AppEventLogger.info("GROUP", "Created group code=$created")
                setupFilterGroups()
                loadContactsAndRender()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showGroupActionsDialog(groupCode: String, groupTitle: String) {
        val options = arrayOf(
            getString(R.string.group_manage_rename),
            getString(R.string.group_manage_delete),
        )
        val dialog = AlertDialog.Builder(this)
            .setTitle(groupTitle)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameGroupDialog(groupCode, groupTitle)
                    1 -> confirmDeleteGroup(groupCode, groupTitle)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showRenameGroupDialog(groupCode: String, currentTitle: String) {
        val input = EditText(this).apply {
            hint = getString(R.string.group_name_hint)
            setText(currentTitle)
            setSelection(text.length)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_dialog_input)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.group_manage_rename_title)
            .setView(input)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val renamed = storage.renameGroup(groupCode, input.text.toString())
                if (!renamed) {
                    Toast.makeText(this, R.string.group_manage_name_invalid, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                AppEventLogger.info("GROUP", "Renamed group code=$groupCode")
                setupFilterGroups()
                loadContactsAndRender()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun confirmDeleteGroup(groupCode: String, groupTitle: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.group_manage_delete_title)
            .setMessage(getString(R.string.group_manage_delete_message, groupTitle))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                val reassigned = storage.deleteGroup(groupCode)
                AppEventLogger.info("GROUP", "Deleted group code=$groupCode, reassigned=$reassigned")
                Toast.makeText(this, getString(R.string.group_manage_delete_done, reassigned), Toast.LENGTH_SHORT).show()
                setupFilterGroups()
                loadContactsAndRender()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showSettingsMenu() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings_panel, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_menu_title)
            .setView(dialogView)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialogView.findViewById<Button>(R.id.btnSettingsImport).setOnClickListener {
                dialog.dismiss()
                showImportContactsConfirmDialog()
            }
            dialogView.findViewById<Button>(R.id.btnSettingsExport).setOnClickListener {
                dialog.dismiss()
                startVcfExportFlow()
            }
            dialogView.findViewById<Button>(R.id.btnSettingsTheme).setOnClickListener {
                dialog.dismiss()
                showThemeSelectorDialog()
            }
            dialogView.findViewById<Button>(R.id.btnSettingsClearAll).setOnClickListener {
                dialog.dismiss()
                confirmClearAllContacts()
            }
            styleDialogButtons(dialog)
        }
        dialog.show()
    }

    private fun showThemeSelectorDialog() {
        val options = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
        )
        val currentIndex = if (ThemeManager.isDarkThemeEnabled(this)) 1 else 0

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme_title)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                ThemeManager.setDarkThemeEnabled(this, which == 1)
                AppEventLogger.info("THEME", "Theme changed to ${if (which == 1) "dark" else "light"}")
                Toast.makeText(this, R.string.settings_theme_saved, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showGoogleModeDialog() {
        val connectedAccount = devPrefs.getString(KEY_NETWORK_ACCOUNT, null).orEmpty()
        val extra = if (connectedAccount.isBlank()) "" else "\n\n${getString(R.string.google_mode_connected, connectedAccount)}"
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.google_mode_title)
            .setMessage(getString(R.string.google_mode_message) + extra)
            .setPositiveButton(R.string.google_mode_connect) { _, _ ->
                startGoogleSignIn()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun startGoogleSignIn() {
        if (!hasInternetConnection()) {
            showNoInternetDialog { startGoogleSignIn() }
            return
        }

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(this, options)
        googleSignInLauncher.launch(client.signInIntent)
    }

    private fun startVcfExportFlow() {
        val contacts = storage.getAllContacts()
        if (contacts.isEmpty()) {
            Toast.makeText(this, R.string.empty_contacts, Toast.LENGTH_SHORT).show()
            return
        }

        val payload = buildVcfPayload(contacts)
        if (payload.isBlank()) {
            Toast.makeText(this, R.string.settings_export_error, Toast.LENGTH_SHORT).show()
            return
        }

        pendingVcfPayload = payload
        val fileName = "ContactManager-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.vcf"
        exportVcfLauncher.launch(fileName)
    }

    private fun confirmClearAllContacts() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_clear_all_title)
            .setMessage(R.string.settings_clear_all_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                storage.clearAll()
                loadContactsAndRender()
                AppEventLogger.info("SETTINGS", "All contacts removed by user")
                Toast.makeText(this, R.string.settings_clear_all_done, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun buildVcfPayload(contacts: List<Contact>): String {
        val builder = StringBuilder()
        contacts.forEach { contact ->
            val fullName = listOfNotNull(contact.name, contact.lastName).joinToString(" ").trim()
            builder.appendLine("BEGIN:VCARD")
            builder.appendLine("VERSION:3.0")
            builder.appendLine("N:${escapeVcf(contact.lastName.orEmpty())};${escapeVcf(contact.name)};;;")
            builder.appendLine("FN:${escapeVcf(fullName.ifBlank { contact.name })}")
            builder.appendLine("TEL;TYPE=CELL:${escapeVcf(contact.phone)}")
            contact.email?.takeIf { it.isNotBlank() }?.let { builder.appendLine("EMAIL;TYPE=INTERNET:${escapeVcf(it)}") }
            contact.address?.takeIf { it.isNotBlank() }?.let { builder.appendLine("ADR;TYPE=HOME:;;${escapeVcf(it)};;;;") }
            contact.comment?.takeIf { it.isNotBlank() }?.let { builder.appendLine("NOTE:${escapeVcf(it)}") }
            toVcfBirthday(contact.birthday)?.let { builder.appendLine("BDAY:$it") }
            builder.appendLine("END:VCARD")
        }
        return builder.toString()
    }

    private fun escapeVcf(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    private fun toVcfBirthday(rawBirthday: String?): String? {
        val normalized = rawBirthday?.let { normalizeBirthday(it) } ?: return null
        val parts = normalized.split("/")
        if (parts.size != 3) return null
        return "${parts[2]}-${parts[1]}-${parts[0]}"
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
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.import_contacts_title)
            .setMessage(R.string.import_contacts_message)
            .setPositiveButton(R.string.import_contacts_action) { _, _ ->
                requestContactsPermissionAndImport()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
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
        val deviceGroupTitles = loadDeviceGroupTitleMap()

        var imported = 0
        var nextIdSeed = System.currentTimeMillis()
        val contactsCursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
            ),
            null,
            null,
            null,
        ) ?: return 0

        contactsCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val contactId = cursor.getLongOrNull(ContactsContract.Contacts._ID) ?: continue
                val displayName = cursor.getStringOrNull(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY).orEmpty()
                val photoUri = cursor.getStringOrNull(ContactsContract.Contacts.PHOTO_URI)

                val details = loadDeviceContactDetails(
                    contactId = contactId,
                    displayName = displayName,
                    photoUriFromContacts = photoUri,
                    deviceGroupTitles = deviceGroupTitles,
                ) ?: continue
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
                        avatarPhotoUri = details.avatarPhotoUri,
                        group = details.groupCode,
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

    private fun loadDeviceContactDetails(
        contactId: Long,
        displayName: String,
        photoUriFromContacts: String?,
        deviceGroupTitles: Map<Long, String>,
    ): ImportedContactDetails? {
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
            avatarPhotoUri = photoUriFromContacts?.trim()?.ifBlank { null } ?: queryPhotoUri(contactId),
            groupCode = resolveImportedGroupCode(contactId, deviceGroupTitles),
        )
    }

    private fun loadDeviceGroupTitleMap(): Map<Long, String> {
        val result = linkedMapOf<Long, String>()
        val cursor = contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
            null,
            null,
            null,
        ) ?: return emptyMap()

        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLongOrNull(ContactsContract.Groups._ID) ?: continue
                val title = it.getStringOrNull(ContactsContract.Groups.TITLE).orEmpty().trim()
                if (title.isBlank()) continue
                result[id] = title
            }
        }
        return result
    }

    private fun resolveImportedGroupCode(contactId: Long, deviceGroupTitles: Map<Long, String>): String {
        val titles = queryContactGroupTitles(contactId, deviceGroupTitles)
        if (titles.isEmpty()) return ContactPrefsStorage.GROUP_OTHER
        val targetTitle = titles.firstOrNull { !isSystemContactGroupTitle(it) } ?: titles.first()
        return storage.ensureGroupByTitle(targetTitle)
    }

    private fun queryContactGroupTitles(contactId: Long, deviceGroupTitles: Map<Long, String>): List<String> {
        val cursor = contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID),
            "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
            arrayOf(
                contactId.toString(),
                ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
            ),
            null,
        ) ?: return emptyList()

        val titles = linkedSetOf<String>()
        cursor.use {
            while (it.moveToNext()) {
                val rowId = it.getLongOrNull(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID) ?: continue
                val title = deviceGroupTitles[rowId].orEmpty().trim()
                if (title.isNotBlank()) titles.add(title)
            }
        }
        return titles.toList()
    }

    private fun isSystemContactGroupTitle(title: String): Boolean {
        val normalized = title.trim().lowercase(Locale.getDefault())
        return normalized == "my contacts" ||
            normalized == "starred in android" ||
            normalized == "избранные" ||
            normalized == "контакты" ||
            normalized == "contacts"
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
                display.isNotBlank() -> display
                else -> fallbackDisplayName.trim()
            }
            return baseName to familyName
        }

        return fallbackDisplayName.trim() to null
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

    private fun queryPhotoUri(contactId: Long): String? {
        val cursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.PHOTO_URI),
            "${ContactsContract.Contacts._ID}=?",
            arrayOf(contactId.toString()),
            null,
        ) ?: return null

        cursor.use {
            while (it.moveToNext()) {
                val uri = it.getStringOrNull(ContactsContract.Contacts.PHOTO_URI).orEmpty().trim()
                if (uri.isNotBlank()) return uri
            }
        }
        return null
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
        val switchColorEditor = dialogView.findViewById<SwitchCompat>(R.id.switchColorEditor)
        val textVersionValue = dialogView.findViewById<TextView>(R.id.textVersionValue)
        val textLogPathValue = dialogView.findViewById<TextView>(R.id.textLogPathValue)
        val btnCheckUpdates = dialogView.findViewById<Button>(R.id.btnCheckUpdates)
        val btnOpenLogs = dialogView.findViewById<Button>(R.id.btnOpenLogs)
        val btnDevelopersInfo = dialogView.findViewById<Button>(R.id.btnDevelopersInfo)
        val textUpdateResult = dialogView.findViewById<TextView>(R.id.textUpdateResult)

        switchLogs.isChecked = AppEventLogger.isLoggingEnabled()
        switchColorEditor.isChecked = isAvatarColorEditorEnabled()
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
        switchColorEditor.setOnCheckedChangeListener { _, enabled ->
            setAvatarColorEditorEnabled(enabled)
            Toast.makeText(
                this,
                if (enabled) getString(R.string.admin_color_editor_on) else getString(R.string.admin_color_editor_off),
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
        btnOpenLogs.setOnClickListener { showLogFilesDialog() }
        btnDevelopersInfo.setOnClickListener { showDevelopersDialog() }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.admin_panel_title)
            .setView(dialogView)
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun isAvatarColorEditorEnabled(): Boolean {
        return devPrefs.getBoolean(KEY_AVATAR_COLOR_EDITOR_ENABLED, false)
    }

    private fun setAvatarColorEditorEnabled(enabled: Boolean) {
        devPrefs.edit().putBoolean(KEY_AVATAR_COLOR_EDITOR_ENABLED, enabled).apply()
    }

    private fun showDevelopersDialog() {
        val lineKurenkov = getString(R.string.developers_line_kurenkov)
        val lineKozin = getString(R.string.developers_line_kozin)
        val lineProject = getString(R.string.developers_line_project)

        val builder = SpannableStringBuilder()
        builder.append(getString(R.string.developers_dialog_title))
        builder.append(":\n")

        val kurStart = builder.length
        builder.append(lineKurenkov)
        builder.setSpan(
            DeveloperLinkSpan("https://vk.com/lxrdx"),
            kurStart,
            kurStart + "Kurenkov E. E.".length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        builder.append('\n')

        val kozStart = builder.length
        builder.append(lineKozin)
        builder.setSpan(
            DeveloperLinkSpan("https://vk.com/id225880613"),
            kozStart,
            kozStart + "Kozin S. V.".length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        builder.append('\n')
        builder.append(lineProject)

        val textView = TextView(this).apply {
            text = builder
            movementMethod = LinkMovementMethod.getInstance()
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setLineSpacing(0f, 1.15f)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.developers_dialog_title)
            .setView(textView)
            .setPositiveButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showLogFilesDialog() {
        val files = AppEventLogger.listLogFiles(this)
        if (files.isEmpty()) {
            Toast.makeText(this, R.string.admin_logs_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val names = files.map { it.name }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.admin_open_logs)
            .setItems(names) { _, which ->
                val fileName = names.getOrNull(which) ?: return@setItems
                showLogContentDialog(fileName)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showLogContentDialog(fileName: String) {
        val content = AppEventLogger.readLogFile(this, fileName)
        if (content == null) {
            Toast.makeText(this, R.string.admin_log_open_error, Toast.LENGTH_SHORT).show()
            return
        }
        val textView = TextView(this).apply {
            text = content
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setLineSpacing(0f, 1.15f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val container = ScrollView(this).apply {
            addView(
                textView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(fileName)
            .setView(container)
            .setPositiveButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun showNoInternetDialog(onRetry: () -> Unit) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.admin_no_internet_title)
            .setMessage(R.string.admin_no_internet_message)
            .setPositiveButton(R.string.admin_action_try_again) { _, _ -> onRetry() }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.show()
        styleDialogButtons(dialog)
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
                        val dialog = AlertDialog.Builder(this)
                            .setTitle(R.string.admin_panel_title)
                            .setMessage(
                                if (latest.isPrerelease) {
                                    getString(R.string.admin_update_available_prerelease, latestNormalized)
                                } else {
                                    getString(R.string.admin_update_available, latestNormalized)
                                },
                            )
                            .setPositiveButton(R.string.admin_action_download_update) { _, _ ->
                                startUpdateDownload(latest.downloadUrl, latestNormalized)
                            }
                            .setNegativeButton(R.string.action_cancel, null)
                            .create()
                        dialog.show()
                        styleDialogButtons(dialog)
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

    private fun startUpdateDownload(downloadUrl: String?, versionName: String) {
        val url = downloadUrl?.trim().orEmpty()
        if (url.isBlank()) {
            Toast.makeText(this, R.string.admin_update_download_unavailable, Toast.LENGTH_SHORT).show()
            AppEventLogger.warn("UPDATE", "Download url is missing for version=$versionName")
            return
        }

        val fileName = "ContactManager-$versionName.apk"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(getString(R.string.admin_download_title, versionName))
            .setDescription(getString(R.string.admin_download_description))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        runCatching {
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
        }.onSuccess {
            Toast.makeText(this, getString(R.string.admin_download_started, fileName), Toast.LENGTH_LONG).show()
            AppEventLogger.info("UPDATE", "APK download started: $fileName")
        }.onFailure { error ->
            Toast.makeText(this, R.string.admin_download_failed, Toast.LENGTH_SHORT).show()
            AppEventLogger.error("UPDATE", "APK download failed", error)
        }
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
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
        }
        val subtitle = TextView(this).apply {
            text = getString(R.string.error_startup_subtitle)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
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
                avatarPhotoUri = it.avatarPhotoUri?.trim()?.ifBlank { null },
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

    private inner class DeveloperLinkSpan(private val url: String) : ClickableSpan() {
        override fun onClick(widget: View) {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }

        override fun updateDrawState(ds: TextPaint) {
            super.updateDrawState(ds)
            ds.color = ContextCompat.getColor(this@MainActivity, R.color.accent)
            ds.isUnderlineText = true
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
        val avatarPhotoUri: String?,
        val groupCode: String,
    )

    companion object {
        private const val COMMENT_MAX_LENGTH = 512
        private const val ADMIN_HOLD_DURATION_MS = 5_000L
        private const val DEV_PREFS_NAME = "contact_manager_dev_settings"
        private const val KEY_NETWORK_MODE_ENABLED = "network_mode_enabled"
        private const val KEY_NETWORK_ACCOUNT = "network_mode_account"
        private const val KEY_AVATAR_COLOR_EDITOR_ENABLED = "avatar_color_editor_enabled"
    }
}
