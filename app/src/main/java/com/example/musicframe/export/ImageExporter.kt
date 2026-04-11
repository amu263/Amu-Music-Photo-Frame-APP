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
        Log.d(TAG, "exportMotionPhoto: 开始导出, videoOffset=${motionInfo.videoOffset}")
        
        // 提取视频数据
        val videoBytes = extractMotionVideo(motionInfo)
        Log.d(TAG, "exportMotionPhoto: 提取视频大小: ${videoBytes.size} bytes")
        
        if (videoBytes.isEmpty()) {
            Log.e(TAG, "exportMotionPhoto: 视频提取失败，返回静态图片")
            return@withContext writeFileToMediaStoreFromBitmap(framed, "$fileName-static.jpg")
        }
        
        // 检查视频数据是否有效
        val isValidVideo = videoBytes.size > 100 && (
            String(videoBytes.copyOfRange(4, 8)) == "ftyp" ||
            String(videoBytes.copyOfRange(4, 8)) == "moov" ||
            String(videoBytes.copyOfRange(0, 4)) == "ftyp" ||
            String(videoBytes.copyOfRange(0, 4)) == "moov" ||
            String(videoBytes.copyOfRange(0, 4)) == "free" ||
            String(videoBytes.copyOfRange(0, 4)) == "mdat"
        )
        Log.d(TAG, "exportMotionPhoto: 视频数据有效: $isValidVideo")
        
        if (!isValidVideo) {
            Log.e(TAG, "exportMotionPhoto: 视频数据无效，返回静态图片")
            return@withContext writeFileToMediaStoreFromBitmap(framed, "$fileName-static.jpg")
        }

        // 创建带 XMP 元数据的 Motion Photo
        // 步骤：创建 JPEG + XMP + 视频数据 的结构
        val tempFile = File(context.cacheDir, "$fileName-motion.jpg")
        
        // 计算视频在文件中的位置 = JPEG 大小
        // 但我们需要考虑 XMP 元数据的大小
        val xmpMetadata = createMotionPhotoXmp(videoBytes.size.toLong())
        val xmpBytes = xmpMetadata.toByteArray(Charsets.UTF_8)
        
        // 创建临时文件: JPEG 数据 + XMP 元数据 + 视频数据
        FileOutputStream(tempFile).use { output ->
            // 1. 写入 JPEG 图片
            ByteArrayOutputStream().use { jpegStream ->
                framed.compress(Bitmap.CompressFormat.JPEG, 95, jpegStream)
                val jpegBytes = jpegStream.toByteArray()
                output.write(jpegBytes)
                Log.d(TAG, "exportMotionPhoto: JPEG 大小: ${jpegBytes.size}")
            }
            
            // 2. 写入 XMP 元数据 (APP1 segment)
            val xmpSegment = createXmpApp1Segment(xmpBytes)
            output.write(xmpSegment)
            Log.d(TAG, "exportMotionPhoto: XMP segment 大小: ${xmpSegment.size}")
            
            // 3. 写入视频数据
            output.write(videoBytes)
        }
        
        Log.d(TAG, "exportMotionPhoto: 组装文件大小: ${tempFile.length()} bytes")
        writeFileToMediaStore(tempFile, "$fileName.jpg", Format.JPEG.mimeType)
    }

    /**
     * 创建 Motion Photo 所需的 XMP 元数据
     * @param videoLength 视频数据的长度（用于计算 MicroVideoOffset）
     */
    private fun createMotionPhotoXmp(videoLength: Long): String {
        // MicroVideoOffset 是从文件末尾计算的偏移量，即视频长度
        return """<?xml version="1.0" encoding="UTF-8"?>
<x:xmpmeta xmlns:x="adobe:ns:meta" x:xmptk="Adobe XMP Core">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
    xmlns:Container="http://ns.google.com/photos/1.0/container/"
    GCamera:MicroVideo="1"
    GCamera:MicroVideoVersion="1"
    GCamera:MicroVideoOffset="$videoLength"
    Container:Directory=""
    Container:Directory[1]/Container:Item
      Container:Item/Mime="image/jpeg"
      Container:Item/Semantic="Primary"
      Container:Item/Length="${videoLength}"
    Container:Directory[2]/Container:Item
      Container:Item/Mime="video/mp4"
      Container:Item/Semantic="MotionPhoto"
      Container:Item/Length="${videoLength}"/>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>"""
    }

    /**
     * 创建 XMP APP1 segment
     * JPEG 结构: FF E1 [长度2字节] [XMP数据]
     * 长度 = XMP数据长度 + 2 (用于存储 "http://ns.adobe.com/xap/1.0/" 头部)
     */
    private fun createXmpApp1Segment(xmpBytes: ByteArray): ByteArray {
        // XMP 头部
        val xmpHeader = "http://ns.adobe.com/xap/1.0/".toByteArray(Charsets.UTF_8)
        
        // APP1 标记 + 长度字段 + XMP头部 + XMP数据
        val totalXmpSize = xmpHeader.size + xmpBytes.size
        // 长度字段包含自己（2字节） + XMP头部 + XMP数据
        val lengthField = totalXmpSize + 2
        
        val segment = ByteArray(2 + 2 + totalXmpSize) // FF E1 + length + xmpHeader + xmpBytes
        
        // FF E1
        segment[0] = 0xFF.toByte()
        segment[1] = 0xE1.toByte()
        
        // 长度 (big-endian)
        segment[2] = ((lengthField shr 8) and 0xFF).toByte()
        segment[3] = (lengthField and 0xFF).toByte()
        
        // XMP 头部
        System.arraycopy(xmpHeader, 0, segment, 4, xmpHeader.size)
        
        // XMP 数据
        System.arraycopy(xmpBytes, 0, segment, 4 + xmpHeader.size, xmpBytes.size)
        
        return segment
    }

    private fun writeFileToMediaStoreFromBitmap(bitmap: Bitmap, displayName: String): Uri {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: error("无法创建图片文件")
        resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
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
            Log.d(TAG, "extractMotionVideo: 开始提取视频，从偏移量 ${info.videoOffset}")
            
            // 使用 ByteArrayInputStream 的 mark/reset 来确保正确跳过
            val fullData = stream.readBytes()
            Log.d(TAG, "extractMotionVideo: 文件总大小: ${fullData.size}")
            
            if (info.videoOffset >= fullData.size) {
                Log.e(TAG, "extractMotionVideo: videoOffset ${info.videoOffset} 超出文件大小 ${fullData.size}")
                error("视频偏移量超出文件大小")
            }
            
            val videoData = fullData.copyOfRange(info.videoOffset.toInt(), fullData.size)
            Log.d(TAG, "extractMotionVideo: 提取到 ${videoData.size} 字节视频数据")
            
            return videoData
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
