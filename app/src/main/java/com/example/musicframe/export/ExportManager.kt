package com.example.musicframe.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.musicframe.cache.FrameCache
import com.example.musicframe.image.AnimatedFrame
import com.example.musicframe.image.FrameComposer
import com.example.musicframe.util.BitmapPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 导出管理器，负责协调整个动态图片导出流程。
 *
 * ExportManager 是导出模块的核心类，提供以下功能：
 * - 从 Uri 解码动态图片
 * - 为帧应用相框效果
 * - 编码为 GIF 或 WebP 格式
 * - 保存到系统相册
 *
 * **主要特性：**
 * - 支持 GIF 和 Animated WebP 两种输出格式
 * - 三档质量等级可选
 * - 帧缓存优化，避免重复解码
 * - Bitmap 对象池复用
 * - 进度回调与节流
 * - 可取消操作
 *
 * **使用示例：**
 * ```kotlin
 * val exportManager = ExportManager(context)
 *
 * // 导出动态图片
 * val result = exportManager.exportAnimatedImage(
 *     sourceUri = uri,
 *     format = OutputFormat.GIF,
 *     quality = QualityLevel.HIGH,
 *     frameConfig = frameConfig,
 *     musicMetadata = musicMetadata,
 *     onProgress = { current, total ->
 *         val progress = current * 100 / total
 *         updateProgressUI(progress)
 *     }
 * )
 *
 * result.onSuccess { file ->
 *     Toast.makeText(context, "已保存: ${file.name}", Toast.LENGTH_SHORT).show()
 * }
 *
 * result.onFailure { error ->
 *     Log.e(TAG, "导出失败: ${error.message}")
 * }
 * ```
 *
 * @param context Android Context，用于文件操作和媒体库访问
 *
 * @see OutputFormat
 * @see QualityLevel
 * @see ExportStatus
 * @see FrameCache
 * @see BitmapPool
 *
 * @author AMuPtoFrame
 * @since 1.0.31
 */
class ExportManager(private val context: Context) {

    companion object {
        private const val TAG = "ExportManager"

        /** 最大输出尺寸（宽度或高度），单位像素 */
        const val MAX_DIMENSION = 1080

        /** 最大帧数限制，防止内存溢出 */
        const val MAX_FRAMES = 100

        private const val FRAME_CACHE_SIZE_MB = 32
        private const val BITMAP_POOL_SIZE_MB = 32

        /** 进度更新频率限制：每秒最多 5 次 */
        private const val MAX_PROGRESS_UPDATES_PER_SECOND = 5
    }

    private val frameComposer = FrameComposer()
    private val frameDecoder = com.example.musicframe.image.AnimatedFrameDecoder(context)
    private val frameCache = FrameCache(FRAME_CACHE_SIZE_MB)
    private val bitmapPool = BitmapPool(BITMAP_POOL_SIZE_MB)
    private val exportMutex = Mutex()

    private var lastProgressUpdateTime = 0L
    private var lastReportedProgress = -1

    /**
     * 导出格式枚举
     *
     * @property displayName 用户可读的显示名称
     * @property extension 文件扩展名
     * @property mimeType MIME 类型
     */
    enum class OutputFormat(val displayName: String, val extension: String, val mimeType: String) {
        /** GIF 动图格式，兼容性最好 */
        GIF("GIF", "gif", "image/gif"),

        /** Animated WebP 格式，更小体积更好质量 */
        WEBP("Animated WebP", "webp", "image/webp")
    }

    /**
     * 导出质量等级
     *
     * @property displayName 用户可读的显示名称
     * @property quality 压缩质量值（0-100）
     */
    enum class QualityLevel(val displayName: String, val quality: Int) {
        /** 低质量，适用于快速分享 */
        LOW("低", 60),

        /** 中等质量，平衡大小和画质 */
        MEDIUM("中", 80),

        /** 高质量，最佳画质输出 */
        HIGH("高", 95)
    }

    /**
     * 导出状态密封类
     *
     * 用于 UI 层观察导出状态变化
     */
    sealed class ExportStatus {
        /** 空闲状态 */
        data object Idle : ExportStatus()

        /**
         * 进行中状态
         *
         * @property current 当前进度
         * @property total 总进度
         * @property phase 当前阶段描述
         */
        data class Progress(
            val current: Int,
            val total: Int,
            val phase: String
        ) : ExportStatus()

        /**
         * 成功状态
         *
         * @property outputFile 输出的文件
         * @property format 使用的格式
         */
        data class Success(
            val outputFile: File,
            val format: OutputFormat
        ) : ExportStatus()

        /**
         * 错误状态
         *
         * @property message 错误信息
         */
        data class Error(val message: String) : ExportStatus()
    }

