package com.example.musicframe.image

data class FrameConfig(
    val frameMode: FrameMode = FrameMode.PREMIUM_LEICA,
    val useLightFrame: Boolean = false,
    val customFrameColorHex: String = "",
    val showHeadphoneInfo: Boolean = true,
    val headphoneTextColor: Int? = null,
    val typeface: android.graphics.Typeface? = null,
    val photoMetadata: PhotoMetadata? = null
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
        
        // 浅色/深色模式
        return if (useLightFrame) {
            enhanceColorSaturation(dominantColor, 1.15f)
        } else {
            darkenColor(dominantColor, if (forMusicFlow) 0.15f else 0.3f)
        }
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
}

enum class FrameMode {
    PREMIUM_LEICA,
    CUSTOM_LEICA,
    MUSIC_FLOW,
    MUSIC_SOLID
}

const val MIN_TEXT_SCALE = 0.6f
const val MAX_TEXT_SCALE = 1.6f
