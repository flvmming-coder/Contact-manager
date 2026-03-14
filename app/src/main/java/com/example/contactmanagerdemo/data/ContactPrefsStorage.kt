package com.example.contactmanagerdemo.data

import android.content.Context
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.core.AppEventLogger
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
            if (!raw.isNullOrBlank()) {
                AppEventLogger.warn("DATA", "Stored contacts were empty/invalid, fallback to seed contacts")
            }
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
                    put("lastName", contact.lastName)
                    put("phone", contact.phone)
                    put("email", contact.email)
                    put("address", contact.address)
                    put("birthday", contact.birthday)
                    put("comment", contact.comment)
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
                val directLastName = repairMojibake(obj.optString("lastName")).trim()
                val fromLegacyName = if (directName.isNotBlank()) {
                    val parts = directName.split(" ").filter { it.isNotBlank() }
                    parts.firstOrNull().orEmpty() to parts.drop(1).joinToString(" ").ifBlank { null }
                } else {
                    "" to null
                }
                val migratedName = if (firstName.isNotBlank()) firstName else fromLegacyName.first
                val lastName = directLastName.ifBlank { fromLegacyName.second }

                if (id <= 0L || migratedName.isBlank() || phone.isBlank()) {
                    continue
                }

                val rawGroup = repairMojibake(obj.optString("group", GROUP_OTHER))
                result.add(
                    Contact(
                        id = id,
                        name = migratedName,
                        lastName = lastName,
                        phone = phone,
                        email = repairMojibake(obj.optString("email")).trim().ifBlank { null },
                        address = repairMojibake(obj.optString("address")).trim().ifBlank { null },
                        birthday = repairMojibake(obj.optString("birthday")).trim().ifBlank { null },
                        comment = repairMojibake(obj.optString("comment")).trim().ifBlank { null },
                        group = normalizeGroup(rawGroup),
                        isImported = obj.optBoolean("isImported", obj.optBoolean("imported", false)),
                    ),
                )
            }
            result
        } catch (e: Exception) {
            AppEventLogger.error("DATA", "Failed to parse contacts JSON", e)
            mutableListOf()
        }
    }

    private fun seedContacts(): List<Contact> {
        val now = System.currentTimeMillis()
        return listOf(
            Contact(
                id = now + 1,
                name = appContext.getString(R.string.seed_name_anna),
                lastName = appContext.getString(R.string.seed_last_name_anna),
                phone = "+7 900 000-00-01",
                group = GROUP_FAMILY,
            ),
            Contact(
                id = now + 2,
                name = appContext.getString(R.string.seed_name_ilya),
                lastName = appContext.getString(R.string.seed_last_name_ilya),
                phone = "+7 900 000-00-02",
                group = GROUP_FRIENDS,
            ),
            Contact(
                id = now + 3,
                name = appContext.getString(R.string.seed_name_office),
                phone = "+7 900 000-00-03",
                group = GROUP_WORK,
            ),
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
