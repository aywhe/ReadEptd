package com.example.readeptd.search

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * 侧滑搜索面板
 * @param visible 是否显示
 * @param onDismiss 关闭回调
 * @param keyword 当前搜索关键词
 * @param onKeywordChange 关键词变化回调
 * @param onSearch 执行搜索回调
 * @param results 搜索结果列表
 * @param onResultClick 点击搜索结果回调
 */
@Composable
fun SlideInSearchPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,  // ✅ 只需要这个回调
    resultsState: StateFlow<List<SearchData.SearchResult>>,
    onResultClick: (SearchData.SearchResult) -> Unit
) {
    var keyword by remember { mutableStateOf("") }  // ✅ 完全内部管理

    // ✅ 获取屏幕配置
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    
    // ✅ 计算面板宽度为屏幕宽度的 1/3
    val panelWidthDp = (screenWidthDp / 3).coerceAtLeast(200).coerceAtMost(400)
    
    val targetOffset = if (visible) IntOffset(screenWidthDp - panelWidthDp, 0) else IntOffset(screenWidthDp, 0)
    val animatedOffset by animateIntOffsetAsState(
        targetValue = targetOffset,
        label = "search_panel_animation"
    )

    val results by resultsState.collectAsState()

    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(panelWidthDp.dp)
            .offset(animatedOffset.x.dp, animatedOffset.y.dp)
            .shadow(8.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 搜索输入框
            OutlinedTextField(
                value = keyword,
                onValueChange = { newValue -> keyword = newValue },
                label = { Text("关键词") },
                placeholder = { Text("请输入搜索内容") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {onSearch(keyword)}) {
                        Icon(Icons.Default.Search, "搜索")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 搜索结果数量
            if (results.isNotEmpty()) {
                Text(
                    text = "找到 ${results.size} 个结果",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // 搜索结果列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            text = "未找到结果",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 搜索结果卡片
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
            .padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = result.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.previewContent,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

