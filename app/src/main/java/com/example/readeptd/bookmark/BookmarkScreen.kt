package com.example.readeptd.bookmark

import android.util.Log
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.activity.ContentUiEvent
import com.example.readeptd.activity.ContentViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.roundToInt
import com.example.readeptd.utils.SlideHint
import kotlinx.coroutines.delay

@Composable
fun BookmarkDialog(
    bookmarkData: BookmarkData,
    onDismiss: () -> Unit = {},
    onAfterConfirm: (BookmarkData) -> Unit = {},
    onAfterDelete:()->Unit = {},
){
    val bookmarkRepository = BookmarkRepository(LocalContext.current)
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(bookmarkData.note) }
    val maxLength = 50
    var deleteButtonClickNum by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = {
            // do nothing
        },
        title = {
            Text(text = if(bookmarkData.id == 0L) "增加书签" else "修改书签")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { newValue ->
                        text = newValue.substring(0, newValue.length.coerceAtMost(maxLength))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 6,
                    label = { Text("备注") },
                    placeholder = { Text("请输入备注") },
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${text.length} / $maxLength",
                                color = if (text.length >= maxLength) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if(text.isBlank()){
                                Text(
                                    text = "请输入备注信息",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    isError = text.isBlank() || text.length > maxLength
                )
            }
        },
        confirmButton = {
            if(bookmarkData.id != 0L) {
                OutlinedButton(
                    onClick = {
                        deleteButtonClickNum++
                        if(deleteButtonClickNum > 1) {
                            scope.launch {
                                bookmarkRepository.removeBookmark(bookmarkData.id)
                            }
                            onAfterDelete()
                        }
                    },
                    colors = if (deleteButtonClickNum > 0) {
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        ButtonDefaults.outlinedButtonColors() // 不传 containerColor
                    },
                    border = if (deleteButtonClickNum > 0) {
                        null
                    } else {
                        ButtonDefaults.outlinedButtonBorder()
                    }
                ) {
                    Text(if (deleteButtonClickNum > 0) "确认删除" else "删除")
                }
            }
            Button(
                enabled = !(text.isBlank() || text.length > maxLength),
                onClick = {
                    val newBookmark = bookmarkData.copyVal(note = text)
                    if(bookmarkData.id != 0L){
                        scope.launch {
                            bookmarkRepository.updateBookmark(newBookmark)
                        }
                    }else {
                        scope.launch {
                            bookmarkRepository.addBookmark(newBookmark)
                        }
                    }
                    onAfterConfirm(newBookmark)
                }
            ) {
                Text( if(bookmarkData.id == 0L) "添加" else "修改")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun BookmarkHint(
    modifier: Modifier = Modifier,
    contentViewModel: ContentViewModel,
    viewModel: BookmarkViewModel = viewModel(),
){
    val isBookmarked by contentViewModel.isBookmarked.collectAsStateWithLifecycle()
    var isShowBookmarkTip by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var delayJob by remember { mutableStateOf<Job?>(null) }
    val config by contentViewModel.configData.collectAsStateWithLifecycle()

    LaunchedEffect(isBookmarked) {
        delayJob?.cancel() // 取消之前的延迟任务
        if (isBookmarked) {
            delayJob = scope.launch {
                isShowBookmarkTip = true
                delay(5000)
                isShowBookmarkTip = false
            }
        } else {
            isShowBookmarkTip = false
        }
    }

    SlideHint(
        tips = "发现书签",
        visible = isShowBookmarkTip && config.isShowBookmarkHint,
        alignment = Alignment.TopStart,
        padding = PaddingValues(top = 72.dp),
        onClick = {
            contentViewModel.onEvent(ContentUiEvent.OnClickBookmark)
        }
    )
}

@Composable
fun BookmarkListPanel(
    modifier: Modifier = Modifier,
    viewModel: BookmarkViewModel,
    onBookmarkClick: (BookmarkData) -> Unit = {},
    currentDistanceToBookmark: (BookmarkData) -> Long = { 0 },  // ✅ 获取当前位置（页码/偏移等）
    onClose: () -> Unit = {}
) {
    var isCollapsed by remember { mutableStateOf(false) }
    var isOnRight by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var currentIndex by remember { mutableIntStateOf(-1) }
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var isFullScreen by remember {  mutableStateOf( false) }
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    var selectIndex by remember { mutableIntStateOf(-1) }  // 当前选中结果索引
    var bookmarkList by remember { mutableStateOf(emptyList<BookmarkData>()) }
    var isShowDelAllDialog by remember { mutableStateOf(false) }
    var currentKeyword by remember{mutableStateOf("")}

    // ✅ 当面板切换到全屏时，自动展开结果
    LaunchedEffect(isFullScreen) {
        if(isFullScreen){
            isCollapsed = false
        }
    }


    //var isFirstShow by remember { mutableStateOf(true) }
    // ✅ 主动获取当前位置并滚动到最近的结果
    LaunchedEffect(bookmarks, bookmarks.size, currentKeyword) {
        selectIndex = -1
        bookmarkList = bookmarks.sorted().filter {
            currentKeyword.isEmpty() || it.note.contains(currentKeyword, ignoreCase = true)
        }
        // ✅ 只在搜索刚完成且结果不为空时触发
        if (bookmarkList.isNotEmpty()) {
            var closestIndex = 0
            var minDistance = Long.MAX_VALUE

            bookmarkList.forEachIndexed { index, bookmark ->
                val distance = currentDistanceToBookmark(bookmark)
                if (distance < minDistance) {
                    minDistance = distance
                    closestIndex = index
                }
            }
            if (closestIndex >= 0) {
                // 更新选中状态并滚动
                if (closestIndex != currentIndex) {
                    currentIndex = closestIndex
                }
                lazyListState.scrollToItem(closestIndex)
            }
        }
    }

    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    Log.d("BookmarkListPanel", "screenWidthDp: $screenWidthDp, screenHeightDp: $screenHeightDp")
    val panelWidthDp = (screenWidthDp * 2 / 5).coerceIn(128,212)
    val panelHeightDp = if (isFullScreen) screenHeightDp else screenHeightDp
    Log.d("BookmarkListPanel", "panelWidthDp: $panelWidthDp, panelHeightDp: $panelHeightDp")
    // ✅ 统一使用 px 进行计算
    val screenWidthPx = with(density) { screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { screenHeightDp.dp.toPx() }

    val panelWidthPx = with(density) { if (isFullScreen) screenWidthDp.dp.toPx() else panelWidthDp.dp.toPx() }
    val panelHeightPx = screenHeightPx
    Log.d("BookmarkListPanel", "screenWidthPx: $screenWidthPx, screenHeightPx: $screenHeightPx")
    Log.d("BookmarkListPanel", "panelWidthPx: $panelWidthPx, panelHeightPx: $panelHeightPx")
    // ✅ 面板位置使用 px
    var panelVisiblePositionPx by remember(screenWidthPx, panelWidthPx, isOnRight, isFullScreen) {
        mutableStateOf(
            if (isOnRight) IntOffset((screenWidthPx - panelWidthPx).toInt(), 0)
            else IntOffset(0, 0)
        )
    }
    // ✅ 使用 px 管理位置
    var panelPositionPx by remember { mutableStateOf(panelVisiblePositionPx) }

    // ✅ 根据 visible 状态更新面板位置
    LaunchedEffect(panelVisiblePositionPx) {
        panelPositionPx = panelVisiblePositionPx
    }
    // ✅ 使用 px 管理位置
    val animatedOffsetPx by animateIntOffsetAsState(
        targetValue = panelPositionPx,
        label = "bookmark_panel_animation"
    )

    Box(
        modifier = Modifier
            .width(if (isFullScreen) screenWidthDp.dp else panelWidthDp.dp)
            .then(if (isFullScreen) Modifier.fillMaxHeight() else Modifier.wrapContentHeight())
            .heightIn(max = panelHeightDp.dp)
            .offset { animatedOffsetPx }
            .shadow(24.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(Unit){
                detectDragGestures { change, dragAmount ->
                    if(!isFullScreen) {
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
            // 标题栏（更紧凑）
            Row(
                modifier = Modifier.fillMaxWidth()
                    .pointerInput( Unit){
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
                    text = "书签列表",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                )
                if(isFullScreen) {
                    Text(
                        text = "${if (selectIndex >= 0) "${selectIndex + 1}/" else ""}${bookmarkList.size}条结果",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if(bookmarkList.isNotEmpty()) {
                        // 删除全部
                        IconButton(
                            onClick = {
                                isShowDelAllDialog = true
                            },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "全部删除",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
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

            OutlinedTextField(
                value = currentKeyword,
                onValueChange = { newValue ->
                    currentKeyword = newValue
                },
                label = null,
                placeholder = { Text("搜索...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                textStyle = MaterialTheme.typography.bodySmall
            )
            // 搜索结果数量（更紧凑）
            if (!isFullScreen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TextButton(
                        onClick = {
                            if(bookmarkList.isNotEmpty()) {
                                isCollapsed = !isCollapsed
                            }
                        },
                        modifier = Modifier
                            .padding(bottom = 0.dp)
                            .height(24.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        val tail = if(bookmarkList.isNotEmpty()) "(${if (isCollapsed) "展开" else "收起"})" else ""
                        val text = "${if(selectIndex >= 0) "${selectIndex+1}/" else ""}${bookmarkList.size}条结果$tail"
                        val tip = if(bookmarkList.isNotEmpty()) text else "没有书签"
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (!isCollapsed) {
                //var editIndex by remember { mutableIntStateOf(-1) }  // 当前编辑结果索引
                var isShowBookmarkDialog by remember { mutableStateOf(false) }
                var editBookmarkData:BookmarkData? by remember { mutableStateOf(null) }
                if(bookmarkList.isNotEmpty() && !isFullScreen) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                }
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .heightIn(max = screenHeightDp.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(bookmarkList.size) { index ->
                        BookmarkCard(
                            bookmarkData = bookmarkList[index],
                            isSelected = index == selectIndex,
                            onClick = {
                                currentIndex = index
                                selectIndex = index
                                onBookmarkClick(bookmarkList[index])
                            },
                            onLongPress = {
                                editBookmarkData = bookmarkList[index]
                                isShowBookmarkDialog = true
                            }
                        )
                    }
                }
                if(isShowBookmarkDialog && editBookmarkData != null){
                    BookmarkDialog(
                        bookmarkData = editBookmarkData!!,
                        onAfterConfirm = {
                            isShowBookmarkDialog = false
                        },
                        onAfterDelete = {
                            isShowBookmarkDialog = false
                        },
                        onDismiss = {
                            isShowBookmarkDialog = false
                        }
                    )
                }
            }
            if(isShowDelAllDialog){
                AlertDialog(
                    onDismissRequest = {
                        isShowDelAllDialog = false
                    },
                    title = {Text("删除全部书签")},
                    text = {Text("确定要删除全部书签吗？")},
                    confirmButton = {
                        Button(
                            onClick = {
                                scope.launch {
                                    viewModel.removeAllBookmarks()
                                }
                                isShowDelAllDialog = false
                            }
                        ) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                isShowDelAllDialog = false
                            }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun BookmarkCard(
    bookmarkData: BookmarkData,
    isSelected: Boolean = false,
    onClick: (BookmarkData) -> Unit = {},
    onLongPress: (BookmarkData) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress(bookmarkData) },
                    onTap = { onClick(bookmarkData) }
                )
            },
        shape = RectangleShape,
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        } else {
            CardDefaults.cardColors() // 不传 containerColor，保持 Card 默认颜色
        }
    ) {
        Column(
            modifier = Modifier.padding(4.dp)
        ) {
            Text(
                maxLines = 3,
                text = buildAnnotatedString {
                    append(bookmarkData.note)
                },
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight
            )
        }
    }
}