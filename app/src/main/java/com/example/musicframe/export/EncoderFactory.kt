package com.example.musicframe.export

import android.os.Build
import android.util.Log

/**
 * 编码器工厂类
 * 根据格式和 API 版本选择合适的编码器
 */
object EncoderFactory {

    private const val TAG = "EncoderFactory"

    /**
     * 根据格式创建编码器
     * @param format 输出格式（"gif" 或 "webp"）
     * @param minSdk 最小 API 版本（用于兼容性检查）
     * @return 对应的编码器实例
     * @throws IllegalArgumentException 当格式不支持时
     * @throws UnsupportedOperationException 当 API 版本不满足要求时
     */
    fun createEncoder(format: String, minSdk: Int = Build.VERSION.SDK_INT): AnimatedImageEncoder {
        Log.d(TAG, "Creating encoder for format: $format, minSdk: $minSdk, current API: ${Build.VERSION.SDK_INT}")

        return when (format.lowercase()) {
            "gif" -> {
                Log.d(TAG, "Creating GifEncoderImpl")
                GifEncoderImpl()
            }
            "webp" -> {
                if (minSdk >= Build.VERSION_CODES.O) { // API 27+
                    Log.d(TAG, "Creating AnimatedWebPEncoder (API ${Build.VERSION.SDK_INT})")
                    AnimatedWebPEncoder()
                } else {
                    val errorMsg = "Animated WebP requires API 27+, but minSdk is $minSdk"
                    Log.e(TAG, errorMsg)
                    throw UnsupportedOperationException(errorMsg)
                }
            }
            "image/gif" -> {
                Log.d(TAG, "Creating GifEncoderImpl (MIME type format)")
                GifEncoderImpl()
            }
            "image/webp" -> {
                if (minSdk >= Build.VERSION_CODES.O) {
                    Log.d(TAG, "Creating AnimatedWebPEncoder (MIME type format)")
                    AnimatedWebPEncoder()
                } else {
                    val errorMsg = "Animated WebP requires API 27+, but minSdk is $minSdk"
                    Log.e(TAG, errorMsg)
                    throw UnsupportedOperationException(errorMsg)
                }
            }
            else -> {
                val errorMsg = "Unsupported format: $format. Supported formats: gif, webp"
                Log.e(TAG, errorMsg)
                throw IllegalArgumentException(errorMsg)
            }
        }
    }

    /**
     * 检查格式是否支持
     * @param format 输出格式
     * @param minSdk 最小 API 版本
     * @return true 如果支持，false 否则
     */
    fun isFormatSupported(format: String, minSdk: Int = Build.VERSION.SDK_INT): Boolean {
        return when (format.lowercase()) {
            "gif", "image/gif" -> true
            "webp", "image/webp" -> minSdk >= Build.VERSION_CODES.O
            else -> false
        }
    }

    /**
     * 获取支持的格式列表
     * @param minSdk 最小 API 版本
     * @return 支持的格式列表
     */
    fun getSupportedFormats(minSdk: Int = Build.VERSION.SDK_INT): List<String> {
        val formats = mutableListOf("gif", "image/gif")
        if (minSdk >= Build.VERSION_CODES.O) {
            formats.add("webp")
            formats.add("image/webp")
        }
        return formats
    }
}
