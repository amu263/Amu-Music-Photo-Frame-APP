# 动态图片导出器使用指南

## 📦 Phase 3 完成状态

✅ **已完成组件**:
1. `AnimatedImageEncoder.kt` - 编码器接口和配置类
2. `GifEncoderImpl.kt` - GIF 编码器实现
3. `AnimatedWebPEncoder.kt` - WebP 编码器实现
4. `EncoderFactory.kt` - 编码器工厂类

✅ **编译状态**: BUILD SUCCESSFUL

## 🚀 快速开始

### 1. 基本使用示例

```kotlin
// 在 ViewModel 或 Repository 中使用
class MusicFrameRepository(private val context: Context) {
    
    /**
     * 导出动态图片为 GIF
     */
    suspend fun exportAsGif(
        animatedResult: AnimatedFrameResult,
        outputFile: File,
        progressCallback: ((Int, Int) -> Unit)? = null
    ): EncodeResult {
        // 创建编码器
        val encoder = EncoderFactory.createEncoder("gif")
        
        // 将 AnimatedFrameResult 转换为 AnimatedFrame 列表
        val frames = animatedResult.frames.mapIndexed { index, bitmap ->
            AnimatedFrame(bitmap, animatedResult.frameDurations[index])
        }
        
        // 执行编码
        return encoder.encode(
            frames = frames,
            outputWidth = animatedResult.width,
            outputHeight = animatedResult.height,
            quality = 85,
            outputPath = outputFile,
            progressCallback = progressCallback
        )
    }
    
    /**
     * 导出动态图片为 WebP
     */
    suspend fun exportAsWebP(
        animatedResult: AnimatedFrameResult,
        outputFile: File,
        progressCallback: ((Int, Int) -> Unit)? = null
    ): EncodeResult {
        val encoder = EncoderFactory.createEncoder("webp")
        
        val frames = animatedResult.frames.mapIndexed { index, bitmap ->
            AnimatedFrame(bitmap, animatedResult.frameDurations[index])
        }
        
        return encoder.encode(
            frames = frames,
            outputWidth = animatedResult.width,
            outputHeight = animatedResult.height,
            quality = 85,
            outputPath = outputFile,
            progressCallback = progressCallback
        )
    }
}
```

### 2. 在 ViewModel 中使用

```kotlin
class MusicFrameViewModel(
    private val context: Context,
    private val frameComposer: FrameComposer
) : ViewModel() {
    
    private val _exportProgress = MutableLiveData<Pair<Int, Int>>()
    val exportProgress: LiveData<Pair<Int, Int>> = _exportProgress
    
    private val _exportState = MutableLiveData<ExportState>()
    val exportState: LiveData<ExportState> = _exportState
    
    /**
     * 导出动态相框
     */
    fun exportAnimatedFrame(
        sourceBitmap: Bitmap,
        config: FrameConfig,
        musicMetadata: MusicMetadata?,
        photoMetadata: PhotoMetadata?,
        headphoneInfo: HeadphoneInfo?,
        format: String = "gif"
    ) {
        viewModelScope.launch {
            try {
                _exportState.value = ExportState.Encoding(0, 0)
                
                // 1. 解码动态图片帧
                val decoder = AnimatedFrameDecoder(context)
                val frames = decoder.decode(sourceBitmap)
                
                // 2. 合成相框
                val animatedResult = frameComposer.composeAnimated(
                    frames = frames,
                    config = config,
                    musicMetadata = musicMetadata,
                    photoMetadata = photoMetadata,
                    headphoneInfo = headphoneInfo,
                    progressCallback = { current, total ->
                        _exportProgress.value = current to total
                    }
                )
                
                // 3. 导出为指定格式
                val encoder = EncoderFactory.createEncoder(format)
                val outputFile = File(
                    context.getExternalFilesDir(null),
                    "music_frame_${System.currentTimeMillis()}.$format"
                )
                
                val frameList = animatedResult.frames.mapIndexed { index, bitmap ->
                    AnimatedFrame(bitmap, animatedResult.frameDurations[index])
                }
                
                val result = encoder.encode(
                    frames = frameList,
                    outputWidth = animatedResult.width,
                    outputHeight = animatedResult.height,
                    quality = 85,
                    outputPath = outputFile,
                    progressCallback = { current, total ->
                        _exportProgress.value = current to total
                    }
                )
                
                _exportState.value = ExportState.Success(result)
                
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class ExportState {
    object Idle : ExportState()
    data class Encoding(val current: Int, val total: Int) : ExportState()
    data class Success(val result: EncodeResult) : ExportState()
    data class Error(val message: String) : ExportState()
}
```

