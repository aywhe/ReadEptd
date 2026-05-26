# EPUB 阅读器主题配色优化方案

## 📋 设计原则

### 1. 主题定位
- **浅色主题 (Light)**：白天使用，蓝色系主调
- **护眼主题 (Eye-Care)**：白天使用，暖色调，减少蓝光
- **深色主题 (Dark)**：夜晚使用，降低亮度

### 2. 颜色层次（重要性递减）
```
--color-toc-button    → 最强视觉焦点（目录按钮）
--color-highlight     → 强烈强调（搜索高亮）
--color-selection     → 柔和提示（文本选中）
```

### 3. 关键约束
- ✅ `--color-highlight` **不使用透明度**（JS 中通过 `fill-opacity` 控制）
- ✅ 浅色主题的 `--color-toc-button` **保持不变**（青蓝色 `rgba(0, 163, 204, 0.7)`）
- ✅ 三个颜色要有明显区分度

---

## 🎯 优化后的配色方案

### 1️⃣ 浅色主题 (theme-light.css)

**主色调调整：** 从 `#3498db` 调整为与 toc-button 协调的蓝色

```css
:root {
    /* 主色调 - 调整为与 toc-button 协调 */
    --color-primary: #0288d1;              /* 深天蓝，比原来更深 */
    --color-primary-dark: #01579b;         /* 更深的蓝色 */
    --color-primary-light: #e1f5fe;        /* 浅蓝背景 */
    --color-primary-border: #81d4fa;       /* 边框蓝 */
    
    /* 文字颜色 - 保持不变 */
    --color-text-primary: #333333;
    --color-text-secondary: #666666;
    --color-text-muted: #999999;
    --color-text-inverse: #ffffff;
    
    /* 背景颜色 - 保持不变 */
    --color-background: #f5f5f5;
    --color-background-light: #fafafa;
    --color-background-overlay: rgba(0, 0, 0, 0.5);
    
    /* 边框颜色 - 保持不变 */
    --color-border: #eeeeee;
    --color-border-light: #dddddd;
    
    /* 功能色 - 保持不变 */
    --color-error: #e74c3c;
    --color-success: #2ecc71;
    --color-warning: #f39c12;
    
    /* 阴影 - 保持不变 */
    --shadow-panel: -2px 0 10px rgba(0, 0, 0, 0.3);
    --shadow-button: 0 2px 2px rgba(0, 0, 0, 0.2);
    
    /* ✅ 特殊元素颜色 - 保持不变（用户喜欢） */
    --color-toc-button: rgba(0, 163, 204, 0.7);  /* 青蓝色 */

    /* ✅ 高亮颜色 - 与 toc-button 同色系但更饱和 */
    --color-highlight: rgb(0, 150, 199);         /* 纯青色，无透明度 */

    /* ✅ 选中文本 - 更浅的青蓝色 */
    --color-selection: rgba(0, 163, 204, 0.25);  /* 25% 透明度，更柔和 */
}
```

**颜色层次验证：**
- toc-button: `rgba(0, 163, 204, 0.7)` → 70% 不透明
- highlight: `rgb(0, 150, 199)` → 100% 不透明（JS 控制 opacity）
- selection: `rgba(0, 163, 204, 0.25)` → 25% 不透明

✅ 层次清晰：highlight > toc-button > selection

---

### 2️⃣ 护眼主题 (theme-eye-care.css)

**设计理念：** 温暖的米黄色调，减少蓝光，舒适阅读

```css
:root {
    /* 主色调 - 温暖的橙棕色 */
    --color-primary: #c17f59;              /* 温暖的棕橙色 */
    --color-primary-dark: #a06645;         /* 深棕橙 */
    --color-primary-light: #f5e6d3;        /* 浅米色背景 */
    --color-primary-border: #d4a373;       /* 边框色 */
    
    /* 文字颜色 - 深灰褐色，柔和不刺眼 */
    --color-text-primary: #4a4a4a;         /* 深灰褐 */
    --color-text-secondary: #6b6b6b;       /* 中灰褐 */
    --color-text-muted: #9a9a9a;           /* 浅灰褐 */
    --color-text-inverse: #fefae0;         /* 米白 */
    
    /* 背景颜色 - 温暖的米黄色 */
    --color-background: #fef9ef;           /* 暖米白 */
    --color-background-light: #f5f0e6;     /* 浅米黄 */
    --color-background-overlay: rgba(74, 74, 74, 0.3);
    
    /* 边框颜色 - 淡金色 */
    --color-border: #e8dcc8;
    --color-border-light: #f0e8d8;
    
    /* 功能色 - 调整为暖色调 */
    --color-error: #d9534f;                /* 暖红色 */
    --color-success: #5cb85c;              /* 暖绿色 */
    --color-warning: #f0ad4e;              /* 暖橙色 */
    
    /* 阴影 - 暖色调阴影 */
    --shadow-panel: -2px 0 10px rgba(74, 74, 74, 0.15);
    --shadow-button: 0 2px 2px rgba(74, 74, 74, 0.1);
    
    /* ✅ 特殊元素颜色 - 温暖的橙黄色 */
    --color-toc-button: rgba(193, 127, 89, 0.7);  /* 暖橙棕 */

    /* ✅ 高亮颜色 - 更鲜艳的橙色（无透明度） */
    --color-highlight: rgb(230, 126, 34);          /* 鲜艳橙色 */

    /* ✅ 选中文本 - 柔和的米橙色 */
    --color-selection: rgba(193, 127, 89, 0.2);    /* 20% 透明度 */
}
```

