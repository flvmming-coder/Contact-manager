package com.example.contactmanagerdemo.data

import android.content.Context
import com.example.contactmanagerdemo.R
import org.json.JSONArray
import org.json.JSONObject

class ContactPrefsStorage(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
                    put("isImported", contact.isImported)
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
                val phone = repairMojibake(obj.optString("phone")).trim()

                val directName = repairMojibake(obj.optString("name")).trim()
                val firstName = repairMojibake(obj.optString("firstName")).trim()
                val lastName = repairMojibake(obj.optString("lastName")).trim()
                val migratedName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
                val name = if (directName.isNotBlank()) directName else migratedName

                if (id <= 0L || name.isBlank() || phone.isBlank()) {
                    continue
                }

                val rawGroup = repairMojibake(obj.optString("group", GROUP_OTHER))
                result.add(
                    Contact(
                        id = id,
                        name = name,
                        phone = phone,
                        group = normalizeGroup(rawGroup),
                        isImported = obj.optBoolean("isImported", obj.optBoolean("imported", false)),
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
            Contact(now + 1, appContext.getString(R.string.seed_name_anna), "+7 900 000-00-01", GROUP_FAMILY),
            Contact(now + 2, appContext.getString(R.string.seed_name_ilya), "+7 900 000-00-02", GROUP_FRIENDS),
            Contact(now + 3, appContext.getString(R.string.seed_name_office), "+7 900 000-00-03", GROUP_WORK),
        )
    }

    private fun normalizeGroup(group: String): String {
        return when (group.trim().lowercase()) {
            GROUP_ALL,
            "\u0432\u0441\u0435",
            fixMojibakeToken("\u0432\u0441\u0435") -> GROUP_ALL

            GROUP_FAMILY,
            "\u0441\u0435\u043c\u044c\u044f",
            fixMojibakeToken("\u0441\u0435\u043c\u044c\u044f") -> GROUP_FAMILY

            GROUP_FRIENDS,
            "\u0434\u0440\u0443\u0437\u044c\u044f",
            fixMojibakeToken("\u0434\u0440\u0443\u0437\u044c\u044f") -> GROUP_FRIENDS

            GROUP_WORK,
            "\u0440\u0430\u0431\u043e\u0442\u0430",
            fixMojibakeToken("\u0440\u0430\u0431\u043e\u0442\u0430") -> GROUP_WORK

            GROUP_OTHER,
            "\u0434\u0440\u0443\u0433\u043e\u0435",
            fixMojibakeToken("\u0434\u0440\u0443\u0433\u043e\u0435") -> GROUP_OTHER

            else -> GROUP_OTHER
        }
    }

    private fun repairMojibake(value: String): String {
        if (value.isBlank()) return value
        val looksBroken = value.contains('\u00D0') || value.contains('\u00D1') || value.contains('\u00C3') || value.contains('\uFFFD')
        if (!looksBroken) return value

        return try {
            String(value.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        } catch (_: Exception) {
            value
        }
    }

    private fun fixMojibakeToken(cleanToken: String): String {
        return try {
            String(cleanToken.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
        } catch (_: Exception) {
            cleanToken
        }
    }

    companion object {
        private const val PREFS_NAME = "contact_manager_prefs"
        private const val KEY_CONTACTS = "contacts"

        const val GROUP_ALL = "all"
        const val GROUP_FAMILY = "family"
        const val GROUP_FRIENDS = "friends"
        const val GROUP_WORK = "work"
        const val GROUP_OTHER = "other"
    }
}
