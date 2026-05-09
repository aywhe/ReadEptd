# EPUB Reader 快速参考

## 🎨 主题颜色变量

在 `epub_reader.html` 的 `:root` 中定义，修改这些值即可切换主题：

### 主色调
- `--color-primary`: 主要颜色（按钮、高亮等）
- `--color-primary-dark`: 深色变体
- `--color-primary-light`: 浅色变体

### 文字颜色
- `--color-text-primary`: 主要文字 (#333)
- `--color-text-secondary`: 次要文字 (#666)
- `--color-text-muted`: 弱化文字 (#999)
- `--color-text-inverse`: 反色文字（白色，用于深色背景）

### 背景颜色
- `--color-background`: 主背景 (#f5f5f5)
- `--color-background-light`: 浅色背景 (#fafafa)
- `--color-background-overlay`: 遮罩层 (rgba(0,0,0,0.5))

### 功能色
- `--color-error`: 错误/警告 (#e74c3c)
- `--color-success`: 成功 (#2ecc71)
- `--color-warning`: 提醒 (#f39c12)

## 📦 模块说明

### AppState - 全局状态
```javascript
AppState.book              // 当前书籍对象
AppState.rendition         // 当前渲染器
AppState.isLoaded          // 是否已加载
AppState.tableOfContents   // 目录数据
AppState.dragState         // 按钮拖动状态
AppState.reset()           // 重置状态
```

### AndroidBridge - Android 通信
所有方法自动调用 `window.Android` 对应接口：
```javascript
AndroidBridge.onPageChanged(jsonLocation)
AndroidBridge.onLoadComplete()
AndroidBridge.onError(message)
AndroidBridge.onDoubleClick()
// ... 等等
```

### UIManager - UI 管理
```javascript
UIManager.showLoading()           // 显示加载指示器
UIManager.hideLoading()           // 隐藏加载指示器
UIManager.showError(message)      // 显示错误
UIManager.openNavPanel()          // 打开目录面板
UIManager.closeNavPanel()         // 关闭目录面板
UIManager.generateTOC(toc)        // 生成目录
UIManager.highlightCurrentChapter(href)  // 高亮当前章节
```

### ReaderCore - 阅读器核心
```javascript
ReaderCore.init(epubUrl, startCfi)  // 初始化阅读器
```

### PageOperations - 页面操作
```javascript
PageOperations.goToLocation(cfi)     // 跳转到指定位置
PageOperations.prevPage()            // 上一页
PageOperations.nextPage()            // 下一页
PageOperations.goToPercentage(pct)   // 跳转到百分比位置
PageOperations.getCurrentPageText()  // 获取当前页文本
PageOperations.getCurrentLocation()  // 获取当前位置
```

### ChapterManager - 章节管理
```javascript
ChapterManager.jumpToChapter(href)   // 跳转到指定章节
```

### SearchManager - 搜索功能
```javascript
SearchManager.search(query)          // 搜索关键词
SearchManager.stopSearchAndWait()    // 停止搜索
```

### HighlightManager - 高亮功能
```javascript
HighlightManager.highlight(cfi, isRemove)  // 添加/移除高亮
```

## 🔌 Android 调用接口

通过 `window.EpubReader` 暴露的方法：

```javascript
// 初始化
EpubReader.init(epubUrl, startCfi)

// 导航
EpubReader.goToLocation(cfi)
EpubReader.prevPage()
EpubReader.nextPage()
EpubReader.goToPercentage(percentage)

// 信息获取
EpubReader.getCurrentPageText()
EpubReader.getCurrentLocation()

// 目录面板
EpubReader.openNavPanel()
EpubReader.closeNavPanel()
EpubReader.toggleNavPanel()

// 搜索和高亮
EpubReader.search(query)
EpubReader.highlight(cfi, isRemove)

// 清理
EpubReader.cleanUp()
```

## 🎯 常见任务

### 切换为深色主题
修改 `epub_reader.html` 中的 CSS 变量：
```css
:root {
    --color-primary: #bb86fc;
    --color-text-primary: #ffffff;
    --color-text-secondary: #b0b0b0;
    --color-background: #121212;
    --color-background-light: #1e1e1e;
    --color-background-overlay: rgba(255, 255, 255, 0.1);
    --color-border: #333;
}
```

### 修改按钮颜色
```css
#toc-btn {
    background: rgba(你的颜色, 0.7);
}
```

### 调整动画速度
```css
:root {
    --transition-fast: 0.1s;      /* 更快 */
    --transition-normal: 0.5s;    /* 更慢 */
}
```

### 修改目录面板宽度
```css
:root {
    --nav-panel-width: 70%;           /* 更窄 */
    --nav-panel-max-width: 350px;     /* 最大宽度 */
}
```

## 📝 代码规范

### 添加新功能
1. 确定功能属于哪个模块
2. 在对应模块中添加方法
3. 如需新模块，创建后在 `window.EpubReader` 中暴露
4. 使用 CSS 变量而非硬编码颜色

### 示例：添加新的 UI 方法
```javascript
const UIManager = {
    // ... 现有方法
    
    // 新方法
    showNotification(message) {
        // 实现逻辑
        console.log('Notification:', message);
    }
};
```

### 示例：使用 CSS 变量
```javascript
// ❌ 不好 - 硬编码颜色
element.style.backgroundColor = '#3498db';

// ✅ 好 - 使用 CSS 变量
element.style.backgroundColor = 'var(--color-primary)';
```

## 🐛 调试技巧

### 查看当前状态
```javascript
console.log('Book:', AppState.book);
console.log('Rendition:', AppState.rendition);
console.log('Is Loaded:', AppState.isLoaded);
console.log('TOC:', AppState.tableOfContents);
```

### 测试 Android 桥接
```javascript
// 模拟 Android 接口
window.Android = {
    onPageChanged: (data) => console.log('Page changed:', data),
    onLoadComplete: () => console.log('Load complete'),
    // ... 其他方法
};
```

### 检查 CSS 变量
```javascript
// 在浏览器控制台
getComputedStyle(document.documentElement)
    .getPropertyValue('--color-primary');
```

## ⚡ 性能提示

1. **避免频繁 DOM 操作** - 批量更新样式
2. **使用 CSS 过渡** - 而非 JavaScript 动画
3. **懒加载目录** - 大型书籍的目录可能很长
4. **及时清理资源** - 调用 `EpubReader.cleanUp()`

## 🕒 HTML/CSS/JS 加载时序

### 完整加载流程

```
时间轴 →
│
├─ T0: HTML 开始解析（同步阻塞）
│   ├─ 加载 base.css ✅ 同步阻塞（基础布局样式）
│   ├─ 创建空的 <link id="dynamic-theme-style">（主题占位符，href 为空）
│   ├─ 加载 JS 库（按顺序同步执行）：
│   │   ├─ jszip.min.js
│   │   ├─ epub.min.js
│   │   ├─ epub.custom.js
│   │   └─ theme-manager.js
│   └─ ⚠️ epub_reader.core.js 在 </body> 前加载（DOMContentLoaded 之后）
│
├─ T1: DOMContentLoaded 事件触发
│   ├─ ThemeManager.init() （来自 theme-manager.js）
│   │   └─ 从 localStorage 读取主题（默认 light）
│   │       └─ 动态创建 <link> 元素并设置 href
│   │           └─ 异步加载 theme-light.css（非阻塞）
│   └─ 点击背景关闭导航面板的事件监听器注册
│
├─ T2: window.onload 事件触发
│   ├─ AndroidBridge.onHtmlReady() → 通知 Android HTML 已准备好
│   └─ UIManager.init() → 初始化 UI（拖拽按钮等）
│
├─ T3: Android 收到 onHtmlReady（EpubWebView.AndroidBridge.onHtmlReady()）
│   ├─ ✅ EpubWebView.setTheme(currentTheme) → 应用初始主题
│   │   └─ 执行 JS: window.EpubReader.setTheme('light'/'dark'/'eye-care')
│   │       └─ ThemeManager.setThemeFromAndroid(themeName)
│   │           └─ ThemeManager.loadTheme(themeName)
│   │               ├─ 移除旧的 <link id="dynamic-theme-style">
│   │               ├─ 创建新的 <link> 并设置 href
│   │               └─ 异步加载主题 CSS（如 theme-light.css）
│   │                   └─ onload 回调：
│   │                       ├─ 更新 currentTheme
│   │                       ├─ 保存到 localStorage
│   │                       └─ 通知 Android: window.Android.onThemeChanged()
│   └─ EpubWebView.loadEpub(epubFilePath)
│       └─ 执行 JS: window.EpubReader.init(epubPath, startCfi)
│
├─ T4: ReaderCore.init() 执行
│   ├─ ResourceManager.clearRendition() / clearBook() → 清理旧资源
│   ├─ UIManager.showLoading() → 显示加载指示器
│   ├─ createBook(epubUrl) → 创建 Book 实例
│   ├─ createRendition() → 创建渲染器（renderTo "viewer"）
│   ├─ setupEventListeners() → 设置事件监听器：
│   │   ├─ relocated → 页面跳转时触发
│   │   ├─ resized → 窗口大小改变时触发
│   │   ├─ rendered → 章节渲染时触发 ✅ 调用 applyThemeToEpub()
│   │   ├─ attached → 渲染器附加时触发
│   │   ├─ displayed → 章节显示时触发
│   │   └─ book.ready → 书籍元数据加载完成
│   ├─ loadNavigation() → 异步加载目录
│   ├─ generateLocationsAsync() → 异步生成位置信息
│   └─ displayBook(startCfi) → 显示书籍
│       └─ rendition.display(cfi)
│           └─ .then() → 书籍显示成功
│               ├─ UIManager.hideLoading() → 隐藏加载指示器
│               ├─ AppState.isLoaded = true
│               ├─ hookMappingFunctions() → 钩住映射函数
│               └─ AndroidBridge.onLoadComplete() → ✅ 通知 Android 加载完成
│
├─ T5: EPUB 内容首次渲染（rendition.on("rendered") 触发）
│   └─ ReaderCore.applyThemeToEpub()
│       ├─ 从 document.documentElement 读取 CSS 变量
│       │   ├─ --color-background
│       │   ├─ --color-text-primary
│       │   └─ --color-primary
│       ├─ 构建规则对象 rules
│       ├─ rendition.themes.register("my-theme", rules)
│       └─ rendition.themes.select("my-theme") → ✅ 应用主题到 EPUB 内容
│           └─ ⚠️ 注意：此时使用的是 T3 阶段加载的主题 CSS 中的颜色值
│
└─ T6: Android 收到 onLoadComplete（可选的主题切换）
    └─ 如果需要切换主题，调用 setTheme()
        └─ 执行 JS: window.EpubReader.setTheme('dark'/'light')
            └─ ThemeManager.setThemeFromAndroid(themeName)
                └─ ThemeManager.loadTheme(themeName)
                    ├─ 移除旧的 <link id="dynamic-theme-style">
                    ├─ 创建新的 <link> 并设置 href
                    └─ 异步加载新主题 CSS（如 theme-dark.css）
                        └─ onload 回调：
                            ├─ 更新 currentTheme
                            ├─ 保存到 localStorage
                            └─ 通知 Android: window.Android.onThemeChanged()
                                └─ ⚠️ 注意：此时不会自动重新应用主题到 EPUB！
                                    └─ 需要等待下一个 rendered 事件或手动调用 applyThemeToEpub()
```

### 关键时序说明

#### 1. CSS 加载顺序
- **base.css**: 在 HTML `<head>` 中同步加载，提供基础布局和结构样式
- **theme-light.css**: 
  - **首次加载**（T1）: DOMContentLoaded 时由 ThemeManager 动态加载（异步），仅包含颜色变量
  - **重新加载**（T3）: Android 收到 onHtmlReady 时，EpubWebView.setTheme() 会重新加载主题 CSS
- **EPUB 内容主题**: 在 T5 阶段通过 `applyThemeToEpub()` 从 CSS 变量读取颜色并应用到 EPUB 内容（通过 rendition.themes API）

#### 2. 主题应用时机
- **初始主题设置**（T3）: EpubWebView 收到 onHtmlReady 后，立即调用 `setTheme(currentTheme)` 加载主题 CSS
- **第一次应用到 EPUB**（T5）: `rendition.on("rendered")` 事件触发时调用 `applyThemeToEpub()`，从主页面的 CSS 变量读取颜色并通过 rendition.themes 应用到 EPUB 内容
- **后续切换**（T6）: Android 调用 `setTheme()` 时，只更新主页面的 CSS 变量，**不会自动重新应用主题到 EPUB 内容**，需要等待下一次 `rendered` 事件或手动调用

#### 3. 主题切换的正确方式
```javascript
// ✅ 正确：同时更新主页面和 EPUB 内容
window.EpubReader.setTheme = function(themeName) {
    if (ThemeManager && ThemeManager.setThemeFromAndroid) {
        ThemeManager.setThemeFromAndroid(themeName);
        // 延迟调用，等待 CSS 加载完成后重新应用主题到 EPUB
        setTimeout(() => {
            if (ReaderCore && ReaderCore.applyThemeToEpub) {
                ReaderCore.applyThemeToEpub();
            }
        }, 100);
    }
};
```

#### 4. JavaScript 执行顺序
1. **同步加载阶段**（T0）: `<head>` 中的 `<script>` 标签按顺序同步加载和执行
2. **DOM 就绪阶段**（T1）: DOMContentLoaded 事件触发，ThemeManager 初始化，动态加载主题 CSS（首次）
3. **完全加载阶段**（T2）: window.onload 事件触发，通知 Android，初始化 UI
4. **Android 回调阶段**（T3）: 
   - EpubWebView.AndroidBridge.onHtmlReady() 被调用
   - ✅ **EpubWebView.setTheme(currentTheme)** → 重新加载主题 CSS
   - EpubWebView.loadEpub() → 调用 window.EpubReader.init()
5. **EPUB 初始化阶段**（T4）: ReaderCore.init() 创建 Book 和 Rendition，设置事件监听器
6. **内容渲染阶段**（T5）: 首个章节渲染完成，`rendered` 事件触发，应用主题到 EPUB 内容
7. **加载完成阶段**（T5 末尾）: 书籍显示完成，`onLoadComplete()` 通知 Android

### 常见问题

#### Q: 为什么主题切换后 EPUB 内容没有立即变化？
**A**: 因为 `ThemeManager.loadTheme()` 只更新了主页面的 CSS 变量，没有主动调用 `applyThemeToEpub()`。EPUB 内容的主题会在下一次 `rendered` 事件（翻页时）自动更新，或者需要手动调用 `applyThemeToEpub()`。

#### Q: 为什么要在 rendered 事件中调用 applyThemeToEpub()？
**A**: 因为此时 rendition 已经创建完成并且章节内容已经渲染到 iframe 中，可以访问 `rendition.themes` API。同时，CSS 变量已经从 theme CSS 文件中加载完成，可以读取到正确的颜色值。

#### Q: 如何确保主题切换时 EPUB 内容也立即更新？
**A**: 修改 `window.EpubReader.setTheme` 方法，在调用 `ThemeManager.loadTheme()` 后，延迟调用 `ReaderCore.applyThemeToEpub()`。这样可以强制重新应用主题到 EPUB 内容，无需等待下一次 `rendered` 事件。

#### Q: epub_reader.core.js 为什么在 </body> 前加载而不是在 <head> 中？
**A**: 这是为了确保 DOM 已经完全解析，避免在脚本执行时访问未创建的 DOM 元素。epub_reader.core.js 依赖于 HTML 中的元素（如 #viewer、#toc-btn 等），在 body 末尾加载可以保证这些元素已经存在。

## 🔒 注意事项

1. **不要直接修改全局变量** - 使用模块提供的方法
2. **保持接口稳定** - `window.EpubReader` 的方法签名不要随意改动
3. **CSS 变量兼容性** - Android 5.0+ 完全支持
4. **错误处理** - 所有异步操作都要有 `.catch()`
5. **主题切换时序** - 确保在 Rendition 创建完成后才应用主题到 EPUB 内容
6. **资源清理** - 退出时调用 `EpubReader.cleanUp()` 释放内存

---

**最后更新**: 2026-05-07
