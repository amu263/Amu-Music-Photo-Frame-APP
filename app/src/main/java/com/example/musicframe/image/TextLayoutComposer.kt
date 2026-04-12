package com.example.musicframe.image

import android.graphics.Paint
import android.graphics.RectF
import com.example.musicframe.domain.model.CaptionDrawParams
import com.example.musicframe.domain.model.TextBlock
import com.example.musicframe.domain.model.TextBlockAlignment
import com.example.musicframe.domain.model.TextBlockParams
import com.example.musicframe.domain.model.TextBlockVerticalAlignment
import com.example.musicframe.domain.model.WrappedLines
import kotlin.math.max
import kotlin.math.min

object TextLayoutComposer {

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun drawCaptionArea(params: CaptionDrawParams) {
        val baseTextSize = calculateFrameTextSize(
            params.borderSize ?: params.bounds.height().toInt(),
            min(params.bounds.width(), params.bounds.height()).toInt()
        )
        val horizontalPadding = (params.borderSize ?: baseTextSize).toFloat() * params.paddingScale
        val verticalPadding = (params.borderSize ?: baseTextSize).toFloat() * (params.paddingScale * 0.9f)

        val musicTextSize = baseTextSize * params.musicTextScale
        val photoTextSize = baseTextSize * params.photoTextScale
        val headphoneInfoTextSize = baseTextSize * params.headphoneTextScale
        val resolvedCustomText = if (params.customText.isNotBlank()) params.customText else ""
        val customTextSize = if (resolvedCustomText.isNotBlank()) baseTextSize * params.customTextScale else baseTextSize

        val iconSize = if (params.appIcon != null) (musicTextSize * 1.05f).toInt().coerceAtLeast(10) else 0
        val headphoneIconSize = if (params.headphoneIcon != null) {
            (headphoneInfoTextSize * 1.05f).toInt().coerceAtLeast(
                10
            )
        } else {
            0
        }
        val metadataBlocks = mutableListOf<TextBlock>()
        var bottomOffset = 0f

        if (params.musicLines.isNotEmpty()) {
            val musicBlock = buildTextBlock(
                TextBlockParams(
                    bounds = params.bounds, lines = params.musicLines, textColor = params.textColor,
                    textSize = musicTextSize, horizontalPadding = horizontalPadding, verticalPadding = verticalPadding,
                    backgroundColor = params.backgroundColor, typeface = params.typeface, icon = params.appIcon, iconSize = iconSize,
                    alignment = if (params.alignLinesToEnd) TextBlockAlignment.END else TextBlockAlignment.START,
                    verticalAlignment = TextBlockVerticalAlignment.BOTTOM, bottomOffset = bottomOffset, useStroke = false, tintIcon = false
                )
            )
            bottomOffset += musicBlock.totalHeight
            metadataBlocks += musicBlock
        }

        if (params.headphoneLines.isNotEmpty()) {
            val headphoneBlock = buildTextBlock(
                TextBlockParams(
                    bounds = params.bounds, lines = params.headphoneLines, textColor = params.headphoneTextColor,
                    textSize = headphoneInfoTextSize, horizontalPadding = horizontalPadding, verticalPadding = verticalPadding,
                    backgroundColor = params.backgroundColor, typeface = params.typeface, icon = params.headphoneIcon,
                    iconSize = headphoneIconSize,
                    alignment = if (params.alignLinesToEnd) TextBlockAlignment.START else TextBlockAlignment.END,
                    verticalAlignment = TextBlockVerticalAlignment.BOTTOM, bottomOffset = bottomOffset, useStroke = false, tintIcon = false
                )
            )
            bottomOffset += headphoneBlock.totalHeight
            metadataBlocks += headphoneBlock
        }

        if (params.photoLines.isNotEmpty()) {
            val photoBlock = buildTextBlock(
                TextBlockParams(
                    bounds = params.bounds, lines = params.photoLines, textColor = params.textColor,
                    textSize = photoTextSize, horizontalPadding = horizontalPadding, verticalPadding = verticalPadding,
                    backgroundColor = params.backgroundColor, typeface = params.typeface, icon = null, iconSize = 0,
                    alignment = if (params.alignLinesToEnd) TextBlockAlignment.END else TextBlockAlignment.START,
                    verticalAlignment = TextBlockVerticalAlignment.BOTTOM, bottomOffset = bottomOffset, useStroke = false, tintIcon = false
                )
            )
            bottomOffset += photoBlock.totalHeight
            metadataBlocks += photoBlock
        }

        val customBlock = if (resolvedCustomText.isNotBlank()) {
            buildTextBlock(
                TextBlockParams(
                    bounds = params.bounds, lines = listOf(resolvedCustomText), textColor = params.textColor,
                    textSize = customTextSize, horizontalPadding = horizontalPadding, verticalPadding = verticalPadding,
                    backgroundColor = if (params.overlayOnly) null else params.backgroundColor?.copyAlpha(0.8f),
                    typeface = params.typeface, icon = null, iconSize = 0,
                    alignment = if (params.centerCustomText) TextBlockAlignment.CENTER else TextBlockAlignment.START,
                    verticalAlignment = TextBlockVerticalAlignment.TOP, useStroke = false
                )
            )
        } else {
            null
        }

        customBlock?.draw(params.canvas)
        metadataBlocks.forEach { it.draw(params.canvas) }
    }

