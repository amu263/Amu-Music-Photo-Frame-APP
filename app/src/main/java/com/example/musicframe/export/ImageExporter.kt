package com.example.musicframe.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class ImageExporter(private val context: Context) {

    companion object {
        private const val TAG = "ImageExporter"
    }

    suspend fun export(
        bitmap: Bitmap,
        fileName: String = defaultFileName(),
        format: Format = Format.PNG
    ): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.${format.extension}")
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: error("无法创建图片文件")
        resolver.openOutputStream(uri).use { stream ->
            writeBitmap(bitmap, stream, format)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    }

    // In file: app/src/main/java/com/example/musicframe/export/ImageExporter.kt

    /**
     * 导出带水印的动态相框
     * 使用 MediaCodec 重新编码视频，让视频每一帧都带有相框水印
     */
    suspend fun exportMotionPhotoWithVideoWatermark(
        framed: Bitmap,
        motionInfo: MotionPhotoInfo,
        fileName: String = defaultFileName()
    ): Uri = withContext(Dispatchers.IO) {
        Log.d(TAG, "exportMotionPhotoWithVideoWatermark: 开始处理")

        val processor = MotionPhotoProcessor(context)
        val outputFile = processor.processMotionPhoto(
            sourceUri = motionInfo.sourceUri,
            videoOffset = motionInfo.videoOffset,
            framedBitmap = framed,
            outputFileName = fileName
        )

        writeFileToMediaStore(outputFile, "$fileName.jpg", Format.JPEG.mimeType)
    }

    /**
     * 导出动态相框（简单版本 - 仅替换预览图，视频保持原样）
     */
    suspend fun exportMotionPhoto(
        framed: Bitmap,
        motionInfo: MotionPhotoInfo,
        fileName: String = defaultFileName()
    ): Uri = withContext(Dispatchers.IO) {
        val framedBytes = ByteArrayOutputStream().use { output ->
            framed.compress(Format.JPEG.compressFormat, Format.JPEG.quality, output)
            output.toByteArray()
        }

        val videoBytes = extractMotionVideo(motionInfo)
        val tempFile = File(context.cacheDir, "$fileName-motion.jpg")
        FileOutputStream(tempFile).use { output ->
            output.write(framedBytes)
            output.write(videoBytes)
        }

        // ... (代码前略)
        val exif = ExifInterface(tempFile)

// 修复：移除对不存在的 TAG_MOTION_PHOTO 的引用。
// ExifInterface 库没有一个标准的标签来标记实况照片。
// 这个功能通常需要一个更复杂的XMP库来实现，以便写入 Google Photos 等应用能够识别的元数据。
// 为了让项目能够编译通过，我们暂时移除这一行。
// exif.setAttribute("MicroVideo", "1") // 这是一个可能的、但不标准的尝试

        exif.saveAttributes()

        writeFileToMediaStore(tempFile, "$fileName.jpg", Format.JPEG.mimeType)
// ... (代码后略)
    }

    private fun writeBitmap(bitmap: Bitmap, stream: OutputStream?, format: Format) {
        requireNotNull(stream) { "输出流为空" }
        bitmap.compress(format.compressFormat, format.quality, stream)
        stream.flush()
    }

    private fun extractMotionVideo(info: MotionPhotoInfo): ByteArray {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(info.sourceUri) ?: error("无法读取实况视频片段")
        input.use { stream ->
            var remaining = info.videoOffset
            while (remaining > 0) {
                val skipped = stream.skip(remaining)
                if (skipped <= 0) break
                remaining -= skipped
            }
            return stream.readBytes()
        }
    }

    private fun writeFileToMediaStore(file: File, displayName: String, mimeType: String): Uri {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: error("无法创建图片文件")
        resolver.openOutputStream(uri).use { stream ->
            file.inputStream().use { input -> input.copyTo(requireNotNull(stream)) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    private fun defaultFileName(): String =
        "music_frame_" + System.currentTimeMillis()

    enum class Format(
        val displayName: String,
        val mimeType: String,
        val extension: String,
        val compressFormat: Bitmap.CompressFormat,
        val quality: Int
    ) {
        PNG("PNG", "image/png", "png", Bitmap.CompressFormat.PNG, 100),
        JPEG("JPEG", "image/jpeg", "jpg", Bitmap.CompressFormat.JPEG, 95),
        WEBP("WEBP", "image/webp", "webp", Bitmap.CompressFormat.WEBP, 95)
    }

    data class MotionPhotoInfo(val sourceUri: Uri, val videoOffset: Long)
}