### 3. 在 UI 中显示进度

```kotlin
@Composable
fun ExportProgressDialog(
    exportState: ExportState,
    onDismiss: () -> Unit
) {
    when (val state = exportState) {
        is ExportState.Encoding -> {
            val progress = state.current.toFloat() / state.total.toFloat()
            AlertDialog(
                title = { Text("正在导出动态图片...") },
                text = {
                    Column {
                        Text("帧：${state.current}/${state.total}")
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                onDismissRequest = onDismiss
            )
        }
        is ExportState.Success -> {
            AlertDialog(
                title = { Text("导出成功") },
                text = {
                    Text(
                        "文件大小：${state.result.fileSize / 1024} KB\n" +
                        "帧数：${state.result.frameCount}\n" +
                        "时长：${state.result.totalDurationMs} ms"
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("确定")
                    }
                },
                onDismissRequest = onDismiss
            )
        }
        is ExportState.Error -> {
            AlertDialog(
                title = { Text("导出失败") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("确定")
                    }
                },
                onDismissRequest = onDismiss
            )
        }
        else -> { /* Idle - 不显示 */ }
    }
}
```

## ⚙️ 配置选项

### EncodeConfig 使用

```kotlin
val config = EncodeConfig(
    format = "gif",          // 输出格式：gif 或 webp
    quality = 85,            // 质量：0-100
    maxWidth = 1080,         // 最大宽度
    maxHeight = 1080,        // 最大高度
    maxFrames = 100          // 最大帧数
)

// 检查格式是否支持
val isSupported = EncoderFactory.isFormatSupported(config.format)

// 获取所有支持的格式
val supportedFormats = EncoderFactory.getSupportedFormats()
```

## 📊 性能优化建议

### 1. 内存管理
```kotlin
// 在导出完成后回收 Bitmap
fun cleanupFrames(frames: List<AnimatedFrame>) {
    frames.forEach { frame ->
        if (!frame.bitmap.isRecycled) {
            frame.bitmap.recycle()
        }
    }
}
```

### 2. 后台线程
```kotlin
// 编码操作已在 Dispatchers.IO 中执行
// 确保在 ViewModel 中使用 viewModelScope.launch
```

### 3. 进度回调频率
```kotlin
// 编码器内部已优化为每 5 帧回调一次
// 避免 UI 线程过度更新
```

## 🔧 兼容性说明

### GIF 格式
- **API 34+**: 使用 Android 原生 GifEncoder（通过反射调用）
- **API 26-33**: 使用简化版 GIF 编码器（内置）
- **全 API 支持**: ✅

### WebP 格式
- **API 31+**: 完整动态 WebP 支持
- **API 26-30**: 静态 WebP（仅第一帧）
- **建议**: 动态图片优先使用 GIF 格式

## 📝 注意事项

1. **文件大小**: 建议限制帧数和尺寸，避免生成过大文件
2. **内存使用**: 大量帧时注意 Bitmap 回收
3. **格式选择**: 
   - GIF: 兼容性好，支持动画
   - WebP: 压缩率高，但 API 31+ 才支持完整动画

## 🧪 测试建议

```kotlin
@Test
fun testGifEncoder() = runTest {
    val encoder = EncoderFactory.createEncoder("gif")
    val frames = createTestFrames()
    val outputFile = File(testDir, "test.gif")
    
    val result = encoder.encode(
        frames = frames,
        outputWidth = 540,
        outputHeight = 960,
        outputPath = outputFile
    )
    
    assertTrue(outputFile.exists())
    assertTrue(result.fileSize > 0)
    assertEquals(frames.size, result.frameCount)
}
```

## 📄 许可证

本项目代码遵循 Apache 2.0 许可证。
