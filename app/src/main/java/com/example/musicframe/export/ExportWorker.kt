package com.example.musicframe.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.musicframe.cache.FrameCache
import com.example.musicframe.image.AnimatedFrame
import com.example.musicframe.image.FrameComposer
import com.example.musicframe.util.BitmapPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 导出工作器，负责在后台执行耗时的图片导出任务。
 *
 * ExportWorker 封装了完整的导出流程，包括：
 * - 帧解码与合成
 * - 缓存管理（FrameCache 和 BitmapPool）
 * - 进度报告与节流
 * - 取消支持
 *
 * **主要特性：**
 * - 后台协程执行，不阻塞主线程
 * - 进度更新节流（每秒最多 5 次）
 * - 可取消的导出任务
 * - 自动内存管理和清理
 *
 * **使用示例：**
 * ```kotlin
 * val worker = ExportWorker(context)
 *
 * // 启动导出
 * val job = worker.startExport(scope) {
 *     worker.exportAnimatedImage(
 *         sourceUri = uri,
 *         format = OutputFormat.GIF,
 *         quality = QualityLevel.HIGH,
 *         frameConfig = frameConfig,
 *         onProgress = { current, total ->
 *             // 更新 UI
 *         }
 *     )
 * }
 *
 * // 取消导出
 * worker.cancel()
 *
 * // 获取统计信息
 * val cacheStats = worker.getFrameCacheStats()
 * val poolStats = worker.getBitmapPoolStats()
 * ```
 *
 * @param context Android Context，用于文件操作和媒体库访问
 *
 * @see FrameCache
 * @see BitmapPool
 * @see ExportManager
 *
 * @author AMuPtoFrame
 * @since 1.0.31
 */
class ExportWorker(private val context: Context) {

    companion object {
        private const val TAG = "ExportWorker"
        private const val FRAME_CACHE_SIZE_MB = 32
        private const val BITMAP_POOL_SIZE_MB = 32
        private const val MAX_PROGRESS_UPDATES_PER_SECOND = 5
    }

    private val frameCache = FrameCache(FRAME_CACHE_SIZE_MB)
    private val bitmapPool = BitmapPool(BITMAP_POOL_SIZE_MB)
    private val frameComposer = FrameComposer()
    private val frameDecoder = com.example.musicframe.image.AnimatedFrameDecoder(context)

    private var currentJob: Job? = null
    private val isCancelled = AtomicBoolean(false)
    private val exportMutex = Mutex()

    private var lastProgressUpdateTime = 0L
    private var lastReportedProgress = -1

    /**
     * 导出动态图片（从源图片 Uri 解码）。
     *
     * 完整的导出流程：
     * 1. 解码动态图片获取帧
     * 2. 为每帧应用相框
     * 3. 编码为目标格式
     * 4. 保存到相册
     *
     * @param sourceUri 源图片的 Uri（支持 GIF、WebP 等动态格式）
     * @param format 输出格式（GIF 或 WebP）
     * @param quality 质量等级
     * @param frameConfig 相框配置
     * @param musicMetadata 音乐元数据（可选）
     * @param photoMetadata 照片元数据（可选）
     * @param headphoneInfo 耳机信息（可选）
     * @param onProgress 进度回调 (current, total)
     * @return 导出结果，包含输出文件
     */
    suspend fun exportAnimatedImage(
        sourceUri: Uri,
        format: ExportManager.OutputFormat,
        quality: ExportManager.QualityLevel,
        frameConfig: com.example.musicframe.image.FrameConfig,
        musicMetadata: com.example.musicframe.model.MusicMetadata? = null,
        photoMetadata: com.example.musicframe.image.PhotoMetadata? = null,
        headphoneInfo: com.example.musicframe.model.HeadphoneInfo? = null,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.Default) {
        Log.d(TAG, "Starting export with optimizations: format=$format, quality=$quality")
        isCancelled.set(false)
        lastProgressUpdateTime = 0L
        lastReportedProgress = -1

        val result = runCatching {
            val frames = frameDecoder.decodeAnimatedImage(sourceUri, ExportManager.MAX_FRAMES)

            if (frames.isEmpty()) {
                throw IllegalStateException("无法解码动态图片或图片无有效帧")
            }

            Log.d(TAG, "Decoded ${frames.size} frames")

            val composedFrames = mutableListOf<AnimatedFrame>()
            val totalFrames = frames.size

            for (index in frames.indices) {
                if (!isActive || isCancelled.get()) {
                    cleanup()
                    throw InterruptedException("Export cancelled")
                }

                val frame = frames[index]
                val framedBitmap = frameComposer.compose(
                    source = frame.bitmap,
                    config = frameConfig,
                    musicMetadata = musicMetadata,
                    photoMetadata = photoMetadata,
                    headphoneInfo = headphoneInfo
                )

                frameCache.put(index, framedBitmap)
                composedFrames.add(AnimatedFrame(framedBitmap, frame.duration))

                if (shouldReportProgress(index + 1, totalFrames * 2)) {
                    onProgress?.invoke(index + 1, totalFrames * 2)
                }
            }

            val outputFile = createOutputFile(format)
            val outputWidth = calculateOutputWidth(composedFrames.first().bitmap)
            val outputHeight = calculateOutputHeight(composedFrames.first().bitmap)

            val encoder = EncoderFactory.createEncoder(format.extension)
            val encodeConfig = EncodeConfig(
                format = format.extension,
                quality = quality.quality,
                maxWidth = ExportManager.MAX_DIMENSION,
                maxHeight = ExportManager.MAX_DIMENSION,
                maxFrames = ExportManager.MAX_FRAMES
            )

            val currentTotalFrames = totalFrames
            encoder.encode(
                frames = composedFrames,
                outputWidth = outputWidth,
                outputHeight = outputHeight,
                quality = encodeConfig.quality,
                outputPath = outputFile
            ) { current: Int, total: Int ->
                if (shouldReportProgress(currentTotalFrames + current, currentTotalFrames * 2)) {
                    onProgress?.invoke(currentTotalFrames + current, currentTotalFrames * 2)
                }
            }

            saveToGallery(outputFile, format)

            Log.d(TAG, "Export complete: ${outputFile.absolutePath}")
            frameCache.logStats()
            bitmapPool.logStats()

            outputFile
        }

        result.onFailure { e ->
            Log.e(TAG, "Export failed: ${e.message}", e)
            cleanup()
        }

        clearCaches()
        result
    }

