package com.example.musicframe.image

import com.example.musicframe.domain.model.FrameColorMode

data class FrameConfig(
    val frameMode: FrameMode = FrameMode.PREMIUM_LEICA,
    val frameColorMode: FrameColorMode = FrameColorMode.DARK,
    val customFrameColorHex: String = "",
    val showHeadphoneInfo: Boolean = true,
    val headphoneTextColor: Int? = null,
    val typeface: android.graphics.Typeface? = null,
    val photoMetadata: PhotoMetadata? = null,
    val useDarkBackground: Boolean = false,
    // 星座运势模式 - 用户生日
    val userBirthdayMonth: Int = 0,
    val userBirthdayDay: Int = 0
) {
    fun headphoneColor(default: Int): Int = headphoneTextColor ?: default
    
    fun getFrameColor(dominantColor: Int, forMusicFlow: Boolean = false): Int {
        // 自定义颜色优先
        if (customFrameColorHex.isNotBlank()) {
            return try {
                android.graphics.Color.parseColor("#$customFrameColorHex")
            } catch (e: Exception) {
                dominantColor
            }
        }
        
        // 原色/深色/浅色模式
        return when (frameColorMode) {
            FrameColorMode.ORIGINAL -> dominantColor
            FrameColorMode.LIGHT -> enhanceColorSaturation(dominantColor, 1.15f)
            FrameColorMode.DARK -> darkenColor(dominantColor, if (forMusicFlow) 0.15f else 0.3f)
        }
    }
    
    fun getStripeColors(dominantColor: Int): Pair<Int, Int> {
        // 高级竖纹模式：返回基色和交替色，根据深色/浅色模式动态调整
        // 浅色模式：原色 + 强对比度的浅色交替；深色模式：原色 + 强对比度的深色交替
        val baseColor = dominantColor  // 始终使用封面原色作为基色
        val alternateColor = when (frameColorMode) {
            FrameColorMode.ORIGINAL -> darkenColor(dominantColor, 0.3f)
            FrameColorMode.LIGHT -> lightenColor(dominantColor, 0.4f)  // 提亮 40%，增强对比度
            FrameColorMode.DARK -> darkenColor(dominantColor, 0.5f)  // 变暗 50%，增强对比度
        }
        return Pair(baseColor, alternateColor)
    }
    
    private fun enhanceColorSaturation(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * factor).coerceIn(0f, 1f)
        return android.graphics.Color.HSVToColor(hsv)
    }
    
    private fun darkenColor(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * (1f - factor)).coerceIn(0f, 1f)
        return android.graphics.Color.HSVToColor(hsv)
    }
    
    private fun lightenColor(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] + (1f - hsv[2]) * factor).coerceIn(0f, 1f)
        return android.graphics.Color.HSVToColor(hsv)
    }
}

enum class FrameMode {
    PREMIUM_LEICA,
    CUSTOM_LEICA,
    MUSIC_FLOW,
    MUSIC_SOLID,
    ZODIAC_HOROSCOPE
}

const val MIN_TEXT_SCALE = 0.6f
const val MAX_TEXT_SCALE = 1.6f
