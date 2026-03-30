package com.example.musicframe.domain.model

import com.example.musicframe.image.FrameMode

sealed interface FrameControlAction {
    data class SetFrameColorMode(val mode: FrameColorMode) : FrameControlAction
    data class SetCustomFrameColor(val colorHex: String) : FrameControlAction
    data class SetDarkBackground(val enabled: Boolean) : FrameControlAction
    data class ToggleHeadphone(val enabled: Boolean) : FrameControlAction
    data class SetMode(val mode: FrameMode) : FrameControlAction
}
