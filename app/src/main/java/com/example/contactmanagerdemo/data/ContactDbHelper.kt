package com.example.contactmanagerdemo.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ContactDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_CONTACTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FIRST_NAME TEXT NOT NULL,
                $COL_LAST_NAME TEXT,
                $COL_PHONE TEXT NOT NULL,
                $COL_EMAIL TEXT,
                $COL_GROUP TEXT NOT NULL,
                $COL_IS_WORK INTEGER NOT NULL DEFAULT 0,
                $COL_WORK_TASK TEXT,
                $COL_ADDRESS TEXT,
                $COL_BIRTHDAY TEXT,
                $COL_IMPORTED INTEGER NOT NULL DEFAULT 0,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Simple demo strategy: drop and recreate
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CONTACTS")
        onCreate(db)
    }

    fun toContentValues(contact: Contact): ContentValues {
        return ContentValues().apply {
            put(COL_FIRST_NAME, contact.firstName)
            put(COL_LAST_NAME, contact.lastName)
            put(COL_PHONE, contact.phone)
            put(COL_EMAIL, contact.email)
            put(COL_GROUP, contact.group)
            put(COL_IS_WORK, if (contact.isWorkContact) 1 else 0)
            put(COL_WORK_TASK, contact.workTask)
            put(COL_ADDRESS, contact.address)
            put(COL_BIRTHDAY, contact.birthday)
            put(COL_IMPORTED, if (contact.imported) 1 else 0)
            put(COL_CREATED_AT, contact.createdAt)
            put(COL_UPDATED_AT, contact.updatedAt)
        }
    }

    companion object {
        const val DB_NAME = "contacts_demo.db"
        const val DB_VERSION = 1

        const val TABLE_CONTACTS = "contacts"
        const val COL_ID = "id"
        const val COL_FIRST_NAME = "first_name"
        const val COL_LAST_NAME = "last_name"
        const val COL_PHONE = "phone"
        const val COL_EMAIL = "email"
        const val COL_GROUP = "group_name"
        const val COL_IS_WORK = "is_work"
        const val COL_WORK_TASK = "work_task"
        const val COL_ADDRESS = "address"
        const val COL_BIRTHDAY = "birthday"
        const val COL_IMPORTED = "imported"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
    }
}
