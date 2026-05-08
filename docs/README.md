# ReadEptd

一个基于 Jetpack Compose 开发的 Android EPUB 阅读器，支持多种电子书格式和智能阅读体验。

## ✨ 特性

- 📖 **多格式支持**：支持 EPUB、TXT、PDF 格式电子书
- 🎨 **主题系统**：浅色/深色/护眼模式，动态主题切换
- 🔊 **TTS 朗读**：文本转语音功能，支持后台播放与通知栏控制
- 🔍 **全文搜索**：跨章节搜索，双向定位
- 💾 **进度保存**：自动记录阅读位置，支持手势快速恢复
- 🎯 **Material You**：遵循 Material Design 3 设计规范，支持动态取色

## 🛠️ 技术栈

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose
- **架构**：MVVM + ViewModel
- **数据存储**：DataStore Preferences
- **EPUB 解析**：epub.js (WebView)
- **最小 SDK**：API 35 (Android 15)
- **目标 SDK**：API 36 (Android 15 QPR1)

## 📦 构建与运行

### 前置要求
- Android Studio Ladybug 或更高版本
- JDK 11+
- Android SDK API 36

### 编译安装
```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本（启用 ProGuard 压缩）
./gradlew assembleRelease
```

### ADB 安装
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/release/app-release.apk  # 覆盖安装
```

## 📁 项目结构

```
ReadEptd/
├── app/src/main/
│   ├── java/com/example/readeptd/
│   │   ├── activity/          # ViewModel (Content, Main)
│   │   ├── books/             # 阅读器实现
│   │   │   ├── epub/          # EPUB 阅读器
│   │   │   ├── pdf/           # PDF 阅读器
│   │   │   └── txt/           # TXT 阅读器
│   │   ├── data/              # 数据层 (DataStore, 模型)
│   │   ├── search/            # 搜索功能
│   │   ├── speech/            # TTS 语音服务
│   │   ├── ui/theme/          # 主题系统
│   │   └── utils/             # 工具类
│   └── assets/
│       ├── css/               # EPUB 主题样式
│       ├── js/                # EPUB 阅读器核心库
│       └── md/                # EPUB 相关文档
├── docs/                      # 项目文档
│   ├── README.md
│   └── MATERIAL_DESIGN_COLOR_GUIDE.md
└── build.gradle.kts
```

## 📝 开发文档

- [Material Design 颜色系统指南](MATERIAL_DESIGN_COLOR_GUIDE.md)
- [EPUB 阅读器架构说明](../app/src/main/assets/md/CSS_ARCHITECTURE.md)
- [主题实现总结](../app/src/main/assets/md/THEME_IMPLEMENTATION_SUMMARY.md)

## 🚀 主要功能模块

### EPUB 阅读器
- 基于 epub.js + WebView 实现
- 支持分页/滚动两种阅读模式
- 跨页文本选择与高亮
- 目录导航与章节跳转

### TTS 语音朗读
- 前台服务实现后台播放
- 通知栏播放控制（播放/暂停/上一章/下一章）
- 定时停止功能
- 语速/音调调节

### 智能搜索
- 全文索引与实时搜索
- 搜索结果上下文预览
- 双向交替搜索策略
- 结果缓存优化

## 📄 License

本项目仅供学习与研究使用。

---

**最新版本**：v1.1 | **APK 大小**：~3MB (Release)
