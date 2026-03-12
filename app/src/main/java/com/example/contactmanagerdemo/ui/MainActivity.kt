package com.example.contactmanagerdemo.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.data.Contact
import com.example.contactmanagerdemo.data.ContactRepository
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.appbar.MaterialToolbar
import android.view.MenuItem
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var repo: ContactRepository
    private lateinit var adapter: ContactAdapter

    private lateinit var searchInput: TextInputEditText
    private lateinit var groupChips: ChipGroup
    private lateinit var totalCount: TextView
    private lateinit var workCount: TextView
    private lateinit var importedCount: TextView

    private var allContacts: List<Contact> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = ContactRepository(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setOnMenuItemClickListener { onMenuItemSelected(it) }

        searchInput = findViewById(R.id.searchInput)
        groupChips = findViewById(R.id.groupChips)
        totalCount = findViewById(R.id.totalCount)
        workCount = findViewById(R.id.workCount)
        importedCount = findViewById(R.id.importedCount)

        adapter = ContactAdapter(
            onEdit = { openEditContact(it.id) },
            onDelete = { deleteContact(it) }
        )

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            openCreateContact()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        groupChips.setOnCheckedChangeListener { _, _ ->
            applyFilters()
        }
    }

    override fun onResume() {
        super.onResume()
        loadContacts()
    }

    private fun loadContacts() {
        allContacts = repo.getAllContacts()
        updateStats(allContacts)
        applyFilters()
    }

    private fun updateStats(list: List<Contact>) {
        totalCount.text = list.size.toString()
        workCount.text = list.count { it.isWorkContact }.toString()
        importedCount.text = list.count { it.imported }.toString()
    }

    private fun applyFilters() {
        val query = searchInput.text?.toString()?.trim().orEmpty().lowercase()
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
                c.firstName.lowercase().contains(query) ||
                (c.lastName?.lowercase()?.contains(query) == true) ||
                c.phone.contains(query)

            matchesGroup && matchesQuery
        }

        adapter.submitList(filtered)
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

    private fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_import -> {
                showImportDialog()
                true
            }
            R.id.action_work -> {
                showWorkDialog()
                true
            }
            else -> false
        }
    }

    private fun showImportDialog() {
        val input = EditText(this).apply {
            hint = "Имя,Телефон,Email (каждая строка)"
            minLines = 4
        }

        AlertDialog.Builder(this)
            .setTitle("Импорт из текста")
            .setView(input)
            .setPositiveButton("Импортировать") { _, _ ->
                val lines = input.text.toString().split("\n")
                val now = System.currentTimeMillis()
                val contacts = lines.mapNotNull { line ->
                    val parts = line.split(",")
                    if (parts.size >= 2) {
                        Contact(
                            firstName = parts[0].trim(),
                            lastName = null,
                            phone = parts[1].trim(),
                            email = parts.getOrNull(2)?.trim(),
                            group = "other",
                            isWorkContact = false,
                            workTask = null,
                            address = null,
                            birthday = null,
                            imported = true,
                            createdAt = now,
                            updatedAt = now
                        )
                    } else {
                        null
                    }
                }

                if (contacts.isNotEmpty()) {
                    repo.bulkImport(contacts)
                    Snackbar.make(findViewById(R.id.recyclerView), "Импортировано: ${contacts.size}", Snackbar.LENGTH_SHORT).show()
                    loadContacts()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showWorkDialog() {
        val tasks = allContacts.filter { it.isWorkContact }.map { c ->
            val task = if (c.workTask.isNullOrBlank()) "Без задачи" else c.workTask
            "${c.firstName} ${c.lastName.orEmpty()} — $task"
        }

        AlertDialog.Builder(this)
            .setTitle("Рабочие задачи")
            .setMessage(if (tasks.isEmpty()) "Нет рабочих контактов" else tasks.joinToString("\n"))
            .setPositiveButton("Ок", null)
            .show()
    }
}