    /**
     * 导出动态图片（从已有的 Bitmap 列表）。
     *
     * @param frames Bitmap 列表，每项代表一帧
     * @param frameDurations 每帧的持续时间（毫秒）
     * @param format 输出格式
     * @param quality 质量等级
     * @param onProgress 进度回调
     * @return 导出结果
     */
    suspend fun exportFromFrames(
        frames: List<Bitmap>,
        frameDurations: List<Int>,
        format: ExportManager.OutputFormat,
        quality: ExportManager.QualityLevel,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting export from frames: ${frames.size} frames")
        isCancelled.set(false)

        val result = runCatching {
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

            encoder.encode(
                frames = animatedFrames,
                outputWidth = outputWidth,
                outputHeight = outputHeight,
                quality = quality.quality,
                outputPath = outputFile,
                progressCallback = onProgress
            )

            saveToGallery(outputFile, format)

            Log.d(TAG, "Export complete: ${outputFile.absolutePath}")

            outputFile
        }

        result.onFailure { e ->
            Log.e(TAG, "Export from frames failed: ${e.message}", e)
        }

        clearCaches()
        result
    }

    /**
     * 取消当前导出任务。
     */
    fun cancel() {
        Log.d(TAG, "Cancelling export")
        isCancelled.set(true)
        currentJob?.cancel()
    }

    /**
     * 启动导出任务。
     *
     * @param scope 协程作用域
     * @param exportJob 导出任务
     * @return Job 对象，可用于取消或等待
     */
    fun startExport(scope: CoroutineScope, exportJob: suspend () -> Result<File>): Job {
        return scope.launch(Dispatchers.Default) {
            exportMutex.withLock {
                exportJob()
            }
        }.also { currentJob = it }
    }

    /**
     * 判断是否应该报告进度。
     *
     * 实现进度节流，避免过于频繁的更新。
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
     * 计算输出宽度，保持宽高比。
     */
    private fun calculateOutputWidth(source: Bitmap): Int {
        val width = source.width
        val height = source.height
        val maxDim = ExportManager.MAX_DIMENSION.toFloat()

        return if (width > maxDim || height > maxDim) {
            val scale = maxDim / maxOf(width, height)
            (width * scale).toInt().coerceAtLeast(1)
        } else {
            width
        }
    }

    /**
     * 计算输出高度，保持宽高比。
     */
    private fun calculateOutputHeight(source: Bitmap): Int {
        val width = source.width
        val height = source.height
        val maxDim = ExportManager.MAX_DIMENSION.toFloat()

        return if (width > maxDim || height > maxDim) {
            val scale = maxDim / maxOf(width, height)
            (height * scale).toInt().coerceAtLeast(1)
        } else {
            height
        }
    }

    /**
     * 创建输出文件。
     */
    private fun createOutputFile(format: ExportManager.OutputFormat): File {
        val fileName = "music_frame_${System.currentTimeMillis()}.${format.extension}"
        val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(cacheDir, fileName)
    }

    /**
     * 保存文件到相册。
     */
    private fun saveToGallery(file: File, format: ExportManager.OutputFormat): Uri {
        val resolver = context.contentResolver
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("无法创建媒体库条目")

        resolver.openOutputStream(uri).use { outputStream ->
            file.inputStream().use { input ->
                input.copyTo(requireNotNull(outputStream))
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            values.clear()
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        return uri
    }

    /**
     * 清理资源（发生错误时调用）。
     */
    private fun cleanup() {
        frameCache.clear()
        bitmapPool.releaseAll()
    }

    /**
     * 清空缓存。
     */
    private fun clearCaches() {
        frameCache.clear()
    }

    /**
     * 获取帧缓存统计信息。
     */
    fun getFrameCacheStats(): String {
        return "FrameCache: size=${frameCache.size / (1024 * 1024)}MB, " +
                "hitRate=${String.format("%.1f", frameCache.getHitRate() * 100)}%, " +
                "evictions=${frameCache.evictionCount}"
    }

    /**
     * 获取 Bitmap 池统计信息。
     */
    fun getBitmapPoolStats(): BitmapPool.PoolStats {
        return bitmapPool.getStats()
    }
}
