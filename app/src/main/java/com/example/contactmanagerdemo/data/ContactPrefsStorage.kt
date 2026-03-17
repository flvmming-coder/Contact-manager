package com.example.contactmanagerdemo.data

import android.content.Context
import com.example.contactmanagerdemo.R
import com.example.contactmanagerdemo.core.AppEventLogger
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class ContactPrefsStorage(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAvailableGroups(): List<String> {
        return getStoredGroups().map { it.code }
    }

    fun getEditableGroups(): List<String> {
        return listOf(GROUP_UNASSIGNED) + getAvailableGroups()
    }

    fun getFilterGroups(): List<String> {
        return listOf(GROUP_ALL) + getAvailableGroups()
    }

    fun getGroupTitle(code: String): String {
        return when (code) {
            GROUP_ALL -> appContext.getString(R.string.group_all)
            GROUP_UNASSIGNED -> appContext.getString(R.string.group_unassigned)
            else -> getStoredGroups().firstOrNull { it.code == code }?.title
                ?: appContext.getString(R.string.group_unassigned)
        }
    }

    fun getGroupsForManage(): List<Pair<String, String>> {
        return getStoredGroups().map { it.code to it.title }
    }

    fun ensureGroupByTitle(rawTitle: String): String {
        val title = rawTitle.trim()
        if (title.isBlank()) return GROUP_UNASSIGNED

        val groups = getStoredGroups().toMutableList()
        val existing = groups.firstOrNull { normalizeTitle(it.title) == normalizeTitle(title) }
        if (existing != null) return existing.code

        val code = generateUniqueCode(title, groups.mapTo(mutableSetOf()) { it.code })
        groups.add(GroupDef(code = code, title = title))
        saveStoredGroups(groups)
        return code
    }

    fun createGroup(rawTitle: String): String? {
        val title = rawTitle.trim()
        if (title.isBlank()) return null
        return ensureGroupByTitle(title)
    }

    fun renameGroup(code: String, rawTitle: String): Boolean {
        val title = rawTitle.trim()
        if (title.isBlank()) return false
        if (code == GROUP_ALL || code == GROUP_UNASSIGNED) return false

        val groups = getStoredGroups().toMutableList()
        val index = groups.indexOfFirst { it.code == code }
        if (index < 0) return false

        val duplicate = groups.any { it.code != code && normalizeTitle(it.title) == normalizeTitle(title) }
        if (duplicate) return false

        groups[index] = groups[index].copy(title = title)
        saveStoredGroups(groups)
        return true
    }

    fun deleteGroup(code: String): Int {
        if (code == GROUP_ALL || code == GROUP_UNASSIGNED) return 0

        val groups = getStoredGroups().toMutableList()
        val removed = groups.removeAll { it.code == code }
        if (!removed) return 0

        saveStoredGroups(groups)

        val contacts = getAllContacts()
        var reassigned = 0
        val updated = contacts.map { contact ->
            if (contact.group == code) {
                reassigned += 1
                contact.copy(group = GROUP_UNASSIGNED)
            } else {
                contact
            }
        }
        saveAllContacts(updated)
        return reassigned
    }

    fun getAllContacts(): MutableList<Contact> {
        ensureGroupsInitialized()
        val raw = prefs.getString(KEY_CONTACTS, null)
        if (raw == null) {
            val seed = seedContacts()
            saveAllContacts(seed)
            return seed.toMutableList()
        }

        if (raw.isBlank()) return mutableListOf()

        val list = parseContactsSafely(raw)
        if (list.isEmpty() && raw.trim() != "[]") {
            AppEventLogger.warn("DATA", "Stored contacts were empty/invalid, fallback to seed contacts")
            val seed = seedContacts()
            saveAllContacts(seed)
            return seed.toMutableList()
        }

        return list
    }

    fun saveAllContacts(contacts: List<Contact>) {
        ensureGroupsInitialized()
        val validCodes = getAvailableGroups().toSet()
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
                    put("avatarColor", contact.avatarColor)
                    put("avatarPhotoUri", contact.avatarPhotoUri)
                    put("group", sanitizeGroup(contact.group, validCodes))
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

    fun clearAll() {
        prefs.edit().putString(KEY_CONTACTS, "[]").apply()
    }

    private fun parseContactsSafely(raw: String): MutableList<Contact> {
        return try {
            val validCodes = getAvailableGroups().toSet()
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

                val rawGroup = repairMojibake(obj.optString("group", GROUP_UNASSIGNED))
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
                        avatarColor = repairMojibake(obj.optString("avatarColor")).trim().ifBlank { null },
                        avatarPhotoUri = repairMojibake(obj.optString("avatarPhotoUri")).trim().ifBlank { null },
                        group = sanitizeGroup(normalizeLegacyGroup(rawGroup), validCodes),
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

    private fun ensureGroupsInitialized() {
        if (prefs.contains(KEY_GROUPS)) return
        saveStoredGroups(defaultGroups())
    }

    private fun getStoredGroups(): List<GroupDef> {
        ensureGroupsInitialized()
        val raw = prefs.getString(KEY_GROUPS, null).orEmpty()
        if (raw.isBlank()) {
            val defaults = defaultGroups()
            saveStoredGroups(defaults)
            return defaults
        }
        return runCatching {
            val parsed = mutableListOf<GroupDef>()
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val code = obj.optString("code").trim()
                val title = obj.optString("title").trim()
                if (code.isBlank() || title.isBlank()) continue
                parsed.add(GroupDef(code = code, title = title))
            }
            if (parsed.isEmpty()) defaultGroups() else parsed
        }.getOrElse {
            defaultGroups()
        }.also { groups ->
            if (groups.isNotEmpty()) saveStoredGroups(groups)
        }
    }

    private fun saveStoredGroups(groups: List<GroupDef>) {
        val unique = linkedMapOf<String, GroupDef>()
        groups.forEach { group ->
            if (group.code.isNotBlank() && group.title.isNotBlank()) {
                unique[group.code] = group
            }
        }
        val array = JSONArray()
        unique.values.forEach { group ->
            array.put(
                JSONObject().apply {
                    put("code", group.code)
                    put("title", group.title)
                },
            )
        }
        prefs.edit().putString(KEY_GROUPS, array.toString()).apply()
    }

    private fun defaultGroups(): MutableList<GroupDef> {
        return mutableListOf(
            GroupDef(GROUP_FAMILY, appContext.getString(R.string.group_family)),
            GroupDef(GROUP_FRIENDS, appContext.getString(R.string.group_friends)),
            GroupDef(GROUP_WORK, appContext.getString(R.string.group_work)),
            GroupDef(GROUP_OTHER, appContext.getString(R.string.group_other)),
        )
    }

    private fun normalizeLegacyGroup(group: String): String {
        return when (group.trim().lowercase(Locale.getDefault())) {
            GROUP_ALL,
            "все",
            fixMojibakeToken("все") -> GROUP_UNASSIGNED

            GROUP_FAMILY,
            "семья",
            fixMojibakeToken("семья") -> GROUP_FAMILY

            GROUP_FRIENDS,
            "друзья",
            fixMojibakeToken("друзья") -> GROUP_FRIENDS

            GROUP_WORK,
            "работа",
            fixMojibakeToken("работа") -> GROUP_WORK

            GROUP_OTHER,
            "другое",
            fixMojibakeToken("другое") -> GROUP_OTHER

            else -> group.trim()
        }
    }

    private fun sanitizeGroup(group: String, validCodes: Set<String>): String {
        val normalized = group.trim()
        if (normalized.isBlank() || normalized == GROUP_ALL) return GROUP_UNASSIGNED
        if (normalized == GROUP_UNASSIGNED) return GROUP_UNASSIGNED
        return if (validCodes.contains(normalized)) normalized else GROUP_UNASSIGNED
    }

    private fun generateUniqueCode(title: String, usedCodes: MutableSet<String>): String {
        val normalized = title.lowercase(Locale.getDefault())
            .replace("[^a-zа-я0-9]+".toRegex(), "_")
            .trim('_')
        var base = if (normalized.isBlank()) "group" else "group_$normalized"
        base = base.take(36)
        if (!usedCodes.contains(base)) return base
        var index = 2
        while (usedCodes.contains("${base}_$index")) {
            index += 1
        }
        return "${base}_$index"
    }

    private fun normalizeTitle(value: String): String {
        return value.trim().lowercase(Locale.getDefault())
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

    private data class GroupDef(
        val code: String,
        val title: String,
    )

    companion object {
        private const val PREFS_NAME = "contact_manager_prefs"
        private const val KEY_CONTACTS = "contacts"
        private const val KEY_GROUPS = "groups"

        const val GROUP_ALL = "all"
        const val GROUP_UNASSIGNED = "ungrouped"
        const val GROUP_FAMILY = "family"
        const val GROUP_FRIENDS = "friends"
        const val GROUP_WORK = "work"
        const val GROUP_OTHER = "other"
    }
}
