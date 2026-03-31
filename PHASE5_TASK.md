# Phase 5: 性能优化

## 目标
优化动态图片导出性能，减少内存占用和导出时间，提升用户体验。

## 性能优化方向

### 1. 内存优化
- **帧缓存策略**: 实现 LRU 缓存，避免一次性加载所有帧到内存
- **Bitmap 复用**: 使用 `BitmapPool` 复用 Bitmap 对象，减少内存分配
- **按需解码**: 只在需要时解码帧，而非预加载所有帧

### 2. 编码优化
- **多线程编码**: 使用协程并行处理帧编码
- **增量编码**: 支持边解码边编码，减少中间存储
- **质量/速度平衡**: 根据用户选择的质量级别调整编码参数

### 3. UI 响应性
- **后台导出**: 确保导出在后台线程进行，不阻塞 UI
- **进度更新**: 优化进度回调频率，避免过度刷新
- **取消支持**: 支持用户取消正在进行的导出任务

### 4. 图片预处理
- **尺寸优化**: 根据输出尺寸调整解码采样率
- **帧率控制**: 支持降低输出帧率以减少文件大小
- **颜色量化**: 对 GIF 使用优化的颜色调色板

## 需要创建/修改的文件

### 创建文件:
1. `app/src/main/java/com/example/musicframe/cache/FrameCache.kt` - LRU 帧缓存
2. `app/src/main/java/com/example/musicframe/util/BitmapPool.kt` - Bitmap 对象池
3. `app/src/main/java/com/example/musicframe/export/ExportWorker.kt` - 后台导出任务

### 修改文件:
1. `app/src/main/java/com/example/musicframe/export/ExportManager.kt` - 集成缓存和池
2. `app/src/main/java/com/example/musicframe/export/GifEncoder.kt` - 优化编码参数
3. `app/src/main/java/com/example/musicframe/export/AnimatedWebPEncoder.kt` - 优化编码参数
4. `app/src/main/java/com/example/musicframe/MusicFrameViewModel.kt` - 添加取消导出功能

## 实现步骤

### 步骤 1: 创建 FrameCache.kt
- 实现基于 LRU 的帧缓存
- 支持内存限制配置
- 提供 get/put/remove 接口

### 步骤 2: 创建 BitmapPool.kt
- 实现 Bitmap 对象池
- 支持按尺寸分类复用
- 添加内存泄漏防护

### 步骤 3: 创建 ExportWorker.kt
- 使用 Kotlin 协程实现后台任务
- 支持进度回调和取消
- 集成缓存和对象池

### 步骤 4: 优化 ExportManager
- 集成 FrameCache 和 BitmapPool
- 修改导出流程使用增量编码
- 添加内存监控

### 步骤 5: 优化编码器
- 调整 GIF 编码的颜色量化算法
- 优化 Animated WebP 的压缩参数
- 添加帧率控制选项

### 步骤 6: ViewModel 扩展
- 添加 `cancelExport()` 方法
- 优化进度更新频率（限制每秒最多 5 次）
- 添加内存使用状态

## 验收标准
- 导出 10 帧 GIF (480p) 内存占用 < 50MB
- 导出时间比 Phase 4 减少 30%
- UI 在导出过程中保持流畅 (60fps)
- 支持取消正在进行的导出任务
- 编译通过，无内存泄漏警告

## 技术要点
- 使用 `android.util.LruCache` 实现帧缓存
- 使用 `CoroutineScope(Dispatchers.Default)` 进行并行处理
- 使用 `AtomicBoolean` 实现取消标志
- 使用 `Bitmap.createBitmap()` 复用配置
