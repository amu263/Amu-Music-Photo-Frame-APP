package com.example.musicframe.domain.model

import com.example.musicframe.image.FrameMode

fun FrameMode.toDisplayName(): String = when (this) {
    FrameMode.FULL_BORDER -> "全边框"
    FrameMode.BOTTOM_BAR -> "底部加框"
    FrameMode.BOTTOM_STRIPE -> "底边条"
    FrameMode.CUSTOM_CARD -> "自定义文字卡片"
    FrameMode.FLOATING_CARD -> "浮动卡片"
    FrameMode.PREMIUM_LEICA -> "高级徕卡式"
    FrameMode.CUSTOM_LEICA -> "自定义徕卡式"
    FrameMode.MUSIC_FLOW -> "音乐高级态"
    FrameMode.MUSIC_SOLID -> "音乐高级雅"
}
