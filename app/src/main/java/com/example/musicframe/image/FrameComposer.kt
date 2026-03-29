package com.example.musicframe.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import androidx.core.graphics.ColorUtils
import com.example.musicframe.domain.model.AdaptiveBorderParams
import com.example.musicframe.domain.model.BorderRenderer
import com.example.musicframe.domain.model.CaptionDrawParams
import com.example.musicframe.domain.model.DrawRenderParams
import com.example.musicframe.model.HeadphoneInfo
import com.example.musicframe.model.MusicMetadata
import kotlin.math.ceil
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
        val musicLines = buildMusicLines(config, musicMetadata)
        val photoLines = buildPhotoLines(config, photoMetadata)
        val headphoneLines = buildHeadphoneLines(config, headphoneInfo)
        val customText = if (config.showCustomText) config.customBottomText else ""
        val frameColor = config.resolvedFrameColor(musicMetadata?.dominantColor)
        val textColor = config.customTextColor() ?: invertedColor(frameColor)
        val headphoneColor = config.headphoneColor(invertedColor(frameColor))
        val headphoneIcon = if (headphoneLines.isNotEmpty()) createHeadphoneIcon(invertedColor(frameColor)) else null
        val badgeIcon = musicMetadata?.appIcon
        val renderParams = DrawRenderParams(
            source = source,
            musicLines = musicLines,
            photoLines = photoLines,
            headphoneLines = headphoneLines,
            customText = customText,
            frameColor = frameColor,
            textColor = textColor,
            headphoneTextColor = headphoneColor,
            appIcon = badgeIcon,
            headphoneIcon = headphoneIcon
        )
        return if (config.overlayOnly || config.frameMode == FrameMode.FLOATING_CARD) {
            drawOverlay(renderParams, config)
        } else {
            drawFramed(renderParams, config)
        }
    }

    private fun drawFramed(
        renderParams: DrawRenderParams,
        config: FrameConfig
    ): Bitmap {
        val minSize = min(renderParams.source.width, renderParams.source.height)
        val baseBorder = max(12f, minSize * config.frameRatio)
        val extraBottom = max(0f, minSize * config.bottomExtraRatio)
        val adaptiveBorder = calculateAdaptiveBorder(
            AdaptiveBorderParams(
                baseBorder = baseBorder,
                minSize = minSize,
                musicLines = renderParams.musicLines,
                photoLines = renderParams.photoLines,
                headphoneLines = renderParams.headphoneLines,
                extraBottom = extraBottom,
                customText = renderParams.customText,
                musicScale = config.musicTextScale,
                photoScale = config.photoTextScale,
                headphoneScale = config.headphoneTextScale,
                customScale = config.customTextScale
            )
        )
        val bottomBorder = ceil(adaptiveBorder + extraBottom).toInt()
        val sideBorder = ceil(adaptiveBorder).toInt()

        return when (config.frameMode) {
            FrameMode.FULL_BORDER -> drawFullBorder(renderParams, sideBorder, bottomBorder, config)
            FrameMode.BOTTOM_BAR -> drawBottomBar(renderParams, sideBorder, bottomBorder, config)
            FrameMode.BOTTOM_STRIPE -> drawBottomStripe(renderParams, sideBorder, max(12, bottomBorder / 2), config)
            FrameMode.CUSTOM_CARD -> drawCustomCard(renderParams, sideBorder, bottomBorder, config)
            FrameMode.FLOATING_CARD -> drawOverlay(renderParams, config)
            FrameMode.PREMIUM_LEICA -> drawPremiumLeica(renderParams, config)
            FrameMode.CUSTOM_LEICA -> drawCustomLeica(renderParams, config)
            FrameMode.MUSIC_FLOW -> drawMusicFlow(renderParams, config)
            FrameMode.MUSIC_SOLID -> drawMusicSolid(renderParams, config)
        }
    }

    private fun drawFullBorder(
        renderParams: DrawRenderParams,
        sideBorder: Int,
        bottomBorder: Int,
        config: FrameConfig
    ): Bitmap {
        val output = Bitmap.createBitmap(
            renderParams.source.width + sideBorder * 2,
            renderParams.source.height + sideBorder + bottomBorder,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        val framePaint = BorderRenderer.createFramePaint(
            renderParams.frameColor,
            output.width.toFloat(),
            output.height.toFloat(),
            config.staticFlowFrame
        )
        BorderRenderer.drawFullFrameBackground(canvas, output, framePaint)
        canvas.drawBitmap(renderParams.source, sideBorder.toFloat(), sideBorder.toFloat(), null)

        val captionBounds = RectF(
            sideBorder.toFloat(),
            renderParams.source.height.toFloat() + sideBorder,
            output.width.toFloat() - sideBorder.toFloat(),
            output.height.toFloat()
        )
        TextLayoutComposer.drawCaptionArea(
            CaptionDrawParams(
                canvas = canvas,
                bounds = captionBounds,
                musicLines = renderParams.musicLines,
                photoLines = renderParams.photoLines,
                headphoneLines = renderParams.headphoneLines,
                customText = renderParams.customText,
                frameColor = renderParams.frameColor,
                textColor = renderParams.textColor,
                headphoneTextColor = renderParams.headphoneTextColor,
                typeface = config.typeface,
                appIcon = renderParams.appIcon,
                headphoneIcon = renderParams.headphoneIcon,
                alignLinesToEnd = true,
                overlayOnly = config.overlayOnly,
                musicTextScale = config.musicTextScale,
                photoTextScale = config.photoTextScale,
                headphoneTextScale = config.headphoneTextScale,
                customTextScale = config.customTextScale
            )
        )
        return output
    }

    private fun drawBottomBar(
        renderParams: DrawRenderParams,
        sideBorder: Int,
        bottomBorder: Int,
        config: FrameConfig
    ): Bitmap {
        val horizontalPadding = (sideBorder * 0.6f).toInt().coerceAtLeast(8)
        val output = Bitmap.createBitmap(
            renderParams.source.width,
            renderParams.source.height + bottomBorder,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        val framePaint = BorderRenderer.createFramePaint(
            renderParams.frameColor,
            output.width.toFloat(),
            output.height.toFloat(),
            config.staticFlowFrame
        )
        BorderRenderer.drawFullFrameBackground(canvas, output, framePaint)
        canvas.drawBitmap(renderParams.source, 0f, 0f, null)

        val captionBounds = RectF(
            horizontalPadding.toFloat(),
            output.height.toFloat() - bottomBorder.toFloat(),
            output.width.toFloat() - horizontalPadding.toFloat(),
            output.height.toFloat()
        )
        TextLayoutComposer.drawCaptionArea(
            CaptionDrawParams(
                canvas = canvas,
                bounds = captionBounds,
                musicLines = renderParams.musicLines,
                photoLines = renderParams.photoLines,
                headphoneLines = renderParams.headphoneLines,
                customText = renderParams.customText,
                frameColor = renderParams.frameColor,
                textColor = renderParams.textColor,
                headphoneTextColor = renderParams.headphoneTextColor,
                typeface = config.typeface,
                appIcon = renderParams.appIcon,
                headphoneIcon = renderParams.headphoneIcon,
                alignLinesToEnd = true,
                overlayOnly = config.overlayOnly,
                musicTextScale = config.musicTextScale,
                photoTextScale = config.photoTextScale,
                headphoneTextScale = config.headphoneTextScale,
                customTextScale = config.customTextScale
            )
        )
        return output
    }

    private fun drawBottomStripe(
        renderParams: DrawRenderParams,
        sideBorder: Int,
        bottomBorder: Int,
        config: FrameConfig
    ): Bitmap {
        val output = Bitmap.createBitmap(
            renderParams.source.width,
            renderParams.source.height + bottomBorder,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        canvas.drawBitmap(renderParams.source, 0f, sideBorder.toFloat() * 0.35f, null)

        val stripeTop = output.height - bottomBorder
        val stripeRect = RectF(0f, stripeTop.toFloat(), output.width.toFloat(), output.height.toFloat())
        val stripePaint = BorderRenderer.createFramePaint(
            renderParams.frameColor,
            output.width.toFloat(),
            bottomBorder.toFloat(),
            config.staticFlowFrame
        )
        BorderRenderer.drawBottomStripeBackground(canvas, stripeRect, stripePaint)

        val captionBounds = RectF(
            sideBorder.toFloat(),
            stripeTop.toFloat(),
            output.width.toFloat() - sideBorder.toFloat(),
            output.height.toFloat()
        )
        TextLayoutComposer.drawCaptionArea(
            CaptionDrawParams(
                canvas = canvas,
                bounds = captionBounds,
                musicLines = renderParams.musicLines,
                photoLines = renderParams.photoLines,
                headphoneLines = renderParams.headphoneLines,
                customText = renderParams.customText,
                frameColor = renderParams.frameColor,
                textColor = renderParams.textColor,
                headphoneTextColor = renderParams.headphoneTextColor,
                typeface = config.typeface,
                appIcon = renderParams.appIcon,
                headphoneIcon = renderParams.headphoneIcon,
                alignLinesToEnd = true,
                overlayOnly = config.overlayOnly,
                musicTextScale = config.musicTextScale,
                photoTextScale = config.photoTextScale,
                headphoneTextScale = config.headphoneTextScale,
                customTextScale = config.customTextScale
            )
        )
        return output
    }

    private fun drawCustomCard(
        renderParams: DrawRenderParams,
        sideBorder: Int,
        bottomBorder: Int,
        config: FrameConfig
    ): Bitmap {
        val innerPadding = (sideBorder * 0.7f).toInt().coerceAtLeast(8)
        val textAreaHeight = (bottomBorder + sideBorder * 1.3f).toInt()
        val availableWidth = renderParams.source.width + innerPadding * 2
        val scaledHeight = (renderParams.source.height * 0.82f).toInt()
        val scaledWidth = (renderParams.source.width * 0.82f).toInt()
        val scaledBitmap = Bitmap.createScaledBitmap(renderParams.source, scaledWidth, scaledHeight, true)

        val output = Bitmap.createBitmap(
            max(availableWidth + sideBorder * 2, scaledBitmap.width + innerPadding * 2 + sideBorder * 2),
            scaledBitmap.height + textAreaHeight + innerPadding * 2 + sideBorder * 2,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        val framePaint = BorderRenderer.createFramePaint(
            renderParams.frameColor,
            output.width.toFloat(),
            output.height.toFloat(),
            config.staticFlowFrame
        )
        BorderRenderer.drawFullFrameBackground(canvas, output, framePaint)

        val imageLeft = (output.width - scaledBitmap.width) / 2f
        val imageTop = sideBorder.toFloat()
        canvas.drawBitmap(scaledBitmap, imageLeft, imageTop, null)

        val captionBounds = RectF(
            sideBorder + innerPadding.toFloat(),
            imageTop + scaledBitmap.height + innerPadding,
            output.width - sideBorder - innerPadding.toFloat(),
            output.height - sideBorder.toFloat()
        )
        TextLayoutComposer.drawCaptionArea(
            CaptionDrawParams(
                canvas = canvas,
                bounds = captionBounds,
                musicLines = renderParams.musicLines,
                photoLines = renderParams.photoLines,
                headphoneLines = renderParams.headphoneLines,
                customText = renderParams.customText,
                frameColor = renderParams.frameColor,
                textColor = renderParams.textColor,
                headphoneTextColor = renderParams.headphoneTextColor,
                typeface = config.typeface,
                appIcon = renderParams.appIcon,
                headphoneIcon = renderParams.headphoneIcon,
                alignLinesToEnd = false,
                centerCustomText = true,
                paddingScale = 0.22f,
                overlayOnly = config.overlayOnly,
                musicTextScale = config.musicTextScale,
                photoTextScale = config.photoTextScale,
                headphoneTextScale = config.headphoneTextScale,
                customTextScale = config.customTextScale
            )
        )
        return output
    }

    private fun drawPremiumLeica(
        renderParams: DrawRenderParams,
        config: FrameConfig
    ): Bitmap {
        val source = renderParams.source
        val width = source.width
        val height = source.height
        
        // 外框宽度：照片短边的 12%（四周）
        val frameWidth = (min(width, height) * 0.12f).toInt()
        // 底部外框额外加高：照片高度的 25%
        val bottomFrameHeight = (height * 0.25f).toInt()
        val outputWidth = width + frameWidth * 2
        val outputHeight = height + frameWidth + bottomFrameHeight
        
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        // 1. 先绘制整个画布的毛玻璃模糊背景（四周外框）- 采样主色调模糊
        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val blurScale = 0.08f  // 8% 缩放采样主色调模糊
        val scaledW = (outputWidth * blurScale).toInt().coerceAtLeast(1)
        val scaledH = (outputHeight * blurScale).toInt().coerceAtLeast(1)
        val blurredBg = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)
        val blurredCanvas = Canvas(blurredBg)
        blurredCanvas.drawBitmap(source, null, android.graphics.Rect(0, 0, scaledW, scaledH), blurPaint)
        canvas.drawBitmap(blurredBg, null, android.graphics.Rect(0, 0, outputWidth, outputHeight), blurPaint)
        blurredBg.recycle()
        
        // 2. 叠加半透明黑色让背景变暗
        val darkOverlay = Paint().apply { color = 0x66000000.toInt() }
        canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), darkOverlay)
        
        // 3. 在中央绘制清晰的原图
        canvas.drawBitmap(source, frameWidth.toFloat(), frameWidth.toFloat(), null)
        
        // 计算动态反色（基于外框颜色）
        val frameColor = renderParams.frameColor.toInt()
        val textColor = invertedColor(frameColor)
        val subTextColor = (textColor and 0x00FFFFFF) or 0x99000000.toInt()  // 60% 不透明度
        
        // 4. 在底部外框区域绘制内容
        val centerX = outputWidth / 2f
        val bottomStartY = frameWidth + height.toFloat()
        val actualBottomHeight = bottomFrameHeight.toFloat()
        
        // 两栏的纵向中心轴（外框纵向中心）
        val frameCenterY = bottomStartY + actualBottomHeight / 2f
        
        // 第一栏：只留歌名（居中对齐）
        val musicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = actualBottomHeight * 0.14f
            textAlign = Paint.Align.CENTER
        }
        val firstRowY = frameCenterY - actualBottomHeight * 0.18f
        if (renderParams.musicLines.isNotEmpty()) {
            val musicText = renderParams.musicLines[0]
            // 计算文字宽度，如果超出则缩小
            val textWidth = musicPaint.measureText(musicText)
            val maxWidth = outputWidth - frameWidth * 4
            var scaleMusic = musicPaint.textSize
            if (textWidth > maxWidth) {
                scaleMusic = musicPaint.textSize * (maxWidth / textWidth) * 0.95f
            }
            musicPaint.textSize = scaleMusic
            canvas.drawText(musicText, centerX, firstRowY, musicPaint)
        }
        
        // 第二栏：图标 || 音乐播放器名称（三者作为整体居中对齐）
        // 纵向位置：图标底部和参数顶部之间的正中间
        val cameraTopY = bottomStartY + actualBottomHeight * 0.78f
        val musicBottomY = firstRowY + musicPaint.textSize
        val secondRowCenterY = (musicBottomY + cameraTopY) / 2f
        val separatorHeight = actualBottomHeight * 0.12f
        
        // || 分隔符
        val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = separatorHeight
            textAlign = Paint.Align.CENTER
        }
        val separatorWidth = separatorPaint.measureText("||")
        
        // 音乐播放器名称（动态获取）
        val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = separatorHeight * 0.9f
            textAlign = Paint.Align.LEFT
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD
            )
        }
        // 从 renderParams 获取音乐播放器名称（第二行信息）
        val playerText = renderParams.musicLines.getOrElse(1) { "光锥音乐" }
        val playerWidth = playerPaint.measureText(playerText)
        
        // 使用 || 的边界作为对齐基准
        val separatorFontMetrics = separatorPaint.fontMetrics
        val separatorTop = secondRowCenterY + separatorFontMetrics.top
        val separatorBottom = secondRowCenterY + separatorFontMetrics.bottom
        val separatorActualHeight = separatorBottom - separatorTop  // || 的实际字体高度
        
        // 计算整体宽度：图标 + 间距 + || + 间距 + 文字
        val iconSize = separatorActualHeight  // 图标是正方形，边长 = || 的实际高度
        val iconGap = separatorActualHeight * 0.3f  // 图标与 || 的间距
        val totalWidth = iconSize + iconGap * 2 + separatorWidth + playerWidth
        
        // 整体居中对齐：起始 X = 中心 X - 整体宽度 / 2
        val startX = centerX - totalWidth / 2f
        
        // 绘制 App 图标（左侧，正方形，上下边缘与 || 完全对齐）
        val iconLeft = startX
        val iconRect = RectF(iconLeft, separatorTop, iconLeft + iconSize, separatorBottom)
        renderParams.appIcon?.let { appIcon ->
            canvas.drawBitmap(appIcon, null, iconRect, Paint(Paint.ANTI_ALIAS_FLAG))
        }
        
        // 绘制 || 分隔符（图标右侧）
        val separatorX = iconLeft + iconSize + iconGap
        canvas.drawText("||", separatorX + separatorWidth / 2f, secondRowCenterY, separatorPaint)
        
        // 绘制音乐播放器名称（|| 右侧，与 || 基线对齐）
        val playerX = separatorX + separatorWidth + iconGap
        canvas.drawText(playerText, playerX, secondRowCenterY, playerPaint)
        
        // 相机参数
        val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subTextColor
            textSize = separatorHeight * 0.7f
            textAlign = Paint.Align.CENTER
        }
        val infoY = bottomStartY + actualBottomHeight * 0.78f
        canvas.drawText("26mm  f/1.8  1/120s  ISO100", centerX, infoY, infoPaint)
        
        // 时间戳 - 优先使用照片原始拍摄时间，否则使用当前时间
        val timeText = config.photoMetadata?.createdDateTime?.let { rawDate ->
            // 将 "yyyy-MM-dd HH:mm" 格式转换为 "yyyy.MM.dd HH:mm"
            runCatching {
                val parser = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                val date = parser.parse(rawDate)
                val formatter = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault())
                formatter.format(date ?: java.util.Date())
            }.getOrNull() ?: rawDate
        } ?: run {
            // 如果没有 EXIF 时间，使用当前时间作为后备
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
        
        // 地点信息 - 在顶部外框区域显示（国家 · 城市）
        val locationText = config.photoMetadata?.locationText
        Log.d("FrameComposer", "drawPremiumLeica: locationText='$locationText', frameWidth=$frameWidth")
        if (!locationText.isNullOrBlank() && frameWidth > 0) {
            // 顶部外框的纵向中心
            val topFrameCenterY = frameWidth.toFloat() / 2f
            val locationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = frameWidth.toFloat() * 0.35f
                textAlign = Paint.Align.CENTER
            }
            // 计算文字宽度，如果超出则缩小
            val textWidth = locationPaint.measureText(locationText)
            val maxWidth = outputWidth.toFloat() - frameWidth * 4
            if (textWidth > maxWidth) {
                locationPaint.textSize = locationPaint.textSize * (maxWidth / textWidth) * 0.95f
            }
            Log.d("FrameComposer", "Drawing location text at y=${topFrameCenterY + locationPaint.textSize * 0.35f}")
            canvas.drawText(locationText, centerX, topFrameCenterY + locationPaint.textSize * 0.35f, locationPaint)
        }
        
        return output
    }

    // 自定义徕卡式：自定义颜色外框（和高级徕卡式一样，只是外框颜色不同）
    private fun drawCustomLeica(
        renderParams: DrawRenderParams,
        config: FrameConfig
    ): Bitmap {
        val source = renderParams.source
        val width = source.width
        val height = source.height
        
        // 外框宽度：照片短边的 12%（四周）
        val frameWidth = (min(width, height) * 0.12f).toInt()
        // 底部外框额外加高：照片高度的 25%
        val bottomFrameHeight = (height * 0.25f).toInt()
        val outputWidth = width + frameWidth * 2
        val outputHeight = height + frameWidth + bottomFrameHeight
        
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        // 1. 绘制整个画布的自定义颜色纯色外框（不是毛玻璃）
        val frameColor = config.userFrameColor ?: 0xFF333333.toInt()
        val framePaint = Paint().apply { color = frameColor }
        canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), framePaint)
        
        // 2. 在中央绘制清晰的原图
        canvas.drawBitmap(source, frameWidth.toFloat(), frameWidth.toFloat(), null)
        
        // 3. 在底部外框区域绘制内容
        drawLeicaContent(canvas, renderParams, config.photoMetadata, outputWidth, height, bottomFrameHeight, frameColor, frameWidth)

        return output
    }

    // 音乐高级态：音乐封面静态流光色外框（和高级徕卡式一样，只是外框是流光色）
    private fun drawMusicFlow(
        renderParams: DrawRenderParams,
        config: FrameConfig
    ): Bitmap {
        val source = renderParams.source
        val width = source.width
        val height = source.height
        
        // 外框宽度：照片短边的 12%（四周）
        val frameWidth = (min(width, height) * 0.12f).toInt()
        // 底部外框额外加高：照片高度的 25%
        val bottomFrameHeight = (height * 0.25f).toInt()
        val outputWidth = width + frameWidth * 2
        val outputHeight = height + frameWidth + bottomFrameHeight
        
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        // 1. 绘制音乐封面颜色的静态流光外框（不是毛玻璃）
        val frameColor = renderParams.frameColor.toInt()
        val flowPaint = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, outputHeight.toFloat(),
                intArrayOf(
                    (frameColor and 0x00FFFFFF) or 0x50000000.toInt(),
                    (frameColor and 0x00FFFFFF) or 0x90000000.toInt(),
                    (frameColor and 0x00FFFFFF) or 0x50000000.toInt()
                ),
                floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), flowPaint)
        
        // 2. 在中央绘制清晰的原图
        canvas.drawBitmap(source, frameWidth.toFloat(), frameWidth.toFloat(), null)
        
        // 3. 在底部外框区域绘制内容
        drawLeicaContent(canvas, renderParams, config.photoMetadata, outputWidth, height, bottomFrameHeight, frameColor, frameWidth)
        
        return output
    }

    // 音乐高级雅：音乐封面主色调纯色外框（和高级徕卡式一样，只是外框是音乐封面色）
    private fun drawMusicSolid(
        renderParams: DrawRenderParams,
        config: FrameConfig
    ): Bitmap {
        val source = renderParams.source
        val width = source.width
        val height = source.height
        
        // 外框宽度：照片短边的 12%（四周）
        val frameWidth = (min(width, height) * 0.12f).toInt()
        // 底部外框额外加高：照片高度的 25%
        val bottomFrameHeight = (height * 0.25f).toInt()
        val outputWidth = width + frameWidth * 2
        val outputHeight = height + frameWidth + bottomFrameHeight
        
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        // 1. 绘制音乐封面主色调的纯色外框（不是毛玻璃）
        val frameColor = renderParams.frameColor.toInt()
        val framePaint = Paint().apply {
            color = (frameColor and 0x00FFFFFF) or 0x70000000  // 70% 不透明度
        }
        canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), framePaint)
        
        // 2. 在中央绘制清晰的原图
        canvas.drawBitmap(source, frameWidth.toFloat(), frameWidth.toFloat(), null)
        
        // 3. 在底部外框区域绘制内容
        drawLeicaContent(canvas, renderParams, config.photoMetadata, outputWidth, height, bottomFrameHeight, frameColor, frameWidth)
        
        return output
    }

    // 提取公共内容绘制逻辑
    private fun drawLeicaContent(
        canvas: Canvas,
        renderParams: DrawRenderParams,
        photoMetadata: PhotoMetadata?,
        width: Int,
        height: Int,
        frameHeight: Int,
        frameColor: Int,
        frameWidth: Int = 0  // 四周外框模式的边框宽度
    ) {
        Log.d("FrameComposer", "drawLeicaContent: locationText='${photoMetadata?.locationText}', frameWidth=$frameWidth")
        
        // 计算动态反色（基于外框颜色）
        val textColor = invertedColor(frameColor)
        val subTextColor = (textColor and 0x00FFFFFF) or 0x99000000.toInt()  // 60% 不透明度
        
        // 如果有四周外框，底部起始位置是 frameWidth + height
        val bottomStartY = (frameWidth + height).toFloat()
        
        // 0. 地点信息 - 在顶部外框区域显示（国家 · 城市）
        photoMetadata?.locationText?.let { locationText ->
            Log.d("FrameComposer", "drawLeicaContent: drawing locationText='$locationText', frameWidth=$frameWidth")
            if (locationText.isNotBlank() && frameWidth > 0) {
                // 顶部外框的纵向中心
                val topFrameCenterY = frameWidth.toFloat() / 2f
                val locationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor
                    textSize = frameWidth.toFloat() * 0.35f
                    textAlign = Paint.Align.CENTER
                }
                // 计算文字宽度，如果超出则缩小
                val textWidth = locationPaint.measureText(locationText)
                val maxWidth = width.toFloat() - frameWidth * 4
                if (textWidth > maxWidth) {
                    locationPaint.textSize = locationPaint.textSize * (maxWidth / textWidth) * 0.95f
                }
                canvas.drawText(locationText, width / 2f, topFrameCenterY + locationPaint.textSize * 0.35f, locationPaint)
            }
        }
        
        // 1. 第一栏：只留歌名（居中对齐）
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
            var scaleMusic = musicPaint.textSize
            if (textWidth > maxWidth) {
                scaleMusic = musicPaint.textSize * (maxWidth / textWidth) * 0.95f
            }
            musicPaint.textSize = scaleMusic
            canvas.drawText(musicText, width / 2f, firstRowY, musicPaint)
        }
        
        // 2. 第二栏：图标 || 音乐播放器名称
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
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD
            )
        }
        val playerText = renderParams.musicLines.getOrElse(1) { "光锥音乐" }
        val playerWidth = playerPaint.measureText(playerText)
        
        val iconSize = separatorHeight
        val iconGap = separatorHeight * 0.3f
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
        
        // 3. 相机参数
        val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subTextColor
            textSize = separatorHeight * 0.7f
            textAlign = Paint.Align.CENTER
        }
        val infoY = bottomStartY + frameHeight * 0.78f
        canvas.drawText("26mm  f/1.8  1/120s  ISO100", width / 2f, infoY, infoPaint)
        
        // 4. 时间戳 - 优先使用照片原始拍摄时间，否则使用当前时间
        val timeText = photoMetadata?.createdDateTime?.let { rawDate ->
            // 将 "yyyy-MM-dd HH:mm" 格式转换为 "yyyy.MM.dd HH:mm"
            runCatching {
                val parser = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                val date = parser.parse(rawDate)
                val formatter = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault())
                formatter.format(date ?: java.util.Date())
            }.getOrNull() ?: rawDate
        } ?: run {
            // 如果没有 EXIF 时间，使用当前时间作为后备
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

    private fun drawOverlay(
        renderParams: DrawRenderParams,
        config: FrameConfig
    ): Bitmap {
        val output = renderParams.source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val border = max(12f, min(renderParams.source.width, renderParams.source.height) * config.frameRatio).toInt()
        val bounds = RectF(0f, 0f, renderParams.source.width.toFloat(), renderParams.source.height.toFloat())
        val bgColor = if (config.overlayOnly) null else renderParams.frameColor.copyAlpha(config.overlayBackgroundAlpha)
        TextLayoutComposer.drawCaptionArea(
            CaptionDrawParams(
                canvas = canvas,
                bounds = bounds,
                musicLines = renderParams.musicLines,
                photoLines = renderParams.photoLines,
                headphoneLines = renderParams.headphoneLines,
                customText = renderParams.customText,
                frameColor = renderParams.frameColor,
                textColor = renderParams.textColor,
                headphoneTextColor = renderParams.headphoneTextColor,
                backgroundColor = bgColor,
                typeface = config.typeface,
                appIcon = renderParams.appIcon,
                headphoneIcon = renderParams.headphoneIcon,
                paddingScale = 0.4f,
                borderSize = border,
                centerCustomText = true,
                overlayOnly = config.overlayOnly,
                musicTextScale = config.musicTextScale,
                photoTextScale = config.photoTextScale,
                headphoneTextScale = config.headphoneTextScale,
                customTextScale = config.customTextScale
            )
        )
        return output
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
        val leftCup = RectF(
            arcRect.left - cupWidth * 0.65f,
            centerY - cupHeight / 2f,
            arcRect.left - cupWidth * 0.1f,
            centerY + cupHeight / 2f
        )
        val rightCup = RectF(
            arcRect.right + cupWidth * 0.1f,
            centerY - cupHeight / 2f,
            arcRect.right + cupWidth * 0.65f,
            centerY + cupHeight / 2f
        )
        canvas.drawRoundRect(leftCup, cupRadius, cupRadius, fillPaint)
        canvas.drawRoundRect(rightCup, cupRadius, cupRadius, fillPaint)
        return bitmap
    }

    private fun buildMusicLines(config: FrameConfig, musicMetadata: MusicMetadata?): List<String> {
        if (!config.showMusicMetadata || musicMetadata == null) return emptyList()
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

    private fun buildPhotoLines(config: FrameConfig, photoMetadata: PhotoMetadata?): List<String> {
        if (!config.showPhotoMetadata || photoMetadata == null) return emptyList()
        val readable = photoMetadata.asReadableText()
        return if (readable.isNotBlank()) listOf(readable) else emptyList()
    }

    private fun buildHeadphoneLines(config: FrameConfig, headphoneInfo: HeadphoneInfo?): List<String> {
        if (!config.showHeadphoneInfo || headphoneInfo == null) return emptyList()
        val readable = headphoneInfo.asDisplayLine()
        return if (readable.isNotBlank()) listOf(readable) else emptyList()
    }

    private fun calculateAdaptiveBorder(params: AdaptiveBorderParams): Float {
        val hasContent = params.musicLines.isNotEmpty() || params.photoLines.isNotEmpty() ||
            params.headphoneLines.isNotEmpty() || params.customText.isNotBlank()
        if (!hasContent) return params.baseBorder
        val baseTextSize = max(params.minSize * 0.018f, params.baseBorder * 0.2f)
        val weightedLines =
            params.musicLines.size * params.musicScale +
                params.photoLines.size * params.photoScale +
                params.headphoneLines.size * params.headphoneScale +
                if (params.customText.isNotBlank()) params.customScale else 0f
        val requiredHeight = baseTextSize * weightedLines * 1.3f
        val padding = baseTextSize * 1.1f
        return max(params.baseBorder, requiredHeight + padding + params.extraBottom)
    }

    private fun invertedColor(color: Int): Int {
        // 计算颜色的亮度（感知亮度公式）
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255f
        
        // 根据背景亮度选择对比色：亮背景用深色，暗背景用浅色
        return if (luminance > 0.5f) {
            // 亮背景：使用深色文字（接近黑色）
            0xFF1A1A1A.toInt()
        } else {
            // 暗背景：使用浅色文字（接近白色）
            0xFFF5F5F5.toInt()
        }
    }

    private fun Int.copyAlpha(alpha: Float): Int {
        val clamped = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return (this and 0x00FFFFFF) or (clamped shl 24)
    }
}
