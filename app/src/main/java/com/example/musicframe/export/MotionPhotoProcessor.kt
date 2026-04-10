package com.example.musicframe.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Motion Photo 处理器
 * 负责：
 * 1. 从 Motion Photo 提取视频片段
 * 2. 对视频每一帧添加相框水印并重新编码
 * 3. 重新组装 Motion Photo
 */
class MotionPhotoProcessor(private val context: Context) {

    companion object {
        private const val TAG = "MotionPhotoProcessor"
        private const val TIMEOUT_US = 10000L
        private const val MIME_TYPE = "video/avc" // H.264
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL = 1
        private const val BIT_RATE = 10_000_000 // 10 Mbps
    }

    /**
     * 处理动态相框导出
     */
    suspend fun processMotionPhoto(
        sourceUri: android.net.Uri,
        videoOffset: Long,
        framedBitmap: Bitmap,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始处理 Motion Photo: videoOffset=$videoOffset")

        // 1. 提取原始视频片段到临时文件
        val tempVideoFile = File(context.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
        extractVideoSegment(sourceUri, videoOffset, tempVideoFile)

        // 2. 创建带水印的临时视频
        val watermarkedVideoFile = File(context.cacheDir, "watermarked_video_${System.currentTimeMillis()}.mp4")
        processVideoWithOverlay(tempVideoFile, framedBitmap, watermarkedVideoFile)

        // 3. 组装新的 Motion Photo
        val outputFile = File(context.cacheDir, "$outputFileName.jpg")
        assembleMotionPhoto(framedBitmap, watermarkedVideoFile, outputFile)

        // 4. 清理临时文件
        tempVideoFile.delete()
        watermarkedVideoFile.delete()

        Log.d(TAG, "Motion Photo 处理完成: ${outputFile.absolutePath}")
        outputFile
    }

    /**
     * 从 Motion Photo 中提取视频片段
     */
    private fun extractVideoSegment(sourceUri: android.net.Uri, videoOffset: Long, outputFile: File) {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            val skipped = input.skip(videoOffset)
            Log.d(TAG, "跳过 $skipped 字节")
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }
        Log.d(TAG, "视频片段提取完成: ${outputFile.absolutePath}, 大小: ${outputFile.length()} bytes")
    }

