package com.example.contactmanagerdemo.data

data class Contact(
    val id: Long = 0,
    val firstName: String,
    val lastName: String?,
    val phone: String,
    val email: String?,
    val group: String,
    val isWorkContact: Boolean,
    val workTask: String?,
    val address: String?,
    val birthday: String?,
    val imported: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
