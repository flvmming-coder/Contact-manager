package com.example.contactmanagerdemo.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.data.Contact

class ContactAdapter(
    private val onEdit: (Contact) -> Unit,
    private val onDelete: (Contact) -> Unit,
    private val mapGroupLabel: (String) -> String,
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    private val contacts = mutableListOf<Contact>()

    fun submitList(items: List<Contact>) {
        contacts.clear()
        contacts.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(contacts[position])
    }

    override fun getItemCount(): Int = contacts.size

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textName: TextView = itemView.findViewById(R.id.textName)
        private val textInfo: TextView = itemView.findViewById(R.id.textInfo)
        private val btnEdit: Button = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: Button = itemView.findViewById(R.id.btnDelete)

        fun bind(contact: Contact) {
            textName.text = contact.name
            textInfo.text = "${contact.phone} | ${mapGroupLabel(contact.group)}"

            btnEdit.setOnClickListener { onEdit(contact) }
            btnDelete.setOnClickListener { onDelete(contact) }
        }
    }
}
