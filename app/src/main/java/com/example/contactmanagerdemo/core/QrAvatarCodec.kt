package com.example.contactmanagerdemo.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

object QrAvatarCodec {

    fun encodeAvatarFromUri(context: Context, avatarUri: String?): String? {
        val uri = avatarUri?.trim()?.ifBlank { null } ?: return null
        val bitmap = runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull() ?: return null

        val candidates = listOf(
            64 to 45,
            48 to 35,
            40 to 28,
            32 to 22,
        )

        candidates.forEach { (size, quality) ->
            val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
            val bytes = runCatching {
                java.io.ByteArrayOutputStream().use { output ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
                    output.toByteArray()
                }
            }.getOrNull() ?: return@forEach
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (encoded.length <= MAX_QR_AVATAR_BASE64_LENGTH) {
                return encoded
            }
        }
        return null
    }

    fun decodeAvatarToLocalUri(context: Context, avatarBase64: String?): String? {
        val encoded = avatarBase64?.trim()?.ifBlank { null } ?: return null
        val decoded = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() ?: return null
        val file = File(context.cacheDir, "qr_avatar_${System.currentTimeMillis()}.jpg")
        val written = runCatching {
            FileOutputStream(file).use { output ->
                output.write(decoded)
                output.flush()
            }
            true
        }.getOrDefault(false)
        if (!written) return null
        return Uri.fromFile(file).toString()
    }

    private const val MAX_QR_AVATAR_BASE64_LENGTH = 900
}
