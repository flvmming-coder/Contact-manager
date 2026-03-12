package com.example.contactmanagerdemo.data

import android.content.Context
import android.database.Cursor

class ContactRepository(context: Context) {
    private val dbHelper = ContactDbHelper(context)

    fun getAllContacts(): List<Contact> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ContactDbHelper.TABLE_CONTACTS,
            null,
            null,
            null,
            null,
            null,
            "${ContactDbHelper.COL_FIRST_NAME} COLLATE NOCASE ASC"
        )

        return cursor.use { c ->
            val list = mutableListOf<Contact>()
            while (c.moveToNext()) {
                list.add(fromCursor(c))
            }
            list
        }
    }

    fun getContactById(id: Long): Contact? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ContactDbHelper.TABLE_CONTACTS,
            null,
            "${ContactDbHelper.COL_ID} = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        return cursor.use { c ->
            if (c.moveToFirst()) fromCursor(c) else null
        }
    }

    fun insertContact(contact: Contact): Long {
        val db = dbHelper.writableDatabase
        return db.insert(ContactDbHelper.TABLE_CONTACTS, null, dbHelper.toContentValues(contact))
    }

    fun updateContact(contact: Contact): Int {
        val db = dbHelper.writableDatabase
        return db.update(
            ContactDbHelper.TABLE_CONTACTS,
            dbHelper.toContentValues(contact),
            "${ContactDbHelper.COL_ID} = ?",
            arrayOf(contact.id.toString())
        )
    }

    fun deleteContact(id: Long): Int {
        val db = dbHelper.writableDatabase
        return db.delete(
            ContactDbHelper.TABLE_CONTACTS,
            "${ContactDbHelper.COL_ID} = ?",
            arrayOf(id.toString())
        )
    }

    fun bulkImport(contacts: List<Contact>) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            contacts.forEach { contact ->
                db.insert(ContactDbHelper.TABLE_CONTACTS, null, dbHelper.toContentValues(contact))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun fromCursor(c: Cursor): Contact {
        return Contact(
            id = c.getLong(c.getColumnIndexOrThrow(ContactDbHelper.COL_ID)),
            firstName = c.getString(c.getColumnIndexOrThrow(ContactDbHelper.COL_FIRST_NAME)),
            lastName = c.getString(c.getColumnIndexOrThrow(ContactDbHelper.COL_LAST_NAME)),
            phone = c.getString(c.getColumnIndexOrThrow(ContactDbHelper.COL_PHONE)),
            email = c.getString(c.getColumnIndexOrThrow(ContactDbHelper.COL_EMAIL)),
            group = c.getString(c.getColumnIndexOrThrow(ContactDbHelper.COL_GROUP)),
            isWorkContact = c.getInt(c.getColumnIndexOrThrow(ContactDbHelper.COL_IS_WORK)) == 1,
            workTask = c.getString(c.getColumnIndexOrThrow(ContactDbHelper.COL_WORK_TASK)),
            address = c.getString(c.getColumnIndexOrThrow(ContactDbHelper.COL_ADDRESS)),
            birthday = c.getString(c.getColumnIndexOrThrow(ContactDbHelper.COL_BIRTHDAY)),
            imported = c.getInt(c.getColumnIndexOrThrow(ContactDbHelper.COL_IMPORTED)) == 1,
            createdAt = c.getLong(c.getColumnIndexOrThrow(ContactDbHelper.COL_CREATED_AT)),
            updatedAt = c.getLong(c.getColumnIndexOrThrow(ContactDbHelper.COL_UPDATED_AT))
        )
    }
}
