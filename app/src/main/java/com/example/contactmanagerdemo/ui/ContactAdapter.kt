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
    private val onDelete: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    private val items = mutableListOf<Contact>()

    fun submitList(list: List<Contact>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ContactViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar: TextView = view.findViewById(R.id.avatar)
        private val name: TextView = view.findViewById(R.id.name)
        private val phone: TextView = view.findViewById(R.id.phone)
        private val group: TextView = view.findViewById(R.id.group)
        private val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        private val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)

        fun bind(contact: Contact) {
            val secondInitial = contact.lastName?.firstOrNull()?.toString().orEmpty()
            val initials = "${contact.firstName.firstOrNull() ?: '?'}$secondInitial".uppercase()
            avatar.text = initials
            name.text = listOfNotNull(contact.firstName, contact.lastName).joinToString(" ")
            phone.text = contact.phone
            group.text = contact.group

            btnEdit.setOnClickListener { onEdit(contact) }
            btnDelete.setOnClickListener { onDelete(contact) }
        }
    }
}
