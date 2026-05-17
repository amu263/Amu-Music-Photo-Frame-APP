package com.example.musicframe.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.musicframe.model.MusicMetadata
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 模板排版渲染器 — 4种精选布局
 * 原则：不遮挡照片主体、不改变照片颜色、信息完整、排版克制
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
        val textColor = invertForText(bgColor)

        return when (templateConfig.template) {
            LayoutTemplate.BOTTOM_STRIP -> renderBottomStrip(source, bgColor, textColor, musicMetadata, photoMetadata)
            LayoutTemplate.SPLIT_SCREEN -> renderSplitScreen(source, bgColor, textColor, musicMetadata, photoMetadata)
            LayoutTemplate.MAGAZINE -> renderMagazine(source, bgColor, textColor, musicMetadata, photoMetadata)
            LayoutTemplate.POLAROID -> renderPolaroid(source, textColor, musicMetadata, photoMetadata)
        }
    }

    private fun invertForText(bgColor: Int): Int {
        val r = (bgColor shr 16) and 0xFF
        val g = (bgColor shr 8) and 0xFF
        val b = bgColor and 0xFF
        val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255f
        return if (lum > 0.6f) 0xFF1A1A1A.toInt() else 0xFFF5F5F5.toInt()
    }

    // ═══════════════════════════════════════════════
    // 底部信息条 — 图片完整 + 底部渐变信息区
    // ═══════════════════════════════════════════════
    private fun renderBottomStrip(
        img: Bitmap, bg: Int, tc: Int,
        mm: MusicMetadata?, pm: PhotoMetadata?
    ): Bitmap {
        val w = img.width
        val h = img.height

        // 底部信息区高度：取决于是否有数据
        val hasMusic = mm != null
        val hasPhoto = pm != null
        val infoMultiplier = when {
            hasMusic && hasPhoto -> 0.18f
            hasMusic || hasPhoto -> 0.14f
            else -> 0f
        }
        if (infoMultiplier == 0f) return img

        val infoH = (h * infoMultiplier).toInt().coerceAtLeast(40)
        val outH = h + infoH
        val result = Bitmap.createBitmap(w, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(img, 0f, 0f, null)

        // 底部纯色背景（不覆盖照片）
        val bgPaint = Paint().apply { color = bg }
        canvas.drawRect(0f, h.toFloat(), w.toFloat(), outH.toFloat(), bgPaint)

        val cx = w / 2f
        val contentW = (w * 0.92f).toFloat()
        val titlePaint = textPaint(tc, infoH * 0.22f, Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
        val subPaint = textPaint(adjustAlpha(tc, 180), infoH * 0.16f, Typeface.DEFAULT)
        val tinyPaint = textPaint(adjustAlpha(tc, 140), infoH * 0.13f, Typeface.DEFAULT)

        var y = h + infoH * 0.2f

        mm?.let { meta ->
            meta.appIcon?.let { icon ->
                val iconSz = infoH * 0.28f
                canvas.drawBitmap(icon, null,
                    RectF(cx - iconSz - 60f, y - iconSz * 0.5f, cx - 60f, y + iconSz * 0.5f),
                    Paint(Paint.ANTI_ALIAS_FLAG))
                canvas.drawText(meta.appName ?: "", cx + 8f, y + iconSz * 0.3f, subPaint)
                y += iconSz + 4f
            }
            drawConstrainedText(canvas, meta.title.ifEmpty { "" }, cx, y + infoH * 0.06f, contentW, titlePaint)
            y += titlePaint.textSize * 1.3f
            if (meta.artist.isNotBlank()) {
                drawConstrainedText(canvas, meta.artist, cx, y + infoH * 0.06f, contentW, subPaint)
                y += subPaint.textSize * 1.3f
            }
        }

        pm?.let { meta ->
            meta.getCameraInfoText()?.let { cam ->
                if (cam.isNotBlank()) {
                    drawConstrainedText(canvas, cam, cx, y + infoH * 0.06f, contentW, tinyPaint)
                    y += tinyPaint.textSize * 1.3f
                }
            }
            val time = formatTimestamp(meta.createdDateTime)
            if (time.isNotBlank()) {
                drawConstrainedText(canvas, time, cx, y + infoH * 0.06f, contentW, tinyPaint)
            }
        }
        return result
    }

    // ═══════════════════════════════════════════════
    // 双栏分割 — 照片居左 + 信息面板居右
    // ═══════════════════════════════════════════════
    private fun renderSplitScreen(
        img: Bitmap, bg: Int, tc: Int,
        mm: MusicMetadata?, pm: PhotoMetadata?
    ): Bitmap {
        val panelW = (img.width * 0.32f).toInt().coerceAtLeast(80)
        val outW = img.width + panelW
        val result = Bitmap.createBitmap(outW, img.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        // 照片保持原色，不修改
        canvas.drawBitmap(img, 0f, 0f, null)
        // 右侧信息面板：纯色背景
        val panelPaint = Paint().apply { color = bg }
        canvas.drawRect(img.width.toFloat(), 0f, outW.toFloat(), img.height.toFloat(), panelPaint)

        val panelX = img.width + panelW / 2f
        val contentW = (panelW * 0.82f).toFloat()
        val titlePaint = textPaint(tc, img.height * 0.04f, Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
        val subPaint = textPaint(adjustAlpha(tc, 180), img.height * 0.028f, Typeface.DEFAULT)
        val tinyPaint = textPaint(adjustAlpha(tc, 130), img.height * 0.022f, Typeface.DEFAULT)

        var y = img.height * 0.08f

        mm?.let { meta ->
            meta.appIcon?.let { icon ->
                val iconSz = img.height * 0.045f
                val left = img.width + panelW * 0.09f
                canvas.drawBitmap(icon, null,
                    RectF(left, y, left + iconSz, y + iconSz),
                    Paint(Paint.ANTI_ALIAS_FLAG))
                canvas.drawText(meta.appName ?: "", left + iconSz + 8f, y + iconSz * 0.75f, subPaint)
                y += iconSz + 16f
            }
            drawConstrainedText(canvas, meta.title.ifEmpty { "" }, panelX, y + img.height * 0.01f, contentW, titlePaint)
            y += titlePaint.textSize * 2f
            if (meta.artist.isNotBlank()) {
                drawConstrainedText(canvas, meta.artist, panelX, y, contentW, subPaint)
                y += subPaint.textSize * 2f
            }
        }

        pm?.let { meta ->
            meta.getCameraInfoText()?.let { cam ->
                if (cam.isNotBlank()) {
                    drawConstrainedText(canvas, cam, panelX, y, contentW, tinyPaint)
                    y += tinyPaint.textSize * 2f
                }
            }
            val time = formatTimestamp(meta.createdDateTime)
            if (time.isNotBlank()) {
                drawConstrainedText(canvas, time, panelX, y, contentW, tinyPaint)
            }
        }
        return result
    }

    // ═══════════════════════════════════════════════
    // 杂志封面 — 照片完整 + 底部半透明信息条
    // ═══════════════════════════════════════════════
    private fun renderMagazine(
        img: Bitmap, bg: Int, tc: Int,
        mm: MusicMetadata?, pm: PhotoMetadata?
    ): Bitmap {
        val w = img.width
        val h = img.height
        // 照片完全不变色，信息在底部半透明条上
        val hasMusic = mm != null
        val hasPhoto = pm != null
        if (!hasMusic && !hasPhoto) return img

        val barH = (h * 0.12f).toInt().coerceAtLeast(48)
        val outH = h + barH
        val result = Bitmap.createBitmap(w, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 照片保持原样
        canvas.drawBitmap(img, 0f, 0f, null)

        // 底部半透明信息条（从照片底部渐变到纯色）
        val gradient = LinearGradient(
            0f, h.toFloat() - barH * 0.5f, 0f, outH.toFloat(),
            intArrayOf(Color.argb(0, Color.red(bg), Color.green(bg), Color.blue(bg)),
                       Color.argb(220, Color.red(bg), Color.green(bg), Color.blue(bg))),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h.toFloat() - barH * 0.5f, w.toFloat(), outH.toFloat(),
            Paint().apply { shader = gradient })

        val cx = w / 2f
        val contentW = (w * 0.92f).toFloat()
        val titlePaint = textPaint(tc, barH * 0.28f, Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
        val subPaint = textPaint(adjustAlpha(tc, 170), barH * 0.2f, Typeface.DEFAULT)
        val tinyPaint = textPaint(adjustAlpha(tc, 130), barH * 0.17f, Typeface.DEFAULT)

        var y = h + barH * 0.1f

        // 紧凑排列：图标+名称 | 歌名 · 歌手 | 相机 · 时间
        mm?.let { meta ->
            meta.appIcon?.let { icon ->
                val iconSz = barH * 0.35f
                canvas.drawBitmap(icon, null,
                    RectF(cx - iconSz - 40f, y + 2f, cx - 40f, y + 2f + iconSz),
                    Paint(Paint.ANTI_ALIAS_FLAG))
                // 不显示全 app 名称以节省空间
                y += iconSz + 2f
            }
            drawConstrainedText(canvas, meta.title.ifEmpty { "" }, cx, y + barH * 0.05f, contentW, titlePaint)
            y += titlePaint.textSize * 1.2f
            if (meta.artist.isNotBlank()) {
                drawConstrainedText(canvas, meta.artist, cx, y + barH * 0.05f, contentW, subPaint)
                y += subPaint.textSize * 1.2f
            }
        }

        pm?.let { meta ->
            val parts = mutableListOf<String>()
            meta.getCameraInfoText()?.let { if (it.isNotBlank()) parts += it }
            formatTimestamp(meta.createdDateTime).let { if (it.isNotBlank()) parts += it }
            val infoStr = parts.joinToString("  ·  ")
            if (infoStr.isNotBlank()) {
                drawConstrainedText(canvas, infoStr, cx, y + barH * 0.05f, contentW, tinyPaint)
            }
        }
        return result
    }

    // ═══════════════════════════════════════════════
    // 拍立得 — 经典白框 + 底部信息 + 箴言
    // ═══════════════════════════════════════════════
    private fun renderPolaroid(
        img: Bitmap, tc: Int,
        mm: MusicMetadata?, pm: PhotoMetadata?
    ): Bitmap {
        val border = (img.width * 0.06f).toInt().coerceAtLeast(12)
        val infoH = (img.height * 0.18f).toInt().coerceAtLeast(60)
        val outW = img.width + border * 2
        val outH = img.height + border * 2 + infoH
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 白色背景
        canvas.drawColor(Color.WHITE)
        // 照片居中
        canvas.drawBitmap(img, border.toFloat(), border.toFloat(), null)

        val cx = outW / 2f
        val contentW = (outW - border * 2.5f).toFloat()
        val textTop = img.height + border + 4f

        val titlePaint = textPaint(0xFF1A1A1A.toInt(), infoH * 0.16f, Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
        val subPaint = textPaint(0xFF555555.toInt(), infoH * 0.12f, Typeface.DEFAULT)
        val tinyPaint = textPaint(0xFF888888.toInt(), infoH * 0.11f, Typeface.DEFAULT)
        val quotePaint = textPaint(0xFFAAAAAA.toInt(), infoH * 0.10f, Typeface.DEFAULT)

        var y = textTop

        // 虚线装饰
        val dashPaint = Paint().apply {
            color = 0xFFDDDDDD.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 3f), 0f)
        }
        canvas.drawLine(border.toFloat(), y, (outW - border).toFloat(), y, dashPaint)
        y += 8f

        // 音乐信息：播放器图标 + 歌名 · 歌手
        mm?.let { meta ->
            meta.appIcon?.let { icon ->
                val iconSz = infoH * 0.22f
                canvas.drawBitmap(icon, null,
                    RectF(border + 4f, y, border + 4f + iconSz, y + iconSz),
                    Paint(Paint.ANTI_ALIAS_FLAG))
                canvas.drawText(meta.appName ?: "", border + 4f + iconSz + 6f, y + iconSz * 0.7f, subPaint)
                y += iconSz + 4f
            }
            drawConstrainedText(canvas, meta.title.ifEmpty { "" }, cx, y + 2f, contentW, titlePaint)
            y += titlePaint.textSize * 1.3f
            if (meta.artist.isNotBlank()) {
                drawConstrainedText(canvas, meta.artist, cx, y + 2f, contentW, subPaint)
                y += subPaint.textSize * 1.3f
            }
        }

        // 相机参数 + 时间
        pm?.let { meta ->
            val parts = mutableListOf<String>()
            meta.getCameraInfoText()?.let { if (it.isNotBlank()) parts += it }
            formatTimestamp(meta.createdDateTime).let { if (it.isNotBlank()) parts += it }
            val infoStr = parts.joinToString("  ·  ")
            if (infoStr.isNotBlank()) {
                drawConstrainedText(canvas, infoStr, cx, y + 2f, contentW, tinyPaint)
                y += tinyPaint.textSize * 1.3f
            }
        }

        // 箴言
        val quote = QuoteLibrary.random()
        drawConstrainedText(canvas, "「$quote」", cx, y + 2f, contentW, quotePaint)

        return result
    }

    // ═══════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════

    private fun formatTimestamp(rawDate: String?): String {
        if (rawDate.isNullOrBlank()) return ""
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val date = parser.parse(rawDate)
            val formatter = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
            formatter.format(date ?: java.util.Date())
        }.getOrDefault("")
    }

    private fun adjustAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun drawConstrainedText(
        canvas: Canvas, text: String,
        cx: Float, baseY: Float, maxWidth: Float, paint: Paint
    ) {
        val measured = paint.measureText(text)
        if (measured <= maxWidth) {
            canvas.drawText(text, cx, baseY, paint)
        } else {
            val scale = maxWidth / measured
            paint.textSize = paint.textSize * scale * 0.95f
            canvas.drawText(text, cx, baseY, paint)
        }
    }

    private fun textPaint(color: Int, size: Float, typeface: Typeface): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = Paint.Align.CENTER
            this.typeface = typeface
        }
}
