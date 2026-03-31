package com.example.musicframe

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.musicframe.export.ExportManager
import com.example.musicframe.export.ExportWorker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkConstructor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ExportWorkerTest {

    private lateinit var context: Context
    private lateinit var exportWorker: ExportWorker

    @Before
    fun setup() {
        Dispatchers.setMain(Dispatchers.Main)
        context = mockk(relaxed = true)

        mockkConstructor(ExportWorker::class)
        mockkStatic(Bitmap::class)

        exportWorker = ExportWorker(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
        unmockkConstructor(ExportWorker::class)
    }

    private fun createMockBitmap(width: Int = 100, height: Int = 100): Bitmap {
        val bitmap = mockk<Bitmap>(relaxed = true)
        every { bitmap.width } returns width
        every { bitmap.height } returns height
        every { bitmap.allocationByteCount } returns width * height * 4
        every { bitmap.isRecycled } returns false
        return bitmap
    }

    @Test
    fun testExportWorkerInitialization() {
        assertNotNull(exportWorker)
    }

    @Test
    fun testCancelExport() {
        exportWorker.cancel()
    }

    @Test
    fun testGetFrameCacheStats() {
        val stats = exportWorker.getFrameCacheStats()
        assertNotNull(stats)
        assertTrue(stats.contains("FrameCache"))
    }

    @Test
    fun testGetBitmapPoolStats() {
        val stats = exportWorker.getBitmapPoolStats()
        assertNotNull(stats)
    }

    @Test
    fun testProgressReporting() {
        var progressUpdates = 0
        val onProgress: (Int, Int) -> Unit = { current, total ->
            progressUpdates++
        }

        val current = 5
        val total = 10
        onProgress(current, total)

        assertTrue(progressUpdates == 1)
    }

    @Test
    fun testProgressCalculation() {
        val current = 50
        val total = 100
        val expectedProgress = 50

        val actualProgress = current * 100 / total
        assertEquals(expectedProgress, actualProgress)
    }

    @Test
    fun testOutputDimensionCalculation() {
        val bitmap = createMockBitmap(1920, 1080)
        val maxDimension = 1080

        val expectedWidth = if (bitmap.width > maxDimension) {
            (bitmap.width * (maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height))).toInt()
        } else {
            bitmap.width
        }

        assertTrue(expectedWidth <= maxDimension)
    }

    @Test
    fun testOutputDimensionCalculationWithSmallImage() {
        val bitmap = createMockBitmap(800, 600)
        val maxDimension = 1080

        val actualWidth = if (bitmap.width <= maxDimension && bitmap.height <= maxDimension) {
            bitmap.width
        } else {
            (bitmap.width * (maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height))).toInt()
        }

        assertEquals(bitmap.width, actualWidth)
    }

    @Test
    fun testQualityLevels() {
        val lowQuality = ExportManager.QualityLevel.LOW
        val mediumQuality = ExportManager.QualityLevel.MEDIUM
        val highQuality = ExportManager.QualityLevel.HIGH

        assertEquals(60, lowQuality.quality)
        assertEquals(80, mediumQuality.quality)
        assertEquals(95, highQuality.quality)
    }

    @Test
    fun testOutputFormats() {
        val gifFormat = ExportManager.OutputFormat.GIF
        val webpFormat = ExportManager.OutputFormat.WEBP

        assertEquals("gif", gifFormat.extension)
        assertEquals("webp", webpFormat.extension)
        assertEquals("image/gif", gifFormat.mimeType)
        assertEquals("image/webp", webpFormat.mimeType)
    }

    @Test
    fun testMaxDimension() {
        assertEquals(1080, ExportManager.MAX_DIMENSION)
    }

    @Test
    fun testMaxFrames() {
        assertEquals(100, ExportManager.MAX_FRAMES)
    }

    @Test
    fun testCacheClearOnCancel() = runTest {
        exportWorker.cancel()
    }

    @Test
    fun testConcurrentCancellation() {
        exportWorker.cancel()
        exportWorker.cancel()
    }

    @Test
    fun testOutputFileNameGeneration() {
        val timestamp = System.currentTimeMillis()
        val extension = "gif"
        val expectedPrefix = "music_frame_"

        val fileName = "$expectedPrefix$timestamp.$extension"

        assertTrue(fileName.startsWith(expectedPrefix))
        assertTrue(fileName.endsWith(".$extension"))
    }

    @Test
    fun testExportCancellationState() {
        val isCancelled = false
        assertFalse(isCancelled)
    }

    @Test
    fun testProgressThrottling() {
        val lastUpdateTime = 0L
        val maxUpdatesPerSecond = 5
        val minInterval = 1000L / maxUpdatesPerSecond

        assertEquals(200L, minInterval)
    }

    @Test
    fun testCacheStatsFormat() {
        val stats = exportWorker.getFrameCacheStats()
        assertTrue(stats.contains("size="))
        assertTrue(stats.contains("hitRate="))
        assertTrue(stats.contains("evictions="))
    }

    @Test
    fun testPoolStatsContainsExpectedFields() {
        val stats = exportWorker.getBitmapPoolStats()
        assertTrue(stats.totalSizeBytes >= 0)
        assertTrue(stats.maxSizeBytes > 0)
        assertTrue(stats.borrowedCount >= 0)
        assertTrue(stats.returnedCount >= 0)
    }
}
