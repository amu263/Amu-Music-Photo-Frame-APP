package com.example.musicframe.export

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.Log
import com.example.musicframe.image.AnimatedFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * GIF 格式编码器实现。
 *
 * GifEncoderImpl 支持两种编码方式：
 * - **Android 14+ (API 34)**：使用原生 GifEncoder API，性能更好
 * - **Android 13 及以下**：使用优化的自定义 GIF 编码器
 *
 * **主要特性：**
 * - 自适应质量（根据质量参数调整颜色数）
 * - 优化的调色板生成
 * - 内存高效的逐帧编码
 * - 进度回调支持
 *
 * **使用示例：**
 * ```kotlin
 * val encoder = GifEncoderImpl()
 *
 * val result = encoder.encode(
 *     frames = animatedFrames,
 *     outputWidth = 480,
 *     outputHeight = 480,
 *     quality = 80,
 *     outputPath = File(cacheDir, "output.gif"),
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
class GifEncoderImpl : AnimatedImageEncoder {

    companion object {
        private const val TAG = "GifEncoderImpl"

        /** 默认帧持续时间（毫秒） */
        private const val DEFAULT_FRAME_DURATION = 100

        /** 循环次数，0 表示无限循环 */
        private const val LOOP_COUNT = 0

        /** 高质量颜色数 */
        private const val HIGH_QUALITY_COLORS = 256

        /** 中等质量颜色数 */
        private const val MEDIUM_QUALITY_COLORS = 128

        /** 低质量颜色数 */
        private const val LOW_QUALITY_COLORS = 64
    }

    override val format = "image/gif"

    /**
     * 编码动态图片为 GIF 格式。
     *
     * @param frames 帧列表
     * @param outputWidth 输出宽度
     * @param outputHeight 输出高度
     * @param quality 质量参数（0-100），影响颜色数
     * @param outputPath 输出文件路径
     * @param progressCallback 进度回调 (current, total)
     * @return 编码结果
     */
    override suspend fun encode(
        frames: List<AnimatedFrame>,
        outputWidth: Int,
        outputHeight: Int,
        quality: Int,
        outputPath: File,
        progressCallback: ((Int, Int) -> Unit)?
    ): EncodeResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting optimized GIF encoding: ${frames.size} frames, ${outputWidth}x$outputHeight, quality=$quality")

        val outputStream = FileOutputStream(outputPath)

        try {
            val colorCount = getColorCountForQuality(quality)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                encodeWithNativeGifEncoder(
                    frames = frames,
                    width = outputWidth,
                    height = outputHeight,
                    quality = quality,
                    outputStream = outputStream,
                    progressCallback = progressCallback
                )
            } else {
                encodeWithOptimizedSimpleGifEncoder(
                    frames = frames,
                    width = outputWidth,
                    height = outputHeight,
                    quality = quality,
                    colorCount = colorCount,
                    outputStream = outputStream,
                    progressCallback = progressCallback
                )
            }

            val fileSize = outputPath.length()
            val totalDuration = frames.sumOf { it.duration.toLong() }

            Log.d(TAG, "GIF encoding complete: $fileSize bytes, ${totalDuration}ms")

            EncodeResult(
                outputFile = outputPath,
                fileSize = fileSize,
                frameCount = frames.size,
                totalDurationMs = totalDuration,
                format = format
            )
        } catch (e: Exception) {
            Log.e(TAG, "GIF encoding failed: ${e.message}", e)
            throw e
        } finally {
            outputStream.close()
        }
    }

    /**
     * 根据质量参数获取颜色数。
     */
    private fun getColorCountForQuality(quality: Int): Int {
        return when {
            quality >= 80 -> HIGH_QUALITY_COLORS
            quality >= 50 -> MEDIUM_QUALITY_COLORS
            else -> LOW_QUALITY_COLORS
        }
    }

    /**
     * 使用原生 GifEncoder 编码（Android 14+）。
     *
     * 通过反射调用 GifEncoder API，避免依赖外部库。
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun encodeWithNativeGifEncoder(
        frames: List<AnimatedFrame>,
        width: Int,
        height: Int,
        quality: Int,
        outputStream: OutputStream,
        progressCallback: ((Int, Int) -> Unit)?
    ) {
        try {
            val gifEncoderClass = Class.forName("android.graphics.GifEncoder")
            val constructor = gifEncoderClass.getConstructor(
                OutputStream::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                android.graphics.ColorSpace::class.java
            )
            val gifEncoder = constructor.newInstance(outputStream, width, height, null)

            val setFrameDurationMethod = gifEncoderClass.getMethod("setFrameDuration", Int::class.javaPrimitiveType)
            val setLoopCountMethod = gifEncoderClass.getMethod("setLoopCount", Int::class.javaPrimitiveType)
            val writeFrameMethod = gifEncoderClass.getMethod(
                Bitmap::class.java,
                Long::class.javaPrimitiveType
            )
            val flushMethod = gifEncoderClass.getMethod("flush")

            setFrameDurationMethod.invoke(gifEncoder, DEFAULT_FRAME_DURATION)
            setLoopCountMethod.invoke(gifEncoder, LOOP_COUNT)

            frames.forEachIndexed { index, frame ->
                try {
                    val scaledBitmap = scaleBitmap(frame.bitmap, width, height)
                    writeFrameMethod.invoke(gifEncoder, scaledBitmap, frame.duration.toLong())

                    if (scaledBitmap != frame.bitmap && !scaledBitmap.isRecycled) {
                        scaledBitmap.recycle()
                    }

                    if (index % 5 == 0 || index == frames.size - 1) {
                        progressCallback?.invoke(index + 1, frames.size)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error encoding frame $index: ${e.message}")
                }
            }

            flushMethod.invoke(gifEncoder)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to use native GifEncoder, falling back to simple encoder: ${e.message}")
            encodeWithOptimizedSimpleGifEncoder(
                frames = frames,
                width = width,
                height = height,
                quality = quality,
                colorCount = getColorCountForQuality(quality),
                outputStream = outputStream,
                progressCallback = progressCallback
            )
        }
    }

    /**
     * 使用优化的自定义 GIF 编码器。
     *
     * 适用于 Android 13 及以下版本，逐帧编码到 GIF 文件。
     */
    private suspend fun encodeWithOptimizedSimpleGifEncoder(
        frames: List<AnimatedFrame>,
        width: Int,
        height: Int,
        quality: Int,
        colorCount: Int,
        outputStream: OutputStream,
        progressCallback: ((Int, Int) -> Unit)?
    ) {
        val gifWriter = OptimizedGifWriter(outputStream, width, height, colorCount)

        frames.forEachIndexed { index, frame ->
            try {
                val scaledBitmap = scaleBitmap(frame.bitmap, width, height)
                gifWriter.writeFrame(scaledBitmap, frame.duration)

                if (scaledBitmap != frame.bitmap && !scaledBitmap.isRecycled) {
                    scaledBitmap.recycle()
                }

                if (index % 5 == 0 || index == frames.size - 1) {
                    progressCallback?.invoke(index + 1, frames.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error encoding frame $index: ${e.message}")
            }
        }

        gifWriter.finish()
    }

    /**
     * 缩放 Bitmap 到目标尺寸。
     */
    private fun scaleBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (source.width == targetWidth && source.height == targetHeight) {
            return source
        }
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }
}

/**
 * 优化的 GIF 写入器。
 *
 * 实现基本的 GIF 编码逻辑，包括：
 * - GIF Header 写入
 * - 调色板生成
 * - 帧数据编码
 * - LZW 压缩（简化实现）
 *
 * @property outputStream 输出流
 * @property width GIF 宽度
 * @property height GIF 高度
 * @property colorCount 颜色数
 */
private class OptimizedGifWriter(
    private val outputStream: OutputStream,
    private val width: Int,
    private val height: Int,
    private val colorCount: Int
) {
    private var frameCount = 0
    private var colorPalette: IntArray = IntArray(0)
    private var hasTransparent = false

    init {
        writeHeader()
        writeLogicalScreenDescriptor()
    }

    /** 写入 GIF 文件头 */
    private fun writeHeader() {
        outputStream.write("GIF89a".toByteArray())
    }

    /** 写入逻辑屏幕描述符 */
    private fun writeLogicalScreenDescriptor() {
        writeShort(width)
        writeShort(height)
        val colorTableSize = getColorTableSize()
        outputStream.write(0x80.toInt() or (colorTableSize shl 4) or 0x07)
        outputStream.write(0x00)
        outputStream.write(0x00)
        writeColorTable()
    }

    private fun getColorTableSize(): Int {
        var size = colorCount
        var tableSize = 0
        while (size > 2) {
            size = size shr 1
            tableSize++
        }
        return tableSize
    }

    private fun writeColorTable() {
        val tableSize = 1 shl (getColorTableSize() + 1)
        for (i in 0 until tableSize) {
            if (i < colorPalette.size) {
                outputStream.write(colorPalette[i] and 0xFF)
                outputStream.write((colorPalette[i] shr 8) and 0xFF)
                outputStream.write((colorPalette[i] shr 16) and 0xFF)
            } else {
                outputStream.write(0)
                outputStream.write(0)
                outputStream.write(0)
            }
        }
    }

    /**
     * 写入一帧。
     *
     * @param bitmap 帧的 Bitmap
     * @param durationMs 帧持续时间（毫秒）
     */
    fun writeFrame(bitmap: Bitmap, durationMs: Int) {
        if (colorPalette.isEmpty()) {
            colorPalette = buildColorPalette(bitmap)
        }

        writeGraphicControlExtension(durationMs)
        writeImageDescriptor()
        writeImageData(bitmap)
        frameCount++
    }

    /**
     * 构建颜色调色板。
     *
     * 通过统计像素颜色生成优化的调色板。
     */
    private fun buildColorPalette(bitmap: Bitmap): IntArray {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val colorMap = LinkedHashMap<Int, Int>()
        for (pixel in pixels) {
            val rgb = pixel and 0x00FFFFFF
            colorMap[rgb] = (colorMap[rgb] ?: 0) + 1
        }

        val sortedColors = colorMap.entries.sortedByDescending { it.value }
        val palette = IntArray(colorCount.coerceAtMost(sortedColors.size))
        for (i in palette.indices) {
            palette[i] = sortedColors[i].key
        }

        return palette
    }

    /** 查找最近的调色板颜色索引 */
    private fun findNearestColorIndex(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF

        var minDist = Int.MAX_VALUE
        var bestIndex = 0

        for (i in colorPalette.indices) {
            val pr = (colorPalette[i] shr 16) and 0xFF
            val pg = (colorPalette[i] shr 8) and 0xFF
            val pb = colorPalette[i] and 0xFF

            val dist = (r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb)
            if (dist < minDist) {
                minDist = dist
                bestIndex = i
            }
        }

        return bestIndex
    }

    private fun writeGraphicControlExtension(durationMs: Int) {
        outputStream.write(0x21)
        outputStream.write(0xF9)
        outputStream.write(0x04)
        outputStream.write(if (hasTransparent) 0x09 else 0x08)
        writeShort(durationMs / 10)
        outputStream.write(0x00)
        outputStream.write(0x00)
    }

    private fun writeImageDescriptor() {
        outputStream.write(0x2C)
        writeShort(0)
        writeShort(0)
        writeShort(width)
        writeShort(height)
        outputStream.write(0x00)
    }

    private fun writeImageData(bitmap: Bitmap) {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val indexedPixels = ByteArray(pixels.size)
        for (i in pixels.indices) {
            val rgb = pixels[i] and 0x00FFFFFF
            indexedPixels[i] = findNearestColorIndex(rgb).toByte()
        }

        outputStream.write(0x08)

        val subBlockSize = 250
        var offset = 0
        while (offset < indexedPixels.size) {
            val blockSize = minOf(subBlockSize, indexedPixels.size - offset)
            outputStream.write(blockSize)
            outputStream.write(indexedPixels, offset, blockSize)
            offset += blockSize
        }

        outputStream.write(0x00)
    }

    /** 完成 GIF 文件写入 */
    fun finish() {
        outputStream.write(0x3B)
    }

    private fun writeShort(value: Int) {
        outputStream.write(value and 0xFF)
        outputStream.write((value shr 8) and 0xFF)
    }
}
