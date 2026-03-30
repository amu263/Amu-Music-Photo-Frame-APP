package com.example.musicframe.domain.model

import com.example.musicframe.image.FrameMode

sealed interface FrameControlAction {
    data class SetLightFrame(val enabled: Boolean) : FrameControlAction
    data class SetCustomFrameColor(val colorHex: String) : FrameControlAction
    data class ToggleHeadphone(val enabled: Boolean) : FrameControlAction
    data class SetMode(val mode: FrameMode) : FrameControlAction
}
