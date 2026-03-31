package com.example.musicframe

import android.graphics.Bitmap
import android.graphics.Color
import com.example.musicframe.util.BitmapPool
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BitmapPoolTest {

    private lateinit var bitmapPool: BitmapPool

    @Before
    fun setup() {
        mockkStatic(Bitmap::class)
        bitmapPool = BitmapPool(maxPoolSizeMB = 8)
    }

    @After
    fun tearDown() {
        bitmapPool.releaseAll()
        unmockkAll()
    }

    private fun createMockBitmap(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        val bitmap = mockk<Bitmap>(relaxed = true)
        every { bitmap.width } returns width
        every { bitmap.height } returns height
        every { bitmap.config } returns config
        every { bitmap.allocationByteCount } returns width * height * 4
        every { bitmap.isRecycled } returns false
        return bitmap
    }

    @Test
    fun testPoolInitialization() {
        val stats = bitmapPool.getStats()
        assertEquals(0, stats.totalSizeBytes)
        assertEquals(8 * 1024 * 1024, stats.maxSizeBytes)
        assertEquals(0, stats.borrowedCount)
        assertEquals(0, stats.returnedCount)
        assertEquals(0, stats.poolCount)
    }

    @Test
    fun testAcquireCreatesNewBitmap() {
        val bitmap = bitmapPool.acquire(100, 100)

        assertNotNull(bitmap)
        assertEquals(100, bitmap.width)
        assertEquals(100, bitmap.height)
    }

    @Test
    fun testAcquireAndRecycle() {
        val original = bitmapPool.acquire(100, 100)
        bitmapPool.release(original)

        val stats = bitmapPool.getStats()
        assertEquals(1, stats.returnedCount)
    }

    @Test
    fun testReuseRecycledBitmap() {
        val bitmap1 = bitmapPool.acquire(100, 100)
        bitmapPool.release(bitmap1)

        val bitmap2 = bitmapPool.acquire(100, 100)

        val stats = bitmapPool.getStats()
        assertTrue(stats.borrowedCount >= 2)
    }

    @Test
    fun testDifferentSizesGoToDifferentPools() {
        val bitmap1 = bitmapPool.acquire(100, 100)
        bitmapPool.release(bitmap1)

        val bitmap2 = bitmapPool.acquire(200, 200)
        bitmapPool.release(bitmap2)

        val stats = bitmapPool.getStats()
        assertTrue(stats.poolCount >= 2)
    }

    @Test
    fun testReleaseNullBitmap() {
        bitmapPool.release(null)

        val stats = bitmapPool.getStats()
        assertEquals(0, stats.returnedCount)
    }

    @Test
    fun testReleaseRecycledBitmap() {
        val recycledBitmap = mockk<Bitmap>()
        every { recycledBitmap.isRecycled } returns true

        bitmapPool.release(recycledBitmap)

        val stats = bitmapPool.getStats()
        assertEquals(0, stats.returnedCount)
    }

    @Test
    fun testReleaseAll() {
        val bitmap1 = bitmapPool.acquire(100, 100)
        val bitmap2 = bitmapPool.acquire(200, 200)
        bitmapPool.release(bitmap1)
        bitmapPool.release(bitmap2)

        bitmapPool.releaseAll()

        val stats = bitmapPool.getStats()
        assertEquals(0, stats.totalSizeBytes)
        assertEquals(0, stats.poolCount)
    }

    @Test
    fun testPoolSizeLimit() {
        for (i in 0 until 10) {
            val bitmap = bitmapPool.acquire(100, 100)
            bitmapPool.release(bitmap)
        }

        val stats = bitmapPool.getStats()
        assertTrue(stats.poolCount <= 4)
    }

    @Test
    fun testMemoryLimitEnforced() {
        val smallPool = BitmapPool(maxPoolSizeMB = 1)

        for (i in 0 until 20) {
            val bitmap = smallPool.acquire(200, 200)
            smallPool.release(bitmap)
        }

        val stats = smallPool.getStats()
        assertTrue(stats.totalSizeBytes <= smallPool.let { it as Any }.
            let { (it.javaClass.getDeclaredField("maxPoolSizeBytes").apply { isAccessible = true }.get(it) as Int) })
    }

    @Test
    fun testStatsUpdateOnAcquire() {
        bitmapPool.acquire(100, 100)

        val stats = bitmapPool.getStats()
        assertEquals(1, stats.borrowedCount)
    }

    @Test
    fun testSameSizeReuseFromPool() {
        val bitmap1 = bitmapPool.acquire(100, 100)
        bitmapPool.release(bitmap1)

        val bitmap2 = bitmapPool.acquire(100, 100)

        assertNotNull(bitmap2)
    }

    @Test
    fun testDifferentConfigsSeparatePools() {
        val argbBitmap = createMockBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val rgb565Bitmap = createMockBitmap(100, 100, Bitmap.Config.RGB_565)

        bitmapPool.release(argbBitmap)
        bitmapPool.release(rgb565Bitmap)

        val stats = bitmapPool.getStats()
        assertTrue(stats.poolCount >= 2)
    }

    @Test
    fun testLogStatsDoesNotThrow() {
        bitmapPool.logStats()
    }

    @Test
    fun testConcurrentAcquireRelease() {
        for (i in 0 until 5) {
            val bitmap = bitmapPool.acquire(50, 50)
            bitmapPool.release(bitmap)
        }

        val stats = bitmapPool.getStats()
        assertTrue(stats.borrowedCount >= 5)
        assertTrue(stats.returnedCount >= 5)
    }

    @Test
    fun testPooledBitmapReuseCount() {
        val bitmap1 = bitmapPool.acquire(100, 100)
        bitmapPool.release(bitmap1)

        val bitmap2 = bitmapPool.acquire(100, 100)
        bitmapPool.release(bitmap2)

        val bitmap3 = bitmapPool.acquire(100, 100)
        bitmapPool.release(bitmap3)

        val stats = bitmapPool.getStats()
        assertTrue(stats.borrowedCount >= 3)
    }

    @Test
    fun testSizeKeyEquality() {
        val bitmap1 = createMockBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val bitmap2 = createMockBitmap(100, 100, Bitmap.Config.ARGB_8888)

        bitmapPool.release(bitmap1)
        bitmapPool.release(bitmap2)

        val stats = bitmapPool.getStats()
        assertEquals(1, stats.poolCount)
    }
}
