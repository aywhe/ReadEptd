package com.example.readeptd.search

import android.util.Log
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 侧滑搜索面板
 */
@Composable
fun SlideInSearchPanel(
    searchExecutor: (String) -> Flow<SearchData.SearchResult> = { emptyFlow() },
    onResultClick: (SearchData.SearchResult) -> Unit = {},
    onKeywordChange: (String) -> Unit = {},
    getCurrentPosition: () -> Int = { 0 },  // ✅ 获取当前位置（页码/偏移等）
    onClose: () -> Unit = {},
    initialVisible: Boolean = true,
    initialKeyword: String = "",
    viewModel: SearchViewModel = viewModel()
) {
    var visible by remember(initialVisible) { mutableStateOf(initialVisible) }
    var keyword by remember(initialKeyword) { mutableStateOf(initialKeyword) }
    var isCollapsed by remember { mutableStateOf(false) }
    var isOnRight by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val results by viewModel.searchResults.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // ✅ 监听屏幕旋转，清除搜索结果
    LaunchedEffect(configuration.orientation) {
        viewModel.clearCache()
        viewModel.clearResults()
    }
    
    // ✅ 搜索完成后，主动获取当前位置并滚动到最近的结果
    LaunchedEffect(results.size) {
        if (results.isNotEmpty()) {
            // ✅ 主动获取当前位置
            val currentPosition = getCurrentPosition()
            
            // ✅ 使用 ViewModel 的方法找到最近的索引
            val closestIndex = viewModel.findClosestResultIndex(currentPosition)
            
            if (closestIndex >= 0) {
                // 更新选中状态并滚动
                if (closestIndex != currentIndex) {
                    viewModel.setCurrentIndex(closestIndex)
                }
                lazyListState.scrollToItem(closestIndex)
            }
        }
    }

    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    Log.d("SlideInSearchPanel", "screenWidthDp: $screenWidthDp, screenHeightDp: $screenHeightDp")
    val panelWidthDp = (screenWidthDp * 2 / 5).coerceIn(128,212)
    val panelHeightDp = screenHeightDp
    Log.d("SlideInSearchPanel", "panelWidthDp: $panelWidthDp, panelHeightDp: $panelHeightDp")
    // ✅ 统一使用 px 进行计算
    val screenWidthPx = with(density) { screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { screenHeightDp.dp.toPx() }

    val panelWidthPx = with(density) { panelWidthDp.dp.toPx() }
    val panelHeightPx = screenHeightPx
    Log.d("SlideInSearchPanel", "screenWidthPx: $screenWidthPx, screenHeightPx: $screenHeightPx")
    Log.d("SlideInSearchPanel", "panelWidthPx: $panelWidthPx, panelHeightPx: $panelHeightPx")
    // ✅ 面板位置使用 px
    val panelVisiblePositionPx by remember(screenWidthPx, panelWidthPx, isOnRight) {
        mutableStateOf(
            if (isOnRight) IntOffset((screenWidthPx - panelWidthPx).toInt(), 0)
            else IntOffset(0, 0)
        )
    }

    val panelHidePositionPx by remember(screenWidthPx, panelWidthPx, isOnRight) {
        mutableStateOf(
            if (isOnRight) IntOffset(screenWidthPx.toInt(), 0)
            else IntOffset((-panelWidthPx).toInt(), 0)
        )
    }

    // ✅ 使用 px 管理位置
    var panelPositionPx by remember { mutableStateOf(panelHidePositionPx) }

    // ✅ 根据 visible 状态更新面板位置
    LaunchedEffect(visible, panelVisiblePositionPx, panelHidePositionPx) {
        panelPositionPx = if (visible) panelVisiblePositionPx else panelHidePositionPx
    }


    val animatedOffsetPx by animateIntOffsetAsState(
        targetValue = panelPositionPx,
        label = "search_panel_animation"
    )

    Box(
        modifier = Modifier
            .width(panelWidthDp.dp)
            .wrapContentHeight()
            .heightIn(max = panelHeightDp.dp)
            .offset { animatedOffsetPx }
            .shadow(24.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .pointerInput(Unit){
                detectDragGestures { change, dragAmount ->
                    panelPositionPx = IntOffset(
                        (panelPositionPx.x + dragAmount.x).roundToInt(),
                        (panelPositionPx.y + dragAmount.y).roundToInt()
                    )
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(4.dp)
        ) {
            // 标题栏（更紧凑）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ✅ 左右切换按钮（更小）
                    IconButton(
                        onClick = { isOnRight = !isOnRight },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = if (isOnRight) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = if (isOnRight) "切换到左侧" else "切换到右侧",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    // 关闭按钮（更小）
                    IconButton(
                        onClick = { onClose() },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            var lastKeyword by remember { mutableStateOf("") }
            
            // ✅ 判断是否应该显示搜索结果：只有当 keyword 与 lastKeyword 一致时才显示
            val shouldShowResults = keyword.isNotBlank() && results.isNotEmpty() && keyword == lastKeyword
            
            // ✅ 判断是否执行过搜索：keyword 不为空且与 lastKeyword 一致
            val hasSearched = keyword.isNotBlank() && keyword == lastKeyword
            
            // 搜索输入框（更紧凑）
            OutlinedTextField(
                value = keyword,
                onValueChange = { newValue ->
                    keyword = newValue
                    onKeywordChange(newValue)
                },
                label = null,
                placeholder = { Text("搜索...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            viewModel.onSearch(keyword, searchExecutor)
                            lastKeyword = keyword
                            isCollapsed = false
                        },
                        modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Search, "搜索", modifier = Modifier.size(16.dp))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 搜索结果数量（更紧凑）
            if (shouldShowResults) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TextButton(
                        onClick = { isCollapsed = !isCollapsed },
                        modifier = Modifier
                            .padding(bottom = 0.dp)
                            .height(24.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 2.dp,
                            vertical = 0.dp
                        )
                    ) {
                        Text(
                            text = "${results.size}条结果(${if (isCollapsed) "展开" else "收起"})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                        )
                    }
                    IconButton(
                        onClick = { 
                            viewModel.navigateToPrevious(bySortKey =  true)
                            viewModel.getCurrentResult()?.let { onResultClick(it) }
                            scope.launch {
                                lazyListState.scrollToItem(index = currentIndex)
                            }
                        },
                        modifier = Modifier.size(24.dp),
                        enabled = results.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "上一项",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { 
                            viewModel.navigateToNext(bySortKey = true)
                            viewModel.getCurrentResult()?.let { onResultClick(it)}
                            scope.launch {
                                lazyListState.scrollToItem(index = currentIndex)
                            }
                        },
                        modifier = Modifier.size(24.dp),
                        enabled = results.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "下一项",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            // ✅ 搜索无结果提示
            if (hasSearched && results.isEmpty()) {
                Text(
                    text = "未找到匹配内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            if (!isCollapsed && shouldShowResults) {
                // 搜索结果列表（更紧凑）
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .heightIn(max = screenHeightDp.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(results.size) { index ->
                        SearchResultCard(
                            result = results[index],
                            onClick = { 
                                viewModel.setCurrentIndex(index)
                                onResultClick(results[index]) 
                            }
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
    onClick: (SearchData.SearchResult) -> Unit
) {
    Card(
        onClick = { onClick(result) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(4.dp)
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
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.2
            )
        }
    }
}

