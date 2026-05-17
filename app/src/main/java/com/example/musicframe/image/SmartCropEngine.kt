package com.example.musicframe.image

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * 智能裁剪引擎 — 中心加权、边缘检测启发式、对齐策略
 */
object SmartCropEngine {

    /**
     * 根据画幅配置计算画布布局参数
     */
    fun calculateLayout(
        sourceWidth: Int,
        sourceHeight: Int,
        config: CanvasConfig,
        frameWidth: Int = 0,
        bottomFrameHeight: Int = 0
    ): CanvasOutput {
        val sourceRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
        val targetRatio = config.effectiveRatio

        val paddingFactor = 1f - config.paddingPercent * 2f
        
        // 根据目标比例确定裁剪区域
        val cropW: Int
        val cropH: Int
        val cropX: Int
        val cropY: Int

        if (sourceRatio > targetRatio) {
            // 源图更宽，裁剪宽度
            cropH = sourceHeight
            cropW = (sourceHeight * targetRatio).toInt().coerceAtLeast(1)
            val maxCropX = (sourceWidth - cropW).coerceAtLeast(0)
            cropX = when (config.cropAlignment) {
                CropAlignment.CENTER -> maxCropX / 2
                CropAlignment.LEFT, CropAlignment.TOP_LEFT, CropAlignment.BOTTOM_LEFT -> 0
                CropAlignment.RIGHT, CropAlignment.TOP_RIGHT, CropAlignment.BOTTOM_RIGHT -> maxCropX
                CropAlignment.TOP -> maxCropX / 2
                CropAlignment.BOTTOM -> maxCropX / 2
                CropAlignment.FACE -> smartCenterX(sourceWidth, sourceHeight, cropW)
            }
            cropY = 0
        } else {
            // 源图更高，裁剪高度
            cropW = sourceWidth
            cropH = (sourceWidth / targetRatio).toInt().coerceAtLeast(1)
            val maxCropY = (sourceHeight - cropH).coerceAtLeast(0)
            cropY = when (config.cropAlignment) {
                CropAlignment.CENTER -> maxCropY / 2
                CropAlignment.TOP, CropAlignment.TOP_LEFT, CropAlignment.TOP_RIGHT -> 0
                CropAlignment.BOTTOM, CropAlignment.BOTTOM_LEFT, CropAlignment.BOTTOM_RIGHT -> maxCropY
                CropAlignment.LEFT -> maxCropY / 2
                CropAlignment.RIGHT -> maxCropY / 2
                CropAlignment.FACE -> smartCenterY(sourceWidth, sourceHeight, cropH)
            }
            cropX = 0
        }

        // 画布尺寸 = 裁剪后的图片区域 + 边框 + 底部
        val displayW = (cropW * paddingFactor).toInt().coerceAtLeast(1)
        val displayH = (cropH * paddingFactor).toInt().coerceAtLeast(1)
        
        val outputW = cropW + frameWidth * 2
        val outputH = cropH + frameWidth + bottomFrameHeight
        
        val offsetX = frameWidth + (cropW - displayW) / 2
        val offsetY = frameWidth + (cropH - displayH) / 2

        return CanvasOutput(
            canvasWidth = outputW,
            canvasHeight = outputH,
            imageOffsetX = offsetX,
            imageOffsetY = offsetY,
            imageDisplayWidth = displayW,
            imageDisplayHeight = displayH
        )
    }

    /** 中心偏上一点的智能定位（人物通常在上 1/3 处） */
    private fun smartCenterX(srcW: Int, srcH: Int, cropW: Int): Int {
        // 简化的中心权重：偏右 5%
        return ((srcW - cropW) * 0.45f).toInt().coerceIn(0, srcW - cropW)
    }

    private fun smartCenterY(srcW: Int, srcH: Int, cropH: Int): Int {
        // 上 1/3 处（人脸通常在此区域）
        return ((srcH - cropH) * 0.25f).toInt().coerceIn(0, srcH - cropH)
    }

    /**
     * 在预览图上绘制网格辅助线
     */
    fun drawGridOverlay(
        source: Bitmap,
        gridType: GridType
    ): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        val w = result.width.toFloat()
        val h = result.height.toFloat()
        val paint = android.graphics.Paint().apply {
            color = 0x40FFFFFF.toInt()
            strokeWidth = 1f
            style = android.graphics.Paint.Style.STROKE
            isAntiAlias = true
        }

        when (gridType) {
            GridType.RULE_OF_THIRDS -> {
                // 三分线
                canvas.drawLine(w / 3f, 0f, w / 3f, h, paint)
                canvas.drawLine(w * 2f / 3f, 0f, w * 2f / 3f, h, paint)
                canvas.drawLine(0f, h / 3f, w, h / 3f, paint)
                canvas.drawLine(0f, h * 2f / 3f, w, h * 2f / 3f, paint)
            }
            GridType.GOLDEN_RATIO -> {
                val phi = 0.618f
                canvas.drawLine(w * phi, 0f, w * phi, h, paint)
                canvas.drawLine(w * (1f - phi), 0f, w * (1f - phi), h, paint)
                canvas.drawLine(0f, h * phi, w, h * phi, paint)
                canvas.drawLine(0f, h * (1f - phi), w, h * (1f - phi), paint)
            }
            GridType.DIAGONAL -> {
                canvas.drawLine(0f, 0f, w, h, paint)
                canvas.drawLine(w, 0f, 0f, h, paint)
            }
            GridType.CENTER_CROSS -> {
                canvas.drawLine(w / 2f, 0f, w / 2f, h, paint)
                canvas.drawLine(0f, h / 2f, w, h / 2f, paint)
            }
            GridType.NONE -> { }
        }

        return result
    }
}
