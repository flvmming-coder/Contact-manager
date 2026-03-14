package com.example.contactmanagerdemo.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.core.AppEventLogger
import com.example.contactmanagerdemo.data.Contact
import com.example.contactmanagerdemo.data.ContactPrefsStorage
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL

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
    private val headerTapHistory: MutableList<Long> = mutableListOf()

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
            findViewById<View>(R.id.headerTriggerArea).setOnClickListener {
                handleHeaderTap()
            }
            inputSearch.doAfterTextChanged { renderContacts() }

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
        val spinnerGroup = dialogView.findViewById<Spinner>(R.id.spinnerGroup)

        setupBirthdayInputMask(inputBirthday)
        btnPickBirthday.setOnClickListener {
            showBirthdayDatePicker(inputBirthday)
        }

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

    private fun handleHeaderTap() {
        val now = SystemClock.elapsedRealtime()
        headerTapHistory.add(now)
        headerTapHistory.removeAll { timestamp -> now - timestamp > ADMIN_TAP_WINDOW_MS }

        if (headerTapHistory.size >= ADMIN_TAP_COUNT) {
            headerTapHistory.clear()
            AppEventLogger.info("ADMIN", "Developer panel requested")
            showAdminPanel()
        }
    }

    private fun showAdminPanel() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_panel, null)
        val switchLogs = dialogView.findViewById<SwitchCompat>(R.id.switchLogs)
        val textVersionValue = dialogView.findViewById<TextView>(R.id.textVersionValue)
        val textLogPathValue = dialogView.findViewById<TextView>(R.id.textLogPathValue)
        val btnCheckUpdates = dialogView.findViewById<Button>(R.id.btnCheckUpdates)
        val textUpdateResult = dialogView.findViewById<TextView>(R.id.textUpdateResult)

        switchLogs.isChecked = AppEventLogger.isLoggingEnabled()
        textVersionValue.text = getString(R.string.admin_version_value, getAppVersionName())
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
            val result = runCatching { fetchLatestReleaseInfo() }
            runOnUiThread {
                result.onSuccess { latest ->
                    val latestNormalized = normalizeVersionTag(latest.tagName)
                    val currentNormalized = normalizeVersionTag(getAppVersionName())
                    val hasUpdate = compareVersions(latestNormalized, currentNormalized) > 0
                    if (hasUpdate) {
                        textUpdateResult.text = getString(R.string.admin_update_available, latestNormalized)
                        AppEventLogger.info("ADMIN", "Update available: $latestNormalized")
                        if (latest.htmlUrl.isNotBlank()) {
                            AlertDialog.Builder(this)
                                .setTitle(R.string.admin_panel_title)
                                .setMessage(getString(R.string.admin_update_available, latestNormalized))
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

    private fun fetchLatestReleaseInfo(): LatestReleaseInfo {
        val url = URL(GITHUB_LATEST_RELEASE_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("GitHub API response code=$responseCode")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            LatestReleaseInfo(
                tagName = json.optString("tag_name", ""),
                htmlUrl = json.optString("html_url", ""),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeVersionTag(rawVersion: String): String {
        return rawVersion.trim().removePrefix("v").removePrefix("V").ifBlank { "0.0.0" }
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = parseVersionParts(left)
        val rightParts = parseVersionParts(right)
        val maxLen = maxOf(leftParts.size, rightParts.size)
        for (i in 0 until maxLen) {
            val a = leftParts.getOrElse(i) { 0 }
            val b = rightParts.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return 0
    }

    private fun parseVersionParts(version: String): List<Int> {
        return version.split(".")
            .map { part ->
                part.takeWhile { ch -> ch.isDigit() }
                    .toIntOrNull() ?: 0
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

    private data class LatestReleaseInfo(
        val tagName: String,
        val htmlUrl: String,
    )

    companion object {
        private const val COMMENT_MAX_LENGTH = 512
        private const val ADMIN_TAP_COUNT = 5
        private const val ADMIN_TAP_WINDOW_MS = 2_000L
        private const val NETWORK_TIMEOUT_MS = 8_000
        private const val GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/flvmming-coder/Contact-manager/releases/latest"
    }
}
