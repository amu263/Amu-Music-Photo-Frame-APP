package com.example.musicframe.image

data class FrameConfig(
    val frameRatio: Float = 0.04f,
    val bottomExtraRatio: Float = 0.02f,
    val frameMode: FrameMode = FrameMode.FULL_BORDER,
    val userFrameColor: Int? = null,
    val userTextColor: Int? = null,
    val defaultFrameColor: Int = 0xFFFFFFFF.toInt(),
    val overlayOnly: Boolean = false,
    val staticFlowFrame: Boolean = false,
    val showPhotoMetadata: Boolean = true,
    val showMusicMetadata: Boolean = true,
    val showHeadphoneInfo: Boolean = true,
    val showCustomText: Boolean = true,
    val photoTextScale: Float = MIN_TEXT_SCALE,
    val musicTextScale: Float = MIN_TEXT_SCALE,
    val headphoneTextScale: Float = MIN_TEXT_SCALE,
    val customTextScale: Float = MIN_TEXT_SCALE,
    val overlayBackgroundAlpha: Float = 0.35f,
    val typeface: android.graphics.Typeface? = null,
    val customBottomText: String = "",
    val headphoneTextColor: Int? = null,
    val photoMetadata: PhotoMetadata? = null
) {
    fun resolvedFrameColor(candidate: Int?): Int {
        return userFrameColor ?: candidate ?: defaultFrameColor
    }

    fun customTextColor(): Int? = userTextColor

    fun headphoneColor(default: Int): Int = headphoneTextColor ?: customTextColor() ?: default
}

enum class FrameMode {
    FULL_BORDER,
    BOTTOM_BAR,
    BOTTOM_STRIPE,
    CUSTOM_CARD,
    FLOATING_CARD,
    PREMIUM_LEICA,
    CUSTOM_LEICA,
    MUSIC_FLOW,
    MUSIC_SOLID
}

const val MIN_TEXT_SCALE = 0.6f
const val MAX_TEXT_SCALE = 1.6f
