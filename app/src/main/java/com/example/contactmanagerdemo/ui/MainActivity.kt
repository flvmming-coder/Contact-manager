package com.example.contactmanagerdemo.ui

import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.data.Contact
import com.example.contactmanagerdemo.data.ContactRepository
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var repo: ContactRepository
    private lateinit var adapter: ContactAdapter

    private lateinit var currentTime: TextView
    private lateinit var searchInput: TextInputEditText
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
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateTime()
            clockHandler.postDelayed(this, 60_000L)
        }
    }

    private var allContacts: List<Contact> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = ContactRepository(this)

        currentTime = findViewById(R.id.currentTime)
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
        findViewById<ImageButton>(R.id.btnWork).setOnClickListener { showWorkDialog() }
        btnGoogleAuth.setOnClickListener { toggleGoogleAuth() }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        groupChips.setOnCheckedChangeListener { _, _ -> applyFilters() }

        updateTime()
        clockHandler.postDelayed(clockRunnable, 60_000L)
    }

    override fun onResume() {
        super.onResume()
        seedContactsIfNeeded()
        loadContacts()
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
    }

    private fun updateTime() {
        val text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        currentTime.text = text
    }

    private fun seedContactsIfNeeded() {
        if (repo.getAllContacts().isNotEmpty()) return

        val now = System.currentTimeMillis()
        repo.bulkImport(
            listOf(
                Contact(
                    firstName = "Анна",
                    lastName = "Иванова",
                    phone = "+7 (999) 123-45-67",
                    email = "anna@work.com",
                    group = "work",
                    isWorkContact = true,
                    workTask = "Связаться по проекту",
                    address = "Москва",
                    birthday = "1990-05-15",
                    imported = false,
                    createdAt = now,
                    updatedAt = now,
                ),
                Contact(
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
        allContacts = repo.getAllContacts().sortedBy { it.firstName.lowercase(Locale.getDefault()) }
        updateStats(allContacts)
        updateChipCounters(allContacts)
        applyFilters()
    }

    private fun updateStats(list: List<Contact>) {
        val workContacts = list.count { it.group == "work" || it.isWorkContact }
        val imported = list.count { it.imported }

        totalCount.text = "Всего: ${list.size}"
        workCount.text = "Работа: $workContacts"
        importedCount.text = "Импорт: $imported"
    }

    private fun updateChipCounters(list: List<Contact>) {
        chipAll.text = "Все (${list.size})"
        chipFamily.text = "Семья (${list.count { it.group == "family" }})"
        chipFriends.text = "Друзья (${list.count { it.group == "friends" }})"
        chipWork.text = "Работа (${list.count { it.group == "work" }})"
        chipOther.text = "Прочие (${list.count { it.group == "other" }})"
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

        val filtered = allContacts.filter { c ->
            val matchesGroup = group == "all" || c.group == group
            val matchesQuery = query.isEmpty() ||
                c.firstName.lowercase(Locale.getDefault()).contains(query) ||
                (c.lastName?.lowercase(Locale.getDefault())?.contains(query) == true) ||
                c.phone.contains(query)

            matchesGroup && matchesQuery
        }

        adapter.submitList(filtered)
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
                repo.deleteContact(contact.id)
                Snackbar.make(findViewById(R.id.recyclerView), "Контакт удален", Snackbar.LENGTH_SHORT).show()
                loadContacts()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showImportOptions() {
        val options = arrayOf("Импорт из текста", "Импорт Google (демо)")
        AlertDialog.Builder(this)
            .setTitle("Импорт")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showBulkImportDialog()
                    1 -> importGoogleMock()
                }
            }
            .show()
    }

    private fun showBulkImportDialog() {
        val input = EditText(this).apply {
            hint = "Имя,Телефон"
            minLines = 5
        }

        AlertDialog.Builder(this)
            .setTitle("Импорт из текста")
            .setView(input)
            .setPositiveButton("Импортировать") { _, _ ->
                val lines = input.text.toString().split("\n")
                val now = System.currentTimeMillis()
                val contacts = lines.mapNotNull { line ->
                    val parts = line.split(",")
                    if (parts.size < 2) return@mapNotNull null

                    val fullName = parts[0].trim()
                    val nameParts = fullName.split(" ")
                    Contact(
                        firstName = nameParts.firstOrNull().orEmpty(),
                        lastName = nameParts.drop(1).joinToString(" ").ifBlank { null },
                        phone = parts[1].trim(),
                        email = parts.getOrNull(2)?.trim()?.ifBlank { null },
                        group = "other",
                        isWorkContact = false,
                        workTask = null,
                        address = null,
                        birthday = null,
                        imported = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                }.filter { it.firstName.isNotBlank() && it.phone.isNotBlank() }

                if (contacts.isNotEmpty()) {
                    repo.bulkImport(contacts)
                    Snackbar.make(findViewById(R.id.recyclerView), "Импортировано: ${contacts.size}", Snackbar.LENGTH_SHORT).show()
                    loadContacts()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun importGoogleMock() {
        val now = System.currentTimeMillis()
        repo.bulkImport(
            listOf(
                Contact(
                    firstName = "Импорт",
                    lastName = "Google 1",
                    phone = "+79991112233",
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
                Contact(
                    firstName = "Импорт",
                    lastName = "Google 2",
                    phone = "+79991112244",
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
            ),
        )

        Snackbar.make(findViewById(R.id.recyclerView), "Импорт Google выполнен", Snackbar.LENGTH_SHORT).show()
        loadContacts()
    }

    private fun showWorkDialog() {
        val tasks = allContacts.filter { it.group == "work" || it.isWorkContact }.map { c ->
            val task = if (c.workTask.isNullOrBlank()) "Без задачи" else c.workTask
            "${c.firstName} ${c.lastName.orEmpty()} - $task"
        }

        AlertDialog.Builder(this)
            .setTitle("Рабочие задачи")
            .setMessage(if (tasks.isEmpty()) "Нет рабочих контактов" else tasks.joinToString("\n"))
            .setPositiveButton("Ок", null)
            .show()
    }

    private fun toggleGoogleAuth() {
        isGoogleAuthed = !isGoogleAuthed
        val color = if (isGoogleAuthed) R.color.accent else android.R.color.white
        btnGoogleAuth.setColorFilter(ContextCompat.getColor(this, color), PorterDuff.Mode.SRC_IN)

        val message = if (isGoogleAuthed) {
            "Вы вошли как user@gmail.com"
        } else {
            "Выход выполнен"
        }
        Snackbar.make(findViewById(R.id.recyclerView), message, Snackbar.LENGTH_SHORT).show()
    }
}
