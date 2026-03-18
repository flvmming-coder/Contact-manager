package com.example.contactmanagerdemo.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ContactDatabaseMirror(private val context: Context) {

    private val helper = MirrorDbHelper(context)
    private val tableName = helper.tableName

    fun replaceAllContacts(contacts: List<Contact>) {
        helper.writableDatabase.use { db ->
            db.beginTransaction()
            try {
                db.delete(tableName, null, null)
                contacts.forEach { contact ->
                    db.insert(tableName, null, contact.toValues())
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun getTableName(): String = tableName

    fun getDatabasePath(): String = context.getDatabasePath(DB_NAME).absolutePath

    fun getSnapshot(limit: Int = 120): String {
        val lines = mutableListOf<String>()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        lines.add("Database: ${getDatabasePath()}")
        lines.add("Table: $tableName")
        lines.add("Timestamp: $timestamp")

        helper.readableDatabase.use { db ->
            val total = db.compileStatement("SELECT COUNT(*) FROM $tableName").simpleQueryForLong()
            lines.add("Rows: $total")
            val cursor = db.rawQuery(
                "SELECT id,name,last_name,phone,group_code,is_favorite,is_imported,updated_at FROM $tableName ORDER BY updated_at DESC LIMIT ?",
                arrayOf(limit.toString()),
            )
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val name = it.getString(1).orEmpty()
                    val lastName = it.getString(2).orEmpty()
                    val phone = it.getString(3).orEmpty()
                    val group = it.getString(4).orEmpty()
                    val favorite = it.getInt(5) == 1
                    val imported = it.getInt(6) == 1
                    val updated = it.getLong(7)
                    lines.add("id=$id | $name $lastName | $phone | group=$group | fav=$favorite | imp=$imported | ts=$updated")
                }
            }
        }
        return lines.joinToString("\n")
    }

    fun clearDatabase() {
        helper.writableDatabase.use { db ->
            db.delete(tableName, null, null)
        }
    }

    private fun Contact.toValues(): ContentValues {
        return ContentValues().apply {
            put("id", id)
            put("name", name)
            put("last_name", lastName)
            put("phone", phone)
            put("email", email)
            put("address", address)
            put("birthday", birthday)
            put("comment", comment)
            put("avatar_color", avatarColor)
            put("avatar_photo_uri", avatarPhotoUri)
            put("group_code", group)
            put("is_favorite", if (isFavorite) 1 else 0)
            put("is_imported", if (isImported) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
    }

    private class MirrorDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        val tableName: String = buildTableName(context)

        override fun onCreate(db: SQLiteDatabase) {
            createTable(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $tableName")
            createTable(db)
        }

        private fun createTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $tableName (
                    id INTEGER PRIMARY KEY,
                    name TEXT NOT NULL,
                    last_name TEXT,
                    phone TEXT NOT NULL,
                    email TEXT,
                    address TEXT,
                    birthday TEXT,
                    comment TEXT,
                    avatar_color TEXT,
                    avatar_photo_uri TEXT,
                    group_code TEXT NOT NULL,
                    is_favorite INTEGER NOT NULL DEFAULT 0,
                    is_imported INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }

        companion object {
            @SuppressLint("HardwareIds", "MissingPermission")
            private fun buildTableName(context: Context): String {
                var rawImei: String? = null
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    rawImei = runCatching {
                        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            telephony?.imei
                        } else {
                            @Suppress("DEPRECATION")
                            telephony?.deviceId
                        }
                    }.getOrNull()
                }
                val fallbackId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                val source = rawImei?.takeIf { it.isNotBlank() } ?: fallbackId ?: "unknown"
                val clean = source.replace("[^A-Za-z0-9]".toRegex(), "").ifBlank { "unknown" }.take(24)
                return "DataBase_$clean"
            }
        }
    }

    companion object {
        private const val DB_NAME = "contact_manager_mirror.db"
        private const val DB_VERSION = 1
    }
}
