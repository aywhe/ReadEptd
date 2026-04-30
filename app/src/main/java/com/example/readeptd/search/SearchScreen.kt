package com.example.readeptd.search

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

/**
 * 侧滑搜索面板
 * @param visible 是否显示
 */
@Composable
fun SlideInSearchPanel(
    visible: Boolean,
    onKeywordChange: (String) -> Unit,
    searchExecutor: (String) -> Flow<SearchData.SearchResult>,
    useKeyword: String = "",
    viewModel: SearchViewModel = viewModel()
) {
    var keyword by remember { mutableStateOf(useKeyword) }  // ✅ 完全内部管理
    
    // ✅ 面板位置状态：true=右侧，false=左侧
    var isOnRight by remember { mutableStateOf(true) }
    
    // ✅ 拖动相关状态
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }  // 添加Y轴偏移记录
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val panelWidthPx = with(density) { 
        val panelWidthDp = (LocalConfiguration.current.screenWidthDp / 3).coerceAtLeast(160).coerceAtMost(400)
        panelWidthDp.dp.toPx() 
    }

    // ✅ 获取屏幕配置
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    
    // ✅ 计算面板宽度为屏幕宽度的 1/3（更激进：最小80dp）
    val panelWidthDp = (screenWidthDp / 3).coerceAtLeast(160).coerceAtMost(400)
    
    // ✅ 根据位置和拖动偏移计算最终偏移量
    val baseOffset = if (visible) {
        if (isOnRight) {
            // 右侧显示
            IntOffset(screenWidthDp - panelWidthDp, 0)
        } else {
            // 左侧显示
            IntOffset(0, 0)
        }
    } else {
        // 隐藏时移到屏幕外
        IntOffset(screenWidthDp, 0)
    }
    
    // ✅ 应用拖动偏移（仅使用X轴偏移进行位置计算）
    val finalOffset = IntOffset(
        x = baseOffset.x + offsetX.roundToInt(),
        y = baseOffset.y
    )
    
    val animatedOffset by animateIntOffsetAsState(
        targetValue = if (offsetX == 0f && offsetY == 0f) baseOffset else finalOffset,  // 拖动时不使用动画
        label = "search_panel_animation"
    )

    val results by resultsState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(panelWidthDp.dp)
            .offset(animatedOffset.x.dp, animatedOffset.y.dp)
            .shadow(8.dp)
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        // 拖动开始时重置偏移量
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDragEnd = {
                        // 拖动结束时决定是否需要切换位置（只考虑X轴偏移）
                        val threshold = panelWidthPx * 0.3f  // 30% 宽度作为阈值
                        
                        if (offsetX > threshold && !isOnRight) {
                            // 向右拖动超过阈值且当前在左侧 -> 切换到右侧
                            isOnRight = true
                        } else if (offsetX < -threshold && isOnRight) {
                            // 向左拖动超过阈值且当前在右侧 -> 切换到左侧
                            isOnRight = false
                        }
                        
                        // 重置偏移量
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDragCancel = {
                        // 拖动取消时重置偏移量
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        // 更新水平和垂直偏移量
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                        
                        // 限制拖动范围（只限制X轴用于位置判断）
                        when {
                            isOnRight -> {
                                // 如果在右侧，主要限制向左拖动（负值）
                                offsetX = offsetX.coerceIn(-panelWidthPx, panelWidthPx * 0.5f)
                            }
                            else -> {
                                // 如果在左侧，主要限制向右拖动（正值）
                                offsetX = offsetX.coerceIn(-panelWidthPx * 0.5f, panelWidthPx)
                            }
                        }
                        
                        // Y轴偏移也做适当限制，避免过度偏离
                        offsetY = offsetY.coerceIn(-panelWidthPx * 0.5f, panelWidthPx * 0.5f)
                        
                        change.consume()
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)  // ✅ 减小内边距：16dp -> 8dp
        ) {
            // 标题栏（更紧凑）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.titleMedium  // ✅ 减小字号：titleLarge -> titleMedium
                )
                
                Row {
                    // ✅ 左右切换按钮（更小）
                    IconButton(
                        onClick = { isOnRight = !isOnRight },
                        modifier = Modifier.padding(0.dp)  // ✅ 移除额外padding
                    ) {
                        Icon(
                            imageVector = if (isOnRight) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = if (isOnRight) "切换到左侧" else "切换到右侧",
                            modifier = Modifier.size(20.dp)  // ✅ 减小图标尺寸
                        )
                    }
                    
                    // 关闭按钮（更小）
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))  // ✅ 减小间距：16dp -> 8dp

            // 搜索输入框（更紧凑）
            OutlinedTextField(
                value = keyword,
                onValueChange = { newValue -> keyword = newValue },
                label = null,  // ✅ 移除label，节省空间
                placeholder = { Text("搜索...", style = MaterialTheme.typography.bodySmall) },  // ✅ 简化placeholder
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {onSearch(keyword)}) {
                        Icon(Icons.Default.Search, "搜索", modifier = Modifier.size(20.dp))  // ✅ 减小图标
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,  // ✅ 更小的圆角
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                textStyle = MaterialTheme.typography.bodySmall  // ✅ 减小字体
            )

            Spacer(modifier = Modifier.height(4.dp))  // ✅ 减小间距：16dp -> 4dp

            // 搜索结果数量（更紧凑）
            if (results.isNotEmpty()) {
                Text(
                    text = "${results.size}条结果",  // ✅ 简化文本
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)  // ✅ 减小间距
                )
            }

            // 搜索结果列表（更紧凑）
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)  // ✅ 减小项间距：8dp -> 4dp
            ) {
                items(results) { result ->
                    SearchResultCard(
                        result = result,
                        onClick = { onResultClick(result) }
                    )
                }

                if (results.isEmpty() && keyword.isNotBlank()) {
                    item {
                        Text(
                            text = "无结果",  // ✅ 简化文本
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)  // ✅ 减小间距
                        )
                    }
                }
            }
        }
    }
}

/**
 * 搜索结果卡片（更紧凑）
 */
@Composable
fun SearchResultCard(
    result: SearchData.SearchResult,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)  // ✅ 减小垂直间距：2dp -> 1dp
    ) {
        Column(
            modifier = Modifier.padding(6.dp)  // ✅ 减小内边距：12dp -> 6dp
        ) {
            // ✅ 合并为一个 Text，使用 AnnotatedString 实现不同样式
            Text(
                text = buildAnnotatedString {
                    // 页码部分（加粗、主题色）
                    pushStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                        )
                    )
                    append("${result.displayName}：")
                    pop()
                    
                    // 预览内容（普通样式）
                    pushStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize
                        )
                    )
                    append(result.previewContent)
                    pop()
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.2  // ✅ 减小行高
            )
        }
    }
}

