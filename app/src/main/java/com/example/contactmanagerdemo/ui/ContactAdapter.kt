package com.example.contactmanagerdemo.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.data.Contact

class ContactAdapter(
    private val onEdit: (Contact) -> Unit,
    private val onDelete: (Contact) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = mutableListOf<RowItem>()

    fun submitContacts(contacts: List<Contact>) {
        rows.clear()

        val grouped = contacts
            .sortedBy { it.firstName.lowercase() }
            .groupBy { it.firstName.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }

        grouped.toSortedMap().forEach { (letter, letterContacts) ->
            rows.add(RowItem.Section(letter))
            letterContacts.forEach { rows.add(RowItem.ContactItem(it)) }
        }

        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]) {
            is RowItem.Section -> TYPE_SECTION
            is RowItem.ContactItem -> TYPE_CONTACT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_SECTION) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_section, parent, false)
            SectionViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
            ContactViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is RowItem.Section -> (holder as SectionViewHolder).bind(row.letter)
            is RowItem.ContactItem -> (holder as ContactViewHolder).bind(row.contact)
        }
    }

    override fun getItemCount(): Int = rows.size

    inner class SectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.sectionTitle)
        fun bind(letter: String) {
            title.text = letter
        }
    }

    inner class ContactViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar: TextView = view.findViewById(R.id.avatar)
        private val workBadge: TextView = view.findViewById(R.id.workBadge)
        private val name: TextView = view.findViewById(R.id.name)
        private val phone: TextView = view.findViewById(R.id.phone)
        private val group: TextView = view.findViewById(R.id.group)
        private val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        private val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)

        fun bind(contact: Contact) {
            val initials = buildString {
                append(contact.firstName.firstOrNull()?.uppercaseChar() ?: '?')
                contact.lastName?.firstOrNull()?.let { append(it.uppercaseChar()) }
            }

            avatar.text = initials
            name.text = listOfNotNull(contact.firstName, contact.lastName).joinToString(" ")
            phone.text = contact.phone
            group.text = mapGroup(contact.group)
            workBadge.visibility = if (contact.group == "work") View.VISIBLE else View.GONE

            itemView.setOnClickListener { onEdit(contact) }
            btnEdit.setOnClickListener { onEdit(contact) }
            btnDelete.setOnClickListener { onDelete(contact) }
        }
    }

    private fun mapGroup(code: String): String {
        return when (code) {
            "family" -> "Семья"
            "friends" -> "Друзья"
            "work" -> "Работа"
            else -> "Другое"
        }
    }

    private sealed interface RowItem {
        data class Section(val letter: String) : RowItem
        data class ContactItem(val contact: Contact) : RowItem
    }

    private companion object {
        const val TYPE_SECTION = 0
        const val TYPE_CONTACT = 1
    }
}
