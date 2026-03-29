package com.example.musicframe.domain.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF

data class TextBlockParams(
    val bounds: RectF,
    val lines: List<String>,
    val textColor: Int,
    val textSize: Float,
    val horizontalPadding: Float,
    val verticalPadding: Float,
    val backgroundColor: Int? = null,
    val typeface: android.graphics.Typeface? = null,
    val icon: Bitmap?,
    val iconSize: Int,
    val alignment: TextBlockAlignment,
    val verticalAlignment: TextBlockVerticalAlignment,
    val bottomOffset: Float = 0f,
    val useStroke: Boolean = false,
    val tintIcon: Boolean = true
)

enum class TextBlockAlignment { START, END, CENTER }
enum class TextBlockVerticalAlignment { TOP, BOTTOM }

data class WrappedLines(val lines: List<String>, val maxLineWidth: Float)

data class TextBlock(
    val lines: List<String>,
    val lineHeight: Float,
    val blockLeft: Float,
    val blockTop: Float,
    val paint: Paint,
    val icon: Bitmap?,
    val iconSize: Int,
    val backgroundColor: Int?,
    val backgroundRect: RectF?,
    val blockWidth: Float,
    val textSize: Float,
    val alignment: TextBlockAlignment,
    val totalHeight: Float,
    val tintIcon: Boolean
) {
    @Suppress("CyclomaticComplexMethod")
    fun draw(canvas: Canvas) {
        if (lines.isEmpty()) return

        backgroundRect?.let { rect ->
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = backgroundColor ?: 0
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(rect, textSize * 0.35f, textSize * 0.35f, bgPaint)
        }

        val iconBitmap = icon?.let { Bitmap.createScaledBitmap(it, iconSize, iconSize, true) }
        val firstLineWidth = lines.firstOrNull()?.let { paint.measureText(it) } ?: 0f
        val iconSpacing = if (iconBitmap != null) textSize * 0.35f else 0f

        val textStart = when (alignment) {
            TextBlockAlignment.START -> blockLeft + if (iconBitmap != null) iconSize + iconSpacing else 0f
            TextBlockAlignment.END -> blockLeft + blockWidth - firstLineWidth
            TextBlockAlignment.CENTER -> blockLeft + (blockWidth - firstLineWidth) / 2f +
                if (iconBitmap != null) iconSize + iconSpacing else 0f
        }

        if (iconBitmap != null) {
            val iconX = when (alignment) {
                TextBlockAlignment.START -> blockLeft
                TextBlockAlignment.END -> blockLeft + blockWidth - firstLineWidth - iconSpacing - iconSize
                TextBlockAlignment.CENTER -> textStart - iconSpacing - iconSize
            }
            val iconY = blockTop + lineHeight - iconSize * 0.85f

            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                if (tintIcon) {
                    colorFilter = PorterDuffColorFilter(paint.color, PorterDuff.Mode.SRC_IN)
                }
            }
            canvas.drawBitmap(iconBitmap, iconX, iconY, iconPaint)
        }

        lines.forEachIndexed { index, line ->
            val lineWidth = paint.measureText(line)
            val x = when (alignment) {
                TextBlockAlignment.START -> textStart
                TextBlockAlignment.END -> blockLeft + blockWidth - lineWidth
                TextBlockAlignment.CENTER -> blockLeft + (blockWidth - lineWidth) / 2f
            }
            val y = blockTop + lineHeight * (index + 1) - lineHeight * 0.2f
            canvas.drawText(line, x, y, paint)
        }
    }
}
