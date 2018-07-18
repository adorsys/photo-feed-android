package de.adorsys.android.shared

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

object PostUtils {
    fun getEncodedBytesFromBitmap(bitmap: Bitmap): String? {
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    fun getBitmapFromEncodedBytes(encodedBytes: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(encodedBytes, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }
}