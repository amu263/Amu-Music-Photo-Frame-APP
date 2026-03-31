# Phase 3 任务指令 - 动态图片导出器

## 🎯 任务目标

实现动态图片导出器，将合成后的多帧 Bitmap 导出为可播放的 GIF 或 Animated WebP 格式。

## 📋 当前架构状态

### 已完成（Phase 1-2）
✅ **数据模型**
- `AnimatedFrame`: 单帧数据（bitmap + duration）
- `AnimatedFrameResult`: 合成结果（多帧 + 元数据）

✅ **核心组件**
- `FrameComposer.composeAnimated()`: 逐帧合成相框
- `AnimatedFrameDecoder`: 动态图片解码（当前简化版只提取首帧）

✅ **编译状态**: BUILD SUCCESSFUL

### 待实现（Phase 3）
⏳ **导出器**
- GIF 编码器
- Animated WebP 编码器
- 导出格式选择

## 🏗️ 架构设计

### 1. 导出器接口设计

创建统一的导出器接口，支持多种格式：

```kotlin
interface AnimatedImageEncoder {
    /**
     * 编码动态图片
     * @param frames 帧列表
     * @param outputWidth 输出宽度
     * @param outputHeight 输出高度
     * @param quality 质量 (0-100)
     * @param outputPath 输出文件路径
     * @param progressCallback 进度回调 (当前帧/总帧数)
     * @return 导出结果（文件路径、大小、时长等）
     */
    suspend fun encode(
        frames: List<AnimatedFrame>,
        outputWidth: Int,
        outputHeight: Int,
        quality: Int = 85,
        outputPath: File,
        progressCallback: ((Int, Int) -> Unit)? = null
    ): EncodeResult
    
    /**
     * 支持的格式
     */
    val format: String
}

data class EncodeResult(
    val outputFile: File,
    val fileSize: Long,
    val frameCount: Int,
    val totalDurationMs: Long,
    val format: String
)
```

### 2. GIF 编码器实现

**方案选择**:
- **API 34+**: 使用 Android 原生 `android.graphics.GifEncoder`
- **API 28-33**: 使用第三方库 `GifEncoder` (com.github.nicklockz.GifEncoder)

**实现要点**:
```kotlin
class GifEncoderImpl : AnimatedImageEncoder {
    override val format = "image/gif"
    
    override suspend fun encode(...): EncodeResult {
        // 1. 创建 GifEncoder
        // 2. 设置尺寸、循环次数、质量
        // 3. 逐帧写入（带进度回调）
        // 4. 完成并返回结果
    }
}
```

### 3. Animated WebP 编码器实现

**API 要求**: API 27+ (Android 8.1)

**实现要点**:
```kotlin
class AnimatedWebPEncoder : AnimatedImageEncoder {
    override val format = "image/webp"
    
    override suspend fun encode(...): EncodeResult {
        // 1. 使用 Bitmap.compress(Bitmap.CompressFormat.WEBP)
        // 2. 需要特殊处理多帧写入
        // 3. 使用 ImageEncoder API (API 28+) 或第三方库
    }
}
```

### 4. 导出器工厂

创建工厂类，根据格式和 API 版本选择合适的编码器：

```kotlin
object EncoderFactory {
    fun createEncoder(format: String, minSdk: Int): AnimatedImageEncoder {
        return when (format.lowercase()) {
            "gif" -> GifEncoderImpl()
            "webp" -> {
                if (minSdk >= Build.VERSION_CODES.O) {
                    AnimatedWebPEncoder()
                } else {
                    throw UnsupportedOperationException("WebP requires API 27+")
                }
            }
            else -> throw IllegalArgumentException("Unknown format: $format")
        }
    }
}
```

## 📁 需要创建的文件

### 1. 导出器接口和结果类
**路径**: `app/src/main/java/com/example/musicframe/export/AnimatedImageEncoder.kt`

内容:
- `AnimatedImageEncoder` 接口
- `EncodeResult` 数据类
- `EncodeConfig` 配置数据类（质量、格式、尺寸等）

