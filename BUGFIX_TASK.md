# Bug 修复任务 - 导出闪退和按钮无响应

## 问题描述
用户反馈应用闪退，点击导出相关的两个按钮没有反应。

## 已定位的问题

### 问题 1: GifEncoderImpl.kt 反射调用错误
**文件**: `app/src/main/java/com/example/musicframe/export/GifEncoderImpl.kt`  
**行号**: 179-180  
**问题**: `getMethod` 调用缺少方法名参数  
**当前代码**:
```kotlin
val writeFrameMethod = gifEncoderClass.getMethod(
    Int::class.javaPrimitiveType
)
```
**应修复为**:
```kotlin
val writeFrameMethod = gifEncoderClass.getMethod(
    "writeFrame",
    Bitmap::class.java,
    Long::class.javaPrimitiveType,
    Int::class.javaPrimitiveType
)
```

### 问题 2: ExportManager.kt 变量名拼写错误
**文件**: `app/src/main/java/com/example/musicframe/export/ExportManager.kt`  
**行号**: 245 附近  
**问题**: 变量名 `fradedBitmap` 拼写错误，应为 `framedBitmap`  
**修复**: 将所有 `fradedBitmap` 改为 `framedBitmap`

## 执行步骤

1. **读取并修复 GifEncoderImpl.kt**
   - 定位第 179-180 行的 getMethod 调用
   - 添加正确的方法名和参数类型

2. **读取并修复 ExportManager.kt**
   - 搜索所有 `fradedBitmap` 引用
   - 修正为 `framedBitmap`

3. **编译验证**
   - 执行 `./gradlew assembleDebug`
   - 确保 BUILD SUCCESSFUL
   - 如有新错误，继续修复

4. **提交代码**
   - `git add -A`
   - `git commit -m "fix: 修复导出闪退问题 (反射调用 + 变量名拼写)"`

5. **生成 APK**
   - 确认 APK 路径：`app/build/outputs/apk/debug/AMuPtoFrame-v1.0.30.debug-debug.apk`

## 注意事项
- 使用正确的 ImageDecoder API（参考现有代码中的用法）
- 反射调用时第一个参数必须是方法名字符串
- 编译通过后才能提交
