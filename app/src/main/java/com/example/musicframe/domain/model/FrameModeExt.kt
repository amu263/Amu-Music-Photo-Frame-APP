package com.example.musicframe.domain.model

import com.example.musicframe.image.FrameMode

fun FrameMode.toDisplayName(): String = when (this) {
    FrameMode.PREMIUM_LEICA -> "高级徕卡式"
    FrameMode.CUSTOM_LEICA -> "自定义徕卡式"
    FrameMode.MUSIC_FLOW -> "音乐流光"
    FrameMode.MUSIC_SOLID -> "音乐纯色"
    FrameMode.ZODIAC_HOROSCOPE -> "星座运势"
    FrameMode.TEMPLATE -> "模板排版"
}
