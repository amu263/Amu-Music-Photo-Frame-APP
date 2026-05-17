package com.example.musicframe.domain.model

import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import com.example.musicframe.export.ImageExporter
import com.example.musicframe.image.FrameMode
import com.example.musicframe.image.CanvasConfig
import com.example.musicframe.image.LayoutTemplate
import com.example.musicframe.image.TemplateConfig
import com.example.musicframe.image.MIN_TEXT_SCALE
import com.example.musicframe.image.PhotoMetadata
import com.example.musicframe.model.HeadphoneInfo
import com.example.musicframe.model.MusicMetadata

data class MusicFrameUiState(
    val selectedImageUri: Uri? = null,
    val originalBitmap: Bitmap? = null,
    val framedBitmap: Bitmap? = null,
    val musicMetadata: MusicMetadata? = null,
    val photoMetadata: PhotoMetadata? = null,
    val headphoneInfo: HeadphoneInfo? = null,
    val frameMode: FrameMode = FrameMode.PREMIUM_LEICA,
    val frameColorMode: FrameColorMode = FrameColorMode.DARK,
    val customFrameColorHex: String = "",
    val showHeadphoneInfo: Boolean = true,
    val useDarkBackground: Boolean = false,
    val isSaving: Boolean = false,
    val isExporting: Boolean = false,
    val exportFormat: ImageExporter.Format = ImageExporter.Format.PNG,
    val pendingShareRequest: ShareRequest? = null,
    val message: String? = null,
    val customTypeface: Typeface? = null,
    val customFontName: String? = null,
    val customFontPath: String? = null,
    val userHeadphoneTextColor: Int? = null,
    // 星座运势模式 - 用户生日
    val userBirthdayMonth: Int = 0,
    val userBirthdayDay: Int = 0,
    // 画幅 + 模板 + 自定义位置
    val canvasConfig: CanvasConfig = CanvasConfig(),
    val canvasExpanded: Boolean = false,
    val templateConfig: TemplateConfig = TemplateConfig(),
    val templateExpanded: Boolean = false,
    val customLocationText: String = "",
    val useCustomLocation: Boolean = false
)

enum class FrameColorMode {
    ORIGINAL,  // 原色
    DARK,      // 深色
    LIGHT      // 浅色
}

data class ShareRequest(val uri: Uri, val mimeType: String)