    /**
     * 导出动态图片（从源图片 Uri 解码）。
     *
     * 完整的导出流程包括：
     * 1. **解码阶段**：从 Uri 解码动态图片
     * 2. **合成阶段**：为每帧应用相框效果
     * 3. **编码阶段**：编码为目标格式（GIF/WebP）
     * 4. **保存阶段**：保存到系统相册
     *
     * @param sourceUri 源图片的 Uri，支持 GIF、WebP 等动态格式
     * @param format 输出格式
     * @param quality 质量等级
     * @param frameConfig 相框配置
     * @param musicMetadata 音乐元数据（可选）
     * @param photoMetadata 照片元数据（可选）
     * @param headphoneInfo 耳机信息（可选）
     * @param onProgress 进度回调 (current, total)
     * @param isCancelled 取消检查回调，返回 true 表示应取消
     * @return 导出结果的 Result 对象
     */
    suspend fun exportAnimatedImage(
        sourceUri: Uri,
        format: OutputFormat,
        quality: QualityLevel,
        frameConfig: com.example.musicframe.image.FrameConfig,
        musicMetadata: com.example.musicframe.model.MusicMetadata? = null,
        photoMetadata: com.example.musicframe.image.PhotoMetadata? = null,
        headphoneInfo: com.example.musicframe.model.HeadphoneInfo? = null,
        onProgress: ((Int, Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting optimized export: format=$format, quality=$quality")
        exportMutex.withLock {
            resetProgressTracking()

            runCatching {
                Log.d(TAG, "Phase 1: Decoding animated image")
                val frames = frameDecoder.decodeAnimatedImage(sourceUri, MAX_FRAMES)

                if (frames.isEmpty()) {
                    throw IllegalStateException("无法解码动态图片或图片无有效帧")
                }

                Log.d(TAG, "Decoded ${frames.size} frames")

                Log.d(TAG, "Phase 2: Composing frames with frame")
                val totalFrames = frames.size
                val composedFrames = mutableListOf<AnimatedFrame>()

                frames.forEachIndexed { index, frame ->
                    if (isCancelled?.invoke() == true) {
                        throw InterruptedException("Export cancelled")
                    }

                    val cached = frameCache.get(index)
                    if (cached != null && !cached.isRecycled) {
                        composedFrames.add(AnimatedFrame(cached, frame.duration))
                    } else {
                        val scaledSource = scaleBitmapForCompose(frame.bitmap)
                        val framedBitmap = frameComposer.compose(
                            source = scaledSource,
                            config = frameConfig,
                            musicMetadata = musicMetadata,
                            photoMetadata = photoMetadata,
                            headphoneInfo = headphoneInfo
                        )
                        if (scaledSource != frame.bitmap) {
                            bitmapPool.release(scaledSource)
                        }

                        frameCache.put(index, framedBitmap)
                        composedFrames.add(AnimatedFrame(fradedBitmap, frame.duration))
                    }

                    if (shouldReportProgress(index + 1, totalFrames * 2)) {
                        onProgress?.invoke(index + 1, totalFrames * 2)
                    }
                }

                Log.d(TAG, "Phase 3: Encoding to ${format.name}")
                val outputFile = createOutputFile(format)
                val outputWidth = calculateOutputWidth(composedFrames.first().bitmap)
                val outputHeight = calculateOutputHeight(composedFrames.first().bitmap)

                val encoder = EncoderFactory.createEncoder(format.extension)
                val encodeConfig = EncodeConfig(
                    format = format.extension,
                    quality = quality.quality,
                    maxWidth = MAX_DIMENSION,
                    maxHeight = MAX_DIMENSION,
                    maxFrames = MAX_FRAMES
                )

                val composePhaseFrames = totalFrames
                encoder.encode(
                    frames = composedFrames,
                    outputWidth = outputWidth,
                    outputHeight = outputHeight,
                    quality = encodeConfig.quality,
                    outputPath = outputFile
                ) { current, total ->
                    if (shouldReportProgress(composePhaseFrames + current, composePhaseFrames * 2)) {
                        onProgress?.invoke(composePhaseFrames + current, composePhaseFrames * 2)
                    }
                }

                Log.d(TAG, "Phase 4: Saving to gallery")
                saveToGallery(outputFile, format)

                Log.d(TAG, "Export complete")
                frameCache.logStats()

                outputFile
            }.onFailure { error ->
                Log.e(TAG, "Export failed: ${error.message}", error)
                frameCache.clear()
                throw error
            }
        }
    }

    /**
     * 缩放 Bitmap 用于相框合成。
     *
     * 如果 Bitmap 尺寸超过 1080px，会进行缩放以节省内存。
     *
     * @param source 源 Bitmap
     * @return 缩放后的 Bitmap（可能与源相同）
     */
    private fun scaleBitmapForCompose(source: Bitmap): Bitmap {
        val maxSize = 1080
        if (source.width <= maxSize && source.height <= maxSize) {
            return source
        }
        val scale = maxSize.toFloat() / maxOf(source.width, source.height)
        val newWidth = (source.width * scale).toInt()
        val newHeight = (source.height * scale).toInt()
        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
    }

    /**
     * 重置进度跟踪状态。
     */
    private fun resetProgressTracking() {
        lastProgressUpdateTime = 0L
        lastReportedProgress = -1
    }

    /**
     * 判断是否应该报告进度（实现节流）。
     */
    private fun shouldReportProgress(current: Int, total: Int): Boolean {
        val now = System.currentTimeMillis()
        val elapsed = now - lastProgressUpdateTime
        val minInterval = 1000L / MAX_PROGRESS_UPDATES_PER_SECOND

        if (elapsed >= minInterval || current >= total) {
            val progress = (current * 100 / total)
            if (progress != lastReportedProgress) {
                lastProgressUpdateTime = now
                lastReportedProgress = progress
                return true
            }
        }
        return false
    }

    /**
     * 导出动态图片（从已有的 Bitmap 列表）。
     *
     * 适用于已经有帧数据的场景，跳过解码阶段。
     *
     * @param frames Bitmap 列表，每项代表一帧
     * @param frameDurations 每帧的持续时间（毫秒）
     * @param format 输出格式
     * @param quality 质量等级
     * @param onProgress 进度回调
     * @param isCancelled 取消检查回调
     * @return 导出结果的 Result 对象
     */
    suspend fun exportFromFrames(
        frames: List<Bitmap>,
        frameDurations: List<Int>,
        format: OutputFormat,
        quality: QualityLevel,
        onProgress: ((Int, Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting export from frames: ${frames.size} frames, format=$format, quality=$quality")
        exportMutex.withLock {
            resetProgressTracking()

            runCatching {
                if (frames.isEmpty()) {
                    throw IllegalStateException("帧列表为空")
                }

                val animatedFrames = frames.mapIndexed { index, bitmap ->
                    AnimatedFrame(bitmap, frameDurations.getOrElse(index) { 100 })
                }

                val outputFile = createOutputFile(format)
                val outputWidth = calculateOutputWidth(frames.first())
                val outputHeight = calculateOutputHeight(frames.first())

                val encoder = EncoderFactory.createEncoder(format.extension)

                val totalFrames = frames.size
                encoder.encode(
                    frames = animatedFrames,
                    outputWidth = outputWidth,
                    outputHeight = outputHeight,
                    quality = quality.quality,
                    outputPath = outputFile
                ) { current, total ->
                    if (shouldReportProgress(current, total)) {
                        onProgress?.invoke(current, total)
                    }
                }

                saveToGallery(outputFile, format)

                outputFile
            }.onFailure { error ->
                Log.e(TAG, "Export from frames failed: ${error.message}", error)
                throw error
            }
        }
    }

    /**
     * 创建输出文件。
     *
     * @param format 输出格式
     * @return 临时输出文件（未保存到相册）
     */
    private fun createOutputFile(format: OutputFormat): File {
        val fileName = "music_frame_${System.currentTimeMillis()}.${format.extension}"
        val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(cacheDir, fileName)
    }

    /**
     * 计算输出宽度，保持宽高比，限制最大尺寸。
     *
     * @param source 源 Bitmap
     * @return 输出宽度（像素）
     */
    private fun calculateOutputWidth(source: Bitmap): Int {
        val width = source.width
        val height = source.height
        val maxDim = MAX_DIMENSION.toFloat()

        return if (width > maxDim || height > maxDim) {
            val scale = maxDim / maxOf(width, height)
            (width * scale).toInt().coerceAtLeast(1)
        } else {
            width
        }
    }

    /**
     * 计算输出高度，保持宽高比，限制最大尺寸。
     *
     * @param source 源 Bitmap
     * @return 输出高度（像素）
     */
    private fun calculateOutputHeight(source: Bitmap): Int {
        val width = source.width
        val height = source.height
        val maxDim = MAX_DIMENSION.toFloat()

        return if (width > maxDim || height > maxDim) {
            val scale = maxDim / maxOf(width, height)
            (height * scale).toInt().coerceAtLeast(1)
        } else {
            height
        }
    }

    /**
     * 保存文件到相册。
     *
     * @param file 要保存的文件
     * @param format 输出格式
     * @return 保存后的 Uri
     */
    private fun saveToGallery(file: File, format: OutputFormat): Uri {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("无法创建媒体库条目")

        resolver.openOutputStream(uri).use { outputStream ->
            file.inputStream().use { input ->
                input.copyTo(requireNotNull(outputStream))
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        return uri
    }

    /**
     * 获取导出缓存目录。
     *
     * @return 缓存目录
     */
    fun getExportCacheDir(): File {
        return File(context.cacheDir, "exports").apply { mkdirs() }
    }

    /**
     * 清理导出缓存。
     *
     * 清空帧缓存并删除所有临时导出文件。
     */
    fun clearExportCache() {
        frameCache.clear()
        val cacheDir = getExportCacheDir()
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * 获取缓存统计信息。
     *
     * @return 缓存统计对象
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            frameCacheSizeBytes = frameCache.size,
            frameCacheHitRate = frameCache.getHitRate(),
            frameCacheEvictions = frameCache.evictionCount
        )
    }

    /**
     * 缓存统计信息数据类
     *
     * @property frameCacheSizeBytes 帧缓存大小（字节）
     * @property frameCacheHitRate 缓存命中率（0.0 - 1.0）
     * @property frameCacheEvictions 缓存淘汰次数
     */
    data class CacheStats(
        val frameCacheSizeBytes: Int,
        val frameCacheHitRate: Float,
        val frameCacheEvictions: Int
    )
}
