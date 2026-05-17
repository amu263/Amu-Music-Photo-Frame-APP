package com.example.musicframe.image

/**
 * 自适应画幅系统 — 9种比例 + 内边距 + 网格辅助线
 */
data class CanvasConfig(
    val aspectRatio: AspectRatio = AspectRatio.RATIO_1_1,
    val paddingPercent: Float = 0f,        // 0..0.2 内边距百分比
    val showGridOverlay: Boolean = false,   // 网格辅助线预览
    val gridType: GridType = GridType.RULE_OF_THIRDS,
    val cropAlignment: CropAlignment = CropAlignment.CENTER,
    val customRatioW: Int = 1,              // 自定义宽
    val customRatioH: Int = 1               // 自定义高
) {
    val effectiveRatio: Float
        get() = when (aspectRatio) {
            AspectRatio.CUSTOM -> customRatioW.toFloat() / customRatioH.toFloat().coerceAtLeast(1f)
            else -> aspectRatio.ratio
        }
}

enum class AspectRatio(val label: String, val ratio: Float) {
    RATIO_1_1("1:1 正方形", 1f),
    RATIO_4_5("4:5 竖版", 0.8f),
    RATIO_3_4("3:4 经典", 0.75f),
    RATIO_2_3("2:3 人像", 0.667f),
    RATIO_9_16("9:16 全屏", 0.5625f),
    RATIO_16_9("16:9 宽屏", 1.778f),
    RATIO_3_2("3:2 相机", 1.5f),
    RATIO_2_1("2:1 全景", 2f),
    CINEMATIC("2.35:1 电影", 2.35f),
    CUSTOM("自定义", 1f)
}

enum class GridType(val label: String) {
    RULE_OF_THIRDS("三分线"),
    GOLDEN_RATIO("黄金比例"),
    DIAGONAL("对角线"),
    CENTER_CROSS("中心十字"),
    NONE("无")
}

enum class CropAlignment(val label: String) {
    CENTER("居中"),
    TOP("上对齐"),
    BOTTOM("下对齐"),
    LEFT("左对齐"),
    RIGHT("右对齐"),
    TOP_LEFT("左上"),
    TOP_RIGHT("右上"),
    BOTTOM_LEFT("左下"),
    BOTTOM_RIGHT("右下"),
    FACE("智能检测")
}

/** 画幅计算输出 */
data class CanvasOutput(
    val canvasWidth: Int,
    val canvasHeight: Int,
    val imageOffsetX: Int,
    val imageOffsetY: Int,
    val imageDisplayWidth: Int,
    val imageDisplayHeight: Int
)
