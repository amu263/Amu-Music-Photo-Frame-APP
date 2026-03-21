# 音乐取色相框 (Mobile Music Photo Frame)

使用 Kotlin 与 Jetpack Compose 编写的 Android 15 相框/取色照片工具。应用会读取当前播放音乐与所选照片的元数据，自动取色生成相框，并允许覆盖文字或以卡片形式排版后保存/分享。

## 功能概览
- **多种相框模式**：全边框、底部加框、底边条、浮动卡片（覆盖显示）、自定义文字卡片（上图下文）。【F:app/src/main/java/com/example/musicframe/image/FrameComposer.kt†L18-L85】【F:app/src/main/java/com/example/musicframe/image/FrameComposer.kt†L188-L241】
- **音乐信息取色**：从通知监听器获取歌名、歌手、专辑、播放进度、应用图标与封面主色，自动用于相框默认色与文字配色。【F:app/src/main/java/com/example/musicframe/media/NowPlayingListenerService.kt†L14-L88】【F:app/src/main/java/com/example/musicframe/image/FrameComposer.kt†L15-L35】
- **照片元数据**：解析 EXIF 拍摄时间、经纬度、设备型号，并识别实况照片偏移用于导出 Motion Photo 相框。【F:app/src/main/java/com/example/musicframe/image/PhotoMetadataReader.kt†L11-L70】【F:app/src/main/java/com/example/musicframe/MusicFrameViewModel.kt†L204-L231】
- **文字分区与独立开关**：照片信息、音乐信息、自定义文字均可独立显示/隐藏，且各自拥有最小字号起步的比例滑条，默认使用最小字号避免遮挡主体。【F:app/src/main/java/com/example/musicframe/MainActivity.kt†L359-L409】【F:app/src/main/java/com/example/musicframe/image/FrameComposer.kt†L370-L451】
- **耳机型号信息**：支持读取当前音频输出设备（蓝牙 A2DP/LE、有线/USB 等），在相框文字区域镜像展示耳机标识与型号行，支持独立开关、颜色与字号调节，耳机图标自动取相框主色反色。【F:app/src/main/java/com/example/musicframe/media/HeadphoneInfoRepository.kt†L1-L58】【F:app/src/main/java/com/example/musicframe/image/FrameComposer.kt†L26-L124】【F:app/src/main/java/com/example/musicframe/MainActivity.kt†L362-L506】
- **自定义色与字体**：支持从封面自动取色或手动指定相框/文字颜色（含十六进制输入）；可导入 TTF 字体或恢复默认字体。【F:app/src/main/java/com/example/musicframe/MainActivity.kt†L68-L113】【F:app/src/main/java/com/example/musicframe/MusicFrameViewModel.kt†L58-L114】
- **静态流光相框**：相框可切换静态流光渐变效果，既能使用音乐封面取色也支持自定义颜色套用渐变，保持边框与文字默认反差配色。【F:app/src/main/java/com/example/musicframe/image/FrameComposer.kt†L104-L204】【F:app/src/main/java/com/example/musicframe/image/FrameConfig.kt†L1-L32】【F:app/src/main/java/com/example/musicframe/MainActivity.kt†L392-L470】
- **模式化文字排版**：自定义卡片模式将图片缩放置于上方、底部保留文字区；仅文字/浮动模式提供透明度调节的覆盖背景，文字支持描边以便在纯文字模式保持可读性。【F:app/src/main/java/com/example/musicframe/image/FrameComposer.kt†L63-L85】【F:app/src/main/java/com/example/musicframe/image/FrameComposer.kt†L123-L182】
- **导出与分享**：支持 PNG/JPEG/WEBP 导出到相册，或直接触发分享意图；当照片包含实况视频片段时可额外导出“实况相框”。【F:app/src/main/java/com/example/musicframe/MusicFrameViewModel.kt†L133-L191】【F:app/src/main/java/com/example/musicframe/export/ImageExporter.kt†L18-L115】

