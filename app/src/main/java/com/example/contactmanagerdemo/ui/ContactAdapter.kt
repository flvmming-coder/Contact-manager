package com.example.contactmanagerdemo.ui

import android.graphics.drawable.GradientDrawable
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.data.Contact
import java.util.Locale

class ContactAdapter(
    private val onEdit: (Contact) -> Unit,
    private val onSelectStarted: (Contact) -> Unit,
    private val onSelectionToggle: (Contact) -> Unit,
    private val onFavoriteToggle: (Contact) -> Unit,
    private val isSelectionMode: () -> Boolean,
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    private val contacts = mutableListOf<Contact>()
    private var lastTappedId: Long = -1L
    private var lastTapAtMs: Long = 0L
    private val selectedIds = mutableSetOf<Long>()

    fun submitList(items: List<Contact>) {
        contacts.clear()
        contacts.addAll(items)
        notifyDataSetChanged()
    }

    fun setSelectedIds(ids: Set<Long>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(contacts[position], position)
    }

    override fun getItemCount(): Int = contacts.size

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root: View = itemView.findViewById(R.id.contactCardRoot)
        private val textSectionLetter: TextView = itemView.findViewById(R.id.textSectionLetter)
        private val imageAvatar: ImageView = itemView.findViewById(R.id.imageAvatar)
        private val textAvatar: TextView = itemView.findViewById(R.id.textAvatar)
        private val textName: TextView = itemView.findViewById(R.id.textName)
        private val textPhone: TextView = itemView.findViewById(R.id.textPhone)
        private val textFavorite: TextView = itemView.findViewById(R.id.textFavorite)

        fun bind(contact: Contact, position: Int) {
            textName.text = listOfNotNull(contact.name, contact.lastName).joinToString(" ")
            textPhone.text = contact.phone
            bindSectionHeader(contact, position)

            bindAvatar(contact)
            bindFavoriteMarker(contact)
            applySelectionStyle(selectedIds.contains(contact.id))

            root.setOnLongClickListener {
                false
            }

            root.setOnClickListener {
                if (longPressTriggered) {
                    longPressTriggered = false
                    return@setOnClickListener
                }
                if (isSelectionMode()) {
                    onSelectionToggle(contact)
                    return@setOnClickListener
                }

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

            root.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        pendingLongPress?.let { view.removeCallbacks(it) }
                        longPressTriggered = false
                        if (!isSelectionMode()) {
                            val runnable = Runnable {
                                longPressTriggered = true
                                onSelectStarted(contact)
                            }
                            pendingLongPress = runnable
                            view.postDelayed(runnable, HOLD_TO_SELECT_MS)
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = kotlin.math.abs(event.x - downX)
                        val dy = kotlin.math.abs(event.y - downY)
                        if (dx > touchSlop || dy > touchSlop) {
                            pendingLongPress?.let { view.removeCallbacks(it) }
                            pendingLongPress = null
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        pendingLongPress?.let { view.removeCallbacks(it) }
                        pendingLongPress = null
                    }
                }
                false
            }
        }

        private fun bindFavoriteMarker(contact: Contact) {
            val active = contact.isFavorite
            val markerColor = when {
                active -> 0xFFBE123C.toInt()
                isDarkTheme() -> 0xFFA3A5BD.toInt()
                else -> 0xFF334155.toInt()
            }
            val density = itemView.resources.displayMetrics.density
            textFavorite.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 7f * density
                setColor(Color.TRANSPARENT)
                setStroke((2f * density).toInt(), markerColor)
            }
            textFavorite.setTextColor(markerColor)
            textFavorite.setOnClickListener {
                onFavoriteToggle(contact)
            }
        }

        private var pendingLongPress: Runnable? = null
        private var longPressTriggered: Boolean = false
        private var downX: Float = 0f
        private var downY: Float = 0f
        private val touchSlop: Int by lazy { ViewConfiguration.get(itemView.context).scaledTouchSlop }

        private fun applySelectionStyle(selected: Boolean) {
            val density = itemView.resources.displayMetrics.density
            val corner = 14f * density
            val background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = corner
                if (selected) {
                    setColor(0x4C648B67)
                    setStroke((2f * density).toInt(), 0xFF15803D.toInt())
                } else {
                    setColor(ContextCompat.getColor(itemView.context, R.color.surface))
                    setStroke((1f * density).toInt(), ContextCompat.getColor(itemView.context, R.color.card_stroke))
                }
            }
            root.background = background
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

        private fun bindSectionHeader(contact: Contact, position: Int) {
            val current = sectionLetter(contact)
            val previous = contacts.getOrNull(position - 1)?.let(::sectionLetter)
            val show = position == 0 || current != previous
            textSectionLetter.visibility = if (show) View.VISIBLE else View.GONE
            textSectionLetter.text = current
        }

        private fun sectionLetter(contact: Contact): String {
            val full = listOfNotNull(contact.name, contact.lastName).joinToString(" ").trim()
            val ch = full.firstOrNull { !it.isWhitespace() } ?: '#'
            return if (ch.isLetter()) ch.uppercaseChar().toString() else "#"
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

        private fun isDarkTheme(): Boolean {
            val mode = itemView.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return mode == Configuration.UI_MODE_NIGHT_YES
        }
    }

    companion object {
        private const val DOUBLE_TAP_WINDOW_MS = 1_000L
        private const val HOLD_TO_SELECT_MS = 1_000L
    }
}