    /**
     * 处理视频，添加相框水印
     * 策略：解码每一帧 -> 绘制水印 -> 编码为新视频
     */
    private fun processVideoWithOverlay(inputFile: File, frameBitmap: Bitmap, outputFile: File) {
        Log.d(TAG, "开始处理视频: ${inputFile.absolutePath}")

        // 获取输入视频信息
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(inputFile.absolutePath)

        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0

        Log.d(TAG, "视频信息: ${width}x${height}, 时长: ${duration}ms")

        if (width == 0 || height == 0) {
            throw IllegalStateException("无法获取视频尺寸")
        }

        // 创建编码器
        val outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        val encoder = MediaCodec.createEncoderByType(MIME_TYPE)
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val encoderSurface = encoder.createInputSurface()
        encoder.start()

        // 创建 muxer
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        // 解码器
        val extractor = MediaExtractor()
        extractor.setDataSource(inputFile.absolutePath)

        // 找到视频轨道
        var videoTrack = -1
        var inputFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) {
                videoTrack = i
                inputFormat = format
                break
            }
        }

        if (videoTrack == -1 || inputFormat == null) {
            throw IllegalStateException("找不到视频轨道")
        }

        extractor.selectTrack(videoTrack)

        val decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(inputFormat, encoderSurface, null, 0)
        decoder.start()

        // 计算缩放比例以匹配视频尺寸（保持比例，全覆盖）
        val scaleX = width.toFloat() / frameBitmap.width
        val scaleY = height.toFloat() / frameBitmap.height
        val scale = maxOf(scaleX, scaleY)
        val scaledWidth = (frameBitmap.width * scale).toInt()
        val scaledHeight = (frameBitmap.height * scale).toInt()
        val offsetX = (width - scaledWidth) / 2
        val offsetY = (height - scaledHeight) / 2
        val scaledFrame = Bitmap.createScaledBitmap(frameBitmap, scaledWidth, scaledHeight, true)

        var inputDone = false
        var decoderDone = false
        var encoderDone = false

        try {
            while (!encoderDone) {
                // 填充解码器输入
                if (!inputDone) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        val sampleSize = extractor.readSampleData(inputBuffer!!, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // 获取解码器输出
                if (!decoderDone) {
                    val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    when {
                        outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // 等待
                        }
                        outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            // 解码器格式改变，忽略
                        }
                        outputBufferIndex >= 0 -> {
                            val render = bufferInfo.size != 0
                            decoder.releaseOutputBuffer(outputBufferIndex, render)

                            if (render && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                                // 解码后的帧已直接渲染到 encoderSurface
                                // 现在在 encoderSurface 上绘制水印
                                val canvas = encoderSurface.lockCanvas(null)
                                canvas.drawBitmap(scaledFrame, offsetX.toFloat(), offsetY.toFloat(), Paint())
                                encoderSurface.unlockCanvasAndPost(canvas)

                                // 排放编码器输出
                                drainEncoder(encoder, bufferInfo, muxer, videoTrackIndex, muxerStarted, true)
                                
                                // 如果 muxer 还没启动，获取编码器输出格式并启动
                                if (!muxerStarted) {
                                    val newFormat = encoder.outputFormat
                                    videoTrackIndex = muxer.addTrack(newFormat)
                                    muxer.start()
                                    muxerStarted = true
                                    Log.d(TAG, "Muxer 已启动，轨道索引: $videoTrackIndex")
                                }
                            }

                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                decoderDone = true
                            }
                        }
                    }
                }

                // 排放编码器剩余输出
                if (decoderDone && !encoderDone) {
                    val result = drainEncoder(encoder, bufferInfo, muxer, videoTrackIndex, muxerStarted, false, endOfStream = true)
                    if (result) {
                        encoderDone = true
                    }
                }
            }
        } finally {
            decoder.stop()
            decoder.release()
            encoder.stop()
            encoder.release()
            encoderSurface.release()
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
            extractor.release()
            retriever.release()
            scaledFrame.recycle()
        }

        Log.d(TAG, "视频处理完成: ${outputFile.absolutePath}")
    }

    /**
     * 排放编码器输出
     */
    private fun drainEncoder(
        encoder: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        trackIndex: Int,
        muxerStarted: Boolean,
        render: Boolean,
        endOfStream: Boolean = false
    ): Boolean {
        if (endOfStream) {
            encoder.signalEndOfInputStream()
        }

        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return false
                }
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // 忽略
                }
                outputBufferIndex >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(outputBufferIndex)
                    if (encodedData != null && bufferInfo.size != 0 && muxerStarted) {
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        return true
                    }
                }
            }
        }
    }

    /**
     * 组装新的 Motion Photo (JPEG 容器 + MP4 视频)
     */
    private fun assembleMotionPhoto(framedBitmap: Bitmap, videoFile: File, outputFile: File) {
        Log.d(TAG, "组装 Motion Photo: framedBitmap=${framedBitmap.width}x${framedBitmap.height}")

        // 将带水印的帧编码为 JPEG
        val frameBytes = ByteArrayOutputStream().use { output ->
            framedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
            output.toByteArray()
        }

        // 读取视频数据
        val videoBytes = videoFile.readBytes()

        // 组装: JPEG + video data
        FileOutputStream(outputFile).use { output ->
            output.write(frameBytes)
            output.write(videoBytes)
        }

        Log.d(TAG, "Motion Photo 组装完成: ${outputFile.absolutePath}, 大小: ${outputFile.length()} bytes")
    }

    private fun maxOf(a: Float, b: Float): Float = if (a > b) a else b
}
