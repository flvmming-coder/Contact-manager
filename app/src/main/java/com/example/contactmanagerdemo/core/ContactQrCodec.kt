package com.example.contactmanagerdemo.core

import android.graphics.Bitmap
import android.util.Base64
import com.example.contactmanagerdemo.data.Contact
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

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
        val avatarPhotoBase64: String?,
    )

    fun toTransferContact(contact: Contact, avatarPhotoBase64: String? = null): TransferContact {
        return TransferContact(
            name = contact.name,
            lastName = contact.lastName,
            phone = contact.phone,
            email = contact.email,
            address = contact.address,
            birthday = contact.birthday,
            comment = contact.comment,
            avatarColor = contact.avatarColor,
            avatarPhotoBase64 = avatarPhotoBase64,
        )
    }

    fun encode(contact: Contact, avatarPhotoBase64: String? = null): String {
        return encode(
            name = contact.name,
            lastName = contact.lastName,
            phone = contact.phone,
            email = contact.email,
            address = contact.address,
            birthday = contact.birthday,
            comment = contact.comment,
            avatarColor = contact.avatarColor,
            avatarPhotoBase64 = avatarPhotoBase64,
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
        avatarPhotoBase64: String? = null,
    ): String {
        val payload = JSONObject().apply {
            put(KEY_TYPE_SHORT, TYPE_SINGLE)
            put(KEY_VERSION_SHORT, PAYLOAD_VERSION)
            put(KEY_NAME_SHORT, name.trim())
            put(KEY_LAST_NAME_SHORT, sanitize(lastName))
            put(KEY_PHONE_SHORT, phone.trim())
            put(KEY_EMAIL_SHORT, sanitize(email))
            put(KEY_ADDRESS_SHORT, sanitize(address))
            put(KEY_BIRTHDAY_SHORT, sanitize(birthday))
            put(KEY_COMMENT_SHORT, sanitize(comment)?.take(COMMENT_MAX_LENGTH))
            put(KEY_AVATAR_COLOR_SHORT, sanitize(avatarColor))
            put(KEY_AVATAR_PHOTO_SHORT, sanitize(avatarPhotoBase64))
        }
        return encodeCompressedPayload(payload)
    }

    fun encodeBulk(contacts: List<TransferContact>): String {
        val array = JSONArray()
        contacts.forEach { contact ->
            array.put(
                JSONObject().apply {
                    put(KEY_NAME_SHORT, contact.name)
                    put(KEY_LAST_NAME_SHORT, sanitize(contact.lastName))
                    put(KEY_PHONE_SHORT, contact.phone)
                    put(KEY_EMAIL_SHORT, sanitize(contact.email))
                    put(KEY_ADDRESS_SHORT, sanitize(contact.address))
                    put(KEY_BIRTHDAY_SHORT, sanitize(contact.birthday))
                    put(KEY_COMMENT_SHORT, sanitize(contact.comment)?.take(COMMENT_MAX_LENGTH))
                    put(KEY_AVATAR_COLOR_SHORT, sanitize(contact.avatarColor))
                    put(KEY_AVATAR_PHOTO_SHORT, sanitize(contact.avatarPhotoBase64))
                },
            )
        }
        val payload = JSONObject().apply {
            put(KEY_TYPE_SHORT, TYPE_BULK)
            put(KEY_VERSION_SHORT, PAYLOAD_VERSION)
            put(KEY_ITEMS_SHORT, array)
        }
        return encodeCompressedPayload(payload)
    }

    fun decode(raw: String): TransferContact? {
        return runCatching {
            val obj = decodePayloadObject(raw)
            val type = obj.optString(KEY_TYPE_SHORT).ifBlank { obj.optString(KEY_TYPE_LONG) }
            if (type != TYPE_SINGLE && type != TYPE_SINGLE_LEGACY) return null
            readTransferContact(obj)
        }.getOrNull()
    }

    fun decodeBulk(raw: String): List<TransferContact>? {
        return runCatching {
            val obj = decodePayloadObject(raw)
            val type = obj.optString(KEY_TYPE_SHORT).ifBlank { obj.optString(KEY_TYPE_LONG) }
            if (type != TYPE_BULK) return null
            val items = obj.optJSONArray(KEY_ITEMS_SHORT) ?: return null
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val parsed = readTransferContact(item) ?: continue
                    add(parsed)
                }
            }
        }.getOrNull()
    }

    fun generateBitmap(payload: String, sizePx: Int): Bitmap? {
        return runCatching {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
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

    private fun decodePayloadObject(raw: String): JSONObject {
        val normalized = raw.trim()
        if (normalized.startsWith(PAYLOAD_PREFIX)) {
            val encoded = normalized.removePrefix(PAYLOAD_PREFIX)
            val compressed = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP)
            val jsonBytes = ungzip(compressed)
            return JSONObject(String(jsonBytes, Charsets.UTF_8))
        }
        return JSONObject(repairMojibake(normalized))
    }

    private fun readTransferContact(obj: JSONObject): TransferContact? {
        val name = repairMojibake(obj.optString(KEY_NAME_SHORT).ifBlank { obj.optString(KEY_NAME_LONG) }).trim()
        val phone = repairMojibake(obj.optString(KEY_PHONE_SHORT).ifBlank { obj.optString(KEY_PHONE_LONG) }).trim()
        if (name.isBlank() || phone.isBlank()) return null

        return TransferContact(
            name = name,
            lastName = sanitize(repairMojibake(obj.optString(KEY_LAST_NAME_SHORT).ifBlank { obj.optString(KEY_LAST_NAME_LONG) })),
            phone = phone,
            email = sanitize(repairMojibake(obj.optString(KEY_EMAIL_SHORT).ifBlank { obj.optString(KEY_EMAIL_LONG) })),
            address = sanitize(repairMojibake(obj.optString(KEY_ADDRESS_SHORT).ifBlank { obj.optString(KEY_ADDRESS_LONG) })),
            birthday = sanitize(repairMojibake(obj.optString(KEY_BIRTHDAY_SHORT).ifBlank { obj.optString(KEY_BIRTHDAY_LONG) })),
            comment = sanitize(repairMojibake(obj.optString(KEY_COMMENT_SHORT).ifBlank { obj.optString(KEY_COMMENT_LONG) }))?.take(COMMENT_MAX_LENGTH),
            avatarColor = sanitize(repairMojibake(obj.optString(KEY_AVATAR_COLOR_SHORT).ifBlank { obj.optString(KEY_AVATAR_COLOR_LONG) })),
            avatarPhotoBase64 = sanitize(repairMojibake(obj.optString(KEY_AVATAR_PHOTO_SHORT))),
        )
    }

    private fun encodeCompressedPayload(obj: JSONObject): String {
        val compressed = gzip(obj.toString().toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(compressed, Base64.URL_SAFE or Base64.NO_WRAP)
        return PAYLOAD_PREFIX + encoded
    }

    private fun gzip(input: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            gzip.write(input)
        }
        return output.toByteArray()
    }

    private fun ungzip(input: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(input)).use { gzip ->
            val buffer = ByteArray(1024)
            while (true) {
                val read = gzip.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
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

    private const val PAYLOAD_VERSION = 3
    private const val COMMENT_MAX_LENGTH = 512
    private const val PAYLOAD_PREFIX = "CM3:"

    private const val TYPE_SINGLE = "c"
    private const val TYPE_BULK = "b"
    private const val TYPE_SINGLE_LEGACY = "contact_manager_contact"

    private const val KEY_TYPE_SHORT = "t"
    private const val KEY_VERSION_SHORT = "v"
    private const val KEY_ITEMS_SHORT = "i"
    private const val KEY_NAME_SHORT = "n"
    private const val KEY_LAST_NAME_SHORT = "l"
    private const val KEY_PHONE_SHORT = "p"
    private const val KEY_EMAIL_SHORT = "e"
    private const val KEY_ADDRESS_SHORT = "a"
    private const val KEY_BIRTHDAY_SHORT = "b"
    private const val KEY_COMMENT_SHORT = "c"
    private const val KEY_AVATAR_COLOR_SHORT = "ac"
    private const val KEY_AVATAR_PHOTO_SHORT = "ap"

    private const val KEY_TYPE_LONG = "type"
    private const val KEY_NAME_LONG = "name"
    private const val KEY_LAST_NAME_LONG = "lastName"
    private const val KEY_PHONE_LONG = "phone"
    private const val KEY_EMAIL_LONG = "email"
    private const val KEY_ADDRESS_LONG = "address"
    private const val KEY_BIRTHDAY_LONG = "birthday"
    private const val KEY_COMMENT_LONG = "comment"
    private const val KEY_AVATAR_COLOR_LONG = "avatarColor"
}
