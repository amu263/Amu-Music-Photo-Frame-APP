package com.example.musicframe.image

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build

/**
 * 动态图片解码器
 * 用于从 GIF 或 Animated WebP 中提取帧
 *
 * 注意：当前实现为简化版本，仅提取首帧用于预览
 * 完整的逐帧解码功能将在后续版本中实现
 */
class AnimatedFrameDecoder(private val context: Context) {

    /**
     * 解码动态图片，提取所有帧
     * @param uri 动态图片的 URI
     * @param maxFrames 最大帧数限制（防止内存溢出），默认 100 帧
     * @return 帧列表
     */
    fun decodeAnimatedImage(
        uri: Uri,
        maxFrames: Int = 100
    ): List<AnimatedFrame> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val frames = mutableListOf<AnimatedFrame>()
                val imageSource = ImageDecoder.createSource(context.contentResolver, uri)

                // 当前简化实现：只提取第一帧
                // TODO: 实现完整的逐帧解码
                val bitmap = ImageDecoder.decodeBitmap(imageSource) { decoder, imageInfo, _ ->
                    val srcWidth = imageInfo.size.width
                    val srcHeight = imageInfo.size.height
                    decoder.setTargetSize(srcWidth, srcHeight)
                }

                // 将第一帧添加到列表，使用默认持续时间 100ms
                frames.add(AnimatedFrame(bitmap, 100))

                frames
            } else {
                // API 28 以下，返回空列表
                emptyList()
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to decode animated image: ${e.message}", e)
        }
    }

    /**
     * 获取动态图片信息（不提取帧，只获取元数据）
     */
    fun getAnimatedImageInfo(uri: Uri): AnimatedImageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val imageSource = ImageDecoder.createSource(context.contentResolver, uri)
                var width = 0
                var height = 0

                ImageDecoder.decodeBitmap(imageSource) { decoder, imageInfo, _ ->
                    width = imageInfo.size.width
                    height = imageInfo.size.height
                }

                if (width > 0 && height > 0) {
                    // 简化版本：只返回尺寸信息，帧数和时长设为默认值
                    AnimatedImageInfo(width, height, 1, 100L)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 动态图片信息
     */
    data class AnimatedImageInfo(
        val width: Int,
        val height: Int,
        val frameCount: Int,
        val durationMs: Long
    )
}
