package com.example.musicframe.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 电影级调色引擎 — 8种 LUT 预设 + 强度控制 + 胶片颗粒 + 暗角
 */
object FilmGradingEngine {

    /** 调色预设 */
    enum class FilmPreset(val label: String, val emoji: String) {
        KODAK_GOLD("柯达金", "🎞️"),
        FUJI_VELVIA("富士维尔维亚", "🌸"),
        TEAL_ORANGE("青橙电影", "🎬"),
        CYBERPUNK("赛博朋克", "🌃"),
        VINTAGE_SEPIA("复古棕褐", "📜"),
        NOIR_BW("黑白 noir", "🖤"),
        PASTEL_DREAM("梦幻粉彩", "🦄"),
        MOODY_INDIGO("情绪靛蓝", "🌙"),
        NONE("无调色", "⊘")
    }

    /** 调色配置 */
    data class GradingConfig(
        val preset: FilmPreset = FilmPreset.NONE,
        val intensity: Float = 0.7f,      // 0..1 调色强度
        val grainAmount: Float = 0f,       // 0..1 胶片颗粒
        val vignetteStrength: Float = 0f,  // 0..1 暗角强度
        val brightness: Float = 0f,        // -1..1 亮度
        val contrast: Float = 0f,          // -1..1 对比度
        val saturation: Float = 0f         // -1..1 额外饱和度
    )

    /** 应用完整调色管线 */
    fun applyGrading(source: Bitmap, config: GradingConfig?): Bitmap {
        if (config == null || config.preset == FilmPreset.NONE && config.intensity == 0f) {
            return source
        }

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // 1. 基础调整（亮度、对比度、饱和度）
        if (config.brightness != 0f || config.contrast != 0f || config.saturation != 0f) {
            applyBasicAdjustments(canvas, result, config)
        }

        // 2. LUT 预设
        if (config.preset != FilmPreset.NONE && config.intensity > 0f) {
            applyPreset(canvas, result, config)
        }

        // 3. 胶片颗粒
        if (config.grainAmount > 0f) {
            applyFilmGrain(canvas, result, config.grainAmount)
        }

        // 4. 暗角
        if (config.vignetteStrength > 0f) {
            applyVignette(canvas, result, config.vignetteStrength)
        }

        return result
    }

