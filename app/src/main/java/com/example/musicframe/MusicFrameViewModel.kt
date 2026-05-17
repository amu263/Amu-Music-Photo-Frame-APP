package com.example.musicframe

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicframe.domain.model.FrameColorMode
import com.example.musicframe.domain.model.MusicFrameUiState
import com.example.musicframe.domain.model.ShareRequest
import com.example.musicframe.export.ImageExporter
import com.example.musicframe.export.ImageExporter.MotionPhotoInfo
import com.example.musicframe.image.FrameComposer
import com.example.musicframe.image.FrameConfig
import com.example.musicframe.image.LayoutTemplate
import com.example.musicframe.image.FrameMode
import com.example.musicframe.image.PhotoMetadata
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
    
    // 默认字体：使用 qiji-combo.ttf
    private val defaultTypeface: Typeface by lazy {
        try {
            Typeface.createFromAsset(application.assets, "fonts/qiji-combo.ttf")
        } catch (e: Exception) {
            android.util.Log.w("MusicFrame", "无法加载默认字体 qiji-combo.ttf，使用系统默认字体", e)
            Typeface.DEFAULT
        }
    }

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
            android.util.Log.i("MusicFrame", "开始处理图片选择：$uri")
            
            // 选择新图片时，先清理旧的缓存图片
            clearImageCache()
            
            // 先尝试获取持久化 URI 权限（在复制之前）
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                android.util.Log.i("MusicFrame", "成功获取原始 URI 的持久化权限")
            } catch (e: Exception) {
                android.util.Log.w("MusicFrame", "无法获取持久化权限，尝试使用临时权限复制", e)
            }
            
            // 方案：将图片复制到 app 私有目录，直接保存文件路径，避免 URI 权限问题
            val copiedFile = copyUriToFile(uri)
            if (copiedFile == null) {
                android.util.Log.e("MusicFrame", "复制图片到缓存失败：$uri")
                _uiState.update { it.copy(message = "无法加载图片 - 请检查是否选择了正确的图片") }
                return@launch
            }
            android.util.Log.i("MusicFrame", "图片已复制到缓存：${copiedFile.absolutePath}, 大小：${copiedFile.length()} bytes")
            
            // 从缓存文件加载图片
            val bitmap = loadBitmapFromFile(copiedFile)
            if (bitmap == null) {
                android.util.Log.e("MusicFrame", "从缓存文件加载图片失败：${copiedFile.absolutePath}")
                _uiState.update { it.copy(message = "无法加载图片") }
                return@launch
            }
            android.util.Log.i("MusicFrame", "图片加载成功，尺寸：${bitmap.width}x${bitmap.height}")
            
            // 从原始 URI 读取元数据（如果失败则忽略）
            val photoMetadata = withContext(Dispatchers.IO) {
                runCatching { photoMetadataReader.read(uri) }.getOrElse {
                    android.util.Log.w("MusicFrame", "读取元数据失败，使用默认值", it)
                    PhotoMetadata(
                        createdDateTime = null,
                        latitude = null,
                        longitude = null,
                        altitude = null,
                        deviceModel = null,
                        isMotionPhoto = false,
                        motionVideoOffset = null,
                        locationText = null,
                        focalLength = null,
                        aperture = null,
                        exposureTime = null,
                        iso = null
                    )
                }
            }
            
            _uiState.update {
                it.copy(
                    selectedImageUri = uri, // 保留原始 URI 用于元数据读取
                    originalBitmap = bitmap,
                    photoMetadata = photoMetadata,
                    message = null
                )
            }
            rebuildFrame()
        }
    }

    private suspend fun copyUriToFile(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(getApplication<Application>().cacheDir, "images")
            cacheDir.mkdirs()
            val fileName = "selected_${System.currentTimeMillis()}.jpg"
            val targetFile = File(cacheDir, fileName)
            
            android.util.Log.d("MusicFrame", "尝试打开 URI 输入流：$uri")
            val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
            if (inputStream == null) {
                android.util.Log.e("MusicFrame", "无法打开 URI 输入流，可能没有读取权限")
                return@withContext null
            }
            
            android.util.Log.d("MusicFrame", "成功打开输入流，开始复制到：${targetFile.absolutePath}")
            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val bytesCopied = input.copyTo(output)
                    android.util.Log.d("MusicFrame", "复制完成，字节数：$bytesCopied")
                }
            }
            
            if (targetFile.exists() && targetFile.length() > 0) {
                android.util.Log.d("MusicFrame", "文件创建成功，大小：${targetFile.length()} bytes")
                targetFile
            } else {
                android.util.Log.e("MusicFrame", "文件创建失败或为空")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicFrame", "复制图片到缓存文件失败：${e.message}", e)
            null
        }
    }

    private suspend fun loadBitmapFromFile(file: File): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            
            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        }.getOrElse { error ->
            android.util.Log.e("MusicFrame", "从文件加载图片失败：${error.message}", error)
            null
        }
    }

    private fun clearImageCache() {
        try {
            val cacheDir = File(getApplication<Application>().cacheDir, "images")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.listFiles()?.forEach { file ->
                    file.delete()
                    android.util.Log.d("MusicFrame", "清理缓存文件：${file.name}")
                }
                android.util.Log.d("MusicFrame", "图片缓存已清理")
            }
        } catch (e: Exception) {
            android.util.Log.w("MusicFrame", "清理缓存失败", e)
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
        // "还原默认字体"是指还原到内置的 qiji-combo.ttf，而不是系统的默认字体
        _uiState.update { it.copy(customTypeface = defaultTypeface, customFontName = "qiji-combo", customFontPath = null) }
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

    fun setUserBirthday(month: Int, day: Int) {
        _uiState.update { it.copy(userBirthdayMonth = month, userBirthdayDay = day) }
    }

    fun confirmUserBirthday() {
        rebuildFrame()
    }

    fun setDarkBackground(enabled: Boolean) {
        _uiState.update { it.copy(useDarkBackground = enabled) }
        rebuildFrame()
    }

    fun selectTemplate(template: LayoutTemplate) {
        _uiState.update { it.copy(templateConfig = it.templateConfig.copy(template = template)) }
        rebuildFrame()
    }
    fun toggleTemplatePanel() {
        _uiState.update { it.copy(templateExpanded = !it.templateExpanded) }
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
            // 如果没有自定义字体，使用默认的 qiji-combo.ttf 字体
            val typefaceToUse = state.customTypeface ?: defaultTypeface
            val config = FrameConfig(
                frameMode = state.frameMode,
                frameColorMode = state.frameColorMode,
                customFrameColorHex = state.customFrameColorHex,
                showHeadphoneInfo = state.showHeadphoneInfo,
                headphoneTextColor = state.userHeadphoneTextColor,
                typeface = typefaceToUse,
                photoMetadata = state.photoMetadata,
                useDarkBackground = state.useDarkBackground,
                userBirthdayMonth = state.userBirthdayMonth,
                userBirthdayDay = state.userBirthdayDay,
                templateConfig = state.templateConfig
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
            // 尝试重新获取临时权限（如果之前获取持久化权限失败）
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // 忽略异常，继续使用现有权限
            }
            
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
        }.getOrElse { error ->
            // 记录错误日志
            android.util.Log.e("MusicFrame", "加载图片失败：${error.message}", error)
            null
        }
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
