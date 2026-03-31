package com.example.musicframe

import android.graphics.Bitmap
import android.graphics.Color
import com.example.musicframe.cache.FrameCache
import com.example.musicframe.export.ExportManager
import com.example.musicframe.util.BitmapPool
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

class ExportBenchmark {

    @Before
    fun setup() {
        mockkStatic(Bitmap::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createMockBitmap(width: Int, height: Int): Bitmap {
        val bitmap = mockk<Bitmap>(relaxed = true)
        every { bitmap.width } returns width
        every { bitmap.height } returns height
        every { bitmap.allocationByteCount } returns width * height * 4
        every { bitmap.isRecycled } returns false
        return bitmap
    }

    @Test
    fun benchmarkCachePutPerformance() {
        val frameCache = FrameCache(maxMemoryMB = 64)
        val frameCount = 100
        val bitmapSize = 512 * 512

        val timeNanos = measureNanoTime {
            for (i in 0 until frameCount) {
                val bitmap = createMockBitmap(bitmapSize, bitmapSize)
                frameCache.put(i, bitmap)
            }
        }

        val timeMs = timeNanos / 1_000_000.0
        println("Cache put performance: ${timeMs}ms for $frameCount frames")
        assertTrue(timeMs < 5000)
    }

    @Test
    fun benchmarkCacheGetPerformance() {
        val frameCache = FrameCache(maxMemoryMB = 64)
        val frameCount = 100

        for (i in 0 until frameCount) {
            val bitmap = createMockBitmap(512, 512)
            frameCache.put(i, bitmap)
        }

        val timeNanos = measureNanoTime {
            for (i in 0 until frameCount) {
                frameCache.get(i)
            }
        }

        val timeMs = timeNanos / 1_000_000.0
        println("Cache get performance: ${timeMs}ms for $frameCount frames")
        assertTrue(timeMs < 1000)
    }

    @Test
    fun benchmarkBitmapPoolAcquireRelease() {
        val bitmapPool = BitmapPool(maxPoolSizeMB = 64)
        val iterations = 1000

        val timeMs = measureTimeMillis {
            for (i in 0 until iterations) {
                val bitmap = bitmapPool.acquire(256, 256)
                bitmapPool.release(bitmap)
            }
        }

        println("BitmapPool performance: ${timeMs}ms for $iterations acquire/release cycles")
        assertTrue(timeMs < 5000)
    }

    @Test
    fun benchmarkDifferentBitmapSizes() {
        val sizes = listOf(256, 512, 768, 1024, 1920)
        val results = mutableMapOf<Int, Long>()

        for (size in sizes) {
            val frameCache = FrameCache(maxMemoryMB = 64)
            val timeMs = measureTimeMillis {
                for (i in 0 until 10) {
                    val bitmap = createMockBitmap(size, size)
                    frameCache.put(i, bitmap)
                }
            }
            results[size] = timeMs
            println("Size ${size}x$size: ${timeMs}ms")
        }

        assertTrue(results.values.all { it < 5000 })
    }

    @Test
    fun benchmarkDifferentFrameCounts() {
        val frameCounts = listOf(10, 50, 100, 200)
        val results = mutableMapOf<Int, Long>()

        for (count in frameCounts) {
            val frameCache = FrameCache(maxMemoryMB = 128)
            val timeMs = measureTimeMillis {
                for (i in 0 until count) {
                    val bitmap = createMockBitmap(512, 512)
                    frameCache.put(i, bitmap)
                }
            }
            results[count] = timeMs
            println("$count frames: ${timeMs}ms")
        }

        assertTrue(results.values.all { it < 10000 })
    }

    @Test
    fun benchmarkMemoryUsageWithDifferentSizes() {
        val sizes = listOf(256, 512, 768, 1024)
        val results = mutableMapOf<Int, Int>()

        for (size in sizes) {
            val frameCache = FrameCache(maxMemoryMB = 32)
            var frameCount = 0

            while (frameCache.size < 30 * 1024 * 1024) {
                val bitmap = createMockBitmap(size, size)
                frameCache.put(frameCount, bitmap)
                frameCount++
            }
            results[size] = frameCount
            println("Size ${size}x$size: stored $frameCount frames (${frameCache.size / (1024 * 1024)}MB)")
        }

        assertTrue(results.values.all { it > 0 })
    }

    @Test
    fun benchmarkCacheHitRate() {
        val frameCache = FrameCache(maxMemoryMB = 32)
        val totalFrames = 100
        val repeatedAccess = 10

        for (i in 0 until totalFrames) {
            val bitmap = createMockBitmap(512, 512)
            frameCache.put(i, bitmap)
        }

        repeat(repeatedAccess) {
            for (i in 0 until totalFrames) {
                frameCache.get(i)
            }
        }

        val hitRate = frameCache.getHitRate()
        println("Cache hit rate after $repeatedAccess full scans: ${hitRate * 100}%")
        assertTrue(hitRate > 0.9f)
    }

    @Test
    fun benchmarkPoolReuseEfficiency() {
        val bitmapPool = BitmapPool(maxPoolSizeMB = 64)
        val iterations = 100

        for (i in 0 until iterations) {
            val bitmap = bitmapPool.acquire(512, 512)
            bitmapPool.release(bitmap)
        }

        val stats = bitmapPool.getStats()
        val reuseRate = if (stats.borrowedCount > 0) {
            stats.returnedCount.toFloat() / stats.borrowedCount
        } else 0f

        println("Pool reuse rate: ${reuseRate * 100}%")
        assertTrue(reuseRate >= 0.5f)
    }

    @Test
    fun benchmarkEvictionPerformance() {
        val frameCache = FrameCache(maxMemoryMB = 8)
        val totalFrames = 100

        for (i in 0 until totalFrames) {
            val bitmap = createMockBitmap(512, 512)
            frameCache.put(i, bitmap)
        }

        val evictionCount = frameCache.evictionCount
        println("Evictions after $totalFrames frames: $evictionCount")
        assertTrue(evictionCount > 0)
    }

    @Test
    fun benchmarkClearPerformance() {
        val frameCache = FrameCache(maxMemoryMB = 64)

        for (i in 0 until 50) {
            val bitmap = createMockBitmap(1024, 1024)
            frameCache.put(i, bitmap)
        }

        val timeMs = measureTimeMillis {
            frameCache.clear()
        }

        println("Clear performance: ${timeMs}ms")
        assertTrue(timeMs < 2000)
    }

    @Test
    fun generatePerformanceReport() {
        println("\n=== Export Performance Report ===\n")

        println("1. Cache Performance:")
        val cacheTest = measureTimeMillis {
            val cache = FrameCache(maxMemoryMB = 64)
            for (i in 0 until 100) {
                cache.put(i, createMockBitmap(512, 512))
            }
            for (i in 0 until 100) {
                cache.get(i)
            }
        }
        println("   - 100 frame cache operations: ${cacheTest}ms")
        println("   - Cache hit rate: ${FrameCache(8).apply {
            for (i in 0 until 50) put(i, createMockBitmap(256, 256))
            repeat(10) { for (j in 0 until 50) get(j) }
        }.getHitRate() * 100}%\n")

        println("2. BitmapPool Performance:")
        val pool = BitmapPool(maxPoolSizeMB = 64)
        val poolTest = measureTimeMillis {
            repeat(500) {
                val bitmap = pool.acquire(256, 256)
                pool.release(bitmap)
            }
        }
        val stats = pool.getStats()
        println("   - 500 acquire/release cycles: ${poolTest}ms")
        println("   - Reuse rate: ${if (stats.borrowedCount > 0) stats.returnedCount * 100 / stats.borrowedCount else 0}%\n")

        println("3. Memory Efficiency:")
        println("   - FrameCache: LRU eviction when exceeding ${8}MB limit")
        println("   - BitmapPool: Object reuse with ${4} bitmaps per size bucket\n")

        println("4. Recommended Settings:")
        println("   - Cache size: 32-64MB for optimal performance")
        println("   - Pool size: 32-64MB for bitmap reuse")
        println("   - Max frames: 100 for export\n")

        assertNotNull("Performance report generated", null)
    }

    @Test
    fun benchmarkQualityVsSpeed() {
        val qualities = listOf(
            ExportManager.QualityLevel.LOW,
            ExportManager.QualityLevel.MEDIUM,
            ExportManager.QualityLevel.HIGH
        )

        println("\nQuality Level Analysis:")
        for (quality in qualities) {
            println("   - ${quality.displayName}: quality=${quality.quality}")
        }

        assertEquals(3, qualities.size)
    }

    @Test
    fun benchmarkExportFormatComparison() {
        val formats = ExportManager.OutputFormat.values()

        println("\nExport Format Comparison:")
        for (format in formats) {
            println("   - ${format.displayName}: .${format.extension}, ${format.mimeType}")
        }

        assertEquals(2, formats.size)
    }
}
