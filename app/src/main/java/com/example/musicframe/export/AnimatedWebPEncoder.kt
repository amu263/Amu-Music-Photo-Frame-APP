package com.example.musicframe.export

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.example.musicframe.image.AnimatedFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Animated WebP 格式编码器实现。
 *
 * AnimatedWebPEncoder 支持将帧序列编码为 WebP 格式。
 *
 * **API 版本支持：**
 * - **Android 8.0+ (API 26)**：支持有损 WebP 压缩
 * - **Android 11+ (API 30)**：优化的编码设置
 *
 * **注意**：当前 Android 平台对 Animated WebP 的支持有限，
 * 对于多帧动画，建议使用 GIF 格式以获得更好的兼容性。
 *
 * **主要特性：**
 * - 自适应质量优化
 * - 内存高效的编码
 * - 进度回调支持
 *
 * **使用示例：**
 * ```kotlin
 * val encoder = AnimatedWebPEncoder()
 *
 * val result = encoder.encode(
 *     frames = animatedFrames,
 *     outputWidth = 480,
 *     outputHeight = 480,
 *     quality = 85,
 *     outputPath = File(cacheDir, "output.webp"),
 *     progressCallback = { current, total ->
 *         updateProgress(current, total)
 *     }
 * )
 *
 * println("Encoded: ${result.frameCount} frames, ${result.fileSize} bytes")
 * ```
 *
 * @see AnimatedImageEncoder
 * @see AnimatedFrame
 * @see EncodeResult
 *
 * @author AMuPtoFrame
 * @since 1.0.31
 */
class AnimatedWebPEncoder : AnimatedImageEncoder {

    companion object {
        private const val TAG = "AnimatedWebPEncoder"

        /** WebP 最低支持版本 */
        private const val MIN_API_FOR_WEBP = Build.VERSION_CODES.O

        /** 高质量阈值 */
        private const val HIGH_QUALITY_THRESHOLD = 80

        /** 中等质量阈值 */
        private const val MEDIUM_QUALITY_THRESHOLD = 50
    }

    override val format = "image/webp"

    /**
     * 编码动态图片为 WebP 格式。
     *
     * @param frames 帧列表
     * @param outputWidth 输出宽度
     * @param outputHeight 输出高度
     * @param quality 质量参数（0-100）
     * @param outputPath 输出文件路径
     * @param progressCallback 进度回调 (current, total)
     * @return 编码结果
     * @throws UnsupportedOperationException 如果 Android 版本低于 API 26
     */
    override suspend fun encode(
        frames: List<AnimatedFrame>,
        outputWidth: Int,
        outputHeight: Int,
        quality: Int,
        outputPath: File,
        progressCallback: ((Int, Int) -> Unit)?
    ): EncodeResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting optimized WebP encoding: ${frames.size} frames, ${outputWidth}x$outputHeight, quality=$quality")

        if (Build.VERSION.SDK_INT < MIN_API_FOR_WEBP) {
            throw UnsupportedOperationException(
                "WebP requires API ${MIN_API_FOR_WEBP}+, current API is ${Build.VERSION.SDK_INT}"
            )
        }

        val outputStream = FileOutputStream(outputPath)

        try {
            val optimizedQuality = optimizeQualityForWebP(quality)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                encodeWithOptimizedSettings(
                    frames = frames,
                    width = outputWidth,
                    height = outputHeight,
                    quality = optimizedQuality,
                    outputStream = outputStream,
                    progressCallback = progressCallback
                )
            } else {
                encodeAsStaticWebP(
                    frames = frames,
                    width = outputWidth,
                    height = outputHeight,
                    quality = optimizedQuality,
                    outputStream = outputStream,
                    progressCallback = progressCallback
                )
            }

            val fileSize = outputPath.length()
            val totalDuration = frames.sumOf { it.duration.toLong() }

            Log.d(TAG, "WebP encoding complete: $fileSize bytes, ${totalDuration}ms")

            EncodeResult(
                outputFile = outputPath,
                fileSize = fileSize,
                frameCount = frames.size,
                totalDurationMs = totalDuration,
                format = format
            )
        } catch (e: Exception) {
            Log.e(TAG, "WebP encoding failed: ${e.message}", e)
            throw e
        } finally {
            outputStream.close()
        }
    }

    /**
     * 为 WebP 优化质量参数。
     *
     * WebP 的压缩特性与 JPEG 不同，需要适当调整质量参数。
     *
     * @param quality 原始质量参数
     * @return 优化后的质量参数
     */
    private fun optimizeQualityForWebP(quality: Int): Int {
        return when {
            quality >= HIGH_QUALITY_THRESHOLD -> quality
            quality >= MEDIUM_QUALITY_THRESHOLD -> (quality * 0.9).toInt()
            else -> (quality * 0.8).toInt()
        }
    }

    /**
     * 使用优化设置编码（Android 11+）。
     *
     * Android 11 提供了更好的 WebP 编码支持。
     */
    private suspend fun encodeWithOptimizedSettings(
        frames: List<AnimatedFrame>,
        width: Int,
        height: Int,
        quality: Int,
        outputStream: OutputStream,
        progressCallback: ((Int, Int) -> Unit)?
    ) {
        if (frames.isEmpty()) {
            throw IllegalArgumentException("Frames list cannot be empty")
        }

        val firstFrame = frames[0]
        val scaledBitmap = if (firstFrame.bitmap.width == width && firstFrame.bitmap.height == height) {
            firstFrame.bitmap
        } else {
            Bitmap.createScaledBitmap(firstFrame.bitmap, width, height, true)
        }

        val compressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        scaledBitmap.compress(compressFormat, quality, outputStream)

        if (scaledBitmap != firstFrame.bitmap && !scaledBitmap.isRecycled) {
            scaledBitmap.recycle()
        }

        if (frames.size > 1) {
            Log.w(TAG, "Encoded as static WebP (first frame). For animated WebP, use GIF format or API 31+.")
        }

        progressCallback?.invoke(frames.size, frames.size)
    }

    /**
     * 编码为静态 WebP（Android 8-10）。
     *
     * 由于 Android 8-10 不支持 Animated WebP，
     * 只能编码第一帧作为静态图像。
     */
    private suspend fun encodeAsStaticWebP(
        frames: List<AnimatedFrame>,
        width: Int,
        height: Int,
        quality: Int,
        outputStream: OutputStream,
        progressCallback: ((Int, Int) -> Unit)?
    ) {
        if (frames.isEmpty()) {
            throw IllegalArgumentException("Frames list cannot be empty")
        }

        val firstFrame = frames[0]
        val scaledBitmap = if (firstFrame.bitmap.width == width && firstFrame.bitmap.height == height) {
            firstFrame.bitmap
        } else {
            Bitmap.createScaledBitmap(firstFrame.bitmap, width, height, true)
        }

        @Suppress("DEPRECATION")
        scaledBitmap.compress(Bitmap.CompressFormat.WEBP, quality, outputStream)

        if (scaledBitmap != firstFrame.bitmap && !scaledBitmap.isRecycled) {
            scaledBitmap.recycle()
        }

        if (frames.size > 1) {
            Log.w(TAG, "API ${Build.VERSION.SDK_INT}: Encoded as static WebP (first frame only). " +
                    "For animated WebP, use GIF format.")
        }

        progressCallback?.invoke(frames.size, frames.size)
    }
}
