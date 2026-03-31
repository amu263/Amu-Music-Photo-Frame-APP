# Phase 4: UI 集成 - 动态图片导出功能

## 📋 任务目标

在 MainActivity 中集成动态图片导出功能，让用户可以：
1. 选择导出格式（GIF / Animated WebP）
2. 设置导出质量
3. 查看导出进度
4. 保存到相册或分享

## 🏗️ 架构设计

### 现有组件
- `AnimatedFrameDecoder.kt` - 解码动态图片
- `FrameComposer.kt` - 合成相框（已添加 composeAnimated 方法）
- `AnimatedImageEncoder.kt` - 编码器接口
- `GifEncoderImpl.kt` - GIF 编码器
- `AnimatedWebPEncoder.kt` - WebP 编码器
- `EncoderFactory.kt` - 编码器工厂

### 需要实现

#### 1. MainActivity 修改
- 添加导出格式选择 Spinner（GIF / WebP）
- 添加质量选择 Slider（低/中/高）
- 添加"导出"按钮
- 添加进度条显示导出进度
- 添加结果预览

#### 2. 导出管理器
创建 `ExportManager.kt`:
```kotlin
class ExportManager(private val context: Context) {
    suspend fun exportAnimatedImage(
        frames: List<Bitmap>,
        format: OutputFormat,
        quality: Int,
        outputDir: File,
        onProgress: (Int) -> Unit
    ): Result<File>
}
```

#### 3. ViewModel 扩展
在 `MusicFrameViewModel.kt` 中添加:
```kotlin
val exportState = MutableStateFlow<ExportState>(ExportState.Idle)
fun exportAnimatedPhoto(format: OutputFormat, quality: Int)
```

## 📝 实现步骤

### Step 1: 创建导出管理器
文件：`app/src/main/java/com/example/musicframe/export/ExportManager.kt`
- 整合解码器、合成器、编码器
- 处理协程和线程切换
- 实现进度回调

### Step 2: 扩展 ViewModel
文件：`app/src/main/java/com/example/musicframe/viewmodel/MusicFrameViewModel.kt`
- 添加导出状态数据类
- 添加导出方法
- 处理生命周期

### Step 3: 修改布局
文件：`app/src/main/res/layout/activity_main.xml`
- 添加格式选择 Spinner
- 添加质量选择 SeekBar
- 添加导出按钮
- 添加进度条 ProgressBar
- 添加结果预览 ImageView

### Step 4: 实现 MainActivity
文件：`app/src/main/java/com/example/musicframe/MainActivity.kt`
- 绑定 ViewModel
- 处理用户交互
- 观察导出状态
- 处理权限（保存相册）

## ✅ 验收标准

1. UI 布局完整，元素位置合理
2. 导出功能可正常触发
3. 进度条实时更新
4. 导出完成后显示预览
5. 可保存到相册或分享
6. 编译成功无错误
7. 代码符合项目现有风格

## 📚 参考文件

- Phase 3 已实现的编码器文件
- 现有 MainActivity 和 ViewModel 代码
- EXPORTER_USAGE.md 使用示例
