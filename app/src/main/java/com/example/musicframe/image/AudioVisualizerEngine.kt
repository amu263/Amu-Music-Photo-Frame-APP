package com.example.musicframe.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 音乐律动可视化引擎 — 从音乐封面色彩生成动态视觉效果
 */
object AudioVisualizerEngine {

    enum class RhythmEffect(val label: String, val emoji: String) {
        COLOR_PULSE("色彩脉冲", "💫"),
        GRADIENT_WAVE("渐变波浪", "🌊"),
        EQ_BARS("EQ 律动条", "🎚️"),
        AURORA_RING("极光光环", "✨"),
        NEON_GLOW("霓虹辉光", "💡"),
        PARTICLE_BURST("粒子爆发", "🎆")
    }

    data class RhythmConfig(
        val effect: RhythmEffect = RhythmEffect.COLOR_PULSE,
        val intensity: Float = 0.7f,       // 0..1 效果强度
        val speed: Float = 0.5f,           // 0..1 动画速度（导出静态时为相位偏移）
        val barCount: Int = 12,            // EQ 柱数
        val glowRadius: Float = 0.3f,      // 辉光半径比例
        val particleCount: Int = 30         // 粒子数量
    )

    /** 从主色分析「能量」参数 */
    fun analyzeEnergy(dominantColor: Int): EnergyProfile {
        val hsv = FloatArray(3)
        Color.colorToHSV(dominantColor, hsv)
        return EnergyProfile(
            hue = hsv[0],
            saturation = hsv[1],
            brightness = hsv[2],
            energy = (hsv[1] * 0.6f + hsv[2] * 0.4f),
            warmth = if (hsv[0] in 0f..60f || hsv[0] in 300f..360f) 1f
            else if (hsv[0] in 180f..260f) -1f else 0f
        )
    }

    data class EnergyProfile(
        val hue: Float,
        val saturation: Float,
        val brightness: Float,
        val energy: Float,     // 0..1 综合能量
        val warmth: Float      // -1(冷)..1(暖)
    )

    fun applyRhythmEffect(
        source: Bitmap,
        dominantColor: Int,
        config: RhythmConfig
    ): Bitmap {
        val energy = analyzeEnergy(dominantColor)
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val w = result.width.toFloat()
        val h = result.height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        when (config.effect) {
            RhythmEffect.COLOR_PULSE -> drawColorPulse(canvas, w, h, cx, cy, energy, config)
            RhythmEffect.GRADIENT_WAVE -> drawGradientWave(canvas, w, h, energy, config)
            RhythmEffect.EQ_BARS -> drawEqBars(canvas, w, h, energy, config)
            RhythmEffect.AURORA_RING -> drawAuroraRing(canvas, w, h, cx, cy, energy, config)
            RhythmEffect.NEON_GLOW -> drawNeonGlow(canvas, w, h, cx, cy, dominantColor, config)
            RhythmEffect.PARTICLE_BURST -> drawParticleBurst(canvas, w, h, cx, cy, energy, config)
        }

        return result
    }

    private fun drawColorPulse(canvas: Canvas, w: Float, h: Float, cx: Float, cy: Float, e: EnergyProfile, cfg: RhythmConfig) {
        val pulseCount = 5
        val colors = generateHarmonicColors(e.hue, pulseCount, e.energy)
        val maxR = sqrt(cx * cx + cy * cy)

        for (i in 0 until pulseCount) {
            val r = maxR * (0.3f + i * 0.15f) * (1f + cfg.intensity * 0.2f)
            val alpha = ((1f - i.toFloat() / pulseCount) * cfg.intensity * 80).toInt().coerceIn(0, 255)
            val paint = Paint().apply {
                color = Color.argb(alpha, Color.red(colors[i]), Color.green(colors[i]), Color.blue(colors[i]))
                style = Paint.Style.STROKE
                strokeWidth = 4f + cfg.intensity * 8f * e.energy
                isAntiAlias = true
            }
            canvas.drawCircle(cx, cy, r, paint)
        }
    }

