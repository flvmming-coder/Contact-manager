package com.example.contactmanagerdemo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ContactPrefsStorage(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAvailableGroups(): List<String> {
        return listOf(GROUP_FAMILY, GROUP_FRIENDS, GROUP_WORK, GROUP_OTHER)
    }

    fun getFilterGroups(): List<String> {
        return listOf(GROUP_ALL, GROUP_FAMILY, GROUP_FRIENDS, GROUP_WORK, GROUP_OTHER)
    }

    fun getAllContacts(): MutableList<Contact> {
        val raw = prefs.getString(KEY_CONTACTS, null)
        val list = if (raw.isNullOrBlank()) {
            mutableListOf()
        } else {
            parseContactsSafely(raw)
        }

        if (list.isEmpty()) {
            val seed = seedContacts()
            saveAllContacts(seed)
            return seed.toMutableList()
        }

        return list
    }

    fun saveAllContacts(contacts: List<Contact>) {
        val array = JSONArray()
        contacts.forEach { contact ->
            array.put(
                JSONObject().apply {
                    put("id", contact.id)
                    put("name", contact.name)
                    put("phone", contact.phone)
                    put("group", contact.group)
                },
            )
        }
        prefs.edit().putString(KEY_CONTACTS, array.toString()).apply()
    }

    fun upsert(contact: Contact) {
        val items = getAllContacts()
        val index = items.indexOfFirst { it.id == contact.id }
        if (index >= 0) {
            items[index] = contact
        } else {
            items.add(contact)
        }
        saveAllContacts(items)
    }

    fun delete(contactId: Long) {
        val items = getAllContacts().filterNot { it.id == contactId }
        saveAllContacts(items)
    }

    private fun parseContactsSafely(raw: String): MutableList<Contact> {
        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<Contact>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue

                val id = obj.optLong("id", 0L)
                val phone = obj.optString("phone").trim()

                val directName = obj.optString("name").trim()
                val firstName = obj.optString("firstName").trim()
                val lastName = obj.optString("lastName").trim()
                val migratedName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
                val name = if (directName.isNotBlank()) directName else migratedName

                if (id <= 0L || name.isBlank() || phone.isBlank()) {
                    continue
                }

                val rawGroup = obj.optString("group", GROUP_OTHER)
                result.add(
                    Contact(
                        id = id,
                        name = name,
                        phone = phone,
                        group = normalizeGroup(rawGroup),
                    ),
                )
            }
            result
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun seedContacts(): List<Contact> {
        val now = System.currentTimeMillis()
        return listOf(
            Contact(now + 1, "Анна Иванова", "+7 900 000-00-01", GROUP_FAMILY),
            Contact(now + 2, "Илья Петров", "+7 900 000-00-02", GROUP_FRIENDS),
            Contact(now + 3, "Офис Менеджер", "+7 900 000-00-03", GROUP_WORK),
        )
    }

    private fun normalizeGroup(group: String): String {
        return when (group.trim().lowercase()) {
            "family", "семья" -> GROUP_FAMILY
            "friends", "друзья" -> GROUP_FRIENDS
            "work", "работа" -> GROUP_WORK
            "other", "другое" -> GROUP_OTHER
            else -> GROUP_OTHER
        }
    }

    companion object {
        private const val PREFS_NAME = "contact_manager_prefs"
        private const val KEY_CONTACTS = "contacts"

        const val GROUP_ALL = "Все"
        const val GROUP_FAMILY = "Семья"
        const val GROUP_FRIENDS = "Друзья"
        const val GROUP_WORK = "Работа"
        const val GROUP_OTHER = "Другое"
    }
}
