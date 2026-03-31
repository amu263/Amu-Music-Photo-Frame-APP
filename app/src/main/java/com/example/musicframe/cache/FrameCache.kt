package com.example.musicframe.cache

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache

/**
 * 基于 LRU 算法的帧缓存，用于存储已解码的动态图片帧。
 *
 * FrameCache 使用 Android 的 LruCache 实现，自动管理内存使用，
 * 当缓存达到指定内存限制时，自动淘汰最近最少使用的帧。
 *
 * **主要特性：**
 * - 可配置内存限制（默认 32MB）
 * - LRU 淘汰策略，确保最近使用的帧优先保留
 * - 自动内存计算（基于 Bitmap.allocationByteCount）
 * - 命中/未命中统计
 *
 * **使用示例：**
 * ```kotlin
 * val cache = FrameCache(maxMemoryMB = 32)
 *
 * // 存储帧
 * cache.put(frameIndex, bitmap)
 *
 * // 获取帧（可能返回 null）
 * val cached = cache.get(frameIndex)
 *
 * // 获取缓存命中率
 * val hitRate = cache.getHitRate()
 *
 * // 清理缓存
 * cache.clear()
 * ```
 *
 * @param maxMemoryMB 缓存的最大内存占用（MB），默认 32MB
 *
 * @see LruCache
 * @see Bitmap
 *
 * @author AMuPtoFrame
 * @since 1.0.31
 */
class FrameCache(maxMemoryMB: Int = 32) {

    companion object {
        private const val TAG = "FrameCache"
    }

    private val cache: LruCache<Int, Bitmap>

    /**
     * 缓存的最大内存限制（字节）
     */
    val maxSize: Int
        get() = cache.maxSize()

    /**
     * 当前缓存使用的内存大小（字节）
     */
    val size: Int
        get() = cache.size()

    /**
     * 缓存命中次数
     */
    val hitCount: Int
        get() = cache.hitCount()

    /**
     * 缓存未命中次数
     */
    val missCount: Int
        get() = cache.missCount()

    /**
     * 缓存淘汰次数
     */
    val evictionCount: Int
        get() = cache.evictionCount()

    /**
     * 初始化帧缓存
     *
     * @throws IllegalArgumentException 如果 maxMemoryMB <= 0
     */
    init {
        require(maxMemoryMB > 0) { "maxMemoryMB must be positive" }
        val maxMemory = (maxMemoryMB * 1024 * 1024)
        cache = object : LruCache<Int, Bitmap>(maxMemory) {
            override fun sizeOf(key: Int, bitmap: Bitmap): Int {
                return bitmap.allocationByteCount
            }

            override fun entryRemoved(
                evicted: Boolean,
                key: Int,
                oldValue: Bitmap,
                newValue: Bitmap?
            ) {
                if (evicted && !oldValue.isRecycled) {
                    Log.d(TAG, "Frame $key evicted from cache, size: ${oldValue.allocationByteCount} bytes")
                }
            }
        }
        Log.d(TAG, "FrameCache initialized with max memory: ${maxMemoryMB}MB")
    }

    /**
     * 获取指定索引的缓存帧。
     *
     * @param frameIndex 帧索引
     * @return 缓存的 Bitmap，如果未命中则返回 null
     */
    fun get(frameIndex: Int): Bitmap? {
        return cache.get(frameIndex)
    }

    /**
     * 存储帧到缓存。
     *
     * 如果 Bitmap 已被回收，则不会存储。
     *
     * @param frameIndex 帧索引
     * @param bitmap 要缓存的 Bitmap
     */
    fun put(frameIndex: Int, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            cache.put(frameIndex, bitmap)
        }
    }

    /**
     * 移除指定索引的缓存帧。
     *
     * @param frameIndex 帧索引
     * @return 被移除的 Bitmap，如果不存在则返回 null
     */
    fun remove(frameIndex: Int): Bitmap? {
        return cache.remove(frameIndex)
    }

    /**
     * 清空所有缓存。
     *
     * 调用此方法后，缓存将变为空。
     */
    fun clear() {
        trimToSize(0)
    }

    /**
     * 将缓存大小调整到指定值。
     *
     * @param maxSizeKB 目标最大大小（KB），设为 0 则清空缓存
     */
    fun trimToSize(maxSizeKB: Int) {
        cache.trimToSize(maxSizeKB)
    }

    /**
     * 计算缓存命中率。
     *
     * @return 命中率，范围 0.0 ~ 1.0，如果没有访问记录则返回 0.0
     */
    fun getHitRate(): Float {
        val total = cache.hitCount() + cache.missCount()
        return if (total > 0) {
            cache.hitCount().toFloat() / total
        } else {
            0f
        }
    }

    /**
     * 记录缓存统计信息到日志。
     *
     * 包含：当前大小、最大大小、命中次数、未命中次数、淘汰次数、命中率
     */
    fun logStats() {
        Log.d(TAG, "FrameCache stats: size=$size, max=$maxSize, hits=${hitCount}, misses=${missCount}, " +
                "evictions=${evictionCount}, hitRate=${String.format("%.2f", getHitRate() * 100)}%")
    }
}
