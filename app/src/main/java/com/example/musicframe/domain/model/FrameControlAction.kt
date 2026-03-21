package com.example.musicframe.domain.model

import com.example.musicframe.image.FrameMode

sealed interface FrameControlAction {
    data class FrameColorSelected(val color: Int?) : FrameControlAction
    data class TextColorSelected(val color: Int?) : FrameControlAction
    data class HeadphoneTextColorSelected(val color: Int?) : FrameControlAction
    data class FrameRatio(val ratio: Float) : FrameControlAction
    data class BottomExtraRatio(val ratio: Float) : FrameControlAction
    data class ToggleOverlay(val enabled: Boolean) : FrameControlAction
    data class ToggleStaticFlow(val enabled: Boolean) : FrameControlAction
    data class TogglePhoto(val enabled: Boolean) : FrameControlAction
    data class ToggleMusic(val enabled: Boolean) : FrameControlAction
    data class ToggleHeadphone(val enabled: Boolean) : FrameControlAction
    data class ToggleCustomText(val enabled: Boolean) : FrameControlAction
    data class UpdatePhotoTextScale(val scale: Float) : FrameControlAction
    data class UpdateMusicTextScale(val scale: Float) : FrameControlAction
    data class UpdateHeadphoneTextScale(val scale: Float) : FrameControlAction
    data class UpdateCustomTextScale(val scale: Float) : FrameControlAction
    data class SetMode(val mode: FrameMode) : FrameControlAction
}
