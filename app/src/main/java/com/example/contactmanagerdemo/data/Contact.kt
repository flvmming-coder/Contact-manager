package com.example.contactmanagerdemo.data

data class Contact(
    val id: Long,
    val name: String,
    val lastName: String? = null,
    val phone: String,
    val email: String? = null,
    val address: String? = null,
    val birthday: String? = null,
    val group: String,
    val isImported: Boolean = false,
)
