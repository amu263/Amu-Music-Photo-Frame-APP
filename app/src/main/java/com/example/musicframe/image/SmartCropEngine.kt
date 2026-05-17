package com.example.musicframe.image

import android.graphics.Bitmap

/**
 * 智能裁剪引擎 — 中心加权、对齐策略
 */
object SmartCropEngine {

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
        
        val cropW: Int
        val cropH: Int
        val cropX: Int
        val cropY: Int

        if (sourceRatio > targetRatio) {
            cropH = sourceHeight
            cropW = (sourceHeight * targetRatio).toInt().coerceAtLeast(1)
            val maxCropX = (sourceWidth - cropW).coerceAtLeast(0)
            cropX = when (config.cropAlignment) {
                CropAlignment.CENTER, CropAlignment.TOP, CropAlignment.BOTTOM -> maxCropX / 2
                CropAlignment.LEFT, CropAlignment.TOP_LEFT, CropAlignment.BOTTOM_LEFT -> 0
                CropAlignment.RIGHT, CropAlignment.TOP_RIGHT, CropAlignment.BOTTOM_RIGHT -> maxCropX
                CropAlignment.FACE -> (sourceWidth - cropW) * 2 / 5
            }
            cropY = 0
        } else {
            cropW = sourceWidth
            cropH = (sourceWidth / targetRatio).toInt().coerceAtLeast(1)
            val maxCropY = (sourceHeight - cropH).coerceAtLeast(0)
            cropY = when (config.cropAlignment) {
                CropAlignment.CENTER, CropAlignment.LEFT, CropAlignment.RIGHT -> maxCropY / 2
                CropAlignment.TOP, CropAlignment.TOP_LEFT, CropAlignment.TOP_RIGHT -> 0
                CropAlignment.BOTTOM, CropAlignment.BOTTOM_LEFT, CropAlignment.BOTTOM_RIGHT -> maxCropY
                CropAlignment.FACE -> (sourceHeight - cropH) / 4
            }
            cropX = 0
        }

        val displayW = (cropW * paddingFactor).toInt().coerceAtLeast(1)
        val displayH = (cropH * paddingFactor).toInt().coerceAtLeast(1)
        val outputW = cropW + frameWidth * 2
        val outputH = cropH + frameWidth + bottomFrameHeight
        val offsetX = frameWidth + (cropW - displayW) / 2
        val offsetY = frameWidth + (cropH - displayH) / 2

        return CanvasOutput(outputW, outputH, offsetX, offsetY, displayW, displayH)
    }
}

data class CanvasOutput(
    val canvasWidth: Int,
    val canvasHeight: Int,
    val imageOffsetX: Int,
    val imageOffsetY: Int,
    val imageDisplayWidth: Int,
    val imageDisplayHeight: Int
)
