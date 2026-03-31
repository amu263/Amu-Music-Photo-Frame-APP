package com.example.musicframe.export

import com.example.musicframe.image.AnimatedFrame
import java.io.File

/**
 * 动态图片编码器接口
 * 支持将多帧 Bitmap 编码为 GIF 或 Animated WebP 格式
 */
interface AnimatedImageEncoder {
    /**
     * 编码动态图片
     * @param frames 帧列表
     * @param outputWidth 输出宽度
     * @param outputHeight 输出高度
     * @param quality 质量 (0-100)
     * @param outputPath 输出文件路径
     * @param progressCallback 进度回调 (当前帧/总帧数)
     * @return 导出结果（文件路径、大小、时长等）
     */
    suspend fun encode(
        frames: List<AnimatedFrame>,
        outputWidth: Int,
        outputHeight: Int,
        quality: Int = 85,
        outputPath: File,
        progressCallback: ((Int, Int) -> Unit)? = null
    ): EncodeResult

    /**
     * 支持的格式
     */
    val format: String
}

/**
 * 导出结果数据类
 * @param outputFile 输出文件
 * @param fileSize 文件大小（字节）
 * @param frameCount 帧数
 * @param totalDurationMs 总时长（毫秒）
 * @param format 格式 MIME 类型
 */
data class EncodeResult(
    val outputFile: File,
    val fileSize: Long,
    val frameCount: Int,
    val totalDurationMs: Long,
    val format: String
)

/**
 * 编码配置数据类
 * @param format 输出格式（"gif" 或 "webp"）
 * @param quality 质量 (0-100)，默认 85
 * @param maxWidth 最大宽度限制，默认 1080
 * @param maxHeight 最大高度限制，默认 1080
 * @param maxFrames 最大帧数限制，默认 100
 */
data class EncodeConfig(
    val format: String = "gif",
    val quality: Int = 85,
    val maxWidth: Int = 1080,
    val maxHeight: Int = 1080,
    val maxFrames: Int = 100
) {
    init {
        require(quality in 0..100) { "Quality must be between 0 and 100" }
        require(maxWidth > 0 && maxHeight > 0) { "Dimensions must be positive" }
        require(maxFrames > 0) { "Max frames must be positive" }
    }
}

// 注意：AnimatedFrame 数据类已在 FrameComposer.kt 中定义
// 此处仅为文档引用，实际使用请导入：
// import com.example.musicframe.image.AnimatedFrame
