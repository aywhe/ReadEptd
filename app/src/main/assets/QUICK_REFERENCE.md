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

## 🔒 注意事项

1. **不要直接修改全局变量** - 使用模块提供的方法
2. **保持接口稳定** - `window.EpubReader` 的方法签名不要随意改动
3. **CSS 变量兼容性** - Android 5.0+ 完全支持
4. **错误处理** - 所有异步操作都要有 `.catch()`

---

**最后更新**: 2026-05-07
