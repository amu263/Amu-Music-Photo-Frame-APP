package com.example.musicframe.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import com.example.musicframe.domain.model.FrameColorMode
import com.example.musicframe.model.HeadphoneInfo
import com.example.musicframe.model.MusicMetadata
import kotlin.math.max
import kotlin.math.min

class FrameComposer {

    /**
     * 获取实际使用的 Typeface，如果配置了自定义字体则使用，否则使用默认字体
     */
    private fun getTypeface(config: FrameConfig?, style: Int = Typeface.NORMAL): Typeface {
        return config?.typeface ?: Typeface.DEFAULT
    }

    /**
     * 获取粗体 Typeface
     */
    private fun getBoldTypeface(config: FrameConfig?): Typeface {
        val base = config?.typeface
        return if (base != null) {
            Typeface.create(base, Typeface.BOLD)
        } else {
            Typeface.DEFAULT_BOLD
        }
    }

    /**
     * 获取斜体 Typeface
     */
    private fun getItalicTypeface(config: FrameConfig?): Typeface {
        val base = config?.typeface
        return if (base != null) {
            Typeface.create(base, Typeface.ITALIC)
        } else {
            Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
    }

    /**
     * 获取粗斜体 Typeface
     */
    private fun getBoldItalicTypeface(config: FrameConfig?): Typeface {
        val base = config?.typeface
        return if (base != null) {
            Typeface.create(base, Typeface.BOLD_ITALIC)
        } else {
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        }
    }

    /**
     * 获取 MONOSPACE 风格的 Typeface（用于播放器名称）
     */
    private fun getMonoTypeface(config: FrameConfig?, style: Int = Typeface.NORMAL): Typeface {
        val base = config?.typeface
        return if (base != null) {
            // 如果有自定义字体，在其基础上应用 style
            Typeface.create(base, style)
        } else {
            // 没有自定义字体时使用系统 MONOSPACE
            Typeface.create(Typeface.MONOSPACE, style)
        }
    }

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
            typeface = getMonoTypeface(config, Typeface.BOLD)
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
        
        // 地点信息（显示在照片顶端中央，国家·省份·城市格式）
    val locationText = config.photoMetadata?.locationText
    val lat = config.photoMetadata?.latitude
    val lon = config.photoMetadata?.longitude
    
    // 最终显示的地点文字：优先用 locationText，否则用 GPS 坐标
    val displayLocationText = when {
        !locationText.isNullOrBlank() -> locationText
        lat != null && lon != null && !(lat == 0.0 && lon == 0.0) -> {
            val latDir = if (lat >= 0) "N" else "S"
            val lonDir = if (lon >= 0) "E" else "W"
            "GPS: ${String.format("%.2f°$latDir", kotlin.math.abs(lat))}, ${String.format("%.2f°$lonDir", kotlin.math.abs(lon))}"
        }
        else -> null
    }
    
    // 调试模式已关闭
    
    // 显示地点文字（如果不为空）
    if (!displayLocationText.isNullOrBlank() && frameWidth > 0) {
            val locationText = displayLocationText  // 保持变量名兼容
            val topFrameCenterY = frameWidth.toFloat() / 2f
            val locationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = frameWidth.toFloat() * 0.35f
                textAlign = Paint.Align.CENTER
                typeface = getBoldTypeface(config)
            }
            
            // 计算文字尺寸以绘制胶囊背景
            val textBounds = android.graphics.Rect()
            locationPaint.getTextBounds(locationText, 0, locationText.length, textBounds)
            val textWidth = textBounds.width().toFloat()
            val textHeight = textBounds.height().toFloat()
            
            // 胶囊背景参数
            val paddingH = frameWidth.toFloat() * 0.15f  // 水平内边距
            val paddingV = frameWidth.toFloat() * 0.08f // 垂直内边距
            val cornerRadius = textHeight * 0.5f + paddingV  // 圆角半径（胶囊状）
            val bgRect = android.graphics.RectF(
                centerX - textWidth / 2 - paddingH,
                topFrameCenterY - textHeight - paddingV,
                centerX + textWidth / 2 + paddingH,
                topFrameCenterY + paddingV
            )
            
            // 获取主色作为文字颜色，对比色作为背景
            val dominantColor = musicMetadata?.dominantColor ?: 0xFF333333.toInt()
            val bgColor = invertedColor(dominantColor)  // 对比色作为背景
            
            // 文字使用主色
            locationPaint.color = dominantColor
            
            // 绘制胶囊背景（带内阴影实现内嵌立体感）
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = bgColor
                setShadowLayer(paddingV * 0.4f, 0f, -paddingV * 0.2f, 0x44000000.toInt())
            }
            canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)
            
            // 绘制文字
            val textY = topFrameCenterY - paddingV * 0.3f
            canvas.drawText(locationText, centerX, textY, locationPaint)
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
        
        // 底部内容（自定义徕卡色模式使用纯色背景，需要高对比色且跳过描边）
        drawLeicaContent(canvas, renderParams, config.photoMetadata, outputWidth, height, bottomFrameHeight, bgColor, frameWidth, isSolidColorMode = true)
        
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
        
        // 底部内容（纯色模式不需要文字描边，避免异色斑点）
        drawLeicaContent(canvas, renderParams, config.photoMetadata, outputWidth, height, bottomFrameHeight, bgColor, frameWidth, isSolidColorMode = true)
        
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
        isSolidColorMode: Boolean = false,
        config: FrameConfig? = null
    ) {
        // 纯色模式和自定义色模式使用相框颜色的高对比色，徕卡模式使用反色
        val textColor = if (isSolidColorMode || isMusicFlowMode) {
            getHighContrastTextColor(frameColor)
        } else {
            invertedColor(frameColor)
        }
        val subTextColor = if (isSolidColorMode || isMusicFlowMode) {
            // 纯色模式下副标题也使用高对比色，但降低不透明度
            (textColor and 0x00FFFFFF) or 0xCC000000.toInt()
        } else {
            (textColor and 0x00FFFFFF) or 0x99000000.toInt()
        }
        val bottomStartY = (frameWidth + height).toFloat()
        val centerX = width / 2f
        
        // 地点信息（显示在照片顶端中央，国家·省份·城市格式，带胶囊背景）
        photoMetadata?.locationText?.let { locationText ->
            if (locationText.isNotBlank() && frameWidth > 0) {
                val topFrameCenterY = frameWidth.toFloat() / 2f
                val locationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt() // 白色文字
                    textSize = frameWidth.toFloat() * 0.35f
                    textAlign = Paint.Align.CENTER
                    typeface = getBoldTypeface(config)
                }
                
                // 计算文字尺寸以绘制胶囊背景
                val textBounds = android.graphics.Rect()
                locationPaint.getTextBounds(locationText, 0, locationText.length, textBounds)
                val textWidthPx = textBounds.width().toFloat()
                val textHeightPx = textBounds.height().toFloat()
                
                // 限制最大宽度
                val maxWidth = width.toFloat() - frameWidth * 4
                if (textWidthPx > maxWidth) {
                    locationPaint.textSize = locationPaint.textSize * (maxWidth / textWidthPx) * 0.95f
                    locationPaint.getTextBounds(locationText, 0, locationText.length, textBounds)
                }
                
                // 胶囊背景参数
                val paddingH = frameWidth.toFloat() * 0.12f
                val paddingV = frameWidth.toFloat() * 0.06f
                val cornerRadius = (textHeightPx + paddingV * 2) * 0.5f
                val bgLeft = centerX - textWidthPx / 2 - paddingH
                val bgTop = topFrameCenterY - textHeightPx - paddingV
                val bgRight = centerX + textWidthPx / 2 + paddingH
                val bgBottom = topFrameCenterY + paddingV
                
                // 确保背景不超出照片区域（照片在 frameWidth 开始）
                val photoLeft = frameWidth.toFloat()
                val photoTop = frameWidth.toFloat()
                val adjustedBgLeft = maxOf(bgLeft, photoLeft - paddingH)
                val adjustedBgRight = minOf(bgRight, (width - frameWidth).toFloat() + paddingH)
                val adjustedBgTop = maxOf(bgTop, photoTop - paddingV)
                
                val bgRect = android.graphics.RectF(adjustedBgLeft, adjustedBgTop, adjustedBgRight, bgBottom)
                
                // 使用相框颜色的反色作为文字颜色，对比色作为背景
                val textColor = getHighContrastTextColor(frameColor)
                val bgColor = frameColor  // 背景使用相框原色
                
                // 文字使用高对比色
                locationPaint.color = textColor
                
                // 绘制胶囊背景
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = bgColor
                    setShadowLayer(paddingV * 0.4f, 0f, -paddingV * 0.2f, 0x44000000.toInt())
                }
                canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)
                
                // 绘制文字（垂直居中于胶囊背景）
                val textY = adjustedBgTop + (bgRect.height() + textHeightPx) / 2 - textBounds.bottom
                canvas.drawText(locationText, centerX, textY, locationPaint)
                
                // 调试信息已关闭
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
                getItalicTypeface(config)
            } else {
                getBoldTypeface(config)
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
            // 纯色模式不需要描边，避免异色斑点
            if (!isMusicFlowMode && !isSolidColorMode) {
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
                getMonoTypeface(config, Typeface.ITALIC)
            } else {
                getMonoTypeface(config, Typeface.BOLD)
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
            // 纯色模式不需要描边，避免异色斑点
            if (!isMusicFlowMode && !isSolidColorMode) {
                // 为播放器信息文字添加对比色描边
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = playerPaint.textSize * 0.08f
                    color = invertColorForStroke(textColor)
                }
                canvas.drawText(playerText, playerX, secondRowCenterY, strokePaint)
            } else if (isMusicFlowMode) {
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
                    getItalicTypeface(config)
                } else {
                    getBoldTypeface(config)
                }
            }
            // 高级徕卡模式：为相机参数文字添加对比色描边
            // 纯色模式不需要描边，避免异色斑点
            if (!isMusicFlowMode && !isSolidColorMode) {
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
                getItalicTypeface(config)
            } else {
                getBoldTypeface(config)
            }
        }
        val timeY = infoY + separatorHeight * 1.2f
        // 音乐流光模式使用动态高对比度颜色
        if (isMusicFlowMode) {
            timePaint.color = getHighContrastTextColor(frameColor).let { color -> (color and 0x00FFFFFF) or 0xCC000000.toInt() }
        }
        // 高级徕卡模式：为时间戳文字添加对比色描边
        // 纯色模式不需要描边，避免异色斑点
        if (!isMusicFlowMode && !isSolidColorMode) {
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
