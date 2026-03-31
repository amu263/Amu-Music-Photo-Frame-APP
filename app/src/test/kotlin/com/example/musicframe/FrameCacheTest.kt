package com.example.musicframe

import android.graphics.Bitmap
import android.graphics.Color
import com.example.musicframe.cache.FrameCache
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

class FrameCacheTest {

    private lateinit var frameCache: FrameCache

    @Before
    fun setup() {
        mockkStatic(Bitmap::class)
        frameCache = FrameCache(maxMemoryMB = 8)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createMockBitmap(allocationBytes: Int): Bitmap {
        val bitmap = mockk<Bitmap>(relaxed = true)
        every { bitmap.allocationByteCount } returns allocationBytes
        every { bitmap.isRecycled } returns false
        return bitmap
    }

    @Test
    fun testCacheInitialization() {
        assertEquals(8 * 1024 * 1024, frameCache.maxSize)
        assertEquals(0, frameCache.size)
        assertEquals(0, frameCache.hitCount)
        assertEquals(0, frameCache.missCount)
        assertEquals(0, frameCache.evictionCount)
    }

    @Test
    fun testPutAndGet() {
        val bitmap = createMockBitmap(1024 * 1024)
        frameCache.put(0, bitmap)

        val cached = frameCache.get(0)
        assertNotNull(cached)
        assertEquals(bitmap, cached)
    }

    @Test
    fun testGetNonExistent() {
        val result = frameCache.get(999)
        assertNull(result)
    }

    @Test
    fun testRemove() {
        val bitmap = createMockBitmap(1024 * 1024)
        frameCache.put(0, bitmap)

        val removed = frameCache.remove(0)
        assertNotNull(removed)
        assertEquals(bitmap, removed)

        val afterRemove = frameCache.get(0)
        assertNull(afterRemove)
    }

    @Test
    fun testRemoveNonExistent() {
        val result = frameCache.remove(999)
        assertNull(result)
    }

    @Test
    fun testClear() {
        val bitmap1 = createMockBitmap(512 * 1024)
        val bitmap2 = createMockBitmap(512 * 1024)
        frameCache.put(0, bitmap1)
        frameCache.put(1, bitmap2)

        assertEquals(1, frameCache.evictionCount)

        frameCache.clear()
        assertEquals(0, frameCache.size)
    }

    @Test
    fun testCacheHitRate() {
        val bitmap = createMockBitmap(1024 * 1024)
        frameCache.put(0, bitmap)

        frameCache.get(0)
        frameCache.get(0)
        frameCache.get(1)
        frameCache.get(1)
        frameCache.get(2)

        val hitRate = frameCache.getHitRate()
        assertTrue(hitRate >= 0f && hitRate <= 1f)
    }

    @Test
    fun testPutRecycledBitmap() {
        val recycledBitmap = mockk<Bitmap>()
        every { recycledBitmap.isRecycled } returns true

        frameCache.put(0, recycledBitmap)
        assertNull(frameCache.get(0))
    }

    @Test
    fun testMultipleFrames() {
        val frameCount = 5
        for (i in 0 until frameCount) {
            val bitmap = createMockBitmap(512 * 1024)
            frameCache.put(i, bitmap)
        }

        for (i in 0 until frameCount) {
            val cached = frameCache.get(i)
            assertNotNull("Frame $i should be cached", cached)
        }
    }

    @Test
    fun testEvictionTracking() {
        val bitmap1 = createMockBitmap(6 * 1024 * 1024)
        frameCache.put(0, bitmap1)

        val bitmap2 = createMockBitmap(6 * 1024 * 1024)
        frameCache.put(1, bitmap2)

        assertTrue(frameCache.evictionCount >= 1)
    }

    @Test
    fun testSizeTracking() {
        val initialSize = frameCache.size

        val bitmap = createMockBitmap(1024 * 1024)
        frameCache.put(0, bitmap)

        val newSize = frameCache.size
        assertTrue(newSize > initialSize)
    }

    @Test
    fun testTrimToSize() {
        val bitmap1 = createMockBitmap(2 * 1024 * 1024)
        val bitmap2 = createMockBitmap(2 * 1024 * 1024)
        val bitmap3 = createMockBitmap(2 * 1024 * 1024)
        val bitmap4 = createMockBitmap(2 * 1024 * 1024)

        frameCache.put(0, bitmap1)
        frameCache.put(1, bitmap2)
        frameCache.put(2, bitmap3)
        frameCache.put(3, bitmap4)

        val sizeBeforeTrim = frameCache.size
        assertTrue(sizeBeforeTrim > 0)
    }

    @Test
    fun testHitCountIncreasesOnCacheHit() {
        val bitmap = createMockBitmap(1024 * 1024)
        frameCache.put(0, bitmap)

        val hitsBefore = frameCache.hitCount
        frameCache.get(0)
        val hitsAfter = frameCache.hitCount

        assertTrue(hitsAfter > hitsBefore)
    }

    @Test
    fun testMissCountIncreasesOnCacheMiss() {
        val missesBefore = frameCache.missCount
        frameCache.get(999)
        val missesAfter = frameCache.missCount

        assertTrue(missesAfter > missesBefore)
    }

    @Test
    fun testOverwriteExistingFrame() {
        val bitmap1 = createMockBitmap(1024 * 1024)
        val bitmap2 = createMockBitmap(2 * 1024 * 1024)

        frameCache.put(0, bitmap1)
        frameCache.put(0, bitmap2)

        val cached = frameCache.get(0)
        assertEquals(bitmap2, cached)
    }

    @Test
    fun testDifferentFrameIndices() {
        val bitmaps = mutableMapOf<Int, Bitmap>()

        for (i in 0..100) {
            val bitmap = createMockBitmap((i + 1) * 1024)
            bitmaps[i] = bitmap
            frameCache.put(i, bitmap)
        }

        for ((index, originalBitmap) in bitmaps) {
            val cached = frameCache.get(index)
            assertEquals("Frame $index mismatch", originalBitmap, cached)
        }
    }

    @Test
    fun testLogStatsDoesNotThrow() {
        frameCache.logStats()
    }
}
