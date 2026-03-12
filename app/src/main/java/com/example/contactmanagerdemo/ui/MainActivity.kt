package com.example.contactmanagerdemo.ui

import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.data.Contact
import com.example.contactmanagerdemo.data.ContactPrefsStorage
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var storage: ContactPrefsStorage
    private lateinit var adapter: ContactAdapter

    private lateinit var searchInput: EditText
    private lateinit var groupChips: ChipGroup
    private lateinit var totalCount: TextView
    private lateinit var workCount: TextView
    private lateinit var importedCount: TextView
    private lateinit var emptyState: TextView

    private lateinit var chipAll: Chip
    private lateinit var chipFamily: Chip
    private lateinit var chipFriends: Chip
    private lateinit var chipWork: Chip
    private lateinit var chipOther: Chip

    private lateinit var btnGoogleAuth: ImageButton

    private var isGoogleAuthed = false
    private var allContacts: List<Contact> = emptyList()

    private val csvPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            importFromCsv(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage = ContactPrefsStorage(this)

        searchInput = findViewById(R.id.searchInput)
        groupChips = findViewById(R.id.groupChips)
        totalCount = findViewById(R.id.totalCount)
        workCount = findViewById(R.id.workCount)
        importedCount = findViewById(R.id.importedCount)
        emptyState = findViewById(R.id.emptyState)

        chipAll = findViewById(R.id.chip_all)
        chipFamily = findViewById(R.id.chip_family)
        chipFriends = findViewById(R.id.chip_friends)
        chipWork = findViewById(R.id.chip_work)
        chipOther = findViewById(R.id.chip_other)

        btnGoogleAuth = findViewById(R.id.btnGoogleAuth)

        adapter = ContactAdapter(
            onEdit = { openEditContact(it.id) },
            onDelete = { deleteContact(it) },
        )

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            openCreateContact()
        }

        findViewById<ImageButton>(R.id.btnImport).setOnClickListener { showImportOptions() }
        btnGoogleAuth.setOnClickListener { toggleGoogleAuth() }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        groupChips.setOnCheckedChangeListener { _, _ -> applyFilters() }
    }

    override fun onResume() {
        super.onResume()
        ensureSeedData()
        loadContacts()
    }

    private fun ensureSeedData() {
        if (storage.getAllContacts().isNotEmpty()) return

        val now = System.currentTimeMillis()
        storage.saveAllContacts(
            listOf(
                Contact(
                    id = now - 3,
                    firstName = "Анна",
                    lastName = "Иванова",
                    phone = "+7 (999) 123-45-67",
                    email = "anna@work.com",
                    group = "work",
                    isWorkContact = true,
                    workTask = null,
                    address = "Москва",
                    birthday = "1990-05-15",
                    imported = false,
                    createdAt = now,
                    updatedAt = now,
                ),
                Contact(
                    id = now - 2,
                    firstName = "Петр",
                    lastName = "Сидоров",
                    phone = "+7 (999) 765-43-21",
                    email = null,
                    group = "friends",
                    isWorkContact = false,
                    workTask = null,
                    address = null,
                    birthday = null,
                    imported = false,
                    createdAt = now,
                    updatedAt = now,
                ),
                Contact(
                    id = now - 1,
                    firstName = "Мария",
                    lastName = "Петрова",
                    phone = "+7 (999) 555-55-55",
                    email = null,
                    group = "family",
                    isWorkContact = false,
                    workTask = null,
                    address = null,
                    birthday = null,
                    imported = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
    }

    private fun loadContacts() {
        allContacts = storage.getAllContacts().sortedBy { it.firstName.lowercase(Locale.getDefault()) }
        updateStats()
        updateChipCounters()
        applyFilters()
    }

    private fun updateStats() {
        val work = allContacts.count { it.group == "work" }
        val imported = allContacts.count { it.imported }
        totalCount.text = "Всего: ${allContacts.size}"
        workCount.text = "Работа: $work"
        importedCount.text = "Импорт: $imported"
    }

    private fun updateChipCounters() {
        chipAll.text = "Все (${allContacts.size})"
        chipFamily.text = "Семья (${allContacts.count { it.group == "family" }})"
        chipFriends.text = "Друзья (${allContacts.count { it.group == "friends" }})"
        chipWork.text = "Работа (${allContacts.count { it.group == "work" }})"
        chipOther.text = "Другое (${allContacts.count { it.group == "other" }})"
    }

    private fun applyFilters() {
        val query = searchInput.text?.toString()?.trim().orEmpty().lowercase(Locale.getDefault())
        val group = when (groupChips.checkedChipId) {
            R.id.chip_family -> "family"
            R.id.chip_friends -> "friends"
            R.id.chip_work -> "work"
            R.id.chip_other -> "other"
            else -> "all"
        }

        val filtered = allContacts.filter { contact ->
            val matchGroup = group == "all" || contact.group == group
            val matchQuery = query.isEmpty() ||
                contact.firstName.lowercase(Locale.getDefault()).contains(query) ||
                (contact.lastName?.lowercase(Locale.getDefault())?.contains(query) == true) ||
                contact.phone.contains(query)
            matchGroup && matchQuery
        }

        adapter.submitContacts(filtered)
        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openCreateContact() {
        startActivity(Intent(this, EditContactActivity::class.java))
    }

    private fun openEditContact(id: Long) {
        val intent = Intent(this, EditContactActivity::class.java)
        intent.putExtra(EditContactActivity.EXTRA_CONTACT_ID, id)
        startActivity(intent)
    }

    private fun deleteContact(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle("Удалить контакт?")
            .setMessage("${contact.firstName} ${contact.lastName.orEmpty()}")
            .setPositiveButton("Удалить") { _, _ ->
                storage.delete(contact.id)
                showToast("Контакт удален", false)
                loadContacts()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showImportOptions() {
        val options = arrayOf("Импорт CSV", "Импорт Google (демо)", "Ручной ввод")
        AlertDialog.Builder(this)
            .setTitle("Импорт контактов")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> csvPicker.launch("text/*")
                    1 -> importGoogleMock()
                    2 -> showManualImportDialog()
                }
            }
            .show()
    }

    private fun importFromCsv(uri: Uri) {
        val rows = mutableListOf<Pair<String, String>>()
        contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split(",")
                    if (parts.size >= 2) {
                        val name = parts[0].trim()
                        val phone = parts[1].trim()
                        if (name.isNotBlank() && phone.isNotBlank()) {
                            rows.add(name to phone)
                        }
                    }
                }
            }
        }

        importNamePhoneRows(rows)
    }

    private fun showManualImportDialog() {
        val input = EditText(this).apply {
            hint = "Имя,Телефон"
            minLines = 5
        }

        AlertDialog.Builder(this)
            .setTitle("Ручной импорт")
            .setView(input)
            .setPositiveButton("Импортировать") { _, _ ->
                val rows = input.text.toString()
                    .split("\n")
                    .mapNotNull { line ->
                        val parts = line.split(",")
                        if (parts.size >= 2) {
                            val name = parts[0].trim()
                            val phone = parts[1].trim()
                            if (name.isNotBlank() && phone.isNotBlank()) name to phone else null
                        } else {
                            null
                        }
                    }
                importNamePhoneRows(rows)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun importGoogleMock() {
        val rows = listOf(
            "Импорт Google 1" to "+79991112233",
            "Импорт Google 2" to "+79991112244",
        )
        importNamePhoneRows(rows)
    }

    private fun importNamePhoneRows(rows: List<Pair<String, String>>) {
        if (rows.isEmpty()) return

        val now = System.currentTimeMillis()
        val list = storage.getAllContacts()
        rows.forEachIndexed { index, pair ->
            val nameParts = pair.first.split(" ").filter { it.isNotBlank() }
            list.add(
                Contact(
                    id = now + index,
                    firstName = nameParts.firstOrNull().orEmpty(),
                    lastName = nameParts.drop(1).joinToString(" ").ifBlank { null },
                    phone = pair.second,
                    email = null,
                    group = "other",
                    isWorkContact = false,
                    workTask = null,
                    address = null,
                    birthday = null,
                    imported = true,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        storage.saveAllContacts(list)
        showToast("Импортировано: ${rows.size}", true)
        loadContacts()
    }

    private fun toggleGoogleAuth() {
        isGoogleAuthed = !isGoogleAuthed
        val color = if (isGoogleAuthed) R.color.accent else android.R.color.white
        btnGoogleAuth.setColorFilter(ContextCompat.getColor(this, color), PorterDuff.Mode.SRC_IN)

        val msg = if (isGoogleAuthed) {
            "Вы вошли как user@gmail.com"
        } else {
            "Выход выполнен"
        }
        showToast(msg, true)
    }

    private fun showToast(message: String, isSuccess: Boolean) {
        val color = if (isSuccess) 0xFF06D6A0.toInt() else 0xFFEF476F.toInt()
        Snackbar.make(findViewById(R.id.recyclerView), message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(color)
            .show()
    }
}
