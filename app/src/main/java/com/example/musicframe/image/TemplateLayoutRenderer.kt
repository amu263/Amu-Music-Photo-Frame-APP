package com.example.musicframe.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.musicframe.domain.model.FrameColorMode
import com.example.musicframe.model.MusicMetadata

/**
 * 模板排版渲染器 — 8种布局独立实现
 */
object TemplateLayoutRenderer {

    fun render(
        source: Bitmap,
        config: FrameConfig,
        templateConfig: TemplateConfig,
        musicMetadata: MusicMetadata?,
        photoMetadata: PhotoMetadata?
    ): Bitmap {
        val dominantColor = musicMetadata?.dominantColor ?: 0xFF333333.toInt()
        val bgColor = config.getFrameColor(dominantColor)
        val textColor = Color.rgb(255 - Color.red(bgColor), 255 - Color.green(bgColor), 255 - Color.blue(bgColor))

        return when (templateConfig.template) {
            LayoutTemplate.CLASSIC_CENTER -> renderClassicCenter(source, bgColor, textColor, musicMetadata, photoMetadata, templateConfig)
            LayoutTemplate.BOTTOM_STRIP -> renderBottomStrip(source, bgColor, textColor, musicMetadata, photoMetadata, templateConfig)
            LayoutTemplate.SPLIT_SCREEN -> renderSplitScreen(source, bgColor, textColor, musicMetadata, photoMetadata, templateConfig)
            LayoutTemplate.MAGAZINE -> renderMagazine(source, bgColor, textColor, musicMetadata, photoMetadata, templateConfig)
            LayoutTemplate.POLAROID -> renderPolaroid(source, bgColor, textColor, musicMetadata, photoMetadata, templateConfig)
            LayoutTemplate.MINIMAL_TOP -> renderMinimalTop(source, bgColor, textColor, musicMetadata, photoMetadata, templateConfig)
            LayoutTemplate.WIDE_BANNER -> renderWideBanner(source, bgColor, textColor, musicMetadata, photoMetadata, templateConfig)
            LayoutTemplate.CORNER_BADGE -> renderCornerBadge(source, bgColor, textColor, musicMetadata, photoMetadata, templateConfig)
        }
    }

