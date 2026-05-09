# Material Design 3 颜色系统完全指南

## 📋 目录
- [颜色系统概览](#颜色系统概览)
- [详细变量说明](#详细变量说明)
- [规范使用建议](#规范使用建议)
- [在阅读器项目中的应用](#在阅读器项目中的应用)
- [快速记忆法则](#快速记忆法则)

---

## 🎨 颜色系统概览

Material Design 3 (Material You) 提供了一套完整的颜色系统，包含以下核心角色：

1. **Primary** - 主色（品牌核心色）
2. **Secondary** - 次要色（辅助强调色）
3. **Tertiary** - 第三色（补充色）
4. **Error** - 错误色（警示色）
5. **Background & Surface** - 背景与表面色
6. **Outline** - 轮廓色（边框与分割）
7. **Inverse** - 反色（高对比度场景）

---

## 📖 详细变量说明

### 1️⃣ Primary（主色）- 品牌核心色

```kotlin
primary              // 主要强调色，用于关键操作、按钮、激活状态
onPrimary            // 在 primary 上显示的文字/图标颜色（通常是白色或黑色）
primaryContainer     // primary 的浅色容器背景
onPrimaryContainer   // 在 primaryContainer 上显示的文字颜色
```

**使用场景：**
- ✅ Filled Button 背景
- ✅ FAB（浮动操作按钮）
- ✅ 选中状态的 Tab
- ✅ 进度条、Slider 激活部分
- ❌ 不要用于大面积背景

---

### 2️⃣ Secondary（次要色）- 辅助强调色

```kotlin
secondary            // 次要强调色，用于较低优先级的操作
onSecondary          // 在 secondary 上显示的文字/图标颜色
secondaryContainer   // secondary 的浅色容器背景
onSecondaryContainer // 在 secondaryContainer 上显示的文字颜色
```

**使用场景：**
- ✅ Filter Chips（筛选标签）
- ✅ 次要操作按钮
- ✅ 分类标识
- ✅ 与 Primary 形成视觉层次

---

### 3️⃣ Tertiary（第三色）- 补充色

```kotlin
tertiary             // 第三强调色，用于对比或补充
onTertiary           // 在 tertiary 上显示的文字/图标颜色
tertiaryContainer    // tertiary 的浅色容器背景
onTertiaryContainer  // 在 tertiaryContainer 上显示的文字颜色
```

**使用场景：**
- ✅ 需要与 Primary/Secondary 区分的功能
- ✅ 特殊状态标识
- ✅ 装饰性元素
- ✅ 个性化功能（如阅读器的主题切换）

---

### 4️⃣ Error（错误色）- 警示色

```kotlin
error                // 错误状态颜色
onError              // 在 error 上显示的文字颜色
errorContainer       // 错误的浅色容器背景
onErrorContainer     // 在 errorContainer 上显示的文字颜色
```

**使用场景：**
- ✅ 表单验证错误提示
- ✅ 删除操作的确认对话框
- ✅ 错误状态的 Icon
- ❌ 不要用于正常状态

---

### 5️⃣ Background & Surface（背景与表面）- 基础层

```kotlin
background           // 页面最底层背景色
onBackground         // 在 background 上显示的文字颜色

surface              // 组件表面颜色（Card、Dialog、Sheet 等）
onSurface            // 在 surface 上显示的主要文字颜色

surfaceVariant       // 表面的变体，用于次级表面
onSurfaceVariant     // 在 surfaceVariant 上显示的文字颜色（次要文字）
```

**使用场景：**
- `background`: Scaffold 背景、页面底色
- `surface`: Card、Dialog、BottomSheet 背景
- `surfaceVariant`: 分隔线背景、禁用状态背景
- `onSurface`: 主要文本、标题
- `onSurfaceVariant`: 副标题、说明文字、禁用文本

---

### 6️⃣ Outline（轮廓色）- 边框与分割

```kotlin
outline              // 边框、分割线颜色
outlineVariant       // 较浅的轮廓色，用于次要分割线
```

**使用场景：**
- ✅ TextField 边框
- ✅ Divider（分割线）
- ✅ Card 边框
- ✅ 列表项之间的分隔

---

### 7️⃣ Inverse（反色）- 高对比度场景

```kotlin
inverseSurface       // 与 surface 相反的颜色（深色模式下的浅色表面）
inverseOnSurface     // 在 inverseSurface 上显示的文字颜色
inversePrimary       // 在深色背景上使用的 primary 变体
```

**使用场景：**
- ✅ Snackbar（在深色背景上显示）
- ✅ Tooltip
- ✅ 需要高对比度的临时浮层

---

### 8️⃣ 其他

```kotlin
surfaceTint          // 用于 Elevational overlay（高度阴影着色）
scrim                // 遮罩层颜色（如 Dialog 背后的半透明黑色）
```

---

## ✅ 规范使用建议

### 推荐做法

```kotlin
// 1. 主要按钮使用 primary
Button(
    colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
) { /* ... */ }

// 2. 卡片使用 surface
Card(
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
) { /* ... */ }

// 3. 主要文本使用 onSurface
Text(
    text = "标题",
    color = MaterialTheme.colorScheme.onSurface
)

// 4. 次要文本使用 onSurfaceVariant
Text(
    text = "副标题",
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

// 5. 分割线使用 outlineVariant
Divider(
    color = MaterialTheme.colorScheme.outlineVariant
)
```

### ❌ 避免的做法

```kotlin
// ❌ 不要硬编码颜色
Text(color = Color.Black)

// ❌ 不要在浅色背景上使用 onPrimary
Text(
    text = "内容",
    color = MaterialTheme.colorScheme.onPrimary  // 应该用 onSurface
)

// ❌ 不要混用不同层级的颜色
Card(
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primary  // 应该用 surface
    )
)
```

---

## 🎯 在阅读器项目中的应用

根据你的 ReadEptd 项目特点，建议这样使用：

| 组件/场景 | 推荐颜色 | 说明 |
|----------|---------|------|
| **阅读页面背景** | `background` 或自定义主题色 | 根据主题动态切换 |
| **顶部 AppBar** | `surface` 或 `primary` | 浅色主题用 surface，深色可用 primary |
| **目录卡片** | `surface` + `onSurface` 文字 | 标准卡片样式 |
| **章节标题** | `onSurface` | 主要文本 |
| **章节副标题** | `onSurfaceVariant` | 次要文本 |
| **当前章节高亮** | `primaryContainer` + `onPrimaryContainer` | 突出显示 |
| **搜索按钮** | `onSurface` (Icon tint) | 保持中性 |
| **朗读按钮** | `secondary` 或 `tertiary` | 功能按钮 |
| **定时器对话框** | `surface` + `outline` 边框 | 标准对话框 |
| **错误提示** | `errorContainer` + `onErrorContainer` | 错误状态 |
| **分割线** | `outlineVariant` | 轻量分隔 |
| **Snackbar** | `inverseSurface` + `inverseOnSurface` | 高对比度提示 |
| **进度指示器** | `primary` | 激活状态 |
| **禁用状态文本** | `onSurfaceVariant` (带透明度) | 降低视觉权重 |

---

## 💡 快速记忆法则

### 核心原则

1. **Container + onContainer**：成对使用
   - Container 做背景
   - onContainer 做文字
   - 确保足够的对比度

2. **Surface 系列**：用于组件表面和文字
   - `surface`: 组件背景
   - `onSurface`: 主要文字
   - `surfaceVariant`: 次要背景
   - `onSurfaceVariant`: 次要文字

3. **Primary/Secondary/Tertiary**：按优先级递减使用
   - Primary: 最重要的操作
   - Secondary: 次要操作
   - Tertiary: 补充或装饰

4. **Variant**：表示"变体"
   - 通常更浅、更低调
   - 用于次要元素

5. **Inverse**：用于需要反差的场景
   - 在深色背景上显示浅色内容
   - 提高可读性

### 颜色选择流程图

```
需要设置颜色？
    │
    ├─ 是背景？
    │   ├─ 页面底层 → background
    │   ├─ 组件表面 → surface
    │   └─ 强调背景 → primaryContainer / secondaryContainer
    │
    ├─ 是文字？
    │   ├─ 主要文字 → onSurface / onBackground
    │   ├─ 次要文字 → onSurfaceVariant
    │   └─ 在彩色背景上 → onPrimary / onSecondary / onTertiary
    │
    ├─ 是按钮/操作？
    │   ├─ 主要操作 → primary
    │   ├─ 次要操作 → secondary
    │   └─ 特殊操作 → tertiary
    │
    ├─ 是边框/分割线？
    │   ├─ 主要边框 → outline
    │   └─ 次要分割 → outlineVariant
    │
    └─ 是错误状态？
        └─ 使用 error 系列
```

---

## 🔧 实际代码示例

### 示例 1：阅读列表项

```kotlin
@Composable
fun BookListItem(
    title: String,
    author: String,
    progress: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 书名 - 主要文字
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 作者 - 次要文字
            Text(
                text = author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 阅读进度 - 使用 primary 强调
            Text(
                text = "已读 $progress",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
```

### 示例 2：主题切换按钮组

```kotlin
@Composable
fun ThemeSwitcher(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 浅色主题按钮
        FilterChip(
            selected = currentTheme == ThemeMode.LIGHT,
            onClick = { onThemeChange(ThemeMode.LIGHT) },
            label = { Text("浅色") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        
        // 深色主题按钮
        FilterChip(
            selected = currentTheme == ThemeMode.DARK,
            onClick = { onThemeChange(ThemeMode.DARK) },
            label = { Text("深色") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )
        
        // 护眼模式按钮
        FilterChip(
            selected = currentTheme == ThemeMode.EYE_CARE,
            onClick = { onThemeChange(ThemeMode.EYE_CARE) },
            label = { Text("护眼") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        )
    }
}
```

### 示例 3：错误提示卡片

```kotlin
@Composable
fun ErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            
            TextButton(onClick = onRetry) {
                Text(
                    text = "重试",
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
```

---

## 📊 颜色对比度要求

根据 WCAG 2.1 无障碍标准：

| 文本大小 | 最小对比度 | 推荐对比度 |
|---------|-----------|-----------|
| 普通文本 (< 18pt) | 4.5:1 | 7:1 |
| 大文本 (≥ 18pt) | 3:1 | 4.5:1 |
| UI 组件 | 3:1 | 4.5:1 |

Material Design 3 的颜色系统已经自动确保了足够的对比度，但在使用自定义颜色时需要注意。

---

## 🎓 总结

1. **始终使用 `MaterialTheme.colorScheme`**，不要硬编码颜色
2. **理解每个颜色的语义**，按照设计意图使用
3. **Container 和 onContainer 成对使用**，确保可读性
4. **Primary > Secondary > Tertiary** 按优先级选择
5. **测试主题切换**，确保浅色/深色模式下都正常显示

通过遵循这些规范，你的应用将具有：
- ✅ 一致的设计语言
- ✅ 良好的可访问性
- ✅ 自动的主题适配
- ✅ 专业的视觉效果

---

*最后更新：2026-05-08*
*适用于：ReadEptd 项目 - Android Jetpack Compose*
