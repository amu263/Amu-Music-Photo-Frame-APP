package com.example.musicframe.image

data class FrameConfig(
    val frameMode: FrameMode = FrameMode.PREMIUM_LEICA,
    val showHeadphoneInfo: Boolean = true,
    val headphoneTextColor: Int? = null,
    val typeface: android.graphics.Typeface? = null,
    val photoMetadata: PhotoMetadata? = null
) {
    fun headphoneColor(default: Int): Int = headphoneTextColor ?: default
}

enum class FrameMode {
    PREMIUM_LEICA,
    CUSTOM_LEICA,
    MUSIC_FLOW,
    MUSIC_SOLID
}

const val MIN_TEXT_SCALE = 0.6f
const val MAX_TEXT_SCALE = 1.6f
