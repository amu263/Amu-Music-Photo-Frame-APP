package com.example.musicframe.domain.model

import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import com.example.musicframe.export.ImageExporter
import com.example.musicframe.image.FrameMode
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
    val useLightFrame: Boolean = false,
    val customFrameColorHex: String = "",
    val showHeadphoneInfo: Boolean = true,
    val isSaving: Boolean = false,
    val isExporting: Boolean = false,
    val exportFormat: ImageExporter.Format = ImageExporter.Format.PNG,
    val pendingShareRequest: ShareRequest? = null,
    val message: String? = null,
    val customTypeface: Typeface? = null,
    val customFontName: String? = null,
    val customFontPath: String? = null,
    val userHeadphoneTextColor: Int? = null
)

data class ShareRequest(val uri: Uri, val mimeType: String)
