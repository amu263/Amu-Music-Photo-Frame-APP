package com.example.musicframe.image

/**
 * 模板排版工坊 — 8种预设相框布局
 */
enum class LayoutTemplate(val label: String, val emoji: String) {
    CLASSIC_CENTER("经典居中", "🖼️"),
    BOTTOM_STRIP("底部信息条", "📊"),
    SPLIT_SCREEN("双栏分割", "📰"),
    MAGAZINE("杂志封面", "📖"),
    POLAROID("拍立得", "📸"),
    MINIMAL_TOP("极简置顶", "✨"),
    WIDE_BANNER("宽幅横幅", "🎬"),
    CORNER_BADGE("角落徽章", "🏷️")
}

data class TemplateConfig(
    val template: LayoutTemplate = LayoutTemplate.CLASSIC_CENTER,
    val showWatermark: Boolean = true,
    val watermarkText: String = "AMuFrame",
    val textSizeScale: Float = 1.0f,        // 0.5..1.5
    val cornerRadius: Float = 0f,            // 0..0.1 圆角比例
    val showPhotoInfo: Boolean = true        // 是否显示拍摄参数
)
