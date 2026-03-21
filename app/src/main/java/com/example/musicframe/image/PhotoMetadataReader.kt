package com.example.musicframe.image

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoMetadataReader(private val context: Context) {

    fun read(uri: Uri): PhotoMetadata {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            parse(stream)
        } ?: PhotoMetadata(null, null, null, null, false, null)
    }

    private fun parse(stream: InputStream): PhotoMetadata {
        val exif = ExifInterface(stream)

        val date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

        val latitude = exif.latLong?.firstOrNull()
        val longitude = exif.latLong?.getOrNull(1)

        val model = listOfNotNull(
            exif.getAttribute(ExifInterface.TAG_MAKE),
            exif.getAttribute(ExifInterface.TAG_MODEL)
        ).joinToString(" ").ifBlank { null }

        // ---- Motion Photo detection (avoid missing TAG_* constants) ----
        val isMotionPhoto = MOTION_PHOTO_FLAG_TAGS.any { tag ->
            exif.getAttributeIntCompat(tag, 0) == 1
        }

        val motionOffset = MOTION_PHOTO_OFFSET_TAGS
            .firstNotNullOfOrNull { tag ->
                exif.getAttributeLongCompat(tag, 0L).takeIf { it > 0L }
            }

        val formattedDate = date?.let { raw ->
            runCatching {
                val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                val result = parser.parse(raw) ?: Date()
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(result)
            }.getOrNull() ?: raw
        }

        return PhotoMetadata(
            createdDateTime = formattedDate,
            latitude = latitude,
            longitude = longitude,
            deviceModel = model,
            isMotionPhoto = isMotionPhoto,
            motionVideoOffset = motionOffset
        )
    }

    // ---- Safe attribute readers ----
    private fun ExifInterface.getAttributeIntCompat(tag: String, defaultValue: Int): Int {
        return runCatching { getAttributeInt(tag, defaultValue) }
            .getOrDefault(defaultValue)
    }

    private fun ExifInterface.getAttributeLongCompat(tag: String, defaultValue: Long): Long {
        return runCatching {
            getAttribute(tag)?.trim()?.toLongOrNull() ?: defaultValue
        }.getOrDefault(defaultValue)
    }

    companion object {
        /**
         * Motion Photo may be stored under different vendor/XMP-ish keys.
         * Using string tags avoids compile errors across exifinterface versions.
         *
         * These are common names used by Google/GCAM and some OEMs.
         * Even if a specific device doesn't use one of these tags,
         * the code still remains safe and just returns false/null.
         */
        private val MOTION_PHOTO_FLAG_TAGS = listOf(
            "MotionPhoto",
            "GCamera:MotionPhoto",
            "MicroVideo"
        )

        /**
         * Offsets used to locate embedded video segment in Motion Photo.
         */
        private val MOTION_PHOTO_OFFSET_TAGS = listOf(
            "GCamera:VideoOffset",
            "GCamera:MicroVideoOffset",
            "MicroVideoOffset",
            "MotionPhotoVideoOffset"
        )
    }
}