### 2. GIF 编码器
**路径**: `app/src/main/java/com/example/musicframe/export/GifEncoderImpl.kt`

内容:
- 实现 `AnimatedImageEncoder` 接口
- 支持 API 28+（使用第三方库）
- 支持 API 34+（使用原生 GifEncoder）
- 进度回调
- 内存优化（逐帧处理，及时回收 Bitmap）

### 3. Animated WebP 编码器
**路径**: `app/src/main/java/com/example/musicframe/export/AnimatedWebPEncoder.kt`

内容:
- 实现 `AnimatedImageEncoder` 接口
- 使用 ImageEncoder API (API 28+)
- 或使用第三方库（如 glide-webp）
- 质量和大小优化

### 4. 导出器工厂
**路径**: `app/src/main/java/com/example/musicframe/export/EncoderFactory.kt`

内容:
- 根据格式返回对应编码器
- 根据 API 版本选择实现
- 错误处理

### 5. build.gradle 依赖
**文件**: `app/build.gradle.kts`

需要添加的依赖（如果使用第三方库）:
```kotlin
dependencies {
    // GIF 编码（API 28-33）
    implementation("com.github.nicklockz.GifEncoder:library:1.0.0")
    
    // 或者使用 Glide 的 encoder
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
}
```

## 🔧 实现步骤

### Step 1: 创建导出器接口
- 定义 `AnimatedImageEncoder` 接口
- 定义 `EncodeResult` 和 `EncodeConfig`
- 编译验证

### Step 2: 实现 GIF 编码器
- 优先实现 API 34+ 原生版本
- 添加 API 28-33 的第三方库支持
- 测试逐帧编码
- 添加进度回调

### Step 3: 实现 WebP 编码器
- 使用 ImageEncoder API
- 或集成第三方库
- 测试编码质量

### Step 4: 创建工厂类
- 实现格式选择逻辑
- 添加 API 版本检查
- 错误处理

### Step 5: 集成测试
- 创建测试用例
- 测试不同格式的导出
- 测试不同尺寸的导出
- 内存泄漏检测

## ⚠️ 注意事项

### 内存管理
1. **Bitmap 回收**: 每帧编码完成后立即回收
2. **逐帧处理**: 不要一次性加载所有帧到内存
3. **尺寸限制**: 建议最大输出尺寸 1080p
4. **帧数限制**: 建议最大 100 帧

### 性能优化
1. **后台线程**: 编码必须在后台线程（使用 suspend）
2. **进度回调**: 每 5-10 帧回调一次，避免 UI 卡顿
3. **质量调节**: 提供质量参数，平衡大小和质量

### 兼容性
1. **API 27+**: 项目 minSdk
2. **GIF**: 全 API 支持
3. **WebP**: API 27+ (静态), API 28+ (动态)

## ✅ 验收标准

- [ ] 成功导出 GIF 格式，可在相册播放
- [ ] 成功导出 Animated WebP 格式
- [ ] 导出过程显示进度条
- [ ] 支持取消操作
- [ ] 文件大小合理（< 10MB）
- [ ] 无内存泄漏
- [ ] 编译通过，无警告
- [ ] 单元测试覆盖核心逻辑

## 📚 参考资料

- Android GifEncoder: https://developer.android.com/reference/android/graphics/GifEncoder
- ImageEncoder API: https://developer.android.com/reference/android/graphics/ImageEncoder
- Glide 库：https://github.com/bumptech/glide
- GifEncoder 库：https://github.com/nicklockz/GifEncoder

## 🎯 交付物

1. 完整的导出器代码（4 个 Kotlin 文件）
2. 更新的 build.gradle.kts（如需要新依赖）
3. 单元测试代码
4. 使用示例文档
5. 编译通过的 APK

---

**优先级**: 高  
**预计时间**: 2-3 天  
**难度**: 中等  
**依赖**: Phase 1-2 已完成
