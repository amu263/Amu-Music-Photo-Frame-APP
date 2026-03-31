package com.example.musicframe.util

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bitmap 对象池，用于复用 Bitmap 对象以减少内存分配开销。
 *
 * BitmapPool 通过按尺寸分组管理可复用的 Bitmap 对象，
 * 避免频繁的创建和销毁操作，从而提升性能并减少内存碎片。
 *
 * **主要特性：**
 * - 按尺寸（width x height x config）分组
 * - 每个尺寸最多缓存 4 个对象
 * - 总内存限制可配置（默认 32MB）
 * - 线程安全实现
 *
 * **使用示例：**
 * ```kotlin
 * val pool = BitmapPool(maxPoolSizeMB = 32)
 *
 * // 获取 Bitmap（优先从池中获取，池空则创建新的）
 * val bitmap = pool.acquire(100, 100)
 *
 * // 使用完成后归还池中
 * pool.release(bitmap)
 *
 * // 获取统计信息
 * val stats = pool.getStats()
 * println("Borrowed: ${stats.borrowedCount}, Returned: ${stats.returnedCount}")
 *
 * // 清理所有池中的 Bitmap
 * pool.releaseAll()
 * ```
 *
 * @param maxPoolSizeMB 对象池的最大内存限制（MB），默认 32MB
 *
 * @see Bitmap
 *
 * @author AMuPtoFrame
 * @since 1.0.31
 */
class BitmapPool(maxPoolSizeMB: Int = 32) {

    companion object {
        private const val TAG = "BitmapPool"
        private const val MAX_POOLS_PER_SIZE = 4
    }

    private val pools = ConcurrentHashMap<SizeKey, PooledBitmapQueue>()
    private val totalSizeBytes = AtomicInteger(0)
    private val maxPoolSizeBytes = maxPoolSizeMB * 1024 * 1024

    private val borrowedCount = AtomicInteger(0)
    private val returnedCount = AtomicInteger(0)

    /**
     * 初始化 Bitmap 对象池
     */
    init {
        Log.d(TAG, "BitmapPool initialized with max size: ${maxPoolSizeMB}MB")
    }

    /**
     * 从池中获取一个 Bitmap，或创建新的 Bitmap。
     *
     * 如果池中有相同尺寸的可用 Bitmap，则复用；否则创建新的。
     *
     * @param width Bitmap 宽度
     * @param height Bitmap 高度
     * @param config Bitmap 配置，默认 ARGB_8888
     * @return 可用的 Bitmap 对象
     */
    fun acquire(width: Int, height: Int, config: Config = Config.ARGB_8888): Bitmap {
        val key = SizeKey(width, height, config)
        val pool = pools.getOrPut(key) { PooledBitmapQueue() }

        synchronized(pool) {
            val bitmap = pool.poll()
            if (bitmap != null && !bitmap.isRecycled) {
                borrowedCount.incrementAndGet()
                Log.v(TAG, "Acquired bitmap from pool: ${key.width}x${key.height}")
                return bitmap
            }
        }

        val newBitmap = Bitmap.createBitmap(width, height, config)
        Log.v(TAG, "Created new bitmap: ${key.width}x${key.height}")
        return newBitmap
    }

    /**
     * 将 Bitmap 归还到对象池。
     *
     * 如果池已满或达到内存限制，Bitmap 将被回收而不是放入池中。
     *
     * @param bitmap 要归还的 Bitmap，null 或已回收的 Bitmap 将被忽略
     */
    fun release(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return

        val key = SizeKey(bitmap.width, bitmap.height, bitmap.config ?: Config.ARGB_8888)
        val pool = pools.getOrPut(key) { PooledBitmapQueue() }

        synchronized(pool) {
            if (pool.size < MAX_POOLS_PER_SIZE) {
                val currentSize = totalSizeBytes.get()
                if (currentSize + bitmap.allocationByteCount <= maxPoolSizeBytes) {
                    pool.offer(bitmap)
                    totalSizeBytes.addAndGet(bitmap.allocationByteCount)
                    returnedCount.incrementAndGet()
                    Log.v(TAG, "Released bitmap to pool: ${key.width}x${key.height}, pool size: ${pool.size}")
                } else {
                    bitmap.recycle()
                    Log.v(TAG, "Pool full, recycled bitmap: ${key.width}x${key.height}")
                }
            } else {
                bitmap.recycle()
                Log.v(TAG, "Pool limit reached, recycled bitmap: ${key.width}x${key.height}")
            }
        }
    }

    /**
     * 释放池中所有 Bitmap 并回收内存。
     *
     * 调用此方法后，对象池将变为空。
     */
    fun releaseAll() {
        pools.forEach { (_, queue) ->
            synchronized(queue) {
                while (true) {
                    val bitmap = queue.poll() ?: break
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }
        }
        totalSizeBytes.set(0)
        Log.d(TAG, "Released all bitmaps from pool")
    }

    /**
     * 获取对象池统计信息。
     *
     * @return 包含各项统计数据的 [PoolStats] 对象
     */
    fun getStats(): PoolStats {
        return PoolStats(
            totalSizeBytes = totalSizeBytes.get(),
            maxSizeBytes = maxPoolSizeBytes,
            borrowedCount = borrowedCount.get(),
            returnedCount = returnedCount.get(),
            poolCount = pools.size
        )
    }

    /**
     * 记录对象池统计信息到日志。
     */
    fun logStats() {
        val stats = getStats()
        Log.d(TAG, "BitmapPool stats: " +
                "size=${stats.totalSizeBytes / (1024 * 1024)}MB/" +
                "${stats.maxSizeBytes / (1024 * 1024)}MB, " +
                "borrowed=${stats.borrowedCount}, " +
                "returned=${stats.returnedCount}, " +
                "pools=${stats.poolCount}")
    }

    /**
     * 内部类：管理特定尺寸的 Bitmap 队列
     */
    private class PooledBitmapQueue {
        private val queue = ArrayDeque<Bitmap>(MAX_POOLS_PER_SIZE)

        val size: Int
            get() = queue.size

        fun poll(): Bitmap? = queue.removeFirstOrNull()

        fun offer(bitmap: Bitmap) {
            queue.addLast(bitmap)
        }
    }

    /**
     * 内部类：Bitmap 尺寸标识
     */
    private data class SizeKey(
        val width: Int,
        val height: Int,
        val config: Config
    )

    /**
     * 对象池统计信息
     *
     * @property totalSizeBytes 当前池中 Bitmap 占用的总内存（字节）
     * @property maxSizeBytes 对象池的最大内存限制（字节）
     * @property borrowedCount 从池中借出的 Bitmap 总数
     * @property returnedCount 归还到池中的 Bitmap 总数
     * @property poolCount 当前池分组的数量
     */
    data class PoolStats(
        val totalSizeBytes: Int,
        val maxSizeBytes: Int,
        val borrowedCount: Int,
        val returnedCount: Int,
        val poolCount: Int
    )
}
