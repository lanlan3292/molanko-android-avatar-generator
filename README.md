# Molanko Avatar Generator (Android)

原生 Android 应用，将 Minecraft 皮肤（64×64）转换为像素风格头像。

完全使用 **Jetpack Compose + Material 3 Expressive** 构建，无 WebView。

## 功能

- 从相册选择 Minecraft 皮肤
- 自动提取头部区域平均色，生成智能轮廓 / 背景色
- 支持轮廓半径 0–3
- 自动 / 手动颜色预设（auto_dark、auto_light 等）
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

## 项目结构

```
app/src/main/java/com/molanko/avatargenerator/
├── MainActivity.kt
├── processing/
│   └── TextureProcessor.kt      # 核心算法（从 JS 精确移植）
└── ui/
    ├── theme/
    │   ├── Theme.kt             # MaterialExpressiveTheme
    │   └── Type.kt
    └── screens/
        └── HomeScreen.kt        # 主界面
```

## 算法说明

处理流程与原 `main.js` 保持一致：

1. 截取 64×16 头部区域计算平均色
2. `createBaseTexture`：从皮肤各部位最近邻拉伸到 32×32，并水平翻转绘制另一侧
3. `buildFinalCanvas`：填充背景 + 绘制内容 + 可选像素扩张轮廓
4. `applyScale`：最终最近邻放大

所有缩放均使用手动最近邻（`floor((px + 0.5) * scale)`），保证与 Node.js / 浏览器版本像素级一致。

## License

与原项目保持一致。
