package com.example.contactmanagerdemo.core

import android.graphics.Bitmap
import com.example.contactmanagerdemo.data.Contact
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import org.json.JSONObject

object ContactQrCodec {

    data class TransferContact(
        val name: String,
        val lastName: String?,
        val phone: String,
        val email: String?,
        val address: String?,
        val birthday: String?,
        val comment: String?,
        val avatarColor: String?,
    )

    fun encode(contact: Contact): String {
        return encode(
            name = contact.name,
            lastName = contact.lastName,
            phone = contact.phone,
            email = contact.email,
            address = contact.address,
            birthday = contact.birthday,
            comment = contact.comment,
            avatarColor = contact.avatarColor,
        )
    }

    fun encode(
        name: String,
        lastName: String?,
        phone: String,
        email: String?,
        address: String?,
        birthday: String?,
        comment: String?,
        avatarColor: String?,
    ): String {
        return JSONObject().apply {
            put(KEY_TYPE, PAYLOAD_TYPE)
            put(KEY_VERSION, PAYLOAD_VERSION)
            put(KEY_NAME, name.trim())
            put(KEY_LAST_NAME, sanitize(lastName))
            put(KEY_PHONE, phone.trim())
            put(KEY_EMAIL, sanitize(email))
            put(KEY_ADDRESS, sanitize(address))
            put(KEY_BIRTHDAY, sanitize(birthday))
            put(KEY_COMMENT, sanitize(comment)?.take(COMMENT_MAX_LENGTH))
            put(KEY_AVATAR_COLOR, sanitize(avatarColor))
        }.toString()
    }

    fun decode(raw: String): TransferContact? {
        return runCatching {
            val obj = JSONObject(repairMojibake(raw))
            if (obj.optString(KEY_TYPE) != PAYLOAD_TYPE) return null
            val name = repairMojibake(obj.optString(KEY_NAME)).trim()
            val phone = repairMojibake(obj.optString(KEY_PHONE)).trim()
            if (name.isBlank() || phone.isBlank()) return null

            TransferContact(
                name = name,
                lastName = sanitize(repairMojibake(obj.optString(KEY_LAST_NAME))),
                phone = phone,
                email = sanitize(repairMojibake(obj.optString(KEY_EMAIL))),
                address = sanitize(repairMojibake(obj.optString(KEY_ADDRESS))),
                birthday = sanitize(repairMojibake(obj.optString(KEY_BIRTHDAY))),
                comment = sanitize(repairMojibake(obj.optString(KEY_COMMENT)))?.take(COMMENT_MAX_LENGTH),
                avatarColor = sanitize(repairMojibake(obj.optString(KEY_AVATAR_COLOR))),
            )
        }.getOrNull()
    }

    fun generateBitmap(payload: String, sizePx: Int): Bitmap? {
        return runCatching {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1,
            )
            val matrix = MultiFormatWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints,
            )
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
                for (x in 0 until sizePx) {
                    for (y in 0 until sizePx) {
                        bitmap.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                    }
                }
            }
        }.getOrNull()
    }

    private fun sanitize(raw: String?): String? {
        return raw?.trim()?.ifBlank { null }
    }

    private fun repairMojibake(rawValue: String): String {
        if (rawValue.isBlank()) return rawValue
        val looksBroken = rawValue.contains('\u00D0') ||
            rawValue.contains('\u00D1') ||
            rawValue.contains('\u00C3') ||
            rawValue.contains('\uFFFD')
        if (!looksBroken) return rawValue
        return runCatching {
            String(rawValue.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        }.getOrDefault(rawValue)
    }

    private const val PAYLOAD_TYPE = "contact_manager_contact"
    private const val PAYLOAD_VERSION = 1
    private const val KEY_TYPE = "type"
    private const val KEY_VERSION = "version"
    private const val KEY_NAME = "name"
    private const val KEY_LAST_NAME = "lastName"
    private const val KEY_PHONE = "phone"
    private const val KEY_EMAIL = "email"
    private const val KEY_ADDRESS = "address"
    private const val KEY_BIRTHDAY = "birthday"
    private const val KEY_COMMENT = "comment"
    private const val KEY_AVATAR_COLOR = "avatarColor"
    private const val COMMENT_MAX_LENGTH = 512
}
