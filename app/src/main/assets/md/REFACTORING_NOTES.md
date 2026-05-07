# EPUB Reader 代码重构说明

## 📋 重构概述

本次重构将原本混乱的 `epub_reader.html` 文件进行了全面优化，使其更加规范、易读和易于维护。

## ✨ 主要改进

### 1. **CSS 变量化（主题支持）**

所有颜色都提取为 CSS 自定义属性（CSS Variables），位于 `:root` 中：

```css
:root {
    /* 主色调 */
    --color-primary: #3498db;
    --color-primary-dark: #1976d2;
    --color-primary-light: #e3f2fd;
    
    /* 文字颜色 */
    --color-text-primary: #333;
    --color-text-secondary: #666;
    --color-text-muted: #999;
    --color-text-inverse: #fff;
    
    /* 背景颜色 */
    --color-background: #f5f5f5;
    --color-background-light: #fafafa;
    --color-background-overlay: rgba(0, 0, 0, 0.5);
    
    /* 功能色 */
    --color-error: #e74c3c;
    --color-success: #2ecc71;
    --color-warning: #f39c12;
    
    /* 尺寸和动画 */
    --button-size: 50px;
    --transition-fast: 0.2s;
    --transition-normal: 0.3s;
}
```

**优势：**
- ✅ 后续只需修改 `:root` 中的变量值即可切换主题
- ✅ 颜色复用，避免硬编码
- ✅ 便于维护和统一调整

### 2. **模块化架构**

JavaScript 代码按功能划分为独立模块：

#### 📦 模块列表

| 模块名 | 职责 | 主要方法 |
|--------|------|----------|
| **AppState** | 全局状态管理 | `reset()` |
| **AndroidBridge** | Android 接口代理 | 所有 `on*` 回调方法 |
| **ResourceManager** | 资源清理 | `clearRendition()`, `clearBook()`, `cleanUp()` |
| **UIManager** | UI 交互管理 | 加载指示器、导航面板、按钮拖动等 |
| **ReaderCore** | 阅读器核心 | `init()`, 事件监听、书籍加载 |
| **PageOperations** | 页面操作 | 翻页、跳转、获取文本/位置 |
| **ChapterManager** | 章节管理 | `jumpToChapter()` |
| **SearchManager** | 搜索功能 | `search()`, `searchInBook()` |
| **HighlightManager** | 高亮功能 | `highlight()` |

### 3. **HTML 结构优化**

- ✅ 移除所有内联样式，全部使用 CSS 类
- ✅ 添加语义化注释标记各个区域
- ✅ 清晰的层级结构

```html
<!-- 加载指示器 -->
<div id="loading">...</div>

<!-- 错误提示 -->
<div id="error">...</div>

<!-- 阅读器容器 -->
<div id="viewer"></div>

<!-- 导航面板（目录） -->
<div id="nav-panel">...</div>

<!-- 打开目录按钮 -->
<button id="toc-btn">☰</button>
```

### 4. **代码规范**

#### 命名规范
- 常量/模块：`PascalCase` (如 `AppState`, `UIManager`)
- 方法：`camelCase` (如 `showLoading`, `handleDragStart`)
- 私有方法：保持简洁描述性命名

#### 注释规范
- 每个模块都有清晰的头部注释
- 关键逻辑有行内注释说明
- 使用分隔线区分不同模块

```javascript
// ============================================
// UI 管理模块
// ============================================
const UIManager = {
    // ...
};
```

### 5. **关注点分离**

| 关注点 | 位置 | 说明 |
|--------|------|------|
| HTML 结构 | `epub_reader.html` | 纯结构，无样式和脚本 |
| CSS 样式 | `epub_reader.html` `<style>` | 所有样式，使用 CSS 变量 |
| JavaScript | `js/epub_reader.core.js` | 所有业务逻辑，模块化组织 |
| 第三方库 | `js/` 目录 | epub.min.js, jszip.min.js, epub.custom.js |

## 📁 文件结构

```
assets/
├── epub_reader.html          # HTML + CSS（304 行，清晰简洁）
└── js/
    ├── epub_reader.core.js   # 核心业务逻辑（1035 行，模块化）
    ├── epub.min.js           # EPUB.js 库
    ├── jszip.min.js          # JSZip 库
    └── epub.custom.js        # 自定义字符分割函数
```

## 🎨 主题切换示例

要切换到深色主题，只需修改 CSS 变量：

```css
:root {
    --color-primary: #bb86fc;
    --color-text-primary: #ffffff;
    --color-text-secondary: #b0b0b0;
    --color-background: #121212;
    --color-background-light: #1e1e1e;
    --color-background-overlay: rgba(255, 255, 255, 0.1);
    /* ... 其他变量 */
}
```

## 🔧 使用方式

Android 端调用保持不变：

```javascript
// 初始化阅读器
EpubReader.init(epubUrl, startCfi);

// 翻页
EpubReader.nextPage();
EpubReader.prevPage();

// 搜索
EpubReader.search(query);

// 高亮
EpubReader.highlight(cfi, isRemove);

// 其他操作...
```

## 📊 重构前后对比

| 指标 | 重构前 | 重构后 | 改进 |
|------|--------|--------|------|
| HTML 文件大小 | ~1256 行 | 304 行 | ⬇️ 76% |
| 代码可读性 | ❌ 混乱 | ✅ 清晰 | 大幅提升 |
| 颜色硬编码 | ✅ 大量 | ❌ 无 | 完全消除 |
| 模块化程度 | ❌ 无 | ✅ 9个模块 | 结构化 |
| 主题支持 | ❌ 困难 | ✅ 简单 | 修改变量即可 |
| 维护难度 | ❌ 高 | ✅ 低 | 易于定位和修改 |

## 🚀 后续优化建议

1. **进一步拆分 JS 文件**（可选）
   - 如果模块继续增长，可以将每个模块拆分为独立文件
   - 使用 ES6 Module 或 IIFE 模式

2. **添加 TypeScript 支持**（可选）
   - 为模块添加类型定义
   - 提高代码安全性和可维护性

3. **单元测试**（可选）
   - 为核心模块编写测试用例
   - 确保功能稳定性

## 📝 注意事项

1. **兼容性**：CSS 变量在现代浏览器中广泛支持，包括 Android WebView
2. **性能**：模块化不会影响性能，反而有助于浏览器缓存和优化
3. **向后兼容**：对外接口 `window.EpubReader` 保持不变，Android 端无需修改

---

**重构完成时间**：2026-05-07  
**重构目标**：提升代码质量、可维护性和可扩展性
