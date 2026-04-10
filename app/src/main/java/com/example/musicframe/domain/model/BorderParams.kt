package com.example.musicframe.domain.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils

data class BorderRenderParams(
    val output: Bitmap,
    val canvas: Canvas,
    val frameColor: Int,
    val framePaint: Paint,
    val staticFlow: Boolean,
    val width: Float,
    val height: Float
)

object BorderRenderer {

    fun createFramePaint(
        color: Int,
        width: Float,
        height: Float,
        staticFlow: Boolean
    ): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            applyGradient(color, width, height, staticFlow)
        }
    }

    private fun Paint.applyGradient(color: Int, width: Float, height: Float, staticFlow: Boolean) {
        shader = if (staticFlow) {
            val lighter = ColorUtils.blendARGB(color, 0xFFFFFFFF.toInt(), 0.28f)
            val darker = ColorUtils.blendARGB(color, 0xFF000000.toInt(), 0.18f)
            LinearGradient(
                0f,
                0f,
                width,
                height,
                intArrayOf(lighter, color, darker, color),
                floatArrayOf(0f, 0.35f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
        } else {
            null
        }
        this.color = color
    }

    fun drawFullFrameBackground(
        canvas: Canvas,
        output: Bitmap,
        framePaint: Paint
    ) {
        canvas.drawRect(0f, 0f, output.width.toFloat(), output.height.toFloat(), framePaint)
    }

    fun drawBottomStripeBackground(
        canvas: Canvas,
        stripeRect: RectF,
        framePaint: Paint
    ) {
        canvas.drawRect(stripeRect, framePaint)
    }
}
