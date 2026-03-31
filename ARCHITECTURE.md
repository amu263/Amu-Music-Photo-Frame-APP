# A Mu PtoFrame Architecture Document

## 概述

A Mu PtoFrame (AMuPtoFrame) 是一款智能音乐取色相框生成工具，采用现代化 Android 架构设计，支持动态图片导出功能。

## 整体架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                         A Mu PtoFrame APP                            │
├─────────────────────────────────────────────────────────────────────┤
│  📱 UI Layer (Jetpack Compose)                                       │
│     ├── MainActivity.kt                                              │
│     ├── MusicFrameViewModel.kt                                       │
│     ├── AnimatedExportPanel.kt                                       │
│     └── ExportFormatSelector.kt                                      │
├─────────────────────────────────────────────────────────────────────┤
│  🔄 Export Layer (Dynamic Image Export)                               │
│     ├── ExportManager.kt                                             │
│     ├── ExportWorker.kt                                              │
│     ├── AnimatedFrameDecoder.kt                                      │
│     ├── GifEncoderImpl.kt                                            │
│     └── AnimatedWebPEncoder.kt                                       │
├─────────────────────────────────────────────────────────────────────┤
│  🧠 Domain Layer (Business Logic)                                    │
│     ├── FrameComposer.kt                                             │
│     ├── ImageExporter.kt                                             │
│     └── EncoderFactory.kt                                            │
├─────────────────────────────────────────────────────────────────────┤
│  💾 Cache & Pool Layer (Memory Management)                            │
│     ├── FrameCache.kt (LRU Cache)                                    │
│     └── BitmapPool.kt (Object Pool)                                  │
├─────────────────────────────────────────────────────────────────────┤
│  📦 Data Layer (Data Sources)                                        │
│     ├── NowPlayingListenerService.kt                                 │
│     ├── HeadphoneInfoRepository.kt                                   │
│     └── PhotoMetadataReader.kt                                       │
└─────────────────────────────────────────────────────────────────────┘
```

## 核心组件

### 1. 导出模块 (Export Module)

#### ExportManager
负责管理整个导出流程，协调各组件工作。

```
ExportManager
    ├── 帧解码 (AnimatedFrameDecoder)
    ├── 帧缓存 (FrameCache)
    ├── 相框合成 (FrameComposer)
    ├── 编码导出 (AnimatedImageEncoder)
    └── 相册保存 (MediaStore)
```

**职责：**
- 管理导出状态
- 协调帧处理流程
- 进度报告
- 错误处理

#### ExportWorker
后台导出任务执行器，处理耗时的导出操作。

**职责：**
- 后台协程执行
- 进度节流（每秒最多5次更新）
- 取消支持
- 内存清理

### 2. 编码器模块 (Encoders)

#### GifEncoderImpl
GIF 格式编码器，支持两种实现：
- **Android 14+ (UPSIDE_DOWN_CAKE)**: 使用原生 `GifEncoder` API
- **Android 13 及以下**: 使用优化的自定义 GIF 编码器

#### AnimatedWebPEncoder
WebP 格式编码器
- **Android 8+**: 支持有损 WebP 压缩
- **Android 11+**: 优化的编码设置

### 3. 内存管理模块 (Memory Management)

#### FrameCache (LRU 缓存)
基于 `LruCache` 的帧缓存，防止重复解码。

**特性：**
- 可配置内存限制（默认 32MB）
- LRU 淘汰策略
- 命中/未命中统计
- 自动内存计算（基于 `allocationByteCount`）

**使用场景：**
```kotlin
val cache = FrameCache(maxMemoryMB = 32)
cache.put(frameIndex, bitmap)
val cached = cache.get(frameIndex)
val hitRate = cache.getHitRate()
```

#### BitmapPool (对象池)
可复用的 Bitmap 对象池，减少内存分配。

**特性：**
- 按尺寸分组（避免频繁缩放）
- 最多 4 个对象每个尺寸
- 总大小限制（默认 32MB）
- 线程安全（`ConcurrentHashMap` + `synchronized`）

**使用场景：**
```kotlin
val pool = BitmapPool(maxPoolSizeMB = 32)
val bitmap = pool.acquire(width, height)
pool.release(bitmap)
```

## 数据流

### 导出流程

```
┌──────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  图片选择    │ ──→ │  AnimatedFrame   │ ──→ │  FrameComposer  │
│  (Source)    │     │  Decoder         │     │  (相框合成)     │
└──────────────┘     └──────────────────┘     └────────┬────────┘
                                                       │
                                                       ▼
