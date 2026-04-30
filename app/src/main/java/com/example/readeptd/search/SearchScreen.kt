package com.example.readeptd.search

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
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
    onResultClick: (SearchData.SearchResult) -> Unit,
    onKeywordChange: (String) -> Unit,
    searchExecutor: (String) -> Flow<SearchData.SearchResult>,
    useKeyword: String = "",
    viewModel: SearchViewModel = viewModel()
) {
    var isDragMode by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf(useKeyword) }
    var isOnRight by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val results by viewModel.searchResults.collectAsState()

    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    val panelWidthDp = 160
    val panelHeightDp = screenHeightDp

    var panelVisiblePositionDp by remember { mutableStateOf(IntOffset(screenWidthDp - panelWidthDp, 0)) }
    var panelHidePositionDp by remember { mutableStateOf(IntOffset(screenWidthDp, 0)) }
    if(isOnRight){
        panelVisiblePositionDp = IntOffset(screenWidthDp - panelWidthDp, 0)
        panelHidePositionDp = IntOffset(screenWidthDp, 0)
    } else {
        panelVisiblePositionDp = IntOffset(0, 0)
        panelHidePositionDp = IntOffset(-panelWidthDp, 0)
    }
    var panelPosition by remember { mutableStateOf(panelVisiblePositionDp) }
    if(isDragMode){
        if (visible) {

        } else {
            panelPosition = panelHidePositionDp
        }
    } else {
        if (visible) {
            panelPosition = panelVisiblePositionDp
        } else {
            panelPosition = panelHidePositionDp
        }
    }
    
    val animatedOffset by animateIntOffsetAsState(
        targetValue = panelPosition,
        label = "search_panel_animation"
    )

    var modifier: Modifier = Modifier
    if(isDragMode){
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        panelPosition += IntOffset(
                            dragAmount.x.toInt(),
                            dragAmount.y.toInt()
                        )
                    }
                )
            }
            .wrapContentWidth()
            .wrapContentHeight()
    } else {
        modifier = Modifier
            .height(panelHeightDp.dp)
            .width(panelWidthDp.dp)
    }
    Box(
        modifier = modifier
            .offset(animatedOffset.x.dp, animatedOffset.y.dp)
            .shadow(8.dp)
            .background(MaterialTheme.colorScheme.surface)
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

                    if(!isDragMode) {
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
                    }
                    // 关闭按钮（更小）
                    IconButton(
                        onClick = { visible = false },
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
                    IconButton(onClick = {viewModel.onSearch(keyword, searchExecutor)}) {
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
                    modifier = Modifier.padding(bottom = 4.dp)
                        .pointerInput( Unit){
                            detectTapGestures(
                                onTap = {
                                    isDragMode = !isDragMode
                                }
                            )
                        }
                )
            }
            if(!isDragMode) {
                // 搜索结果列表（更紧凑）
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)  // ✅ 减小项间距：8dp -> 4dp
                ) {
                    items(results.size) { index ->
                        SearchResultCard(
                            result = results[index],
                            onClick = { onResultClick(results[index]) }
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

