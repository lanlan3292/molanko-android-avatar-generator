# Molanko Avatar Generator (Android)

原生 Android 应用，将 Minecraft 皮肤（64×64）转换为像素风格头像。

完全使用 **Jetpack Compose + Material 3 Expressive** 构建，无 WebView。

## 功能

- 从相册选择 Minecraft 皮肤
- 自动提取头部区域平均色，生成智能轮廓 / 背景色
- 支持轮廓半径 0–2
- 可选放大到 48×48 基础尺寸
- 最终最近邻缩放（1×–16×）
- 一键保存到 Pictures/Molanko

## 技术栈

- Kotlin + Jetpack Compose
- Material 3 Expressive (`MaterialExpressiveTheme` + `MotionScheme.expressive()`)
- 手动最近邻采样（与原 JS 实现完全一致）
- Coil / ImageDecoder 加载图片
- MediaStore 保存

## 构建

1. 用 Android Studio (Ladybug 或更新) 打开本目录
2. Sync Gradle
3. 运行到设备 / 模拟器（minSdk 26）

```bash
./gradlew assembleDebug
```