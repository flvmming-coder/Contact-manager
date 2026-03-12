package com.example.contactmanagerdemo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ContactPrefsStorage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllContacts(): MutableList<Contact> {
        val raw = prefs.getString(KEY_CONTACTS, "[]") ?: "[]"
        val array = JSONArray(raw)
        val list = mutableListOf<Contact>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                Contact(
                    id = obj.optLong("id"),
                    firstName = obj.optString("firstName"),
                    lastName = obj.optString("lastName").ifBlank { null },
                    phone = obj.optString("phone"),
                    email = obj.optString("email").ifBlank { null },
                    group = obj.optString("group", "other"),
                    isWorkContact = obj.optBoolean("isWorkContact", false),
                    workTask = obj.optString("workTask").ifBlank { null },
                    address = obj.optString("address").ifBlank { null },
                    birthday = obj.optString("birthday").ifBlank { null },
                    imported = obj.optBoolean("imported", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                ),
            )
        }
        return list
    }

    fun getContactById(id: Long): Contact? {
        return getAllContacts().firstOrNull { it.id == id }
    }

    fun saveAllContacts(contacts: List<Contact>) {
        val array = JSONArray()
        contacts.forEach { contact ->
            val obj = JSONObject().apply {
                put("id", contact.id)
                put("firstName", contact.firstName)
                put("lastName", contact.lastName)
                put("phone", contact.phone)
                put("email", contact.email)
                put("group", contact.group)
                put("isWorkContact", contact.isWorkContact)
                put("workTask", contact.workTask)
                put("address", contact.address)
                put("birthday", contact.birthday)
                put("imported", contact.imported)
                put("createdAt", contact.createdAt)
                put("updatedAt", contact.updatedAt)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CONTACTS, array.toString()).apply()
    }

    fun upsert(contact: Contact) {
        val list = getAllContacts()
        val index = list.indexOfFirst { it.id == contact.id }
        if (index == -1) {
            list.add(contact)
        } else {
            list[index] = contact
        }
        saveAllContacts(list)
    }

    fun delete(id: Long) {
        val list = getAllContacts().filter { it.id != id }
        saveAllContacts(list)
    }

    companion object {
        private const val PREFS_NAME = "contact_manager_prefs"
        private const val KEY_CONTACTS = "contacts"
    }
}
