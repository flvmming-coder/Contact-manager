package com.example.contactmanagerdemo.data

data class DeletedContactEntry(
    val contact: Contact,
    val deletedAtMs: Long,
    val retentionDays: Int,
) {
    val expiresAtMs: Long
        get() = deletedAtMs + retentionDays * DAY_MS

    fun remainingDays(nowMs: Long): Int {
        val leftMs = expiresAtMs - nowMs
        if (leftMs <= 0L) return 0
        return ((leftMs + DAY_MS - 1) / DAY_MS).toInt()
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
