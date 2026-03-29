package com.example.musicframe.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import com.example.musicframe.model.HeadphoneInfo
import com.example.musicframe.model.MusicMetadata
import kotlin.math.max
import kotlin.math.min

class FrameComposer {

    fun compose(
        source: Bitmap,
        config: FrameConfig,
        musicMetadata: MusicMetadata?,
        photoMetadata: PhotoMetadata?,
        headphoneInfo: HeadphoneInfo?
    ): Bitmap {
        val musicLines = buildMusicLines(musicMetadata)
        val headphoneLines = buildHeadphoneLines(headphoneInfo)
        val frameColor = musicMetadata?.dominantColor ?: 0xFF333333.toInt()
        val textColor = invertedColor(frameColor)
        val headphoneColor = config.headphoneColor(invertedColor(frameColor))
        val headphoneIcon = if (headphoneLines.isNotEmpty()) createHeadphoneIcon(invertedColor(frameColor)) else null
        val badgeIcon = musicMetadata?.appIcon ?: headphoneIcon
        
        val renderParams = DrawRenderParams(
            source = source,
            musicLines = musicLines,
            photoLines = emptyList(),
            headphoneLines = headphoneLines,
            customText = "",
            frameColor = frameColor,
            textColor = textColor,
            headphoneTextColor = headphoneColor,
            appIcon = badgeIcon,
            headphoneIcon = headphoneIcon
        )
        
        return when (config.frameMode) {
            FrameMode.PREMIUM_LEICA -> drawPremiumLeica(renderParams, config)
            FrameMode.CUSTOM_LEICA -> drawCustomLeica(renderParams, config)
            FrameMode.MUSIC_FLOW -> drawMusicFlow(renderParams, config)
            FrameMode.MUSIC_SOLID -> drawMusicSolid(renderParams, config)
        }
    }

    private fun drawPremiumLeica(renderParams: DrawRenderParams, config: FrameConfig): Bitmap {
        val source = renderParams.source
        val width = source.width
        val height = source.height
        
        val frameWidth = (min(width, height) * 0.12f).toInt()
        val bottomFrameHeight = (height * 0.25f).toInt()
        val outputWidth = width + frameWidth * 2
        val outputHeight = height + frameWidth + bottomFrameHeight
        
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        // 1. 毛玻璃模糊背景
        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val blurScale = 0.08f
        val scaledW = (outputWidth * blurScale).toInt().coerceAtLeast(1)
        val scaledH = (outputHeight * blurScale).toInt().coerceAtLeast(1)
        val blurredBg = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)
        val blurredCanvas = Canvas(blurredBg)
        blurredCanvas.drawBitmap(source, null, android.graphics.Rect(0, 0, scaledW, scaledH), blurPaint)
        canvas.drawBitmap(blurredBg, null, android.graphics.Rect(0, 0, outputWidth, outputHeight), blurPaint)
        blurredBg.recycle()
        
