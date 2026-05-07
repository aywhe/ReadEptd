# 主题系统使用指南

## 📋 概述

EPUB Reader 现在支持动态主题切换功能，通过加载不同的 CSS 文件来实现主题变化。所有主题使用相同的 CSS 变量名，只需修改变量值即可。

## 🎨 现有主题

### 1. 浅色主题 (light)
- 文件：`css/theme-light.css`
- 特点：清爽明亮，适合白天使用
- 主色调：蓝色 (#3498db)

### 2. 深色主题 (dark)
- 文件：`css/theme-dark.css`
- 特点：护眼暗色，适合夜间阅读
- 主色调：紫色 (#bb86fc)

### 3. 护眼主题 (eye-care)
- 文件：`css/theme-eye-care.css`
- 特点：暖色调，减少蓝光
- 主色调：橙色 (#d4a373)

## 📁 文件结构

```
assets/
├── css/
│   ├── theme-light.css      # 浅色主题
│   ├── theme-dark.css       # 深色主题
│   └── theme-eye-care.css   # 护眼主题
├── js/
│   ├── theme-manager.js     # 主题管理器
│   └── epub_reader.core.js  # 核心业务逻辑
└── epub_reader.html         # HTML 主文件
```

## 🔧 使用方法

### JavaScript 调用

```javascript
// 1. 切换到指定主题
EpubReader.setTheme('dark');        // 深色主题
EpubReader.setTheme('light');       // 浅色主题
EpubReader.setTheme('eye-care');    // 护眼主题

// 2. 循环切换主题（在可用主题间轮换）
const nextTheme = EpubReader.toggleTheme();
console.log('当前主题:', nextTheme);

// 3. 获取当前主题
const currentTheme = EpubReader.getCurrentTheme();
console.log('Current theme:', currentTheme);

// 4. 获取所有可用主题
const themes = ThemeManager.getAvailableThemes();
console.log('Available themes:', themes);
```

### Android Kotlin/Java 调用

```kotlin
// Kotlin 示例
webView.evaluateJavascript("EpubReader.setTheme('dark')", null)
webView.evaluateJavascript("EpubReader.toggleTheme()", null)
webView.evaluateJavascript("EpubReader.getCurrentTheme()", null) { result ->
    Log.d("Theme", "Current theme: $result")
}
```

```java
// Java 示例
webView.evaluateJavascript("EpubReader.setTheme('dark')", null);
webView.evaluateJavascript("EpubReader.toggleTheme()", null);
webView.evaluateJavascript("EpubReader.getCurrentTheme()", new ValueCallback<String>() {
    @Override
    public void onReceiveValue(String result) {
        Log.d("Theme", "Current theme: " + result);
    }
});
```

## 🎯 创建新主题

### 步骤 1：创建主题 CSS 文件

在 `assets/css/` 目录下创建新的 CSS 文件，例如 `theme-blue.css`：

```css
/* ============================================
   蓝色主题
   ============================================ */
:root {
    /* 主色调 */
    --color-primary: #2196f3;
    --color-primary-dark: #1976d2;
    --color-primary-light: #bbdefb;
    --color-primary-border: #64b5f6;
    
    /* 文字颜色 */
    --color-text-primary: #212121;
    --color-text-secondary: #757575;
    --color-text-muted: #9e9e9e;
    --color-text-inverse: #ffffff;
    
    /* 背景颜色 */
    --color-background: #ffffff;
    --color-background-light: #f5f5f5;
    --color-background-overlay: rgba(0, 0, 0, 0.5);
    
    /* 边框颜色 */
    --color-border: #e0e0e0;
    --color-border-light: #eeeeee;
    
    /* 功能色 */
    --color-error: #f44336;
    --color-success: #4caf50;
    --color-warning: #ff9800;
    
    /* 阴影 */
    --shadow-panel: -2px 0 10px rgba(0, 0, 0, 0.3);
    --shadow-button: 0 2px 2px rgba(0, 0, 0, 0.2);
}
```

### 步骤 2：注册主题

在 `js/theme-manager.js` 中添加新主题：

```javascript
const ThemeManager = {
    // ...
    
    themeFiles: {
        'light': 'css/theme-light.css',
        'dark': 'css/theme-dark.css',
        'eye-care': 'css/theme-eye-care.css',
        'blue': 'css/theme-blue.css'  // 添加新主题
    },
    
    // ...
};
```

### 步骤 3：使用新主题

```javascript
EpubReader.setTheme('blue');
```

## 💡 高级用法

### 1. 根据时间自动切换主题

```javascript
function autoSwitchTheme() {
    const hour = new Date().getHours();
    
    if (hour >= 6 && hour < 18) {
        // 白天使用浅色主题
        EpubReader.setTheme('light');
    } else if (hour >= 18 && hour < 22) {
        // 傍晚使用护眼主题
        EpubReader.setTheme('eye-care');
    } else {
        // 夜间使用深色主题
        EpubReader.setTheme('dark');
    }
}

// 每小时检查一次
setInterval(autoSwitchTheme, 3600000);
autoSwitchTheme(); // 立即执行一次
```

### 2. 根据系统主题偏好切换

```javascript
// 检测系统是否处于深色模式
if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
    EpubReader.setTheme('dark');
} else {
    EpubReader.setTheme('light');
}

// 监听系统主题变化
window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', event => {
    if (event.matches) {
        EpubReader.setTheme('dark');
    } else {
        EpubReader.setTheme('light');
    }
});
```

### 3. 用户偏好设置界面

在 Android 端创建主题选择器：

```kotlin
// Kotlin 示例
fun showThemeSelector() {
    val themes = arrayOf("浅色", "深色", "护眼")
    val themeValues = arrayOf("light", "dark", "eye-care")
    
    AlertDialog.Builder(this)
        .setTitle("选择主题")
        .setItems(themes) { _, which ->
            val selectedTheme = themeValues[which]
            webView.evaluateJavascript("EpubReader.setTheme('$selectedTheme')", null)
            
            // 保存用户偏好
            preferences.edit().putString("theme", selectedTheme).apply()
        }
        .show()
}
```

### 4. 主题切换动画

在主题 CSS 文件中添加过渡效果：

```css
:root {
    /* 其他变量... */
    
    /* 添加全局过渡 */
    --theme-transition: all 0.3s ease;
}

/* 应用到需要过渡的元素 */
body, #viewer, #nav-content {
    transition: var(--theme-transition);
}
```

## 🔍 调试技巧

### 查看当前使用的 CSS 变量值

```javascript
// 在浏览器控制台或 WebView 中执行
const style = getComputedStyle(document.documentElement);
console.log('Primary color:', style.getPropertyValue('--color-primary'));
console.log('Background:', style.getPropertyValue('--color-background'));
console.log('Text color:', style.getPropertyValue('--color-text-primary'));
```

### 手动测试主题加载

```javascript
// 测试主题文件是否存在
fetch('css/theme-dark.css')
    .then(response => {
        console.log('Theme file exists:', response.ok);
        return response.text();
    })
    .then(css => {
        console.log('CSS content length:', css.length);
    })
    .catch(error => {
        console.error('Failed to load theme:', error);
    });
```

### 监听主题切换事件

在 Android 端接收主题切换通知：

```kotlin
// 在 WebView 的 JavaScriptInterface 中添加
@JavascriptInterface
fun onThemeChanged(themeName: String) {
    Log.d("Theme", "Theme changed to: $themeName")
    runOnUiThread {
        // 更新 UI 显示当前主题
        updateThemeIndicator(themeName)
    }
}
```

## ⚠️ 注意事项

1. **CSS 变量兼容性**
   - Android 5.0+ (API 21+) 完全支持 CSS 变量
   - 如需支持更低版本，考虑使用 PostCSS 插件降级

2. **主题文件路径**
   - 确保主题文件路径正确（相对于 HTML 文件）
   - 使用相对路径：`css/theme-dark.css`
   - 不要使用绝对路径

3. **性能优化**
   - 主题切换时会移除旧的 `<link>` 标签并创建新的
   - 避免频繁切换主题
   - 主题文件应尽量小（只包含 CSS 变量）

4. **持久化存储**
   - 主题偏好自动保存到 `localStorage`
   - 页面刷新后会自动恢复上次使用的主题
   - 如需清除：`localStorage.removeItem('epub-reader-theme')`

5. **离线支持**
   - 所有主题文件都是本地的，支持离线使用
   - 无需网络连接即可切换主题

## 🎨 主题设计建议

### 配色原则

1. **对比度**：确保文字和背景有足够的对比度（WCAG AA 标准）
2. **一致性**：保持相同元素在不同主题中的视觉层次一致
3. **可读性**：优先保证阅读体验，其次才是美观

### 推荐工具

- **Color Contrast Checker**: https://webaim.org/resources/contrastchecker/
- **Material Design Color Tool**: https://material.io/resources/color/
- **Coolors**: https://coolors.co/ （配色方案生成器）

### 无障碍考虑

- 提供至少一种高对比度主题
- 避免仅依靠颜色传达信息
- 确保焦点状态在所有主题中都清晰可见

---

**最后更新**: 2026-05-07  
**版本**: 1.0
