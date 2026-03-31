package com.example.musicframe.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import com.example.musicframe.domain.model.FrameColorMode
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
        val dominantColor = musicMetadata?.dominantColor ?: 0xFF333333.toInt()
        val frameColor = config.getFrameColor(dominantColor, forMusicFlow = false)
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
            headphoneIcon = headphoneIcon,
            config = config
        )
        
        return when (config.frameMode) {
            FrameMode.PREMIUM_LEICA -> drawPremiumLeica(renderParams, config, musicMetadata)
            FrameMode.CUSTOM_LEICA -> drawCustomLeica(renderParams, config, musicMetadata)
            FrameMode.MUSIC_FLOW -> drawMusicFlow(renderParams, config, musicMetadata)
            FrameMode.MUSIC_SOLID -> drawMusicSolid(renderParams, config, musicMetadata)
        }
    }

    private fun drawPremiumLeica(renderParams: DrawRenderParams, config: FrameConfig, musicMetadata: MusicMetadata?): Bitmap {
        val source = renderParams.source
        val width = source.width
        val height = source.height
        
        val frameWidth = (min(width, height) * 0.12f).toInt()
        val bottomFrameHeight = (height * 0.25f).toInt()
        val outputWidth = width + frameWidth * 2
        val outputHeight = height + frameWidth + bottomFrameHeight
        
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        // 1. 增强毛玻璃模糊背景（更高模糊度）
        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val blurScale = 0.04f // 更小的缩放比例 = 更强的模糊
        val scaledW = (outputWidth * blurScale).toInt().coerceAtLeast(1)
        val scaledH = (outputHeight * blurScale).toInt().coerceAtLeast(1)
        val blurredBg = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)
        val blurredCanvas = Canvas(blurredBg)
        blurredCanvas.drawBitmap(source, null, android.graphics.Rect(0, 0, scaledW, scaledH), blurPaint)
        canvas.drawBitmap(blurredBg, null, android.graphics.Rect(0, 0, outputWidth, outputHeight), blurPaint)
        blurredBg.recycle()
        
        // 2. 深色背景模式：使用封面取色的深色，而非全黑
        if (config.useDarkBackground) {
            val dominantColor = musicMetadata?.dominantColor ?: 0xFF333333.toInt()
            val darkBgColor = darkenColor(dominantColor, 0.85f)
            val darkBgPaint = Paint().apply { color = darkBgColor }
            canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), darkBgPaint)
        }
        
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

    private fun drawCustomLeica(renderParams: DrawRenderParams, config: FrameConfig, musicMetadata: MusicMetadata?): Bitmap {
        val source = renderParams.source
        val width = source.width
        val height = source.height
        
        val frameWidth = (min(width, height) * 0.12f).toInt()
        val bottomFrameHeight = (height * 0.25f).toInt()
        val outputWidth = width + frameWidth * 2
        val outputHeight = height + frameWidth + bottomFrameHeight
        
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        // 自定义颜色纯色外框：使用 config.getFrameColor() 获取自定义颜色或模式颜色
        val dominantColor = musicMetadata?.dominantColor ?: 0xFF333333.toInt()
        val bgColor = config.getFrameColor(dominantColor, forMusicFlow = false)
        val framePaint = Paint().apply { color = bgColor }
        canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), framePaint)
        
        // 中央清晰原图
        canvas.drawBitmap(source, frameWidth.toFloat(), frameWidth.toFloat(), null)
        
        // 底部内容
        drawLeicaContent(canvas, renderParams, config.photoMetadata, outputWidth, height, bottomFrameHeight, bgColor, frameWidth)
        
        return output
    }

    private fun drawMusicFlow(renderParams: DrawRenderParams, config: FrameConfig, musicMetadata: MusicMetadata?): Bitmap {
        val source = renderParams.source
        val width = source.width
        val height = source.height
        val dominantColor = musicMetadata?.dominantColor ?: 0xFF333333.toInt()
        
        val frameWidth = (min(width, height) * 0.12f).toInt()
        val bottomFrameHeight = (height * 0.25f).toInt()
        val outputWidth = width + frameWidth * 2
        val outputHeight = height + frameWidth + bottomFrameHeight
        
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        // 高级竖纹模式外框：封面原色和浅色/深色交替的垂直条纹
        val (baseColor, alternateColor) = config.getStripeColors(dominantColor)
        
        val stripeWidth = 20 // 条纹宽度 20px
        val stripePaint = Paint()
        
        // 绘制垂直条纹背景
        for (x in 0 until outputWidth step stripeWidth * 2) {
            // 原色条纹
            stripePaint.color = baseColor
            canvas.drawRect(x.toFloat(), 0f, (x + stripeWidth).toFloat(), outputHeight.toFloat(), stripePaint)
            // 浅色/深色条纹
            stripePaint.color = alternateColor
            canvas.drawRect((x + stripeWidth).toFloat(), 0f, (x + stripeWidth * 2).toFloat(), outputHeight.toFloat(), stripePaint)
        }
        
        // 中央清晰原图
        canvas.drawBitmap(source, frameWidth.toFloat(), frameWidth.toFloat(), null)
        
        // 底部内容（高级竖纹模式使用斜体 + 反色描边）
        drawLeicaContent(canvas, renderParams, config.photoMetadata, outputWidth, height, bottomFrameHeight, baseColor, frameWidth, isMusicFlowMode = true, config = config)
        
        return output
    }

    private fun drawMusicSolid(renderParams: DrawRenderParams, config: FrameConfig, musicMetadata: MusicMetadata?): Bitmap {
        val source = renderParams.source
        val width = source.width
        val height = source.height
        
        val frameWidth = (min(width, height) * 0.12f).toInt()
        val bottomFrameHeight = (height * 0.25f).toInt()
        val outputWidth = width + frameWidth * 2
        val outputHeight = height + frameWidth + bottomFrameHeight
        
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        // 音乐纯色模式：支持深色/浅色模式（自定义颜色不影响音乐纯色模式，只影响徕卡模式）
        val dominantColor = musicMetadata?.dominantColor ?: 0xFF333333.toInt()
        val bgColor = config.getFrameColor(dominantColor, forMusicFlow = false)
        val framePaint = Paint().apply { color = bgColor }
        canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), framePaint)
        
        // 中央清晰原图
        canvas.drawBitmap(source, frameWidth.toFloat(), frameWidth.toFloat(), null)
        
        // 底部内容（使用封面原色计算文字颜色）
        drawLeicaContent(canvas, renderParams, config.photoMetadata, outputWidth, height, bottomFrameHeight, dominantColor, frameWidth)
        
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
        frameWidth: Int = 0,
        isMusicFlowMode: Boolean = false,
        config: FrameConfig? = null
    ) {
        val textColor = config?.let { invertedColor(it.getFrameColor(renderParams.frameColor.toInt(), forMusicFlow = isMusicFlowMode)) } ?: invertedColor(frameColor)
        val subTextColor = (textColor and 0x00FFFFFF) or 0x99000000.toInt()
        val bottomStartY = (frameWidth + height).toFloat()
        val centerX = width / 2f
        
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
                canvas.drawText(locationText, centerX, topFrameCenterY + locationPaint.textSize * 0.35f, locationPaint)
            }
        }
        
        // 歌名（高级徕卡模式使用粗体 + 对比色描边，音乐流光模式使用斜体 + 高对比度颜色）
        val musicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isMusicFlowMode) {
                getHighContrastTextColor(frameColor)
            } else {
                textColor
            }
            textSize = frameHeight * 0.14f
            textAlign = Paint.Align.CENTER
            typeface = if (isMusicFlowMode) {
                android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
            } else {
                android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
        }
        val firstRowY = bottomStartY + frameHeight * 0.18f
        if (renderParams.musicLines.isNotEmpty()) {
            val musicText = renderParams.musicLines[0]
            val textWidth = musicPaint.measureText(musicText)
            val maxWidth = width * 0.9f
            if (textWidth > maxWidth) {
                musicPaint.textSize = musicPaint.textSize * (maxWidth / textWidth) * 0.95f
            }
            
            // 高级徕卡模式：为文字添加对比色描边（保护文字不受复杂照片颜色影响）
            if (!isMusicFlowMode) {
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = musicPaint.textSize * 0.08f  // 描边宽度为文字大小的 8%
                    color = invertColorForStroke(textColor)
                }
                canvas.drawText(musicText, centerX, firstRowY, strokePaint)
            }
            
            canvas.drawText(musicText, centerX, firstRowY, musicPaint)
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
            typeface = if (isMusicFlowMode) {
                android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.ITALIC)
            } else {
                android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            }
        }
        
        val playerText = renderParams.musicLines.getOrElse(1) { "" }
        val hasPlayerInfo = playerText.isNotBlank() && renderParams.appIcon != null
        
        val iconSize = separatorHeight
        val iconGap = separatorHeight * 0.3f
        
        if (hasPlayerInfo) {
            val playerWidth = playerPaint.measureText(playerText)
            val totalWidth = iconSize + iconGap * 2 + separatorWidth + playerWidth
            val startX = centerX - totalWidth / 2f
            
            val separatorFontMetrics = separatorPaint.fontMetrics
            val separatorTop = secondRowCenterY + separatorFontMetrics.top
            val separatorBottom = secondRowCenterY + separatorFontMetrics.bottom
            val separatorActualHeight = separatorBottom - separatorTop
            
            val actualIconSize = separatorActualHeight
            val actualIconGap = separatorActualHeight * 0.3f
            val actualTotalWidth = actualIconSize + actualIconGap * 2 + separatorWidth + playerWidth
            val actualStartX = centerX - actualTotalWidth / 2f
            
            val iconLeft = actualStartX
            val iconRect = RectF(iconLeft, separatorTop, iconLeft + actualIconSize, separatorBottom)
            renderParams.appIcon?.let { appIcon ->
                canvas.drawBitmap(appIcon, null, iconRect, Paint(Paint.ANTI_ALIAS_FLAG))
            }
            
            val separatorX = iconLeft + actualIconSize + actualIconGap
            canvas.drawText("||", separatorX + separatorWidth / 2f, secondRowCenterY, separatorPaint)
            
            val playerX = separatorX + separatorWidth + actualIconGap
            // 高级徕卡模式使用对比色描边，音乐流光模式使用高对比度颜色
            if (!isMusicFlowMode) {
                // 为播放器信息文字添加对比色描边
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = playerPaint.textSize * 0.08f
                    color = invertColorForStroke(textColor)
                }
                canvas.drawText(playerText, playerX, secondRowCenterY, strokePaint)
            } else {
                playerPaint.color = getHighContrastTextColor(frameColor)
            }
            canvas.drawText(playerText, playerX, secondRowCenterY, playerPaint)
        } else {
            val separatorFontMetrics = separatorPaint.fontMetrics
            val separatorTop = secondRowCenterY + separatorFontMetrics.top
            val separatorBottom = secondRowCenterY + separatorFontMetrics.bottom
            val separatorActualHeight = separatorBottom - separatorTop
            val iconSizeCentered = separatorActualHeight
            
            val iconRect = RectF(
                centerX - iconSizeCentered / 2f,
                separatorTop,
                centerX + iconSizeCentered / 2f,
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
                color = if (isMusicFlowMode) {
                    getHighContrastTextColor(frameColor).let { color -> (color and 0x00FFFFFF) or 0xCC000000.toInt() }
                } else {
                    subTextColor
                }
                textSize = separatorHeight * 0.7f
                textAlign = Paint.Align.CENTER
                typeface = if (isMusicFlowMode) {
                    android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                } else {
                    android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }
            }
            // 高级徕卡模式：为相机参数文字添加对比色描边
            if (!isMusicFlowMode) {
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = infoPaint.textSize * 0.08f
                    color = invertColorForStroke(subTextColor)
                }
                canvas.drawText(cameraInfo, centerX, infoY, strokePaint)
            }
            canvas.drawText(cameraInfo, centerX, infoY, infoPaint)
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
            typeface = if (isMusicFlowMode) {
                android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
            } else {
                android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
        }
        val timeY = infoY + separatorHeight * 1.2f
        // 音乐流光模式使用动态高对比度颜色
        if (isMusicFlowMode) {
            timePaint.color = getHighContrastTextColor(frameColor).let { color -> (color and 0x00FFFFFF) or 0xCC000000.toInt() }
        }
        // 高级徕卡模式：为时间戳文字添加对比色描边
        if (!isMusicFlowMode) {
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = timePaint.textSize * 0.08f
                color = invertColorForStroke(subTextColor)
            }
            canvas.drawText(timeText, centerX, timeY, strokePaint)
        }
        canvas.drawText(timeText, centerX, timeY, timePaint)
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
    
    private fun getHighContrastTextColor(stripeColor: Int): Int {
        // 动态计算文字颜色：基于竖纹基色的高对比度颜色
        // 先对竖纹基色取反，再根据亮度微调确保可读性
        val r = (stripeColor shr 16) and 0xFF
        val g = (stripeColor shr 8) and 0xFF
        val b = stripeColor and 0xFF
        val invertedR = 255 - r
        val invertedG = 255 - g
        val invertedB = 255 - b
        val luminance = (0.299 * invertedR + 0.587 * invertedG + 0.114 * invertedB) / 255f
        // 如果反色后亮度过高或过低，进一步调整
        return if (luminance > 0.8f) {
            // 太亮，稍微调暗
            android.graphics.Color.rgb((invertedR * 0.7).toInt(), (invertedG * 0.7).toInt(), (invertedB * 0.7).toInt())
        } else if (luminance < 0.2f) {
            // 太暗，稍微调亮
            android.graphics.Color.rgb((invertedR * 1.3).toInt().coerceAtMost(255), (invertedG * 1.3).toInt().coerceAtMost(255), (invertedB * 1.3).toInt().coerceAtMost(255))
        } else {
            android.graphics.Color.rgb(invertedR, invertedG, invertedB)
        }
    }

    private fun invertColorForStroke(color: Int): Int {
        // 反色描边：原色的反色（保留用于其他地方）
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255f
        return if (luminance > 0.5f) {
            0xFF000000.toInt()
        } else {
            0xFFFFFFFF.toInt()
        }
    }

    private fun adjustColorBrightness(color: Int, brightnessFactor: Float): Int {
        val a = (color shr 24) and 0xFF
        val r = ((color shr 16) and 0xFF) * brightnessFactor
        val g = ((color shr 8) and 0xFF) * brightnessFactor
        val b = (color and 0xFF) * brightnessFactor
        return (a shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }
    
    private fun darkenColor(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * (1f - factor)).coerceIn(0f, 1f)
        return android.graphics.Color.HSVToColor(hsv)
    }
    
    private fun lightenColor(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] + (1f - hsv[2]) * factor).coerceIn(0f, 1f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    /**
     * 合成动态图片相框
     * @param frames 动态图片的所有帧
     * @param config 相框配置
     * @param musicMetadata 音乐元数据
     * @param photoMetadata 照片元数据
     * @param headphoneInfo 耳机信息
     * @param progressCallback 进度回调 (当前帧/总帧数)
     */
    fun composeAnimated(
        frames: List<AnimatedFrame>,
        config: FrameConfig,
        musicMetadata: MusicMetadata?,
        photoMetadata: PhotoMetadata?,
        headphoneInfo: HeadphoneInfo?,
        progressCallback: ((Int, Int) -> Unit)? = null
    ): AnimatedFrameResult {
        if (frames.isEmpty()) {
            throw IllegalArgumentException("Frames list cannot be empty")
        }

        val totalFrames = frames.size
        val resultFrames = mutableListOf<Bitmap>()
        val resultDurations = mutableListOf<Int>()
        
        // 使用第一帧的尺寸作为输出尺寸参考
        val firstFrame = frames[0]
        val sampleParams = DrawRenderParams(
            source = firstFrame.bitmap,
            musicLines = buildMusicLines(musicMetadata),
            photoLines = emptyList(),
            headphoneLines = buildHeadphoneLines(headphoneInfo),
            customText = "",
            frameColor = 0,
            textColor = 0,
            headphoneTextColor = 0,
            appIcon = null,
            headphoneIcon = null,
            config = config
        )
        
        // 计算输出尺寸（只需要计算一次）
        val outputSize = calculateOutputSize(sampleParams, config)
        
        // 逐帧处理
        frames.forEachIndexed { index, animatedFrame ->
            try {
                // 合成当前帧
                val framedBitmap = compose(
                    source = animatedFrame.bitmap,
                    config = config,
                    musicMetadata = musicMetadata,
                    photoMetadata = photoMetadata,
                    headphoneInfo = headphoneInfo
                )
                
                resultFrames += framedBitmap
                resultDurations += animatedFrame.duration
                
                // 进度回调
                progressCallback?.invoke(index + 1, totalFrames)
                
            } catch (e: Exception) {
                // 记录错误但继续处理其他帧
                Log.e("FrameComposer", "Error processing frame $index: ${e.message}")
                // 如果某帧处理失败，使用原图
                resultFrames += animatedFrame.bitmap
                resultDurations += animatedFrame.duration
            }
        }
        
        val totalDuration = resultDurations.sum().toLong()
        
        return AnimatedFrameResult(
            frames = resultFrames,
            frameDurations = resultDurations,
            width = outputSize.width,
            height = outputSize.height,
            totalFrames = totalFrames,
            totalDuration = totalDuration
        )
    }
    
    /**
     * 计算输出尺寸（辅助方法）
     */
    private fun calculateOutputSize(params: DrawRenderParams, config: FrameConfig): android.graphics.Point {
        val source = params.source
        val width = source.width
        val height = source.height
        
        val frameWidth = (min(width, height) * 0.12f).toInt()
        val bottomFrameHeight = (height * 0.25f).toInt()
        val outputWidth = width + frameWidth * 2
        val outputHeight = height + frameWidth + bottomFrameHeight
        
        return android.graphics.Point(outputWidth, outputHeight)
    }
    
    /**
     * 从 Point 获取宽度
     */
    private val android.graphics.Point.width: Int
        get() = x
    
    /**
     * 从 Point 获取高度
     */
    private val android.graphics.Point.height: Int
        get() = y
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
    val headphoneIcon: Bitmap?,
    val config: FrameConfig? = null
)

/**
 * 动态图片合成结果
 */
data class AnimatedFrameResult(
    val frames: List<Bitmap>,
    val frameDurations: List<Int>,
    val width: Int,
    val height: Int,
    val totalFrames: Int,
    val totalDuration: Long
)

/**
 * 动态图片帧数据
 */
data class AnimatedFrame(
    val bitmap: Bitmap,
    val duration: Int
)
