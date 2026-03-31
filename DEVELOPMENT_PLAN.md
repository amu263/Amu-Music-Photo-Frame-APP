# 动态照片水印相框适配开发计划

## 🎯 功能目标

开发音乐水印相框对动态照片（GIF/Animated WebP）的适配功能，当用户导入动态照片时，可导出带动态视觉的音乐水印相框照片。

## 📋 需求分析

### 当前功能状态
- ✅ 已支持静态照片（JPEG/PNG/WEBP）的音乐水印相框合成
- ✅ 已支持 Motion Photo（实况照片）的视频片段提取和导出
- ❌ 不支持 GIF 动态图片的导入和处理
- ❌ 不支持 Animated WebP 的导入和处理
- ❌ 不支持动态水印相框的导出

### 目标功能
1. **动态照片识别**：自动检测导入的图片是否为 GIF 或 Animated WebP
2. **动态相框合成**：为动态照片的每一帧合成音乐水印边框
3. **动态导出**：导出带有音乐水印的动态 GIF/Animated WebP
4. **性能优化**：确保动态照片处理不会导致 UI 卡顿

## 🏗️ 技术方案

### 1. 动态照片检测
**文件**: `app/src/main/java/com/example/musicframe/image/PhotoMetadataReader.kt`

**修改内容**:
- 添加 `isAnimated` 字段到 `PhotoMetadata`
- 添加 `frameCount` 字段记录帧数
- 添加 `duration` 字段记录总时长（毫秒）
- 使用 `ImageDecoder` 或 `GifDecoder` 检测动态图片

```kotlin
data class PhotoMetadata(
    // ... 现有字段
    val isAnimated: Boolean = false,
    val frameCount: Int = 1,
    val duration: Long = 0L,  // 总时长（毫秒）
    val animationType: AnimationType? = null  // GIF / WEBP
) {
    enum class AnimationType { GIF, WEBP }
}
```

### 2. 动态相框合成器
**文件**: `app/src/main/java/com/example/musicframe/image/FrameComposer.kt`

**新增方法**:
```kotlin
fun composeAnimated(
    frames: List<Bitmap>,
    frameDurations: List<Int>,
    config: FrameConfig,
    musicMetadata: MusicMetadata?,
    photoMetadata: PhotoMetadata?,
    headphoneInfo: HeadphoneInfo?
): AnimatedFrameResult

data class AnimatedFrameResult(
    val frames: List<Bitmap>,
    val frameDurations: List<Int>,
    val width: Int,
    val height: Int
)
```

**实现策略**:
- 复用现有的 `compose` 方法处理每一帧
- 使用协程并行处理多帧（提高性能）
- 内存管理：及时回收中间 Bitmap，避免 OOM

### 3. 动态图片导出器
**文件**: `app/src/main/java/com/example/musicframe/export/ImageExporter.kt`

**新增方法**:
```kotlin
suspend fun exportAnimatedPhoto(
    frames: List<Bitmap>,
    frameDurations: List<Int>,
    fileName: String = defaultFileName(),
    format: AnimatedFormat = AnimatedFormat.GIF
): Uri

enum class AnimatedFormat(
    val displayName: String,
    val mimeType: String,
    val extension: String
) {
    GIF("GIF", "image/gif", "gif"),
    WEBP("Animated WEBP", "image/webp", "webp")
}
```

**依赖库**:
- GIF 编码：使用 `android.graphics.gif.GifEncoder` (API 34+) 或第三方库 `GifEncoder`
- WebP 编码：使用 `Bitmap.compress(Bitmap.CompressFormat.WEBP)` (支持 Animated WebP)

### 4. ViewModel 层适配
**文件**: `app/src/main/java/com/example/musicframe/MusicFrameViewModel.kt`

**修改内容**:
- 修改 `onImageSelected` 方法，支持动态图片加载
- 添加 `exportAnimatedPhoto` 方法
- 在 `uiState` 中添加动态图片相关状态

```kotlin
data class MusicFrameUiState(
    // ... 现有字段
    val isProcessingAnimated: Boolean = false,
    val animatedFrameCount: Int = 0,
    val processingProgress: Int = 0  // 0-100
)
```

### 5. UI 层适配
**文件**: `app/src/main/java/com/example/musicframe/MainActivity.kt`

**修改内容**:
- 在导出按钮处根据图片类型显示不同选项
- 添加进度条显示动态图片处理进度
- 添加提示信息告知用户动态图片处理可能需要时间

