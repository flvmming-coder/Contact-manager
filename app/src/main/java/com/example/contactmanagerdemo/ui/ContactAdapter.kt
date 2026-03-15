package com.example.contactmanagerdemo.ui

import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.data.Contact
import java.util.Locale

class ContactAdapter(
    private val onEdit: (Contact) -> Unit,
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    private val contacts = mutableListOf<Contact>()
    private var lastTappedId: Long = -1L
    private var lastTapAtMs: Long = 0L

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
        private val root: View = itemView.findViewById(R.id.contactCardRoot)
        private val imageAvatar: ImageView = itemView.findViewById(R.id.imageAvatar)
        private val textAvatar: TextView = itemView.findViewById(R.id.textAvatar)
        private val textName: TextView = itemView.findViewById(R.id.textName)
        private val textPhone: TextView = itemView.findViewById(R.id.textPhone)

        fun bind(contact: Contact) {
            textName.text = listOfNotNull(contact.name, contact.lastName).joinToString(" ")
            textPhone.text = contact.phone

            bindAvatar(contact)

            root.setOnClickListener {
                val now = SystemClock.elapsedRealtime()
                if (lastTappedId == contact.id && now - lastTapAtMs <= DOUBLE_TAP_WINDOW_MS) {
                    lastTappedId = -1L
                    lastTapAtMs = 0L
                    onEdit(contact)
                } else {
                    lastTappedId = contact.id
                    lastTapAtMs = now
                }
            }
        }

        private fun bindAvatar(contact: Contact) {
            val avatarUri = contact.avatarPhotoUri?.trim().orEmpty()
            if (avatarUri.isNotEmpty()) {
                val shown = runCatching {
                    imageAvatar.setImageURI(Uri.parse(avatarUri))
                    true
                }.getOrElse { false }
                if (shown) {
                    imageAvatar.visibility = View.VISIBLE
                    textAvatar.visibility = View.GONE
                    return
                }
            }

            imageAvatar.visibility = View.GONE
            textAvatar.visibility = View.VISIBLE
            textAvatar.text = buildInitials(contact)
            textAvatar.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * itemView.resources.displayMetrics.density
                setColor(avatarColorFor(contact))
            }
        }

        private fun buildInitials(contact: Contact): String {
            val firstName = contact.name.trim()
            val lastName = contact.lastName?.trim().orEmpty()

            if (firstName.isBlank() && lastName.isBlank()) return "?"

            if (lastName.isNotBlank()) {
                val first = firstName.firstOrNull { !it.isWhitespace() }?.toString().orEmpty()
                val last = lastName.firstOrNull { !it.isWhitespace() }?.toString().orEmpty()
                return (first + last).ifBlank { "?" }.uppercase(Locale.getDefault())
            }

            val chars = firstName.filter { !it.isWhitespace() }.take(2)
            return if (chars.isBlank()) "?" else chars.uppercase(Locale.getDefault())
        }

        private fun avatarColorFor(contact: Contact): Int {
            return AvatarColorPalette.resolveColorInt(contact.avatarColor, contact.id)
        }
    }

    companion object {
        private const val DOUBLE_TAP_WINDOW_MS = 1_000L
    }
}
