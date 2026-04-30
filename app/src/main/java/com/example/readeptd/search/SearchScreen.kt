package com.example.readeptd.search

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.roundToInt

/**
 * 侧滑搜索面板
 * @param visible 是否显示
 */
@Composable
fun SlideInSearchPanel(
    searchExecutor: (String) -> Flow<SearchData.SearchResult> = { emptyFlow() },
    onResultClick: (SearchData.SearchResult) -> Unit = {},
    onKeywordChange: (String) -> Unit = {},
    onClose: () -> Unit = {},
    initialVisible: Boolean = true,
    initialKeyword: String = "",
    viewModel: SearchViewModel = viewModel()
) {
    var visible by remember(initialVisible) { mutableStateOf(initialVisible) }
    var keyword by remember(initialKeyword) { mutableStateOf(initialKeyword) }
    var isCollapse by remember { mutableStateOf(false) }
    var isOnRight by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val results by viewModel.searchResults.collectAsState()

    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val panelWidthDp = screenWidthDp * 2 / 5
    val panelHeightDp = screenHeightDp
    // ✅ 统一使用 px 进行计算
    val screenWidthPx = with(density) { screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { screenHeightDp.dp.toPx() }

    val panelWidthPx = with(density) { panelWidthDp.dp.toPx() }
    val panelHeightPx = screenHeightPx

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
        if (!isCollapse) {
            panelPositionPx = if (visible) panelVisiblePositionPx else panelHidePositionPx
        }
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
            .shadow(8.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(8.dp)
        ) {
            // 标题栏（更紧凑）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.titleMedium
                )
                Row {
                    // ✅ 左右切换按钮（更小）
                    IconButton(
                        onClick = { isOnRight = !isOnRight },
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Icon(
                            imageVector = if (isOnRight) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = if (isOnRight) "切换到左侧" else "切换到右侧",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // 关闭按钮（更小）
                    IconButton(
                        onClick = { onClose() },
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

            Spacer(modifier = Modifier.height(8.dp))

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
                    IconButton(onClick = { viewModel.onSearch(keyword, searchExecutor) }) {
                        Icon(Icons.Default.Search, "搜索", modifier = Modifier.size(20.dp))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                textStyle = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 搜索结果数量（更紧凑）
            if (results.isNotEmpty()) {
                TextButton(
                    onClick = { isCollapse = !isCollapse },
                    modifier = Modifier
                        .padding(bottom = 0.dp)
                        .height(24.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "${results.size}条结果",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9
                    )
                }
            }
            if (!isCollapse) {
                // 搜索结果列表（更紧凑）
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .heightIn(max = screenHeightDp.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(results.size) { index ->
                        SearchResultCard(
                            result = results[index],
                            onClick = { onResultClick(results[index]) }
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
            .padding(vertical = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp)
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
                lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.2
            )
        }
    }
}