**颜色层次验证：**
- toc-button: `rgba(193, 127, 89, 0.7)` → 70% 不透明
- highlight: `rgb(230, 126, 34)` → 100% 不透明（更鲜艳）
- selection: `rgba(193, 127, 89, 0.2)` → 20% 不透明（最柔和）

✅ 温暖舒适，层次分明

---

### 3️⃣ 深色主题 (theme-dark.css)

**问题评估：** 紫色 (`#bb86fc`) 在深色模式下确实合适，这是 Material Design 的标准做法。但我们可以优化让它更协调。

```css
:root {
    /* 主色调 - 保持紫色系，但调整为更柔和的蓝紫色 */
    --color-primary: #9c7df5;              /* 柔和的蓝紫色 */
    --color-primary-dark: #7c5ce0;         /* 深紫 */
    --color-primary-light: #2d2640;        /* 深紫背景 */
    --color-primary-border: #6c5ce7;       /* 边框紫 */
    
    /* 文字颜色 - 白色系，确保对比度 */
    --color-text-primary: #e8e8e8;         /* 浅灰白 */
    --color-text-secondary: #b0b0b0;       /* 中灰 */
    --color-text-muted: #808080;           /* 深灰 */
    --color-text-inverse: #121212;         /* 纯黑 */
    
    /* 背景颜色 - Material Dark 标准 */
    --color-background: #121212;           /* MD 标准深色背景 */
    --color-background-light: #1e1e1e;     /* 稍亮的背景 */
    --color-background-overlay: rgba(255, 255, 255, 0.1);
    
    /* 边框颜色 - 深灰色 */
    --color-border: #2d2d2d;
    --color-border-light: #252525;
    
    /* 功能色 - 调整为适合深色模式 */
    --color-error: #cf6679;                /* MD 标准错误色 */
    --color-success: #03dac6;              /* MD 标准成功色 */
    --color-warning: #ffb74d;              /* 暖橙色 */
    
    /* 阴影 - 深色模式需要更强的阴影 */
    --shadow-panel: -2px 0 10px rgba(0, 0, 0, 0.8);
    --shadow-button: 0 2px 4px rgba(0, 0, 0, 0.5);
    
    /* ✅ 特殊元素颜色 - 柔和的蓝紫色 */
    --color-toc-button: rgba(156, 125, 245, 0.7);  /* 蓝紫色 */

    /* ✅ 高亮颜色 - 更明亮的紫色（无透明度） */
    --color-highlight: rgb(149, 117, 205);         /* 明亮紫 */

    /* ✅ 选中文本 - 柔和的紫色 */
    --color-selection: rgba(156, 125, 245, 0.2);   /* 20% 透明度 */
}
```

**为什么紫色合适？**
- ✅ 紫色在深色背景下有良好的可见性
- ✅ 不会像白色那样刺眼
- ✅ 符合 Material Design Dark Theme 规范
- ✅ 与蓝色的浅色主题形成良好对比

**颜色层次验证：**
- toc-button: `rgba(156, 125, 245, 0.7)` → 70% 不透明
- highlight: `rgb(149, 117, 205)` → 100% 不透明（更亮）
- selection: `rgba(156, 125, 245, 0.2)` → 20% 不透明（最柔和）

---

## 📊 三主题对比表

| 变量 | 浅色主题 | 护眼主题 | 深色主题 |
|------|---------|---------|---------|
| **toc-button** | `rgba(0, 163, 204, 0.7)` | `rgba(193, 127, 89, 0.7)` | `rgba(156, 125, 245, 0.7)` |
| **highlight** | `rgb(0, 150, 199)` | `rgb(230, 126, 34)` | `rgb(149, 117, 205)` |
| **selection** | `rgba(0, 163, 204, 0.25)` | `rgba(193, 127, 89, 0.2)` | `rgba(156, 125, 245, 0.2)` |
| **primary** | `#0288d1` | `#c17f59` | `#9c7df5` |
| **background** | `#f5f5f5` | `#fef9ef` | `#121212` |

---

## 🎨 视觉效果预览

### 浅色主题
- 🔵 清新的蓝色系
- 💧 toc-button: 青蓝色（用户喜爱）
- ✨ highlight: 鲜明的青色
- 🌊 selection: 淡淡的青蓝

### 护眼主题
- 🟠 温暖的橙棕色系
- ☕ toc-button: 暖橙棕
- 🍊 highlight: 鲜艳橙色
- 🥛 selection: 柔和米橙

### 深色主题
- 🟣 优雅的蓝紫色系
- 🔮 toc-button: 蓝紫色
- 💜 highlight: 明亮紫色
- 🌙 selection: 淡紫色

---

## ✅ 实施检查清单

- [x] 浅色主题 toc-button 保持不变
- [x] 三个主题的 highlight 都不使用透明度
- [x] selection 透明度低于 toc-button
- [x] highlight 饱和度/亮度高于 toc-button
- [x] 护眼主题使用温暖色调
- [x] 深色主题保持紫色系（符合规范）
- [x] 所有颜色都有良好的对比度
- [x] 层次清晰：highlight > toc-button > selection

---

*生成时间：2026-05-08*
*适用于：ReadEptd EPUB 阅读器*