    /** 经典居中 — 图片居中，白框 + 底部文字 */
    private fun renderClassicCenter(img: Bitmap, bg: Int, tc: Int, mm: MusicMetadata?, pm: PhotoMetadata?, cfg: TemplateConfig): Bitmap {
        val border = (img.width * 0.06f).toInt()
        val bottom = (img.height * 0.2f).toInt()
        val outW = img.width + border * 2
        val outH = img.height + border * 2 + bottom
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        canvas.drawColor(bg)
        canvas.drawBitmap(img, border.toFloat(), border.toFloat(), null)

        val titlePaint = textPaint(tc, bottom * 0.18f, Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
        val subPaint = textPaint(tc, bottom * 0.12f, Typeface.DEFAULT)

        mm?.let {
            val cx = outW / 2f
            val midY = img.height + border + bottom * 0.35f
            canvas.drawText(it.title.ifEmpty { " " }, cx, midY, titlePaint)
            if (cfg.showPhotoInfo && it.artist.isNotBlank()) {
                canvas.drawText(it.artist, cx, midY + titlePaint.textSize * 1.3f, subPaint)
            }
        }

        drawWatermark(canvas, outW.toFloat(), outH.toFloat(), cfg, tc)
        return result
    }

    /** 底部信息条 — 图片全宽，底部半透明渐变条 + 信息 */
    private fun renderBottomStrip(img: Bitmap, bg: Int, tc: Int, mm: MusicMetadata?, pm: PhotoMetadata?, cfg: TemplateConfig): Bitmap {
        val stripH = (img.height * 0.2f).toInt()
        val outH = img.height + stripH
        val result = Bitmap.createBitmap(img.width, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        canvas.drawBitmap(img, 0f, 0f, null)

        // 底部渐变遮罩
        val gradient = LinearGradient(0f, img.height - stripH.toFloat(), 0f, outH.toFloat(),
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(200, Color.red(bg), Color.green(bg), Color.blue(bg))),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, img.height - stripH.toFloat(), img.width.toFloat(), outH.toFloat(), Paint().apply { shader = gradient })

        val cx = img.width / 2f
        val titlePaint = textPaint(tc, stripH * 0.22f, Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
        val subPaint = textPaint(Color.argb(180, Color.red(tc), Color.green(tc), Color.blue(tc)), stripH * 0.16f, Typeface.DEFAULT)

        mm?.let {
            canvas.drawText(it.title.ifEmpty { " " }, cx, img.height + stripH * 0.35f, titlePaint)
            if (it.artist.isNotBlank()) canvas.drawText(it.artist, cx, img.height + stripH * 0.65f, subPaint)
        }
        return result
    }

    /** 双栏分割 — 左图右文字 */
    private fun renderSplitScreen(img: Bitmap, bg: Int, tc: Int, mm: MusicMetadata?, pm: PhotoMetadata?, cfg: TemplateConfig): Bitmap {
        val gap = (img.width * 0.05f).toInt()
        val textW = (img.width * 0.35f).toInt()
        val outW = img.width + gap + textW
        val outH = img.height
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        canvas.drawColor(bg)
        canvas.drawBitmap(img, 0f, 0f, null)

        val txtX = (img.width + gap).toFloat()
        val titlePaint = textPaint(tc, outH * 0.06f, Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
        val subPaint = textPaint(Color.argb(180, Color.red(tc), Color.green(tc), Color.blue(tc)), outH * 0.04f, Typeface.DEFAULT)

        mm?.let {
            drawMultilineText(canvas, it.title, txtX + textW / 2, outH * 0.35f, textW.toFloat(), titlePaint)
            drawMultilineText(canvas, "${it.artist}\n${it.album}", txtX + textW / 2, outH * 0.6f, textW.toFloat(), subPaint)
        }

        drawWatermark(canvas, outW.toFloat(), outH.toFloat(), cfg, tc)
        return result
    }

    /** 杂志封面 — 大标题叠加 + 副标题 */
    private fun renderMagazine(img: Bitmap, bg: Int, tc: Int, mm: MusicMetadata?, pm: PhotoMetadata?, cfg: TemplateConfig): Bitmap {
        val result = img.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val w = result.width.toFloat()
        val h = result.height.toFloat()

        val overlayAlpha = 80
        canvas.drawColor(Color.argb(overlayAlpha, Color.red(bg), Color.green(bg), Color.blue(bg)))

        mm?.let {
            val titlePaint = textPaint(Color.WHITE, h * 0.08f, Typeface.create(Typeface.DEFAULT, Typeface.BOLD)).apply {
                setShadowLayer(4f, 0f, 2f, Color.BLACK)
            }
            drawMultilineText(canvas, it.title.uppercase(), w / 2, h * 0.4f, w * 0.9f, titlePaint)

            val subPaint = textPaint(Color.argb(220, 255, 255, 255), h * 0.04f, Typeface.DEFAULT)
            drawMultilineText(canvas, "${it.artist}\n${it.album}", w / 2, h * 0.65f, w * 0.8f, subPaint)
        }

        // 顶部装饰线
        val linePaint = Paint().apply { color = Color.WHITE; strokeWidth = 2f; alpha = 180 }
        canvas.drawLine(w * 0.15f, h * 0.12f, w * 0.85f, h * 0.12f, linePaint)
        canvas.drawLine(w * 0.15f, h * 0.88f, w * 0.85f, h * 0.88f, linePaint)

        return result
    }

    /** 拍立得 — 宽白边 + 底部签名区 */
    private fun renderPolaroid(img: Bitmap, bg: Int, tc: Int, mm: MusicMetadata?, pm: PhotoMetadata?, cfg: TemplateConfig): Bitmap {
        val border = (img.width * 0.08f).toInt()
        val bottom = (img.height * 0.18f).toInt()
        val outW = img.width + border * 2
        val outH = img.height + border * 3 + bottom
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(img, border.toFloat(), border.toFloat(), null)

        val textPaint = textPaint(0xFF444444.toInt(), bottom * 0.18f, Typeface.DEFAULT)
        val text = mm?.title?.ifEmpty { cfg.watermarkText } ?: cfg.watermarkText
        val cx = outW / 2f
        val ty = img.height + border * 2 + bottom * 0.45f

        // 手写虚线装饰
        val dashPaint = Paint().apply {
            color = 0xFFCCCCCC.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1f
            pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
        }
        canvas.drawLine(border.toFloat(), ty - textPaint.textSize, (outW - border).toFloat(), ty - textPaint.textSize, dashPaint)
        canvas.drawText(text, cx, ty, textPaint)

        return result
    }

    /** 极简置顶 — 图片置顶 + 窄间隔 + 底部文字 */
    private fun renderMinimalTop(img: Bitmap, bg: Int, tc: Int, mm: MusicMetadata?, pm: PhotoMetadata?, cfg: TemplateConfig): Bitmap {
        val pad = (img.width * 0.04f).toInt()
        val infoH = (img.height * 0.14f).toInt()
        val outW = img.width + pad * 2
        val outH = img.height + pad * 2 + infoH
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        canvas.drawColor(bg)
        canvas.drawBitmap(img, pad.toFloat(), pad.toFloat(), null)

        val titlePaint = textPaint(tc, infoH * 0.28f, Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
        val subPaint = textPaint(Color.argb(150, Color.red(tc), Color.green(tc), Color.blue(tc)), infoH * 0.2f, Typeface.DEFAULT)
        val cx = outW / 2f

        mm?.let {
            canvas.drawText(it.title.ifEmpty { " " }, cx, img.height + pad + infoH * 0.35f, titlePaint)
            canvas.drawText(it.artist, cx, img.height + pad + infoH * 0.65f, subPaint)
        }

        return result
    }

    /** 宽幅横幅 — 上下黑边 + 居中文字（电影感） */
    private fun renderWideBanner(img: Bitmap, bg: Int, tc: Int, mm: MusicMetadata?, pm: PhotoMetadata?, cfg: TemplateConfig): Bitmap {
        val barH = (img.height * 0.15f).toInt()
        val outH = img.height + barH * 2
        val result = Bitmap.createBitmap(img.width, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        canvas.drawColor(bg)
        canvas.drawBitmap(img, 0f, barH.toFloat(), null)

        val cx = img.width / 2f
        mm?.let {
            val titlePaint = textPaint(Color.WHITE, barH * 0.3f, Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
            canvas.drawText(it.title.ifEmpty { " " }, cx, barH * 0.55f, titlePaint)
        }
        pm?.let {
            val camPaint = textPaint(Color.argb(150, 255, 255, 255), barH * 0.2f, Typeface.DEFAULT)
            canvas.drawText(it.getCameraInfoText() ?: "", cx, outH - barH * 0.35f, camPaint)
        }

        return result
    }

    /** 角落徽章 — 图片 + 右下角半透明徽章 */
    private fun renderCornerBadge(img: Bitmap, bg: Int, tc: Int, mm: MusicMetadata?, pm: PhotoMetadata?, cfg: TemplateConfig): Bitmap {
        val result = img.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val w = result.width.toFloat()
        val h = result.height.toFloat()

        mm?.let { meta ->
            val badgeW = w * 0.35f
            val badgeH = h * 0.12f
            val bx = w - badgeW - 16f
            val by = h - badgeH - 16f

            val badgePaint = Paint().apply { color = Color.argb(180, Color.red(bg), Color.green(bg), Color.blue(bg)) }
            canvas.drawRoundRect(RectF(bx, by, bx + badgeW, by + badgeH), 12f, 12f, badgePaint)

            val textPaint = textPaint(tc, badgeH * 0.35f, Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
            canvas.drawText("🎵 ${meta.title}", bx + badgeW / 2, by + badgeH * 0.6f, textPaint)
        }

        return result
    }

    private fun textPaint(color: Int, size: Float, typeface: Typeface): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        textAlign = Paint.Align.CENTER
        this.typeface = typeface
    }

    private fun drawMultilineText(canvas: Canvas, text: String, cx: Float, startY: Float, maxWidth: Float, paint: Paint) {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { w ->
            val test = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(test) > maxWidth && current.isNotEmpty()) {
                lines.add(current)
                current = w
            } else current = test
        }
        if (current.isNotEmpty()) lines.add(current)
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, cx, startY + i * paint.textSize * 1.3f, paint)
        }
    }

    private fun drawWatermark(canvas: Canvas, w: Float, h: Float, cfg: TemplateConfig, tc: Int) {
        if (!cfg.showWatermark) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, Color.red(tc), Color.green(tc), Color.blue(tc))
            textSize = h * 0.025f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(cfg.watermarkText, w - 16f, h - 16f, paint)
    }
}
