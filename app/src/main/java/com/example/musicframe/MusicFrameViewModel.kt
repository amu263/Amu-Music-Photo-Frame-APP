package com.example.musicframe

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicframe.domain.model.FrameColorMode
import com.example.musicframe.domain.model.MusicFrameUiState
import com.example.musicframe.domain.model.ShareRequest
import com.example.musicframe.export.ImageExporter
import com.example.musicframe.export.ImageExporter.MotionPhotoInfo
import com.example.musicframe.image.FrameComposer
import com.example.musicframe.image.FrameConfig
import com.example.musicframe.image.FrameMode
import com.example.musicframe.image.PhotoMetadataReader
import com.example.musicframe.media.HeadphoneInfoRepository
import com.example.musicframe.media.MusicMetadataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MusicFrameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicMetadataRepository()
    private val headphoneRepository = HeadphoneInfoRepository(application)
    private val frameComposer = FrameComposer()
    private val photoMetadataReader = PhotoMetadataReader(application)
    private val exporter = ImageExporter(application)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(MusicFrameUiState())
    val uiState: StateFlow<MusicFrameUiState> = _uiState.asStateFlow()

    init {
        loadPersistedFont()
        viewModelScope.launch {
            repository.nowPlaying.collect { metadata ->
                _uiState.update { it.copy(musicMetadata = metadata) }
                rebuildFrame()
            }
        }
        viewModelScope.launch {
            headphoneRepository.headphoneInfo.collect { info ->
                _uiState.update { it.copy(headphoneInfo = info) }
                rebuildFrame()
            }
        }
    }

    fun onImageSelected(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val bitmap = loadBitmap(uri)
            val photoMetadata = withContext(Dispatchers.IO) { photoMetadataReader.read(uri) }
            if (bitmap == null) {
                _uiState.update { it.copy(message = "无法加载图片") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    selectedImageUri = uri,
                    originalBitmap = bitmap,
                    photoMetadata = photoMetadata,
                    message = null
                )
            }
            rebuildFrame()
        }
    }

    fun onFontSelected(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val fontDir = File(getApplication<Application>().filesDir, "fonts").apply { mkdirs() }
            val displayName = resolveDisplayName(uri) ?: "custom_font.ttf"
            val target = File(fontDir, displayName)
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            val typeface = runCatching { Typeface.createFromFile(target) }.getOrNull()
            _uiState.update {
                it.copy(
                    customTypeface = typeface,
                    customFontName = displayName,
                    customFontPath = target.absolutePath
                )
            }
            prefs.edit().putString(KEY_FONT_PATH, target.absolutePath).apply()
            rebuildFrame()
        }
    }

    fun useDefaultFont() {
        _uiState.update { it.copy(customTypeface = null, customFontName = null, customFontPath = null) }
        prefs.edit().remove(KEY_FONT_PATH).apply()
        rebuildFrame()
    }

    fun toggleHeadphoneInfo(value: Boolean) {
        _uiState.update { it.copy(showHeadphoneInfo = value) }
        rebuildFrame()
    }

    fun updateFrameMode(mode: FrameMode) {
        _uiState.update { it.copy(frameMode = mode) }
        rebuildFrame()
    }

    fun setFrameColorMode(mode: FrameColorMode) {
        _uiState.update { it.copy(frameColorMode = mode) }
        rebuildFrame()
    }

    fun setCustomFrameColor(colorHex: String) {
        _uiState.update { it.copy(customFrameColorHex = colorHex) }
        rebuildFrame()
    }

    fun clearCustomFrameColor() {
        _uiState.update { it.copy(customFrameColorHex = "") }
        rebuildFrame()
    }

    fun setDarkBackground(enabled: Boolean) {
        _uiState.update { it.copy(useDarkBackground = enabled) }
        rebuildFrame()
    }

    fun saveFramedImage() {
        val bitmap = _uiState.value.framedBitmap ?: return
        val format = _uiState.value.exportFormat
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            runCatching {
                exporter.export(bitmap, format = format)
            }.onSuccess {
                _uiState.update { state -> state.copy(isSaving = false, message = "已保存到相册") }
            }.onFailure { error ->
                _uiState.update { state -> state.copy(isSaving = false, message = error.localizedMessage) }
            }
        }
    }

    fun shareFramedImage() {
        val bitmap = _uiState.value.framedBitmap ?: return
        val format = _uiState.value.exportFormat
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, message = null) }
            runCatching {
                exporter.export(bitmap, format = format)
            }.onSuccess { uri ->
                _uiState.update { state ->
                    state.copy(
                        isExporting = false,
                        message = "已导出，可分享",
                        pendingShareRequest = ShareRequest(uri, format.mimeType)
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(isExporting = false, message = error.localizedMessage)
                }
            }
        }
    }

    fun exportMotionPhoto() {
        val state = _uiState.value
        val bitmap = state.framedBitmap ?: return
        val motionOffset = state.photoMetadata?.motionVideoOffset ?: return
        val sourceUri = state.selectedImageUri ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, message = null) }
            runCatching {
                exporter.exportMotionPhoto(bitmap, MotionPhotoInfo(sourceUri, motionOffset))
            }.onSuccess { uri ->
                _uiState.update { state ->
                    state.copy(isExporting = false, message = "已导出实况相框", pendingShareRequest = ShareRequest(uri, ImageExporter.Format.JPEG.mimeType))
                }
            }.onFailure { error ->
                _uiState.update { state -> state.copy(isExporting = false, message = error.localizedMessage) }
            }
        }
    }

    fun onExportFormatSelected(format: ImageExporter.Format) {
        _uiState.update { it.copy(exportFormat = format) }
    }

    fun onShareRequestHandled() {
        _uiState.update { it.copy(pendingShareRequest = null) }
    }

    fun onShareFailed(message: String?) {
        _uiState.update { it.copy(message = message ?: "分享失败") }
    }

    internal fun rebuildFrame() {
        viewModelScope.launch(Dispatchers.Default) {
            val state = _uiState.value
            val source = state.originalBitmap ?: return@launch
            val config = FrameConfig(
                frameMode = state.frameMode,
                frameColorMode = state.frameColorMode,
                customFrameColorHex = state.customFrameColorHex,
                showHeadphoneInfo = state.showHeadphoneInfo,
                headphoneTextColor = state.userHeadphoneTextColor,
                typeface = state.customTypeface,
                photoMetadata = state.photoMetadata,
                useDarkBackground = state.useDarkBackground
            )
            val framed = frameComposer.compose(
                source = source,
                config = config,
                musicMetadata = state.musicMetadata,
                photoMetadata = state.photoMetadata,
                headphoneInfo = state.headphoneInfo
            )
            _uiState.update { it.copy(framedBitmap = framed) }
        }
    }

    private suspend fun loadBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val (targetWidth, targetHeight) = calculateTargetSize(info.size.width, info.size.height)
                    decoder.setTargetSize(targetWidth, targetHeight)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                resolver.openInputStream(uri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    val sampleSize = calculateSampleSize(options.outWidth, options.outHeight)
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    resolver.openInputStream(uri)?.use { decodeStream ->
                        BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
                    }
                }
            }
        }.getOrNull()
    }

    private fun calculateTargetSize(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return MAX_BITMAP_DIMENSION to MAX_BITMAP_DIMENSION
        val scale = MAX_BITMAP_DIMENSION.toFloat() / maxOf(width, height)
        return if (scale >= 1f) {
            width to height
        } else {
            (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth > MAX_BITMAP_DIMENSION || currentHeight > MAX_BITMAP_DIMENSION) {
            currentWidth /= 2
            currentHeight /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun resolveDisplayName(uri: Uri): String? {
        val cursor = getApplication<Application>().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) {
                return it.getString(index)
            }
        }
        return null
    }

    private fun loadPersistedFont() {
        val path = prefs.getString(KEY_FONT_PATH, null) ?: return
        val typeface = runCatching { Typeface.createFromFile(path) }.getOrNull()
        _uiState.update { it.copy(customTypeface = typeface, customFontPath = path, customFontName = File(path).name) }
    }

    override fun onCleared() {
        headphoneRepository.stop()
        super.onCleared()
    }

    companion object {
        private const val MAX_BITMAP_DIMENSION = 2048
        private const val PREFS_NAME = "music_frame_prefs"
        private const val KEY_FONT_PATH = "custom_font_path"
    }
}