    private fun drawGradientWave(canvas: Canvas, w: Float, h: Float, e: EnergyProfile, cfg: RhythmConfig) {
        val amplitude = h * 0.08f * cfg.intensity * e.energy
        val frequency = 3f + cfg.intensity * 4f

        // 多种颜色层的波浪
        val layers = 3
        val colors = generateHarmonicColors(e.hue, layers, e.energy)

        for (layer in 0 until layers) {
            val path = Path()
            val yBase = h * (0.35f + layer * 0.15f)
            path.moveTo(0f, yBase)
            for (x in 0..w.toInt() step 4) {
                val phase = layer * 0.5f + cfg.speed * 2f
                val y = yBase + sin(x / w * frequency * kotlin.math.PI.toFloat() * 2f + phase) * amplitude * (1f + layer * 0.3f)
                path.lineTo(x.toFloat(), y)
            }
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()

            val paint = Paint().apply {
                color = Color.argb(
                    (cfg.intensity * 60).toInt(),
                    Color.red(colors[layer]), Color.green(colors[layer]), Color.blue(colors[layer])
                )
                isAntiAlias = true
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun drawEqBars(canvas: Canvas, w: Float, h: Float, e: EnergyProfile, cfg: RhythmConfig) {
        val barW = w / cfg.barCount
        val maxH = h * 0.3f * cfg.intensity

        for (i in 0 until cfg.barCount) {
            // 模拟频谱：混合正弦波和能量
            val freq = (i.toFloat() / cfg.barCount) * 6f
            val barH = (sin(freq + cfg.speed * 4f) * 0.5f + 0.5f) * maxH * e.energy +
                       (cos(freq * 2.3f + cfg.speed * 3f) * 0.3f + 0.3f) * maxH

            val hue = (e.hue + i * 15f) % 360f
            val hsv = floatArrayOf(hue, 0.8f, 0.9f)
            val barColor = Color.HSVToColor(
                (cfg.intensity * 180).toInt().coerceIn(0, 255), hsv
            )

            val left = i * barW
            val top = h - barH
            val gradient = LinearGradient(left, top, left, h,
                intArrayOf(barColor, Color.argb(0, 0, 0, 0)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            val paint = Paint().apply {
                shader = gradient
            }
            canvas.drawRect(left, top, left + barW - 1f, h, paint)
        }
    }

    private fun drawAuroraRing(canvas: Canvas, w: Float, h: Float, cx: Float, cy: Float, e: EnergyProfile, cfg: RhythmConfig) {
        val maxR = sqrt(cx * cx + cy * cy) * 0.7f
        val ringCount = 3

        for (r in 0 until ringCount) {
            val radius = maxR * (0.5f + r * 0.2f)
            val sweepColors = IntArray(8) { i ->
                val hue = (e.hue + i * 45f + cfg.speed * 60f) % 360f
                Color.HSVToColor(
                    (cfg.intensity * 100).toInt().coerceIn(0, 255),
                    floatArrayOf(hue, 0.85f, 0.9f)
                )
            }
            val sweep = SweepGradient(cx, cy, sweepColors, null)
            val paint = Paint().apply {
                shader = sweep
                style = Paint.Style.STROKE
                strokeWidth = 3f + cfg.intensity * 5f * (1f - r * 0.2f)
                isAntiAlias = true
            }
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    private fun drawNeonGlow(canvas: Canvas, w: Float, h: Float, cx: Float, cy: Float, dominantColor: Int, cfg: RhythmConfig) {
        val hsv = FloatArray(3)
        Color.colorToHSV(dominantColor, hsv)

        // 多层辉光
        val layers = 4
        for (l in layers downTo 0) {
            val r = sqrt(cx * cx + cy * cy) * (0.3f + l * 0.18f)
            val alpha = ((1f - l.toFloat() / layers) * cfg.intensity * 60).toInt().coerceIn(0, 255)
            val glowColor = Color.HSVToColor(alpha, floatArrayOf(hsv[0], hsv[1] * 0.8f, 1f))

            val gradient = RadialGradient(cx, cy, r,
                intArrayOf(glowColor, Color.argb(0, 0, 0, 0)),
                floatArrayOf(0.3f, 1f),
                Shader.TileMode.CLAMP
            )
            val paint = Paint().apply {
                shader = gradient
                isAntiAlias = true
            }
            canvas.drawRect(0f, 0f, w, h, paint)
        }
    }

    private fun drawParticleBurst(canvas: Canvas, w: Float, h: Float, cx: Float, cy: Float, e: EnergyProfile, cfg: RhythmConfig) {
        val count = cfg.particleCount
        val colors = generateHarmonicColors(e.hue, 3, e.energy)
        val rng = java.util.Random(42)

        for (i in 0 until count) {
            val angle = rng.nextFloat() * 360f
            val dist = rng.nextFloat() * sqrt(cx * cx + cy * cy) * 0.6f * cfg.intensity
            val rad = Math.toRadians(angle.toDouble())
            val px = cx + cos(rad).toFloat() * dist
            val py = cy + sin(rad).toFloat() * dist

            val size = 2f + rng.nextFloat() * 6f * e.energy
            val colorIdx = rng.nextInt(colors.size)
            val alpha = ((0.3f + rng.nextFloat() * 0.7f) * cfg.intensity * 200).toInt().coerceIn(0, 255)
            val color = Color.argb(
                alpha, Color.red(colors[colorIdx]), Color.green(colors[colorIdx]), Color.blue(colors[colorIdx])
            )

            val paint = Paint().apply {
                this.color = color
                isAntiAlias = true
            }
            canvas.drawCircle(px, py, size, paint)
        }
    }

    private fun generateHarmonicColors(baseHue: Float, count: Int, energy: Float): List<Int> {
        return (0 until count).map { i ->
            val hue = (baseHue + i * (360f / count) + energy * 30f) % 360f
            Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.9f))
        }
    }
}
