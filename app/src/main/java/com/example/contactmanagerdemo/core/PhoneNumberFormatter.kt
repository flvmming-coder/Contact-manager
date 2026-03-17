package com.example.contactmanagerdemo.core

object PhoneNumberFormatter {

    fun isServiceNumber(raw: String): Boolean {
        val value = raw.trim()
        if (value.isBlank()) return false
        if (value.contains('*') || value.contains('#')) return true
        if (value.any { it.isLetter() }) return true

        val digits = value.filter { it.isDigit() }
        if (digits.isBlank()) return true
        val startsWithPlus = value.startsWith("+")
        return !startsWithPlus && digits.length <= 5
    }

    fun isForeignInternational(raw: String): Boolean {
        val value = raw.trim()
        if (!value.startsWith("+")) return false
        val digits = value.filter { it.isDigit() }
        if (digits.isBlank()) return false
        return !digits.startsWith("7")
    }

    fun normalizeImportedPhone(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return value
        if (isServiceNumber(value)) return value
        if (isForeignInternational(value)) return value
        if (!canFormatAsRuKz(value)) return value
        return formatRuKzMask(value)
    }

    fun normalizeForStorage(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return value
        if (isServiceNumber(value)) return value
        if (isForeignInternational(value)) return value
        if (!canFormatAsRuKz(value)) return value
        return formatRuKzMask(value)
    }

    fun formatRuKzMask(raw: String): String {
        val input = raw.trim()
        if (input.isBlank()) return ""

        val digitsOnly = input.filter { it.isDigit() }.toMutableList()
        if (digitsOnly.isEmpty()) return ""

        if (digitsOnly[0] == '8') {
            digitsOnly[0] = '7'
        }
        if (digitsOnly.size == 10) {
            digitsOnly.add(0, '7')
        }
        if (digitsOnly[0] != '7') {
            digitsOnly.add(0, '7')
        }
        val digits = digitsOnly.take(11).joinToString("")
        if (digits.length == 1) return "+7"

        val body = digits.substring(1)
        val builder = StringBuilder("+7")
        if (body.isNotEmpty()) {
            builder.append(" (")
            builder.append(body.take(3))
            if (body.length >= 3) {
                builder.append(")")
            }
        }
        if (body.length > 3) {
            builder.append(" ")
            builder.append(body.substring(3, minOf(6, body.length)))
        }
        if (body.length > 6) {
            builder.append("-")
            builder.append(body.substring(6, minOf(8, body.length)))
        }
        if (body.length > 8) {
            builder.append("-")
            builder.append(body.substring(8, minOf(10, body.length)))
        }
        return builder.toString()
    }

    fun normalizedPhoneKey(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return ""
        return if (isServiceNumber(value)) {
            value.lowercase()
                .replace("\\s+".toRegex(), "")
        } else {
            value.filter { it.isDigit() || it == '+' }
        }
    }

    private fun canFormatAsRuKz(raw: String): Boolean {
        val digits = raw.filter { it.isDigit() }
        if (digits.isBlank()) return false
        if (raw.startsWith("+7")) return true
        return when (digits.length) {
            in 1..10 -> true
            11 -> digits.startsWith("7") || digits.startsWith("8")
            else -> false
        }
    }
}