    private fun applyBasicAdjustments(canvas: Canvas, bitmap: Bitmap, config: GradingConfig) {
        val brightness = config.brightness * 50f
        val contrast = 1f + config.contrast * 0.5f
        val saturation = 1f + config.saturation

        val cm = ColorMatrix().apply {
            // 亮度偏移
            val b = floatArrayOf(
                1f, 0f, 0f, 0f, brightness,
                0f, 1f, 0f, 0f, brightness,
                0f, 0f, 1f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
            set(b)
            // 叠加饱和度和对比度
            postConcat(ColorMatrix().apply {
                setSaturation(saturation)
            })
        }

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
    }

    private fun applyPreset(canvas: Canvas, bitmap: Bitmap, config: GradingConfig) {
        // 使用 ColorMatrix 组合模拟电影调色 LUT
        val matrix = getPresetMatrix(config.preset)
        blendMatrix(matrix, config.intensity)

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
    }

    private fun getPresetMatrix(preset: FilmPreset): ColorMatrix = ColorMatrix().apply {
        when (preset) {
            FilmPreset.KODAK_GOLD -> {
                // 暖金色调：高光偏金，暗部偏暖
                set(floatArrayOf(
                    1.15f, -0.05f, -0.05f, 0f, 8f,
                    -0.02f, 1.10f, -0.08f, 0f, 5f,
                    -0.08f, -0.12f, 1.20f, 0f, -5f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            FilmPreset.FUJI_VELVIA -> {
                // 富士维尔维亚：高饱和，微绿阴影，品红高光
                set(floatArrayOf(
                    1.25f, -0.10f, -0.05f, 0f, 3f,
                    -0.05f, 1.20f, -0.05f, 0f, -2f,
                    -0.05f, -0.10f, 1.30f, 0f, -1f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            FilmPreset.TEAL_ORANGE -> {
                // 好莱坞青橙：高光偏橙，暗部偏青
                set(floatArrayOf(
                    1.10f, 0.05f, -0.10f, 0f, 10f,
                    0.02f, 1.05f, 0.05f, 0f, -2f,
                    -0.15f, -0.05f, 1.20f, 0f, -8f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            FilmPreset.CYBERPUNK -> {
                // 赛博朋克：蓝紫阴影，洋红高光
                set(floatArrayOf(
                    1.20f, 0f, -0.15f, 0f, -5f,
                    -0.10f, 1.15f, 0.05f, 0f, -8f,
                    0.10f, -0.15f, 1.25f, 0f, 5f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            FilmPreset.VINTAGE_SEPIA -> {
                // 复古棕褐：去饱和 + 暖棕
                set(floatArrayOf(
                    0.60f, 0.30f, 0.10f, 0f, 10f,
                    0.30f, 0.55f, 0.15f, 0f, 8f,
                    0.15f, 0.25f, 0.50f, 0f, 5f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            FilmPreset.NOIR_BW -> {
                // 黑白 noir：经典黑白 + 强对比
                set(floatArrayOf(
                    0.33f, 0.33f, 0.33f, 0f, 5f,
                    0.33f, 0.33f, 0.33f, 0f, 5f,
                    0.33f, 0.33f, 0.33f, 0f, 5f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            FilmPreset.PASTEL_DREAM -> {
                // 梦幻粉彩：提亮 + 柔粉
                set(floatArrayOf(
                    1.10f, 0.10f, -0.05f, 0f, 25f,
                    -0.05f, 1.10f, 0.08f, 0f, 20f,
                    0.05f, -0.05f, 1.10f, 0f, 25f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            FilmPreset.MOODY_INDIGO -> {
                // 情绪靛蓝：深蓝滤镜 + 褪色感
                set(floatArrayOf(
                    0.80f, 0f, -0.10f, 0f, -15f,
                    -0.05f, 0.85f, 0f, 0f, -10f,
                    0.10f, 0.05f, 1.10f, 0f, 5f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            FilmPreset.NONE -> { /* no-op */ }
        }
    }

    /** 混合矩阵与原图（intensity 控制强度） */
    private fun blendMatrix(preset: ColorMatrix, intensity: Float) {
        if (intensity >= 1f) return
        val identity = ColorMatrix()
        val blended = ColorMatrix().apply {
            val p = preset.floatArray.copyOf()
            val i = identity.floatArray
            val result = FloatArray(20)
            for (j in 0 until 20) {
                result[j] = i[j] + (p[j] - i[j]) * intensity
            }
            set(result)
        }
        preset.set(blended)
    }

    private fun applyFilmGrain(canvas: Canvas, bitmap: Bitmap, amount: Float) {
        val w = bitmap.width
        val h = bitmap.height
        val grainBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val grainCanvas = Canvas(grainBmp)
        val paint = Paint()

        val rng = Random(42) // 固定种子保证可复现但不显重复
        val step = (4 + (1f - amount) * 16).toInt().coerceAtLeast(2)

        for (x in 0 until w step step) {
            for (y in 0 until h step step) {
                val noise = (rng.nextInt(51) - 25) * amount
                val alpha = ((amount * 0.15f) * 255).toInt()
                paint.color = android.graphics.Color.argb(
                    alpha,
                    (128 + noise).coerceIn(0, 255),
                    (128 + noise).coerceIn(0, 255),
                    (128 + noise).coerceIn(0, 255)
                )
                grainCanvas.drawRect(
                    x.toFloat(), y.toFloat(),
                    (x + step).toFloat(), (y + step).toFloat(),
                    paint
                )
            }
        }

        canvas.drawBitmap(grainBmp, 0f, 0f, null)
        grainBmp.recycle()
    }

    private fun applyVignette(canvas: Canvas, bitmap: Bitmap, strength: Float) {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val maxR = kotlin.math.sqrt(cx * cx + cy * cy)

        // 径向渐变暗角
        val colors = intArrayOf(
            android.graphics.Color.argb((strength * 180).toInt(), 0, 0, 0),
            android.graphics.Color.argb((strength * 80).toInt(), 0, 0, 0),
            android.graphics.Color.argb(0, 0, 0, 0)
        )
        val stops = floatArrayOf(0f, 0.5f, 1f)
        val gradient = android.graphics.RadialGradient(
            cx, cy, maxR,
            colors, stops,
            android.graphics.Shader.TileMode.CLAMP
        )
        val paint = Paint().apply {
            shader = gradient
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        }
        canvas.drawRect(0f, 0f, w, h, paint)
    }
}
