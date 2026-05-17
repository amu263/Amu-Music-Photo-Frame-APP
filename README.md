# 🎵 A Mu PtoFrame - Amu Music Photo Frame APP

<div align="center">

![Android](https://img.shields.io/badge/Android-15-green?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple?logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-2025.02.00-blue?logo=jetpackcompose)
![License](https://img.shields.io/badge/License-MIT-yellow)
![Version](https://img.shields.io/badge/Version-1.5-blue)
![VersionCode](https://img.shields.io/badge/VersionCode-38-green)

**一款智能音乐取色相框生成工具 · 让每一张照片都有专属的 BGM**

[![GitHub Repo stars](https://img.shields.io/github/stars/amu263/Amu-Music-Photo-Frame-APP?style=social)](https://github.com/amu263/Amu-Music-Photo-Frame-APP)

[📱 功能特性](#-功能特性) • [🔐 权限说明](#-权限说明) • [🛠️ 构建编译](#️-构建编译) • [📸 使用指南](#-使用指南) • [🎨 项目生态](#-项目生态)

</div>

---

## 🌟 新版本 v1.5 更新内容

### 🎨 音乐封面采样颜色算法全面升级

**彻底重写封面取色与色彩增强管线**，让 MUSIC_FLOW（音乐流光）和 MUSIC_SOLID（音乐纯色）模式的色彩更鲜艳、更大胆、更有高级感。

#### 取色升级：从「安全均值」到「大胆鲜艳」

| 旧算法 | 新算法 |
|--------|--------|
| `Palette.getDominantColor()` — 取最安全的均值色 | `extractPremiumColor()` — 优先 Vibrant 鲜艳色板 |
| 灰暗、保守、缺乏辨识度 | 鲜艳、大胆、高级感十足 |

**取色优先级**：DarkVibrant → Vibrant → LightVibrant → Dominant（兜底）

#### 智能 Vibrance 色彩增强（三项核心算法）

| 算法 | 效果 | 技术细节 |
|------|------|----------|
| **`boostVibrancy()`** | 通用自然饱和度增强 | 渐进式：低饱和猛拉 2.8x，高饱和克制微调 |
| **`enhanceVibrancyForLightMode()`** | 浅色模式鲜亮优化 | 饱和度 3.5x 起跳 + 亮度锁定高级亮区间(0.55-0.85) + 色温暖调 |
| **`enhanceVibrancyForDarkMode()`** | 深色模式浓郁优化 | 饱和提升 + 亮度深沉但不过黑，保持色彩辨识度 |

#### MUSIC_FLOW 条纹交替色革新

- **旧**：纯亮度 ±40%/50%，单调乏味
- **新**：色相偏移 ±18° + 智能亮度对比，条纹层次丰富

#### 改动范围

- `NowPlayingListenerService.kt` — 封面取色器重写
- `FrameConfig.kt` — +156 行智能色彩增强算法
- 零破坏性：PREMIUM_LEICA / CUSTOM_LEICA / ZODIAC_HOROSCOPE 模式不受影响

---

## 🌟 v1.4 更新内容

### 🔔 通知权限修复（重要！）

**通知权限检测机制全面重写**，彻底解决权限丢失问题：

| 状态 | 含义 | 处理方式 |
|------|------|----------|
| **已授权 (GRANTED)** | 通知使用权已开启 | ✅ 正常工作 |
| **未授权 (NOT_GRANTED)** | 用户未授权通知权限 | 点击按钮引导授权 |
| **组件被禁用 (COMPONENT_DISABLED)** | 系统禁用了组件 | 引导用户手动开启 |
| **服务无响应 (SERVICE_NOT_RESPONDING)** | 服务断开 | 自动尝试重新绑定 |

#### 📌 如何重新获取通知授权？

如果发现音乐信息无法获取，按以下步骤操作：

1. **点击"已授权"按钮** — 应用会立即重新检测当前权限状态
2. 如果仍显示未授权，前往 **系统设置 → 应用设置 → 音乐水印相框**
3. 进入 **通知使用权** 页面，确保开关已开启
4. 返回应用后**再次点击"已授权"按钮**，验证状态

> 💡 **提示**：应用会在 `onResume` 时自动检查权限，也会定期检测元数据是否停止更新（服务断开检测）。如果服务断开，应用会自动尝试重新绑定。

---

### 🎨 幸运色与运势系统（全新功能）

| 功能 | 描述 |
|------|------|
| **12 种高级电影感冷调色系** | 霓虹红 / 翡翠绿 / 激光青 / 幻影紫 / 熔岩橙 / 月光银 / 水银灰 / 深玫红 / 电光蓝 / 深空蓝 / 冰冷青 / 热粉 |
| **10 级运势配色** | 1-10 级运势，配色从红→橙→黄→绿→紫→青→蓝渐变 |
| **238 条行动建议库** | 核心种子库，每日 40 条唯一组合，支撑数十年不重复 |
| **运势前缀/后缀多样化** | 增加变化性，更具趣味性 |

---

## 🌟 项目生态

### 项目架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Music Photo Frame APP                     │
├─────────────────────────────────────────────────────────────┤
│  🎨 UI Layer (Jetpack Compose)                              │
│     ├── MainActivity.kt                                     │
│     ├── FrameControls.kt                                    │
│     ├── ColorSelectionRow.kt                                │
│     └── ExportFormatSelector.kt                             │
├─────────────────────────────────────────────────────────────┤
│  🧠 Domain Layer (Business Logic)                           │
│     ├── MusicFrameViewModel.kt                              │
│     ├── FrameComposer.kt                                    │
│     ├── HoroscopeCalculator.kt                              │
│     └── ImageExporter.kt                                    │
├─────────────────────────────────────────────────────────────┤
│  📦 Data Layer (Data Sources)                               │
│     ├── NowPlayingListenerService.kt (音乐通知监听)          │
│     ├── MusicMetadataBroadcaster.kt (元数据广播)             │
│     ├── HeadphoneInfoRepository.kt (耳机信息)                │
│     └── PhotoMetadataReader.kt (照片元数据)                  │
└─────────────────────────────────────────────────────────────┘
```

### 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| **语言** | Kotlin | 2.0.21 |
| **UI 框架** | Jetpack Compose | BOM 2025.02.00 |
| **设计系统** | Material 3 + SaltUI 风格 | 1.3.0+ |
| **构建工具** | Gradle | 8.7.3 |
| **目标平台** | Android | SDK 36 (Android 16) |
| **最低支持** | Android | SDK 26 (8.0) |

### 核心依赖

- 🎨 **Compose BOM** - 声明式 UI 框架
- 🎯 **Accompanist Permissions** - 运行时权限处理
- 🎨 **Palette** - 封面取色算法
- 📷 **ExifInterface** - 照片元数据解析
- 🔍 **Detekt** - 代码质量检查
- ✨ **幸运色系统** - 电影感冷调色系

---

## ✨ 功能特性

### 🖼️ 相框模式（4 种）

| 模式 | 描述 | 适用场景 |
|------|------|----------|
| **高端莱卡 (PREMIUM_LEICA)** | 莱卡风格高端边框设计，文字粗体 + 对比色描边保护 | 专业摄影展示，高端质感 |
| **自定义莱卡 (CUSTOM_LEICA)** | 可自定义颜色的莱卡风格，支持 HEX 颜色输入 | 个性化专业展示 |
| **音乐流光 (MUSIC_FLOW)** | 高级竖纹模式，支持原色/深色/浅色三档，斜体文字 + 对比色描边保护 | 音乐主题相框，高级质感 |
| **音乐纯色 (MUSIC_SOLID)** | 纯色边框，简洁稳重，支持深色背景模式 | 简约风格，突出音乐信息 |

### 🎨 相框自定义功能

| 功能 | 描述 |
|------|------|
| **三档颜色模式** | 原色模式（封面原色）/ 深色模式（变暗 65%）/ 浅色模式（提亮 35%） |
| **自定义徕卡颜色** | 支持 HEX 颜色输入，即时应用，可清除恢复默认 |
| **深色背景模式** | 支持切换深色背景，增强毛玻璃模糊效果 |

### 🎵 音乐信息集成

- **实时取色** - 从当前播放的音乐封面自动提取主色调
- **通知监听** - 获取歌名、歌手、专辑、播放进度、应用图标
- **耳机识别** - 自动检测并显示当前音频输出设备型号（蓝牙 A2DP/LE、有线/USB 等）
- **流光渐变** - 支持静态/动态渐变边框效果
- **深色背景模式** - 支持切换深色背景，增强毛玻璃模糊效果

### 📸 照片元数据解析

- 📅 **拍摄时间** - 读取 EXIF 日期信息
- 📍 **地理位置** - 解析经纬度坐标
- 📱 **设备型号** - 显示拍摄设备
- 🎬 **实况照片** - 识别并导出 Motion Photo 相框

### 🎨 文字系统

#### 文字分区与独立开关

| 文字区域 | 独立开关 | 字号调节 | 默认状态 |
|----------|----------|----------|----------|
| **照片信息** | ✅ | 0.6x - 1.6x | 最小字号 |
| **音乐信息** | ✅ | 0.6x - 1.6x | 最小字号 |
| **耳机信息** | ✅ | 0.6x - 1.6x | 最小字号 |
| **自定义文字** | ✅ | 0.6x - 1.6x | 最小字号 |

#### 文字样式

- **颜色选择** - 自动反色或手动指定（支持 HEX 输入 `#RRGGBB` / `#AARRGGBB`）
- **字体导入** - 支持 TTF 字体文件导入，默认使用 qiji-combo.ttf
- **文字描边** - 纯文字模式保持可读性，音乐流光模式使用对比色描边（8% 文字大小）
- **文字效果** - 高级徕卡模式使用粗体，音乐流光模式使用斜体
- **对齐方式** - 支持左对齐、居中对齐、右对齐

### 📤 导出与分享

| 功能 | 描述 |
|------|------|
| **格式选择** | PNG（无损）/ JPEG / WEBP |
| **保存到相册** | 一键导出到系统相册 |
| **直接分享** | 触发系统分享意图 |
| **实况相框** | Motion Photo 视频片段导出（JPEG 容器附带原视频流） |

---

## 🔐 权限说明

### 必需权限

| 权限 | 用途 | Android 版本要求 |
|------|------|------------------|
| `READ_MEDIA_IMAGES` | 读取用户选择的照片 | Android 13+ |
| `READ_EXTERNAL_STORAGE` | 读取照片（旧版本） | Android 12 及以下 |
| `WRITE_EXTERNAL_STORAGE` | 保存导出的相框 | Android 9 及以下 (maxSdk 28) |
| `ACCESS_MEDIA_LOCATION` | 读取照片 GPS 位置信息 | 所有版本 |
| `POST_NOTIFICATIONS` | 通知权限提示 | Android 13+ |

### 特殊授权

**📢 通知使用权（Notification Listener）**

为了获取当前播放的音乐信息，应用需要**通知使用权**：

1. 首次启动时，系统会弹出权限申请对话框
2. 点击"允许"后，需前往系统设置 → **通知使用权**
3. 在列表中找到 **"音乐水印相框"** 并开启开关

> ⚠️ **注意**：仅授予通知权限不足以获取音乐信息，必须同时在系统设置中启用通知使用权。

### ❓ 权限丢失或音乐信息不显示？

如果发现音乐信息无法获取，按以下步骤操作：

1. **点击"已授权"按钮** — 应用会立即重新检测当前权限状态
2. 如果仍显示未授权，前往 **系统设置 → 应用设置 → 音乐水印相框**
3. 进入 **通知使用权** 页面，确保开关已开启
4. 返回应用后**再次点击"已授权"按钮**，验证状态

> 💡 **提示**：应用内置多层检测机制 — 会自动检测 Settings 权限状态、组件启用状态、服务实际响应情况。如果服务断开，应用会自动尝试重新绑定。

### 权限流程图

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  首次启动    │ ──→ │  系统权限弹窗 │ ──→ │  通知使用权  │
│  应用        │     │  (媒体读取)  │     │  (系统设置)  │
└──────────────┘     └──────────────┘     └──────────────┘
        │                    │                    │
        ▼                    ▼                    ▼
   检查权限状态         用户点击允许          手动开启开关
        │                    │                    │
        └────────────────────┴────────────────────┘
                              │
                              ▼
                      ✅ 可以读取音乐信息
                              
         ┌─────────────────────────────────────┐
         │  ⚠️ 权限丢失？音乐信息不显示？        │
         │                                     │
         │  1. 点击"已授权"按钮重新检测        │
         │  2. 前往系统设置确认通知使用权已开启 │
         │  3. 返回后再次点击验证状态           │
         └─────────────────────────────────────┘
```

---

## 🛠️ 构建编译

### 环境要求

| 组件 | 最低版本 | 推荐版本 |
|------|----------|----------|
| **JDK** | 17 | 17+ |
| **Android Studio** | Giraffe | Meerkat / Ladybug |
| **Gradle** | 8.7.3 | 8.7.3+ |
| **Android SDK** | 36 | 36 |

### 快速开始

#### 1️⃣ 克隆项目

```bash
git clone https://github.com/amu263/Amu-Music-Photo-Frame-APP.git
cd Amu-Music-Photo-Frame-APP
```

#### 2️⃣ 使用 Android Studio（推荐）

```
File → Open → 选择项目根目录
等待 Gradle 同步完成
点击 Run (▶️) 或按 Shift+F10
```

#### 3️⃣ 命令行构建

```bash
# 安装调试版本
./gradlew assembleDebug

# 安装到连接的设备
./gradlew installDebug

# 构建发布版本（启用 R8 混淆）
./gradlew assembleRelease

# 运行代码质量检查
./gradlew detekt

# 运行测试
./gradlew test
```

#### 4️⃣ 输出位置

```
app/build/outputs/apk/debug/       # 调试版 APK
app/build/outputs/apk/release/     # 发布版 APK
app/build/outputs/bundle/release/  # AAB 发布包
```

### 项目结构

```
Amu-Music-Photo-Frame-APP/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/musicframe/
│   │   │   ├── MainActivity.kt              # 主界面
│   │   │   ├── MusicFrameViewModel.kt       # 视图模型
│   │   │   ├── MusicFrameViewModelFactory.kt
│   │   │   ├── image/
│   │   │   │   ├── FrameComposer.kt         # 相框合成器
│   │   │   │   ├── FrameConfig.kt           # 相框配置
│   │   │   │   ├── FrameMode.kt             # 相框模式枚举
│   │   │   │   ├── HoroscopeCalculator.kt   # 幸运色/运势计算
│   │   │   │   └── PhotoMetadataReader.kt   # 照片元数据
│   │   │   ├── media/
│   │   │   │   ├── NowPlayingListenerService.kt  # 音乐监听
│   │   │   │   ├── MusicMetadataBroadcaster.kt   # 元数据广播
│   │   │   │   ├── HeadphoneInfoRepository.kt    # 耳机信息
│   │   │   │   └── MusicMetadataRepository.kt    # 音乐元数据
│   │   │   ├── export/
│   │   │   │   └── ImageExporter.kt         # 图片导出
│   │   │   ├── domain/model/                # 领域模型
│   │   │   │   ├── BorderParams.kt
│   │   │   │   ├── FrameControlAction.kt
│   │   │   │   ├── FrameDrawParams.kt
│   │   │   │   ├── FrameModeExt.kt
│   │   │   │   ├── MusicFrameUiState.kt
│   │   │   │   └── TextBlockParams.kt
│   │   │   ├── model/                       # 数据模型
│   │   │   │   ├── HeadphoneInfo.kt
│   │   │   │   └── MusicMetadata.kt
│   │   │   └── ui/
│   │   │       ├── components/              # UI 组件
│   │   │       │   └── ColorSelectionRow.kt
│   │   │       ├── screen/                  # 界面屏幕
│   │   │       │   ├── FrameControls.kt
│   │   │       │   └── ExportFormatSelector.kt
│   │   │       └── theme/                   # 主题
│   │   │           ├── Color.kt
│   │   │           ├── Theme.kt
│   │   │           └── Type.kt
│   │   ├── assets/fonts/                     # 字体文件
│   │   │   └── qiji-combo.ttf               # 默认水印字体
│   │   ├── res/                             # 资源文件
│   │   └── AndroidManifest.xml              # 应用清单
│   └── build.gradle.kts                     # 模块构建配置
├── build.gradle.kts                         # 项目构建配置
├── settings.gradle.kts                      # 项目设置
├── gradle.properties                        # Gradle 属性
├── detekt-config.yml                        # 代码检查配置
└── README.md                                # 项目文档
```

### 关键配置

**`app/build.gradle.kts`**
```kotlin
android {
    compileSdk = 36
    namespace = "com.example.musicframe"
    
    defaultConfig {
        applicationId = "com.example.musicframe"
        minSdk = 26
        targetSdk = 35
        versionCode = 37
        versionName = "1.4"
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.palette:palette-ktx")
    implementation("androidx.exifinterface:exifinterface")
    implementation("com.google.accompanist:accompanist-permissions")
    // ... 其他依赖
}
```

---

## 📸 使用指南

### 第一步：授权权限

1. 启动应用
2. 授予 **媒体读取权限**
3. 授予 **通知使用权**（需前往系统设置）

### 第二步：选择照片

点击 **"选择图片"** 按钮，从系统相册选择一张照片。

### 第三步：自动取色

应用会自动：
- 检测当前播放的音乐
- 提取封面主色调
- 生成相框预览

### 第四步：自定义调整

| 控制项 | 操作 |
|--------|------|
| 相框模式 | 点击模式芯片切换（4 种模式：高端莱卡/自定义莱卡/音乐流光/音乐纯色） |
| 文字显示 | 勾选/取消对应开关（照片/音乐/耳机/自定义） |
| 字号大小 | 拖动滑条调节（0.6x - 1.6x） |
| 颜色选择 | 点击预设色或输入 HEX（支持三档颜色模式 + 自定义徕卡颜色） |
| 深色背景 | 切换深色背景模式开关 |
| 导出格式 | 选择 PNG/JPEG/WEBP |

### 第五步：导出分享

- **保存到相册** - 点击保存按钮
- **直接分享** - 点击分享按钮
- **实况相框** - 如照片含视频，可导出 Motion Photo

---

## 🎯 项目进度

### 已完成 ✅

- [x] 核心相框合成功能
- [x] 4 种相框模式（高端莱卡/自定义莱卡/音乐流光/音乐纯色）
- [x] 音乐通知监听集成
- [x] 耳机型号识别（蓝牙 A2DP/LE、有线/USB）
- [x] 照片元数据解析（EXIF/GPS/设备/实况照片）
- [x] 文字分区独立控制（4 区域独立开关 + 字号调节）
- [x] 自定义颜色与字体（HEX 输入/TTF 导入）
- [x] 多格式导出（PNG/JPEG/WEBP/Motion Photo）
- [x] 代码架构重构（UI/Domain/Data 分层）
- [x] Detekt 代码质量检查

### 已完成 ✅ (续)

- [x] 应用名称更新为 A Mu PtoFrame
- [x] 高级竖纹模式（v1.0.13）
- [x] 音乐流光模式文字斜体效果（v1.0.17）
- [x] 音乐流光模式文字对比色描边保护（v1.0.20）
- [x] 深色背景模式开关（v1.0.11）
- [x] 三档颜色模式：原色/深色/浅色（v1.0.23）
- [x] 自定义徕卡颜色即时应用 + 清除按钮（v1.0.26）
- [x] 高级徕卡模式胶囊背景框（v1.0.28）
- [x] 高级徕卡模式对比色描边替代胶囊背景（v1.0.29）
- [x] 高级徕卡模式文字粗体效果（v1.0.30）
- [x] GitHub PR 工作流集成

### v1.4 新功能 ✅

- [x] **通知权限检测机制重写** — 修复权限丢失问题
- [x] **SaltUI 风格 UI 重构** — 薄荷绿主题配色
- [x] **幸运色系统** — 12 种高级电影感冷调色系
- [x] **10 级运势配色** — 红→橙→黄→绿→紫→青→蓝渐变
- [x] **238 条行动建议库** — 每日 40 条唯一组合
- [x] **运势前缀/后缀多样化** — 增加趣味性
- [x] **qiji-combo.ttf 默认字体** — 水印相框文字
- [x] **compileSdk 升级到 36** — 适配 Android 16

### 待优化 🚧

- [ ] 自动修复 Detekt 风格问题
- [ ] 添加单元测试
- [ ] 支持更多音乐平台
- [ ] 云端模板同步

---

## 📄 许可证

本项目采用 **MIT 许可证** - 详见 [LICENSE](LICENSE) 文件

---

<div align="center">

**Made with ❤️ by amu263**

如果这个项目对你有帮助，请给一个 ⭐️ Star！

</div>
