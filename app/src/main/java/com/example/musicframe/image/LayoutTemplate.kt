package com.example.musicframe.image

/**
 * 模板排版工坊 — 4种精选预设布局
 */
enum class LayoutTemplate(val label: String, val emoji: String) {
    BOTTOM_STRIP("底部信息条", "📊"),
    SPLIT_SCREEN("双栏分割", "📰"),
    MAGAZINE("杂志封面", "📖"),
    POLAROID("拍立得", "📸")
}

data class TemplateConfig(
    val template: LayoutTemplate = LayoutTemplate.BOTTOM_STRIP,
    val showWatermark: Boolean = false,
    val watermarkText: String = "AMuFrame"
)
