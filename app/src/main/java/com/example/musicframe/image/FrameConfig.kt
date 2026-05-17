package com.example.musicframe.image

import com.example.musicframe.domain.model.FrameColorMode
import com.example.musicframe.image.FilmGradingEngine.GradingConfig

data class FrameConfig(
    val frameMode: FrameMode = FrameMode.PREMIUM_LEICA,
    val frameColorMode: FrameColorMode = FrameColorMode.DARK,
    val customFrameColorHex: String = "",
    val showHeadphoneInfo: Boolean = true,
    val headphoneTextColor: Int? = null,
    val typeface: android.graphics.Typeface? = null,
    val photoMetadata: PhotoMetadata? = null,
    val useDarkBackground: Boolean = false,
    // 星座运势模式 - 用户生日
    val userBirthdayMonth: Int = 0,
    val userBirthdayDay: Int = 0,
    val gradingConfig: GradingConfig = GradingConfig()
) {
    fun headphoneColor(default: Int): Int = headphoneTextColor ?: default
    
    fun getFrameColor(dominantColor: Int, forMusicFlow: Boolean = false): Int {
        // 自定义颜色优先
        if (customFrameColorHex.isNotBlank()) {
            return try {
                android.graphics.Color.parseColor("#$customFrameColorHex")
            } catch (e: Exception) {
                dominantColor
            }
        }
        
        // 原色/深色/浅色模式
        return when (frameColorMode) {
            FrameColorMode.ORIGINAL -> dominantColor
            FrameColorMode.LIGHT -> enhanceVibrancyForLightMode(dominantColor)
            FrameColorMode.DARK -> enhanceVibrancyForDarkMode(dominantColor)
        }
    }
    
    fun getStripeColors(dominantColor: Int): Pair<Int, Int> {
        // 音乐流光模式：底色使用增强后的鲜艳色，交替色使用智能对比色
        val baseColor = when (frameColorMode) {
            FrameColorMode.ORIGINAL -> boostVibrancy(dominantColor, 1.8f)
            FrameColorMode.LIGHT -> enhanceVibrancyForLightMode(dominantColor)
            FrameColorMode.DARK -> enhanceVibrancyForDarkMode(dominantColor)
        }
        val alternateColor = getPremiumAlternateColor(baseColor, frameColorMode)
        return Pair(baseColor, alternateColor)
    }
    
    // ═══════════════════════════════════════════════
    // 🎨 智能 Vibrance 色彩增强算法
    // ═══════════════════════════════════════════════

    /**
     * 智能 Vibrance 提升：类似修图软件的自然饱和度。
     * 低饱和区大幅提纯、高饱和区微调保持，避免过曝/死黑。
     * 
     * @param factor 基础提升倍率（1.0=不变, 1.5=中等, 2.0=激进）
     */
    private fun boostVibrancy(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        val s = hsv[1]
        val v = hsv[2]
        
        // 渐进式饱和度提升：低饱和猛拉，高饱和克制
        hsv[1] = when {
            s < 0.2f -> (s * factor * 1.4f).coerceAtMost(0.92f)      // 灰暗色 → 猛提
            s < 0.4f -> (s * factor * 1.2f).coerceAtMost(0.92f)      // 中度色 → 加强
            s < 0.6f -> (s * factor * 1.05f).coerceAtMost(0.94f)     // 较鲜艳 → 微调
            s < 0.8f -> (s * factor * 0.9f).coerceAtMost(0.95f)      // 已鲜艳 → 略收
            else -> s.coerceAtMost(0.96f)                              // 极鲜艳 → 保持
        }
        
        // 亮度微调：过暗提亮，过亮压暗，保持色彩层次
        hsv[2] = when {
            v < 0.25f -> (v * 1.3f).coerceAtMost(0.6f)   // 极暗 → 提至可见
            v < 0.4f -> (v * 1.15f).coerceAtMost(0.6f)   // 偏暗 → 轻微提亮
            v > 0.85f -> (v * 0.88f).coerceAtLeast(0.55f) // 过亮 → 压至高级感区间
            else -> v
        }
        
        return android.graphics.Color.HSVToColor(hsv)
    }

    /**
     * 浅色模式专用的高级鲜亮色彩。
     * 目标：明亮但不荧光，鲜艳但不刺眼。
     */
    private fun enhanceVibrancyForLightMode(color: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        val h = hsv[0]
        val s = hsv[1]
        val v = hsv[2]
        
        // 1. 饱和度激进提升
        hsv[1] = when {
            s < 0.15f -> (s * 3.5f).coerceAtMost(0.85f)    // 灰暗 → 猛拉 3.5x
            s < 0.35f -> (s * 2.2f).coerceAtMost(0.88f)    // 低饱和 → 2.2x
            s < 0.55f -> (s * 1.6f).coerceAtMost(0.90f)    // 中饱和 → 1.6x
            s < 0.75f -> (s * 1.25f).coerceAtMost(0.93f)   // 较鲜艳 → 微调
            else -> s.coerceAtMost(0.95f)
        }
        
        // 2. 亮度优化：提升至 0.55~0.85 的「高级亮」区间
        hsv[2] = when {
            v < 0.3f -> 0.58f                                  // 极暗 → 跳至高级亮
            v < 0.5f -> (v * 1.35f).coerceIn(0.5f, 0.75f)    // 偏暗 → 提至中亮
            v < 0.7f -> (v * 1.15f).coerceAtMost(0.82f)      // 中等 → 微提
            v > 0.9f -> 0.82f                                  // 过亮 → 压下
            else -> v.coerceIn(0.5f, 0.85f)
        }
        
        // 3. 色相微调：蓝紫色系略向暖移，更有高级感
        if (h in 200f..280f) {
            hsv[0] = (h + 8f) % 360f  // 略向紫红偏 8°
        }
        
        return android.graphics.Color.HSVToColor(hsv)
    }

    /**
     * 深色模式专用的浓郁深沉色彩。
     * 目标：浓郁而不沉闷，保持色彩辨识度。
     */
    private fun enhanceVibrancyForDarkMode(color: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        val s = hsv[1]
        val v = hsv[2]
        
        // 1. 饱和度提升（深色模式下也需保持鲜艳）
        hsv[1] = when {
            s < 0.2f -> (s * 2.5f).coerceAtMost(0.85f)
            s < 0.45f -> (s * 1.8f).coerceAtMost(0.88f)
            s < 0.65f -> (s * 1.3f).coerceAtMost(0.92f)
            else -> s.coerceAtMost(0.94f)
        }
        
        // 2. 亮度降低但保留色彩深度（不过黑）
        hsv[2] = when {
            v < 0.15f -> 0.18f                                   // 极暗 → 保底
            v < 0.35f -> v                                      // 已偏暗 → 保持
            v < 0.6f -> (v * 0.7f).coerceAtLeast(0.25f)       // 中等 → 压至深沉
            v > 0.8f -> 0.4f                                    // 过亮 → 压下
            else -> (v * 0.55f).coerceAtLeast(0.25f)
        }
        
        return android.graphics.Color.HSVToColor(hsv)
    }

    /**
     * 为音乐流光模式生成高级交替条纹色。
     * 使用色相偏移 + 亮度对比，而非简单的明暗变化。
     */
    private fun getPremiumAlternateColor(baseColor: Int, mode: FrameColorMode): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(baseColor, hsv)
        
        // 色相偏移 15-25°（创造丰富的条纹层次，而非单一明暗）
        val hueShift = if (hsv[2] > 0.5f) -18f else 18f
        hsv[0] = (hsv[0] + hueShift + 360f) % 360f
        
        when (mode) {
            FrameColorMode.LIGHT -> {
                // 浅色模式：交替条纹饱和度一致，亮度略低形成层次
                hsv[1] = hsv[1].coerceAtLeast(0.5f)  // 保持鲜艳
                hsv[2] = (hsv[2] * 0.75f).coerceIn(0.35f, 0.7f)
            }
            FrameColorMode.DARK -> {
                // 深色模式：交替条纹更深，但保持色彩
                hsv[1] = hsv[1].coerceAtLeast(0.4f)
                hsv[2] = (hsv[2] * 0.6f).coerceIn(0.15f, 0.35f)
            }
            FrameColorMode.ORIGINAL -> {
                hsv[1] = hsv[1].coerceAtLeast(0.4f)
                hsv[2] = (hsv[2] * 0.65f).coerceIn(0.2f, 0.5f)
            }
        }
        
        return android.graphics.Color.HSVToColor(hsv)
    }

    // ═══════════════════════════════════════════════
    // 基础色彩工具（保留给其他模式使用）
    // ═══════════════════════════════════════════════

    private fun enhanceColorSaturation(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * factor).coerceIn(0f, 1f)
        return android.graphics.Color.HSVToColor(hsv)
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

enum class FrameMode {
    PREMIUM_LEICA,
    CUSTOM_LEICA,
    MUSIC_FLOW,
    MUSIC_SOLID,
    ZODIAC_HOROSCOPE
}

const val MIN_TEXT_SCALE = 0.6f
const val MAX_TEXT_SCALE = 1.6f
