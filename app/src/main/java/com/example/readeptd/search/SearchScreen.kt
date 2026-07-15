package com.example.readeptd.search

import android.util.Log
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    onVisibleChange: (Boolean) -> Unit = {},
    getDistanceToResult: (SearchData.SearchResult) -> Long = { 0 },  // ✅ 获取当前位置（页码/偏移等）
    onClose: () -> Unit = {},
    visible: Boolean = true,
    initKeyword: String = "",
    fileUri: String? = null,  // ✅ 新增：文件 URI，用于隔离搜索历史
    viewModel: SearchViewModel = viewModel()
) {
    var isVisible by remember(visible) { mutableStateOf(visible) }
    var currentKeyword by remember { mutableStateOf(initKeyword) }
    var isCollapsed by remember { mutableStateOf(false) }
    var isOnRight by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val results by viewModel.searchResults.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val lastSearchedKeyword by viewModel.lastSearchedKeyword.collectAsState()
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var isFullScreen by remember {  mutableStateOf( false) }

    // ✅ 初始化 ViewModel 并绑定到当前文件
    LaunchedEffect(fileUri) {
        if (fileUri != null) {
            viewModel.initialize(fileUri)
            Log.d("SlideInSearchPanel", "已初始化 SearchViewModel，文件 URI: $fileUri")
        }
    }

    // ✅ 当面板切换到全屏时，自动展开搜索结果
    LaunchedEffect(isFullScreen) {
        if(isFullScreen){
            isCollapsed = false
        }
    }

    DisposableEffect(Unit) {
        Log.d("SlideInSearchPanel", "DisposableEffect: 设置 onClickHistoryKeyword 回调")
        viewModel.setOnClickHistoryKeyword {
            Log.d("SlideInSearchPanel", "onClickHistoryKeyword 被调用: keyword=$it, visible 原来是 $isVisible")
            currentKeyword = it
            onKeywordChange(it)
            isVisible = true
            onVisibleChange(true)
            Log.d("SlideInSearchPanel", "visible 现在是 $isVisible, 开始搜索")
            viewModel.onSearch(currentKeyword, searchExecutor)
            isCollapsed = false
        }
        onDispose {
            Log.d("SlideInSearchPanel", "DisposableEffect: 清理 onClickHistoryKeyword 回调")
            viewModel.setOnClickHistoryKeyword(null)
        }
    }
    
    // ✅ 搜索完成后，主动获取当前位置并滚动到最近的结果
    LaunchedEffect(isSearching, results.size) {
        // ✅ 只在搜索刚完成且结果不为空时触发
        if (!isSearching && results.isNotEmpty()) {
            // ✅ 使用 ViewModel 的方法找到最近的索引
            val closestIndex = viewModel.findClosestResultIndex(getDistanceToResult)
            
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
    val panelWidthDp = (screenWidthDp * 3 / 5).coerceIn(128,212)
    val panelHeightDp = if (isFullScreen) screenHeightDp else screenHeightDp
    Log.d("SlideInSearchPanel", "panelWidthDp: $panelWidthDp, panelHeightDp: $panelHeightDp")
    // ✅ 统一使用 px 进行计算
    val screenWidthPx = with(density) { screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { screenHeightDp.dp.toPx() }

    val panelWidthPx = with(density) { if (isFullScreen) screenWidthDp.dp.toPx() else panelWidthDp.dp.toPx() }
    val panelHeightPx = screenHeightPx
    Log.d("SlideInSearchPanel", "screenWidthPx: $screenWidthPx, screenHeightPx: $screenHeightPx")
    Log.d("SlideInSearchPanel", "panelWidthPx: $panelWidthPx, panelHeightPx: $panelHeightPx")
    // ✅ 面板位置使用 px
    val panelVisiblePositionPx by remember(screenWidthPx, panelWidthPx, isOnRight, isFullScreen) {
        mutableStateOf(
            if (isOnRight) IntOffset((screenWidthPx - panelWidthPx).toInt(), 0)
            else IntOffset(0, 0)
        )
    }

    val panelHidePositionPx by remember(screenWidthPx, panelWidthPx, isOnRight, isFullScreen) {
        mutableStateOf(
            if (isOnRight) IntOffset(screenWidthPx.toInt(), 0)
            else IntOffset((-panelWidthPx).toInt(), 0)
        )
    }

    // ✅ 使用 px 管理位置
    var panelPositionPx by remember { mutableStateOf(panelHidePositionPx) }

    // ✅ 根据 visible 状态更新面板位置
    LaunchedEffect(isVisible, panelVisiblePositionPx, panelHidePositionPx) {
        panelPositionPx = if (isVisible) panelVisiblePositionPx else panelHidePositionPx
    }


    val animatedOffsetPx by animateIntOffsetAsState(
        targetValue = panelPositionPx,
        label = "search_panel_animation"
    )

    Box(
        modifier = Modifier
            .width(if (isFullScreen) screenWidthDp.dp else panelWidthDp.dp)
            .then(if (isFullScreen) Modifier.fillMaxHeight() else Modifier.wrapContentHeight())
            .heightIn(max = panelHeightDp.dp)
            .offset { animatedOffsetPx }
            .shadow(24.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    if (!isFullScreen) {
                        panelPositionPx = IntOffset(
                            (panelPositionPx.x + dragAmount.x).roundToInt(),
                            (panelPositionPx.y + dragAmount.y).roundToInt()
                        )
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isFullScreen) Modifier.fillMaxHeight() else Modifier.wrapContentHeight())
                .padding(4.dp)
        ) {
            // ✅ 判断是否应该显示搜索结果：从结果中获取关键词
            val currentSearchKeyword = results.firstOrNull()?.keyword
            val shouldShowResults = currentKeyword.isNotBlank() && results.isNotEmpty() && currentKeyword == currentSearchKeyword
            var selectIndex by remember { mutableIntStateOf(-1) }  // 当前选中结果索引
            // 标题栏（更紧凑）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                isFullScreen = !isFullScreen
                            }
                        )
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                )
                if(isFullScreen && shouldShowResults) {
                    Text(
                        text = "${if (selectIndex >= 0) "${selectIndex + 1}/" else ""}${results.size}条结果",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if(!isFullScreen) {
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
                    }
                    // 关闭按钮（更小）
                    IconButton(
                        onClick = { 
                            viewModel.stopSearching()
                            onClose() 
                        },
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

            Spacer(modifier = Modifier.height(2.dp))

            // ✅ 判断是否执行过搜索：关键词不为空、不在搜索中、且与最后搜索的关键词一致
            val hasSearched = currentKeyword.isNotBlank() && !isSearching && currentKeyword == lastSearchedKeyword
            // 搜索输入框（更紧凑）
            OutlinedTextField(
                value = currentKeyword,
                onValueChange = { newValue ->
                    currentKeyword = newValue
                    onKeywordChange(newValue)
                },
                leadingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            // 搜索取消
                                            viewModel.stopSearching()
                                        }
                                    )
                                },
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = {
                                selectIndex = -1
                                viewModel.onSearch(currentKeyword, searchExecutor)
                                isCollapsed = false
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Search, "搜索", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                label = null,
                placeholder = { Text("搜索...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                trailingIcon = {
                    if (!isSearching) {
                        IconButton(
                            onClick = {
                                currentKeyword = ""
                                onKeywordChange("")
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, "清除", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                textStyle = MaterialTheme.typography.bodySmall
            )

            // ✅ 搜索状态提示
            if (isSearching && results.isEmpty()) {

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "搜索中...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 搜索结果数量（更紧凑）
            if (shouldShowResults && !isFullScreen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TextButton(
                        onClick = { isCollapsed = !isCollapsed },
                        modifier = Modifier
                            .padding(bottom = 0.dp)
                            .height(24.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        Text(
                            text = "${if(selectIndex >= 0) "${selectIndex+1}/" else ""}${results.size}条结果(${if (isCollapsed) "展开" else "收起"})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(
                        onClick = { 
                            viewModel.navigateToPrevious(bySortKey =  true)
                            viewModel.getCurrentResult()?.let { onResultClick(it) }
                            scope.launch {
                                selectIndex = currentIndex
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
                                selectIndex = currentIndex
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
            
            // ✅ 搜索无结果提示（只在搜索完成后且无结果时显示）
            if (!isSearching && hasSearched && results.isEmpty()) {
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
                            searchResult = results[index],
                            isSelected = index == selectIndex,
                            onClick = { 
                                viewModel.setCurrentIndex(index)
                                selectIndex = index
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
    searchResult: SearchData.SearchResult,
    isSelected: Boolean = false,
    onClick: (SearchData.SearchResult) -> Unit = {}
) {
    Card(
        onClick = { onClick(searchResult) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors() // 不传 containerColor，保持 Card 默认颜色
        }
    ) {
        Column(
            modifier = Modifier.padding(4.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    // 页码部分（加粗、主题色）
                    pushStyle(
                        SpanStyle(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            fontWeight = MaterialTheme.typography.labelSmall.fontWeight,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                        )
                    )
                    append("${searchResult.displayName}：")
                    pop()

                    // 预览内容（普通样式）
                    pushStyle(
                        SpanStyle(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = MaterialTheme.typography.bodySmall.fontSize
                        )
                    )
                    append(searchResult.previewContent)
                    pop()
                },
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchHistoryDialog(
    viewModel: SearchViewModel = viewModel(),
    onDismiss: () -> Unit = {},
    onClickKeyword: (String) -> Unit = {},
){
    var keywords by remember { mutableStateOf(viewModel.getKeywords()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "搜索历史",)
        },
        text = {
            if(keywords.isEmpty()){
                Text(
                    text = "无搜索历史",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.Start,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    keywords.forEach { keyword ->
                        Surface(
                            modifier = Modifier
                                .wrapContentSize()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .clickable {
                                    Log.d("SearchHistoryDialog", "点击历史关键词: $keyword")
                                    onClickKeyword(keyword)
                                    // ✅ 从缓存中恢复搜索结果
                                    viewModel.onEvent(SearchEvent.onClickHistoryKeyword(keyword))
                                }
                            ,
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                text = keyword,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        dismissButton =  {
            if(keywords.isNotEmpty()) {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        keywords = emptyList()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("清空")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}