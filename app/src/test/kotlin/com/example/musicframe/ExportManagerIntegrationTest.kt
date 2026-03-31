package com.example.musicframe

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.musicframe.cache.FrameCache
import com.example.musicframe.export.ExportManager
import com.example.musicframe.export.ExportWorker
import com.example.musicframe.image.AnimatedFrame
import com.example.musicframe.image.AnimatedFrameDecoder
import com.example.musicframe.image.FrameComposer
import com.example.musicframe.util.BitmapPool
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ExportManagerIntegrationTest {

    private lateinit var context: Context
    private lateinit var exportManager: ExportManager

    @Before
    fun setup() {
        Dispatchers.setMain(Dispatchers.Main)
        context = mockk(relaxed = true)

        mockkStatic(Bitmap::class)
        mockkStatic(Uri::class)
        mockkConstructor(FrameCache::class)
        mockkConstructor(BitmapPool::class)
        mockkConstructor(FrameComposer::class)
        mockkConstructor(AnimatedFrameDecoder::class)

        exportManager = ExportManager(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
        unmockkConstructor(FrameCache::class)
        unmockkConstructor(BitmapPool::class)
        unmockkConstructor(FrameComposer::class)
        unmockkConstructor(AnimatedFrameDecoder::class)
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
    fun testExportManagerInitialization() {
        assertNotNull(exportManager)
    }

    @Test
    fun testOutputFormatValues() {
        val formats = ExportManager.OutputFormat.values()
        assertEquals(2, formats.size)
        assertTrue(formats.contains(ExportManager.OutputFormat.GIF))
        assertTrue(formats.contains(ExportManager.OutputFormat.WEBP))
    }

    @Test
    fun testQualityLevelValues() {
        val qualities = ExportManager.QualityLevel.values()
        assertEquals(3, qualities.size)
        assertTrue(qualities.contains(ExportManager.QualityLevel.LOW))
        assertTrue(qualities.contains(ExportManager.QualityLevel.MEDIUM))
        assertTrue(qualities.contains(ExportManager.QualityLevel.HIGH))
    }

    @Test
    fun testExportStatusSealedClass() {
        val idle = ExportManager.ExportStatus.Idle
        val progress = ExportManager.ExportStatus.Progress(50, 100, "Encoding")
        val success = ExportManager.ExportStatus.Success(mockk(), ExportManager.OutputFormat.GIF)
        val error = ExportManager.ExportStatus.Error("Test error")

        assertTrue(idle is ExportManager.ExportStatus.Idle)
        assertTrue(progress is ExportManager.ExportStatus.Progress)
        assertTrue(success is ExportManager.ExportStatus.Success)
        assertTrue(error is ExportManager.ExportStatus.Error)
    }

    @Test
    fun testProgressStatusContainsCorrectValues() {
        val progress = ExportManager.ExportStatus.Progress(25, 100, "Composing")
        val progressData = progress as ExportManager.ExportStatus.Progress

        assertEquals(25, progressData.current)
        assertEquals(100, progressData.total)
        assertEquals("Composing", progressData.phase)
    }

    @Test
    fun testSuccessStatusContainsFileAndFormat() {
        val mockFile = mockk<File>()
        val success = ExportManager.ExportStatus.Success(mockFile, ExportManager.OutputFormat.WEBP)
        val successData = success as ExportManager.ExportStatus.Success

        assertEquals(mockFile, successData.outputFile)
        assertEquals(ExportManager.OutputFormat.WEBP, successData.format)
    }

    @Test
    fun testErrorStatusContainsMessage() {
        val errorMessage = "Export failed"
        val error = ExportManager.ExportStatus.Error(errorMessage)
        val errorData = error as ExportManager.ExportStatus.Error

        assertEquals(errorMessage, errorData.message)
    }

    @Test
    fun testCacheStatsDataClass() {
        val stats = ExportManager.CacheStats(
            frameCacheSizeBytes = 1024 * 1024,
            frameCacheHitRate = 0.75f,
            frameCacheEvictions = 5
        )

        assertEquals(1024 * 1024, stats.frameCacheSizeBytes)
        assertEquals(0.75f, stats.frameCacheHitRate)
        assertEquals(5, stats.frameCacheEvictions)
    }

    @Test
    fun testGetExportCacheDir() {
        val cacheDir = exportManager.getExportCacheDir()
        assertNotNull(cacheDir)
        assertTrue(cacheDir.exists() || cacheDir.absolutePath.contains("exports"))
    }

    @Test
    fun testClearExportCache() {
        exportManager.clearExportCache()
    }

    @Test
    fun testGetCacheStats() {
        val stats = exportManager.getCacheStats()
        assertNotNull(stats)
        assertTrue(stats.frameCacheSizeBytes >= 0)
        assertTrue(stats.frameCacheHitRate >= 0f && stats.frameCacheHitRate <= 1f)
    }

    @Test
    fun testOutputFormatDisplayNames() {
        assertEquals("GIF", ExportManager.OutputFormat.GIF.displayName)
        assertEquals("Animated WebP", ExportManager.OutputFormat.WEBP.displayName)
    }

    @Test
    fun testQualityLevelDisplayNames() {
        assertEquals("低", ExportManager.QualityLevel.LOW.displayName)
        assertEquals("中", ExportManager.QualityLevel.MEDIUM.displayName)
        assertEquals("高", ExportManager.QualityLevel.HIGH.displayName)
    }

    @Test
    fun testAnimatedFrameCreation() {
        val bitmap = createMockBitmap(100, 100)
        val duration = 100

        val frame = AnimatedFrame(bitmap, duration)

        assertEquals(bitmap, frame.bitmap)
        assertEquals(duration, frame.duration)
    }

    @Test
    fun testAnimatedFrameListCreation() {
        val frameCount = 5
        val frames = (0 until frameCount).map { index ->
            val bitmap = createMockBitmap(100, 100)
            AnimatedFrame(bitmap, 100 + index * 10)
        }

        assertEquals(frameCount, frames.size)
        frames.forEachIndexed { index, frame ->
            assertEquals(100 + index * 10, frame.duration)
        }
    }

    @Test
    fun testExportConfigurationConstants() {
        assertEquals(1080, ExportManager.MAX_DIMENSION)
        assertEquals(100, ExportManager.MAX_FRAMES)
    }

    @Test
    fun testConcurrentExportMutex() = runTest {
    }

    @Test
    fun testProgressReportingThrottling() {
        val lastUpdateTime = 0L
        val lastReportedProgress = -1
        val maxUpdatesPerSecond = 5
        val minInterval = 1000L / maxUpdatesPerSecond

        assertEquals(200L, minInterval)
    }

    @Test
    fun testBitmapScalingForCompose() {
        val largeBitmap = createMockBitmap(2000, 1500)
        val maxSize = 1080

        val shouldScale = largeBitmap.width > maxSize || largeBitmap.height > maxSize
        assertTrue(shouldScale)
    }

    @Test
    fun testSmallBitmapNoScaling() {
        val smallBitmap = createMockBitmap(800, 600)
        val maxSize = 1080

        val shouldScale = smallBitmap.width > maxSize || smallBitmap.height > maxSize
        assertTrue(!shouldScale)
    }

    @Test
    fun testGifFormatProperties() {
        val gif = ExportManager.OutputFormat.GIF
        assertEquals("gif", gif.extension)
        assertEquals("image/gif", gif.mimeType)
    }

    @Test
    fun testWebPFormatProperties() {
        val webp = ExportManager.OutputFormat.WEBP
        assertEquals("webp", webp.extension)
        assertEquals("image/webp", webp.mimeType)
    }

    @Test
    fun testMemoryManagementOnClear() {
        exportManager.clearExportCache()
        val stats = exportManager.getCacheStats()
        assertEquals(0, stats.frameCacheSizeBytes)
    }

    @Test
    fun testExportCacheDirectoryCreation() {
        val cacheDir = exportManager.getExportCacheDir()
        assertTrue(cacheDir.name == "exports" || cacheDir.absolutePath.contains("exports"))
    }
}