    fun buildTextBlock(params: TextBlockParams): TextBlock {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = params.textColor
            textSize = params.textSize
            textAlign = Paint.Align.LEFT
            typeface = params.typeface
            if (params.useStroke) {
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = params.textSize * 0.08f
                isFakeBoldText = true
                strokeJoin = Paint.Join.ROUND
            }
        }
        val iconTextSpacing = if (params.iconSize > 0) params.iconSize + params.textSize * 0.35f else 0f
        val availableWidth = params.bounds.width() - params.horizontalPadding * 2 - iconTextSpacing
        val wrapped = wrapLines(params.lines, paint, availableWidth)
        val lineHeight = paint.textSize * 1.3f
        val blockHeight = lineHeight * wrapped.lines.size
        val clampedVerticalPadding = calculateVerticalPadding(params.bounds, blockHeight, params.verticalPadding)
        val clampedHorizontalPadding = calculateHorizontalPadding(params.bounds, params.horizontalPadding)
        val firstLineWidth = wrapped.lines.firstOrNull()?.let { paint.measureText(it) } ?: 0f
        val hasIconAndLines = params.iconSize > 0 && wrapped.lines.isNotEmpty()
        val iconWidthAdjusted = if (hasIconAndLines) firstLineWidth + params.iconSize + params.textSize * 0.35f else 0f
        val contentWidth = max(wrapped.maxLineWidth, iconWidthAdjusted)
        val blockWidth = min(params.bounds.width() - clampedHorizontalPadding * 2, contentWidth)
        val blockLeft = when (params.alignment) {
            TextBlockAlignment.START -> params.bounds.left + clampedHorizontalPadding
            TextBlockAlignment.END -> params.bounds.right - clampedHorizontalPadding - blockWidth
            TextBlockAlignment.CENTER -> params.bounds.centerX() - blockWidth / 2f
        }
        val blockTop = when (params.verticalAlignment) {
            TextBlockVerticalAlignment.TOP -> params.bounds.top + clampedVerticalPadding
            TextBlockVerticalAlignment.BOTTOM -> params.bounds.bottom - params.bottomOffset - clampedVerticalPadding - blockHeight
        }
        val backgroundRect = params.backgroundColor?.let {
            RectF(
                blockLeft - clampedHorizontalPadding,
                blockTop - clampedVerticalPadding,
                blockLeft + blockWidth + clampedHorizontalPadding,
                blockTop + blockHeight + clampedVerticalPadding
            )
        }
        return TextBlock(
            lines = wrapped.lines, lineHeight = lineHeight, blockLeft = blockLeft, blockTop = blockTop, paint = paint,
            icon = params.icon, iconSize = params.iconSize, backgroundColor = params.backgroundColor, backgroundRect = backgroundRect,
            blockWidth = blockWidth, textSize = params.textSize, alignment = params.alignment,
            totalHeight = blockHeight + clampedVerticalPadding * 2, tintIcon = params.tintIcon
        )
    }

    fun wrapLines(lines: List<String>, paint: Paint, maxWidth: Float): WrappedLines {
        if (maxWidth <= 0f) return WrappedLines(lines, lines.maxOfOrNull { paint.measureText(it) } ?: 0f)
        val result = mutableListOf<String>()
        var maxLine = 0f
        lines.forEach { line ->
            var remaining = line
            while (remaining.isNotEmpty()) {
                val count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
                val piece = remaining.substring(
                    0,
                    count
                )
                maxLine = max(maxLine, paint.measureText(piece))
                result += piece
                remaining = remaining.substring(count)
            }
        }
        return WrappedLines(result, maxLine)
    }

    private fun calculateVerticalPadding(bounds: RectF, blockHeight: Float, desiredPadding: Float): Float {
        if (bounds.height() <= 0f) return 0f
        val available = (bounds.height() - blockHeight).coerceAtLeast(0f)
        val totalPadding = (desiredPadding * 2).coerceAtMost(available)
        return totalPadding / 2f
    }

    private fun calculateHorizontalPadding(bounds: RectF, desiredPadding: Float): Float {
        val maxPadding = (bounds.width() / 2f).coerceAtLeast(0f)
        return desiredPadding.coerceAtMost(maxPadding)
    }

    fun calculateFrameTextSize(border: Int, minSize: Int): Float {
        return max(minSize * 0.018f, border * 0.2f).coerceIn(8f, border * 0.45f)
    }
}

private fun Int.copyAlpha(alpha: Float): Int {
    val clamped = (alpha.coerceIn(0f, 1f) * 255).toInt()
    return (this and 0x00FFFFFF) or (clamped shl 24)
}
