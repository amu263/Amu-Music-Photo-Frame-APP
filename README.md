# 🎵 音乐水印相框 - Amu Music Photo Frame APP

<div align="center">

![Android](https://img.shields.io/badge/Android-15-green?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple?logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-2025.01.00-blue?logo=jetpackcompose)
![License](https://img.shields.io/badge/License-MIT-yellow)

**一款智能音乐取色相框生成工具 · 让每一张照片都有专属的 BGM**

[![GitHub Repo stars](https://img.shields.io/github/stars/amu/Mobile-music-photo-frame-app?style=social)](https://github.com/amu/Mobile-music-photo-frame-app)

[📱 功能特性](#-功能特性) • [🔐 权限说明](#-权限说明) • [🛠️ 构建编译](#️-构建编译) • [📸 使用指南](#-使用指南) • [🎨 项目生态](#-项目生态)

</div>

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
│     └── ImageExporter.kt                                    │
├─────────────────────────────────────────────────────────────┤
│  📦 Data Layer (Data Sources)                               │
│     ├── NowPlayingListenerService.kt (音乐通知监听)          │
│     ├── HeadphoneInfoRepository.kt (耳机信息)                │
│     └── PhotoMetadataReader.kt (照片元数据)                  │
└─────────────────────────────────────────────────────────────┘
```

### 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| **语言** | Kotlin | 2.0.21 |
| **UI 框架** | Jetpack Compose | BOM 2025.01.00 |
| **设计系统** | Material 3 | 1.3.0+ |
| **构建工具** | Gradle | 8.7.3 |
| **目标平台** | Android | SDK 35 (Android 15) |
| **最低支持** | Android | SDK 26 (8.0) |

### 核心依赖

- 🎨 **Compose BOM** - 声明式 UI 框架
- 🎯 **Accompanist Permissions** - 运行时权限处理
- 🎨 **Palette** - 封面取色算法
- 📷 **ExifInterface** - 照片元数据解析
- 🔍 **Detekt** - 代码质量检查

---

## ✨ 功能特性

### 🖼️ 相框模式（9 种）

| 模式 | 描述 | 适用场景 |
|------|------|----------|
| **全边框 (FULL_BORDER)** | 四周围绕彩色边框，底部留白展示信息 | 经典相框风格，适合社交媒体分享 |
| **底部加框 (BOTTOM_BAR)** | 底部加宽留白区域，两侧窄边框 | 简洁大方，突出照片主体 |
| **底边条 (BOTTOM_STRIPE)** | 窄条底部信息栏，边框高度减半 | 最小遮挡，保持照片完整性 |
| **自定义文字卡片 (CUSTOM_CARD)** | 上图下文排版，图片缩放在上，底部保留文字区 | 社交媒体分享，海报风格 |
| **浮动卡片 (FLOATING_CARD)** | 半透明覆盖卡片，可调节透明度 | 现代感设计，不遮挡照片 |
| **高端莱卡 (PREMIUM_LEICA)** | 莱卡风格高端边框设计 | 专业摄影展示，高端质感 |
| **自定义莱卡 (CUSTOM_LEICA)** | 可自定义参数的莱卡风格 | 个性化专业展示 |
| **音乐流光 (MUSIC_FLOW)** | 动态流光渐变边框，自动取色 | 音乐主题相框，动感效果 |
| **音乐纯色 (MUSIC_SOLID)** | 纯色边框，简洁稳重 | 简约风格，突出音乐信息 |

### 🎨 相框自定义功能

| 功能 | 描述 |
|------|------|
| **边框粗细调节** | 通过滑条调节边框厚度比例（默认 4%） |
| **底部留白调节** | 独立控制底部额外留白高度（默认 2%） |
| **流光渐变效果** | 支持静态流光渐变边框，可使用封面取色或自定义颜色 |
| **覆盖模式** | 支持纯覆盖模式（无边框），仅显示文字卡片 |
| **背景透明度** | 浮动/卡片模式可调节背景透明度（默认 35%） |

### 🎵 音乐信息集成

- **实时取色** - 从当前播放的音乐封面自动提取主色调
- **通知监听** - 获取歌名、歌手、专辑、播放进度、应用图标
- **耳机识别** - 自动检测并显示当前音频输出设备型号（蓝牙 A2DP/LE、有线/USB 等）
- **流光渐变** - 支持静态/动态渐变边框效果

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
- **字体导入** - 支持 TTF 字体文件导入
- **文字描边** - 纯文字模式保持可读性
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
```

---

## 🛠️ 构建编译

### 环境要求

| 组件 | 最低版本 | 推荐版本 |
|------|----------|----------|
| **JDK** | 17 | 17+ |
| **Android Studio** | Giraffe | Meerkat / Ladybug |
| **Gradle** | 8.7.3 | 8.7.3+ |
| **Android SDK** | 35 | 35 |

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
│   │   │   │   └── PhotoMetadataReader.kt   # 照片元数据
│   │   │   ├── media/
│   │   │   │   ├── NowPlayingListenerService.kt  # 音乐监听
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
    compileSdk = 35
    
    defaultConfig {
        applicationId = "com.example.musicframe"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
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
| 相框模式 | 点击模式芯片切换（9 种模式） |
| 文字显示 | 勾选/取消对应开关（照片/音乐/耳机/自定义） |
| 字号大小 | 拖动滑条调节（0.6x - 1.6x） |
| 颜色选择 | 点击预设色或输入 HEX |
| 边框粗细 | 拖动"相框粗细"滑条 |
| 底部留白 | 拖动"底部留白"滑条 |
| 流光效果 | 切换"静态流光"开关 |
| 导出格式 | 选择 PNG/JPEG/WEBP |

### 第五步：导出分享

- **保存到相册** - 点击保存按钮
- **直接分享** - 点击分享按钮
- **实况相框** - 如照片含视频，可导出 Motion Photo

---

## 🎯 项目进度

### 已完成 ✅

- [x] 核心相框合成功能
- [x] 9 种相框模式（全边框/底部加框/底边条/自定义卡片/浮动卡片/高端莱卡/自定义莱卡/音乐流光/音乐纯色）
- [x] 音乐通知监听集成
- [x] 耳机型号识别（蓝牙 A2DP/LE、有线/USB）
- [x] 照片元数据解析（EXIF/GPS/设备/实况照片）
- [x] 文字分区独立控制（4 区域独立开关 + 字号调节）
- [x] 自定义颜色与字体（HEX 输入/TTF 导入）
- [x] 流光渐变效果（静态/动态）
- [x] 多格式导出（PNG/JPEG/WEBP/Motion Photo）
- [x] 代码架构重构（UI/Domain/Data 分层）
- [x] Detekt 代码质量检查

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