┌──────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  相册保存    │ ←── │  AnimatedImage    │ ←── │  FrameCache     │
│  (MediaStore)│     │  Encoder         │     │  (帧缓存)       │
└──────────────┘     └──────────────────┘     └─────────────────┘
```

### 内存管理流程

```
┌─────────────────────────────────────────────────────────┐
│                    Memory Management                     │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌─────────────┐      ┌─────────────┐      ┌───────────┐ │
│  │  Frame      │ ──→  │  Bitmap     │ ──→  │  Export   │ │
│  │  Cache      │      │  Pool       │      │  Output   │ │
│  │  (LRU)      │      │  (Reuse)   │      │  (File)   │ │
│  └──────┬──────┘      └──────┬──────┘      └───────────┘ │
│         │                   │                            │
│         ▼                   ▼                            │
│  ┌─────────────┐      ┌─────────────┐                     │
│  │  Eviction   │      │  Recycle    │                     │
│  │  (淘汰)     │      │  (回收)     │                     │
│  └─────────────┘      └─────────────┘                     │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

## 性能优化策略

### 1. 缓存策略
- **帧缓存**: 避免重复解码相同帧
- **LRU 淘汰**: 内存紧张时自动释放旧帧
- **命中统计**: 监控缓存效率

### 2. 对象复用
- **Bitmap 对象池**: 减少内存分配开销
- **尺寸分组**: 避免缩放操作
- **弱引用**: 防止内存泄漏

### 3. 编码优化
- **质量自适应**: 根据用户选择调整压缩质量
- **尺寸限制**: 最大 1080px，避免过大文件
- **帧数限制**: 最多 100 帧，平衡质量和大小

### 4. 进度节流
- 限制更新频率（每秒最多 5 次）
- 避免 UI 线程过载
- 提供流畅的用户体验

## 配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `FRAME_CACHE_SIZE_MB` | 32MB | 帧缓存大小 |
| `BITMAP_POOL_SIZE_MB` | 32MB | 对象池大小 |
| `MAX_DIMENSION` | 1080px | 最大输出尺寸 |
| `MAX_FRAMES` | 100 | 最大帧数 |
| `MAX_PROGRESS_UPDATES_PER_SECOND` | 5 | 进度更新频率 |

## 线程模型

```
┌─────────────────────────────────────────────────────────┐
│                    Thread Model                         │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  Main Thread (UI)                                         │
│  └── 进度显示、用户交互                                   │
│                                                          │
│  Dispatchers.IO                                           │
│  ├── 帧解码                                              │
│  ├── 文件读写                                            │
│  └── 编码操作                                            │
│                                                          │
│  Dispatchers.Default                                      │
│  ├── 帧合成                                              │
│  └── 内存密集操作                                         │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

## 错误处理

### 导出错误类型

```kotlin
sealed class ExportStatus {
    data object Idle
    data class Progress(val current: Int, val total: Int, val phase: String)
    data class Success(val outputFile: File, val format: OutputFormat)
    data class Error(val message: String)
}
```

### 常见错误
- `IllegalStateException`: 无法解码动态图片
- `UnsupportedOperationException`: 不支持的格式（WebP < API 26）
- `IOException`: 文件操作失败
- `InterruptedException`: 用户取消

## 测试策略

### 单元测试
- `FrameCacheTest.kt`: LRU 缓存行为
- `BitmapPoolTest.kt`: 对象池复用
- `ExportWorkerTest.kt`: 后台任务执行

### 集成测试
- `ExportManagerIntegrationTest.kt`: 完整导出流程

### 性能测试
- `ExportBenchmark.kt`: 性能基准测试

## 扩展指南

### 添加新编码格式

1. 实现 `AnimatedImageEncoder` 接口
2. 在 `EncoderFactory` 中注册
3. 添加对应的 `OutputFormat` 枚举值

```kotlin
class NewFormatEncoder : AnimatedImageEncoder {
    override val format = "image/newformat"
    // 实现 encode() 方法
}
```

### 自定义缓存策略

继承 `FrameCache` 并重写 `entryRemoved` 方法：

```kotlin
class CustomFrameCache : FrameCache {
    override fun entryRemoved(...) {
        // 自定义淘汰逻辑，如保存到磁盘
    }
}
```

## 性能监控

使用 `logStats()` 方法监控运行时状态：

```kotlin
frameCache.logStats()
// 输出: FrameCache stats: size=16MB/32MB, hits=150, misses=10, evictions=2

bitmapPool.logStats()
// 输出: BitmapPool stats: size=8MB/32MB, borrowed=50, returned=45, pools=3
```