## 运行与使用
1. **首次启动授权**：允许读取媒体与通知，系统会弹出权限框；若需读取播放信息，请在“通知使用权”中启用本应用。
2. **选择图片**：点击“选择图片”调用系统照片选择器；再次点击可更换图片。【F:app/src/main/java/com/example/musicframe/MainActivity.kt†L46-L83】
3. **导入/还原字体**：在控制栏选择“导入TTF字体”从文件选择字体，或点击“恢复默认字体”。【F:app/src/main/java/com/example/musicframe/MainActivity.kt†L68-L83】
4. **切换相框模式**：在“相框模式”区域选择芯片或使用“添加模式切换”按钮循环切换。【F:app/src/main/java/com/example/musicframe/MainActivity.kt†L207-L231】
5. **控制文字显示与大小**：分别勾选“显示照片信息/显示歌曲信息/显示自定义文字”，并通过各自滑条调节字号比例；自定义文字内容在“底部自定义文字”输入框修改。【F:app/src/main/java/com/example/musicframe/MainActivity.kt†L186-L278】
6. **相框与文字颜色**：可点选预设色或输入 `#RRGGBB` / `#AARRGGBB` 手动应用；默认使用音乐封面主色与反色文字。【F:app/src/main/java/com/example/musicframe/MainActivity.kt†L232-L278】【F:app/src/main/java/com/example/musicframe/image/FrameConfig.kt†L3-L24】
7. **边框与留白**：通过“相框粗细”“底部留白/自定义模式文字区高度”滑条调节边框厚度与底部文字区高度；仅文字/浮动模式可调背景透明度。【F:app/src/main/java/com/example/musicframe/MainActivity.kt†L169-L220】【F:app/src/main/java/com/example/musicframe/image/FrameComposer.kt†L35-L85】
8. **保存或分享**：生成预览后点击“保存到相册”或“导出图片”；实况照片会出现“导出实况相框”按钮，可同时写入视频片段。【F:app/src/main/java/com/example/musicframe/MainActivity.kt†L141-L166】【F:app/src/main/java/com/example/musicframe/MusicFrameViewModel.kt†L133-L191】

## 权限与获取方式
- **媒体读取**：Android 13+ 申请 `READ_MEDIA_IMAGES`；更低版本使用 `READ_EXTERNAL_STORAGE`，Android 9 及以下额外申请 `WRITE_EXTERNAL_STORAGE`（仅限 maxSdk 28）。【F:app/src/main/AndroidManifest.xml†L3-L11】【F:app/src/main/java/com/example/musicframe/MainActivity.kt†L50-L63】
- **精确地理位置**：`ACCESS_MEDIA_LOCATION` 用于读取照片经纬度。【F:app/src/main/AndroidManifest.xml†L7-L8】
- **通知读取**：`POST_NOTIFICATIONS` 授权提示弹窗后，还需在系统“通知使用权限”中开启监听，以便 `NotificationListenerService` 读取音乐通知。【F:app/src/main/AndroidManifest.xml†L3-L9】【F:app/src/main/java/com/example/musicframe/MainActivity.kt†L84-L111】

## 构建与运行
- **环境**：JDK 17、Android Gradle Plugin 8.5.2（Gradle Wrapper 已配置）、Compose Compiler 1.5.11，Compile/Target SDK 35，minSdk 26。【F:build.gradle.kts†L1-L24】【F:app/build.gradle.kts†L1-L48】
- **IDE**：推荐 Android Studio Giraffe+，使用 “Open an existing project” 打开仓库后同步 Gradle。
- **命令行**：执行 `./gradlew assembleDebug` 或 `./gradlew installDebug` 生成/安装调试版；如需发布版会启用 R8 混淆（`release` 构建类型）。【F:app/build.gradle.kts†L10-L37】
- **依赖**：Compose BOM 2024.09.01、Material3 1.3.0、Accompanist Permissions、Palette、ExifInterface 等已在 `app/build.gradle.kts` 声明，无需额外配置。【F:app/build.gradle.kts†L50-L88】

## 导出格式
- 可在导出格式区域选择 PNG（无损）、JPEG、WEBP；Motion Photo 导出沿用 JPEG 容器并附带原视频流。【F:app/src/main/java/com/example/musicframe/MainActivity.kt†L129-L166】【F:app/src/main/java/com/example/musicframe/export/ImageExporter.kt†L18-L115】

## 故障排查
- **无法读取音乐信息**：确认已授予通知权限，并在系统设置中启用“正在播放”/通知使用权；再次播放音乐以刷新相框颜色与文字。【F:app/src/main/java/com/example/musicframe/MainActivity.kt†L84-L111】【F:app/src/main/java/com/example/musicframe/media/NowPlayingListenerService.kt†L14-L71】
- **图片加载失败**：更换图片或检查文件权限；日志中会提示“无法加载图片”。【F:app/src/main/java/com/example/musicframe/MusicFrameViewModel.kt†L32-L70】