## 📝 实现步骤

### Phase 1: 基础支持（1-2 天）
- [ ] 修改 `PhotoMetadata` 添加动态图片字段
- [ ] 实现动态图片检测逻辑
- [ ] 在 `PhotoMetadataReader` 中解析帧数和时长
- [ ] 更新 UI 显示动态图片信息

### Phase 2: 合成器开发（2-3 天）
- [ ] 实现 `FrameComposer.composeAnimated` 方法
- [ ] 添加帧处理进度回调
- [ ] 优化内存管理，避免 OOM
- [ ] 添加性能日志

### Phase 3: 导出器开发（2-3 天）
- [ ] 实现 GIF 导出功能
- [ ] 实现 Animated WebP 导出功能
- [ ] 添加导出格式选择器
- [ ] 测试导出文件兼容性

### Phase 4: 集成测试（1-2 天）
- [ ] 在 ViewModel 中集成动态图片导出
- [ ] UI 适配和用户体验优化
- [ ] 测试各种尺寸的动态图片
- [ ] 性能测试和优化

### Phase 5: 文档和发布（1 天）
- [ ] 更新 README.md
- [ ] 编写使用指南
- [ ] 代码审查和清理
- [ ] 提交到 GitHub

## 🔧 依赖库

### 需要添加的依赖
```kotlin
// app/build.gradle.kts
dependencies {
    // GIF 解码/编码（如果需要支持 API 34 以下）
    implementation("com.github.bumptech.glide:glide:4.16.0")
    
    // 或者使用纯 Kotlin 的 GIF 编码器
    // implementation("net.engawapg.lib:zoomable:1.5.1") // 如果有合适的库
}
```

### Android 原生支持
- **GIF**: API 34+ 原生支持 `GifEncoder`，低版本需要第三方库
- **Animated WebP**: API 27+ 原生支持 `Bitmap.compress(WEBP)`

## ⚠️ 注意事项

### 性能考虑
1. **内存管理**: 动态图片可能包含数十到数百帧，需要谨慎管理内存
2. **处理时间**: 合成多帧需要时间，需要显示进度提示
3. **文件大小**: 动态相框文件会显著增大，需要提示用户

### 兼容性考虑
1. **Android 版本**: GIF 编码在不同 API 级别的支持不同
2. **图片尺寸**: 超大尺寸动态图片需要降采样处理
3. **帧数限制**: 过多帧数可能导致处理失败，考虑限制最大帧数（如 100 帧）

### 用户体验
1. **进度反馈**: 处理动态图片时显示实时进度
2. **取消操作**: 允许用户取消长时间处理
3. **预览功能**: 提供导出前的动态预览

## 📊 测试计划

### 测试用例
1. **GIF 导入**: 测试不同尺寸、帧数的 GIF 图片
2. **Animated WebP 导入**: 测试 WebP 动态图片
3. **静态图片**: 确保不影响现有静态图片功能
4. **Motion Photo**: 确保与现有 Motion Photo 功能不冲突
5. **边界情况**: 超大图片、超多帧数、损坏文件

### 性能指标
- 单帧处理时间 < 100ms
- 100 帧 GIF 处理时间 < 10s
- 内存峰值 < 500MB
- 导出文件大小合理（< 10MB）

## 🎉 验收标准

- [ ] 用户可以导入 GIF 和 Animated WebP 图片
- [ ] 系统自动识别动态图片并显示相关信息
- [ ] 音乐水印正确应用到每一帧
- [ ] 导出的动态图片保持原有帧率和时长
- [ ] 导出文件可以在相册中正常播放
- [ ] 处理过程有进度提示
- [ ] 性能满足指标要求
- [ ] 不影响现有静态图片和 Motion Photo 功能

## 📚 参考资料

- [Android ImageDecoder](https://developer.android.com/reference/android/graphics/ImageDecoder)
- [Animated WebP](https://developers.google.com/speed/webp/docs/animated)
- [GIF Format Specification](https://www.w3.org/Graphics/GIF/spec-gif89a.txt)
- [Bitmap.compress()](https://developer.android.com/reference/android/graphics/Bitmap#compress(android.graphics.Bitmap.CompressFormat,%20int,%20java.io.OutputStream))

---

**创建时间**: 2026-03-31  
**分支**: feature/dynamic-photo-watermark  
**基于版本**: v1.0.30-release