        // 2. 半透明黑色叠加
        val darkOverlay = Paint().apply { color = 0x66000000.toInt() }
        canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), darkOverlay)
        
        // 3. 中央清晰原图
        canvas.drawBitmap(source, frameWidth.toFloat(), frameWidth.toFloat(), null)
        
        val frameColor = renderParams.frameColor.toInt()
        val textColor = invertedColor(frameColor)
        val subTextColor = (textColor and 0x00FFFFFF) or 0x99000000.toInt()
        
        // 4. 底部内容
        val centerX = outputWidth / 2f
        val bottomStartY = frameWidth + height.toFloat()
        val actualBottomHeight = bottomFrameHeight.toFloat()
        val frameCenterY = bottomStartY + actualBottomHeight / 2f
        
        // 歌名
        val musicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = actualBottomHeight * 0.14f
            textAlign = Paint.Align.CENTER
        }
        val firstRowY = frameCenterY - actualBottomHeight * 0.18f
        if (renderParams.musicLines.isNotEmpty()) {
            val musicText = renderParams.musicLines[0]
            val textWidth = musicPaint.measureText(musicText)
            val maxWidth = outputWidth - frameWidth * 4
            if (textWidth > maxWidth) {
                musicPaint.textSize = musicPaint.textSize * (maxWidth / textWidth) * 0.95f
            }
            canvas.drawText(musicText, centerX, firstRowY, musicPaint)
        }
        
        // 图标 || 播放器名称
        val cameraTopY = bottomStartY + actualBottomHeight * 0.78f
        val musicBottomY = firstRowY + musicPaint.textSize
        val secondRowCenterY = (musicBottomY + cameraTopY) / 2f
        val separatorHeight = actualBottomHeight * 0.12f
        
        val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = separatorHeight
            textAlign = Paint.Align.CENTER
        }
        val separatorWidth = separatorPaint.measureText("||")
        
        val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = separatorHeight * 0.9f
            textAlign = Paint.Align.LEFT
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
        
        val playerText = renderParams.musicLines.getOrElse(1) { "" }
        val hasPlayerInfo = playerText.isNotBlank() && renderParams.appIcon != null
        
        val separatorFontMetrics = separatorPaint.fontMetrics
        val separatorTop = secondRowCenterY + separatorFontMetrics.top
        val separatorBottom = secondRowCenterY + separatorFontMetrics.bottom
        val separatorActualHeight = separatorBottom - separatorTop
        val iconSize = separatorActualHeight
        val iconGap = separatorActualHeight * 0.3f
        
        if (hasPlayerInfo) {
            val playerWidth = playerPaint.measureText(playerText)
            val totalWidth = iconSize + iconGap * 2 + separatorWidth + playerWidth
            val startX = centerX - totalWidth / 2f
            
            val iconLeft = startX
            val iconRect = RectF(iconLeft, separatorTop, iconLeft + iconSize, separatorBottom)
            renderParams.appIcon?.let { appIcon ->
                canvas.drawBitmap(appIcon, null, iconRect, Paint(Paint.ANTI_ALIAS_FLAG))
            }
            
            val separatorX = iconLeft + iconSize + iconGap
            canvas.drawText("||", separatorX + separatorWidth / 2f, secondRowCenterY, separatorPaint)
            
            val playerX = separatorX + separatorWidth + iconGap
            canvas.drawText(playerText, playerX, secondRowCenterY, playerPaint)
        } else {
            val iconRect = RectF(centerX - iconSize / 2f, separatorTop, centerX + iconSize / 2f, separatorBottom)
            renderParams.appIcon?.let { icon ->
                canvas.drawBitmap(icon, null, iconRect, Paint(Paint.ANTI_ALIAS_FLAG))
            }
        }
        
        // 相机参数（只有当图片有真实相机参数时才显示）
        val infoY = bottomStartY + actualBottomHeight * 0.78f
        config.photoMetadata?.getCameraInfoText()?.let { cameraInfo ->
            val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subTextColor
                textSize = separatorHeight * 0.7f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(cameraInfo, centerX, infoY, infoPaint)
        }
        
        // 时间戳
        val timeText = config.photoMetadata?.createdDateTime?.let { rawDate ->
            runCatching {
                val parser = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                val date = parser.parse(rawDate)
                val formatter = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault())
                formatter.format(date ?: java.util.Date())
            }.getOrNull() ?: rawDate
        } ?: run {
            val timeFormat = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault())
            timeFormat.format(java.util.Date())
        }
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subTextColor
            textSize = separatorHeight * 0.7f
            textAlign = Paint.Align.CENTER
        }
        val timeY = infoY + separatorHeight * 1.2f
        canvas.drawText(timeText, centerX, timeY, timePaint)
        
        // 地点信息
        val locationText = config.photoMetadata?.locationText
        if (!locationText.isNullOrBlank() && frameWidth > 0) {
            val topFrameCenterY = frameWidth.toFloat() / 2f
            val locationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = frameWidth.toFloat() * 0.35f
                textAlign = Paint.Align.CENTER
            }
            val textWidth = locationPaint.measureText(locationText)
            val maxWidth = outputWidth.toFloat() - frameWidth * 4
            if (textWidth > maxWidth) {
                locationPaint.textSize = locationPaint.textSize * (maxWidth / textWidth) * 0.95f
            }
            canvas.drawText(locationText, centerX, topFrameCenterY + locationPaint.textSize * 0.35f, locationPaint)
        }
        
        return output
    }

    private fun drawCustomLeica(renderParams: DrawRenderParams, config: FrameConfig): Bitmap {
        val source = renderParams.source
        val width = source.width
        val height = source.height
        
        val frameWidth = (min(width, height) * 0.12f).toInt()
        val bottomFrameHeight = (height * 0.25f).toInt()
        val outputWidth = width + frameWidth * 2
        val outputHeight = height + frameWidth + bottomFrameHeight
        
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        // 自定义颜色纯色外框
        val frameColor = 0xFF333333.toInt()
        val framePaint = Paint().apply { color = frameColor }
        canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), framePaint)
        
        // 中央清晰原图
        canvas.drawBitmap(source, frameWidth.toFloat(), frameWidth.toFloat(), null)
        
        // 底部内容
        drawLeicaContent(canvas, renderParams, config.photoMetadata, outputWidth, height, bottomFrameHeight, frameColor, frameWidth)
        
        return output
    }

    private fun drawMusicFlow(renderParams: DrawRenderParams, config: FrameConfig): Bitmap {
        val source = renderParams.source
        val width = source.width
        val height = source.height
        
        val frameWidth = (min(width, height) * 0.12f).toInt()
        val bottomFrameHeight = (height * 0.25f).toInt()
        val outputWidth = width + frameWidth * 2
        val outputHeight = height + frameWidth + bottomFrameHeight
        
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        // 音乐封面颜色的静态流光外框
        val frameColor = renderParams.frameColor.toInt()
        val flowPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, outputHeight.toFloat(),
                intArrayOf(
                    (frameColor and 0x00FFFFFF) or 0x50000000.toInt(),
                    (frameColor and 0x00FFFFFF) or 0x90000000.toInt(),
                    (frameColor and 0x00FFFFFF) or 0x50000000.toInt()
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), flowPaint)
        
        // 中央清晰原图
        canvas.drawBitmap(source, frameWidth.toFloat(), frameWidth.toFloat(), null)
        
        // 底部内容
        drawLeicaContent(canvas, renderParams, config.photoMetadata, outputWidth, height, bottomFrameHeight, frameColor, frameWidth)
        
        return output
    }

    private fun drawMusicSolid(renderParams: DrawRenderParams, config: FrameConfig): Bitmap {
        val source = renderParams.source
        val width = source.width
        val height = source.height
        
        val frameWidth = (min(width, height) * 0.12f).toInt()
        val bottomFrameHeight = (height * 0.25f).toInt()
        val outputWidth = width + frameWidth * 2
        val outputHeight = height + frameWidth + bottomFrameHeight
        
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        // 音乐封面主色调纯色外框
        val frameColor = renderParams.frameColor.toInt()
        val framePaint = Paint().apply {
            color = (frameColor and 0x00FFFFFF) or 0x70000000
        }
        canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), framePaint)
        
        // 中央清晰原图
        canvas.drawBitmap(source, frameWidth.toFloat(), frameWidth.toFloat(), null)
        
        // 底部内容
        drawLeicaContent(canvas, renderParams, config.photoMetadata, outputWidth, height, bottomFrameHeight, frameColor, frameWidth)
        
        return output
    }

    private fun drawLeicaContent(
        canvas: Canvas,
        renderParams: DrawRenderParams,
        photoMetadata: PhotoMetadata?,
        width: Int,
        height: Int,
        frameHeight: Int,
        frameColor: Int,
        frameWidth: Int = 0
    ) {
        val textColor = invertedColor(frameColor)
        val subTextColor = (textColor and 0x00FFFFFF) or 0x99000000.toInt()
        val bottomStartY = (frameWidth + height).toFloat()
        
        // 地点信息
        photoMetadata?.locationText?.let { locationText ->
            if (locationText.isNotBlank() && frameWidth > 0) {
                val topFrameCenterY = frameWidth.toFloat() / 2f
                val locationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor
                    textSize = frameWidth.toFloat() * 0.35f
                    textAlign = Paint.Align.CENTER
                }
                val textWidth = locationPaint.measureText(locationText)
                val maxWidth = width.toFloat() - frameWidth * 4
                if (textWidth > maxWidth) {
                    locationPaint.textSize = locationPaint.textSize * (maxWidth / textWidth) * 0.95f
                }
                canvas.drawText(locationText, width / 2f, topFrameCenterY + locationPaint.textSize * 0.35f, locationPaint)
            }
        }
        
        // 歌名
        val musicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = frameHeight * 0.14f
            textAlign = Paint.Align.CENTER
        }
        val firstRowY = bottomStartY + frameHeight * 0.18f
        if (renderParams.musicLines.isNotEmpty()) {
            val musicText = renderParams.musicLines[0]
            val textWidth = musicPaint.measureText(musicText)
            val maxWidth = width * 0.9f
            if (textWidth > maxWidth) {
                musicPaint.textSize = musicPaint.textSize * (maxWidth / textWidth) * 0.95f
            }
            canvas.drawText(musicText, width / 2f, firstRowY, musicPaint)
        }
        
        // 图标 || 播放器名称
        val cameraTopY = bottomStartY + frameHeight * 0.78f
        val musicBottomY = firstRowY + musicPaint.textSize
        val secondRowCenterY = (musicBottomY + cameraTopY) / 2f
        val separatorHeight = frameHeight * 0.12f
        
        val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = separatorHeight
            textAlign = Paint.Align.CENTER
        }
        val separatorWidth = separatorPaint.measureText("||")
        
        val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = separatorHeight * 0.9f
            textAlign = Paint.Align.LEFT
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
        
        val playerText = renderParams.musicLines.getOrElse(1) { "" }
        val hasPlayerInfo = playerText.isNotBlank() && renderParams.appIcon != null
        
        val iconSize = separatorHeight
        val iconGap = separatorHeight * 0.3f
        
        if (hasPlayerInfo) {
            val playerWidth = playerPaint.measureText(playerText)
            val totalWidth = iconSize + iconGap * 2 + separatorWidth + playerWidth
            val startX = width / 2f - totalWidth / 2f
            
            val separatorFontMetrics = separatorPaint.fontMetrics
            val separatorTop = secondRowCenterY + separatorFontMetrics.top
            val separatorBottom = secondRowCenterY + separatorFontMetrics.bottom
            val separatorActualHeight = separatorBottom - separatorTop
            
            val actualIconSize = separatorActualHeight
            val actualIconGap = separatorActualHeight * 0.3f
            val actualTotalWidth = actualIconSize + actualIconGap * 2 + separatorWidth + playerWidth
            val actualStartX = width / 2f - actualTotalWidth / 2f
            
            val iconLeft = actualStartX
            val iconRect = RectF(iconLeft, separatorTop, iconLeft + actualIconSize, separatorBottom)
            renderParams.appIcon?.let { appIcon ->
                canvas.drawBitmap(appIcon, null, iconRect, Paint(Paint.ANTI_ALIAS_FLAG))
            }
            
            val separatorX = iconLeft + actualIconSize + actualIconGap
            canvas.drawText("||", separatorX + separatorWidth / 2f, secondRowCenterY, separatorPaint)
            
            val playerX = separatorX + separatorWidth + actualIconGap
            canvas.drawText(playerText, playerX, secondRowCenterY, playerPaint)
        } else {
            val separatorFontMetrics = separatorPaint.fontMetrics
            val separatorTop = secondRowCenterY + separatorFontMetrics.top
            val separatorBottom = secondRowCenterY + separatorFontMetrics.bottom
            val separatorActualHeight = separatorBottom - separatorTop
            val iconSizeCentered = separatorActualHeight
            
            val iconRect = RectF(
                width / 2f - iconSizeCentered / 2f,
                separatorTop,
                width / 2f + iconSizeCentered / 2f,
                separatorBottom
            )
            renderParams.appIcon?.let { icon ->
                canvas.drawBitmap(icon, null, iconRect, Paint(Paint.ANTI_ALIAS_FLAG))
            }
        }
        
        // 相机参数（只有当图片有真实相机参数时才显示）
        val infoY = bottomStartY + frameHeight * 0.78f
        photoMetadata?.getCameraInfoText()?.let { cameraInfo ->
            val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subTextColor
                textSize = separatorHeight * 0.7f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(cameraInfo, width / 2f, infoY, infoPaint)
        }
        
        // 时间戳
        val timeText = photoMetadata?.createdDateTime?.let { rawDate ->
            runCatching {
                val parser = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                val date = parser.parse(rawDate)
                val formatter = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault())
                formatter.format(date ?: java.util.Date())
            }.getOrNull() ?: rawDate
        } ?: run {
            val timeFormat = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault())
            timeFormat.format(java.util.Date())
        }
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subTextColor
            textSize = separatorHeight * 0.7f
            textAlign = Paint.Align.CENTER
        }
        val timeY = infoY + separatorHeight * 1.2f
        canvas.drawText(timeText, width / 2f, timeY, timePaint)
    }

    private fun createHeadphoneIcon(color: Int): Bitmap {
        val size = 72
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = size * 0.12f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        val radius = size * 0.32f
        val centerX = size / 2f
        val centerY = size * 0.52f
        val arcRect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        canvas.drawArc(arcRect, 200f, 140f, false, paint)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val cupWidth = size * 0.18f
        val cupHeight = size * 0.26f
        val cupRadius = size * 0.08f
        val leftCup = RectF(arcRect.left - cupWidth * 0.65f, centerY - cupHeight / 2f, arcRect.left - cupWidth * 0.1f, centerY + cupHeight / 2f)
        val rightCup = RectF(arcRect.right + cupWidth * 0.1f, centerY - cupHeight / 2f, arcRect.right + cupWidth * 0.65f, centerY + cupHeight / 2f)
        canvas.drawRoundRect(leftCup, cupRadius, cupRadius, fillPaint)
        canvas.drawRoundRect(rightCup, cupRadius, cupRadius, fillPaint)
        return bitmap
    }

    private fun buildMusicLines(musicMetadata: MusicMetadata?): List<String> {
        if (musicMetadata == null) return emptyList()
        val lines = mutableListOf<String>()
        val titleLine = listOf(musicMetadata.title, musicMetadata.artist)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        if (titleLine.isNotBlank()) {
            lines += titleLine
        }
        if (musicMetadata.album.isNotBlank()) {
            lines += musicMetadata.album
        }
        musicMetadata.appName?.takeIf { it.isNotBlank() }?.let { appName ->
            lines += "来自 $appName"
        }
        return lines
    }

    private fun buildHeadphoneLines(headphoneInfo: HeadphoneInfo?): List<String> {
        if (headphoneInfo == null) return emptyList()
        val readable = headphoneInfo.asDisplayLine()
        return if (readable.isNotBlank()) listOf(readable) else emptyList()
    }

    private fun invertedColor(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255f
        return if (luminance > 0.5f) {
            0xFF1A1A1A.toInt()
        } else {
            0xFFF5F5F5.toInt()
        }
    }
}

data class DrawRenderParams(
    val source: Bitmap,
    val musicLines: List<String>,
    val photoLines: List<String>,
    val headphoneLines: List<String>,
    val customText: String,
    val frameColor: Int,
    val textColor: Int,
    val headphoneTextColor: Int,
    val appIcon: Bitmap?,
    val headphoneIcon: Bitmap?
)
