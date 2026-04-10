package com.example.musicframe.domain.model

import android.graphics.Bitmap
import android.graphics.RectF
import com.example.musicframe.image.FrameConfig
import com.example.musicframe.image.PhotoMetadata
import com.example.musicframe.model.HeadphoneInfo
import com.example.musicframe.model.MusicMetadata

data class FrameDrawParams(
    val source: Bitmap,
    val config: FrameConfig,
    val musicMetadata: MusicMetadata?,
    val photoMetadata: PhotoMetadata?,
    val headphoneInfo: HeadphoneInfo?,
    val musicLines: List<String>,
    val photoLines: List<String>,
    val headphoneLines: List<String>,
    val customText: String,
    val frameColor: Int,
    val textColor: Int,
    val headphoneColor: Int,
    val appIcon: Bitmap?,
    val headphoneIcon: Bitmap?
)

data class BorderParams(
    val sideBorder: Int,
    val bottomBorder: Int,
    val baseBorder: Float,
    val extraBottom: Float,
    val adaptiveBorder: Float,
    val minSize: Int
)

data class CaptionDrawParams(
    val canvas: android.graphics.Canvas,
    val bounds: RectF,
    val musicLines: List<String>,
    val photoLines: List<String>,
    val headphoneLines: List<String>,
    val customText: String,
    val frameColor: Int,
    val textColor: Int,
    val headphoneTextColor: Int,
    val backgroundColor: Int? = null,
    val typeface: android.graphics.Typeface? = null,
    val appIcon: Bitmap?,
    val headphoneIcon: Bitmap?,
    val alignLinesToEnd: Boolean = false,
    val centerCustomText: Boolean = false,
    val paddingScale: Float = 0.18f,
    val borderSize: Int? = null,
    val overlayOnly: Boolean = false,
    val musicTextScale: Float,
    val photoTextScale: Float,
    val headphoneTextScale: Float,
    val customTextScale: Float
)

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

data class AdaptiveBorderParams(
    val baseBorder: Float,
    val minSize: Int,
    val musicLines: List<String>,
    val photoLines: List<String>,
    val headphoneLines: List<String>,
    val extraBottom: Float,
    val customText: String,
    val musicScale: Float,
    val photoScale: Float,
    val headphoneScale: Float,
    val customScale: Float
)
