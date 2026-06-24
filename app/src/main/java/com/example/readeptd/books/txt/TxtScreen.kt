package com.example.readeptd.books.txt

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.data.FileInfo
import com.example.readeptd.data.ReadingState
import com.example.readeptd.books.BookUiState
import com.example.readeptd.activity.ContentUiEvent
import com.example.readeptd.speech.TtsViewModel
import com.example.readeptd.utils.JumpToPageDialog
import com.example.readeptd.activity.ContentViewModel
import com.example.readeptd.search.SearchData
import com.example.readeptd.search.SlideInSearchPanel
import com.example.readeptd.utils.JumpToProgressDialog
import com.example.readeptd.utils.LayoutSettingDialog
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.text.toInt

/**
 * ✅ TXT 阅读器主屏幕
 * 
 * 负责显示 TXT 文件的阅读界面，支持：
 * - 滑动/滚动两种阅读模式
 * - 字体大小和行距调整
 * - 全文搜索和高亮
 * - TTS 语音朗读
 * - 阅读进度保存和恢复
 *
 * @param fileInfo 文件信息
 * @param contentViewModel 内容 ViewModel
 * @param ttsModel TTS ViewModel
 * @param modifier 修饰符
 * @param viewModel TXT ViewModel
 */
@OptIn(FlowPreview::class)
@Composable
fun TxtScreen(
    fileInfo: FileInfo,
    contentViewModel: ContentViewModel,
    ttsModel: TtsViewModel,
    modifier: Modifier = Modifier,
    viewModel: TxtViewModel = viewModel()
) {
    Log.d("TxtScreen", "[TxtScreen] 组件创建, fileInfo=${fileInfo.fileName}")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Log.d("TxtScreen", "[TxtScreen] uiState=$uiState")

    // 准备 TXT 文件
    LaunchedEffect(fileInfo.uri) {
        Log.d("TxtScreen", "[LaunchedEffect] 准备 TXT 文件: ${fileInfo.uri}")
        viewModel.prepareBookFile(fileInfo.uri.toUri(), fileInfo.fileName)
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is BookUiState.Loading -> LoadingState()
            is BookUiState.Ready -> ReadyState(
                fileInfo = fileInfo,
                contentViewModel = contentViewModel,
                viewModel = viewModel,
                ttsModel = ttsModel
            )
            is BookUiState.Error -> ErrorState(state.message)
        }
    }
}

/**
 * ✅ 加载状态组件
 */
@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text(
            text = "准备阅读器...",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/**
 * ✅ 错误状态组件
 */
@Composable
private fun ErrorState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * ✅ 就绪状态组件（主要阅读界面）
 */
@OptIn(FlowPreview::class)
@Composable
private fun ReadyState(
    fileInfo: FileInfo,
    contentViewModel: ContentViewModel,
    viewModel: TxtViewModel,
    ttsModel: TtsViewModel
) {
    Log.d("TxtScreen", "[ReadyState] 组件创建")
    var lastClickTime by remember { mutableStateOf(0L) }
    val readingState by viewModel.readingState.collectAsStateWithLifecycle()
    val isSwipeLayout = readingState?.isSwipeLayout ?: true
    Log.d("TxtScreen", "[ReadyState] readingState=$readingState, isSwipeLayout=$isSwipeLayout")
    // ✅ 在这里计算 padding，避免从上层传递
    val leftPaddingDp = 16
    val rightPaddingDp = 16
    val topPaddingDp = if (isSwipeLayout) 16 else 0
    val bottomPaddingDp = if (isSwipeLayout) 16 else 0
    val isPagesReady by viewModel.isPagesReady.collectAsStateWithLifecycle()
    Log.d("TxtScreen", "[ReadyState] isPagesReady=$isPagesReady")
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    var isShowLayoutSettingDialog by remember { mutableStateOf(false) }

    // 监听屏幕旋转
    LaunchedEffect(configuration.orientation) {
        Log.d("TxtScreen", "[LaunchedEffect] 屏幕方向变化: ${configuration.orientation}")
        viewModel.onEvent(TxtEvent.OnScreenOrientationChanged(configuration.orientation))
    }

    // 监听分页模式切换
    LaunchedEffect(isSwipeLayout) {
        Log.d("TxtScreen", "[LaunchedEffect] 切换分页模式: isSwipeLayout=$isSwipeLayout")
        if (isSwipeLayout) {
            Log.d("TxtScreen", "[LaunchedEffect] 设置为 ByLayoutSize 模式")
            viewModel.setSplitPagesMode(SplitPagesMode.ByLayoutSize)
        } else {
            Log.d("TxtScreen", "[LaunchedEffect] 设置为 ByLinesCount 模式")
            viewModel.setSplitPagesMode(SplitPagesMode.ByLinesCount)
//            Log.d("TxtScreen", "[LaunchedEffect] 设置为 ByCharsCount 模式")
//            viewModel.setSplitPagesMode(SplitPagesMode.ByCharsCount)
        }
    }
    
    // ✅ 在这里计算 contentPadding
    val contentPadding = PaddingValues(
        start = leftPaddingDp.dp,
        end = rightPaddingDp.dp,
        top = topPaddingDp.dp,
        bottom = bottomPaddingDp.dp
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                Log.d("TxtScreen", "[onSizeChanged] 视图尺寸变化: ${size.width}x${size.height}")
                scope.launch {
                    viewModel.onEvent(
                        TxtEvent.OnViewMetricsChanged(
                            size = size,
                            leftPaddingDp = leftPaddingDp,
                            rightPaddingDp = rightPaddingDp,
                            topPaddingDp = topPaddingDp,
                            bottomPaddingDp = bottomPaddingDp
                        )
                    )
                    Log.d("TxtScreen", "[onSizeChanged] 事件发射完成")
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        val clickTime = SystemClock.uptimeMillis()
                        val timeDiff = clickTime - lastClickTime
                        lastClickTime = clickTime
                        val isDoubleClick = timeDiff <= 300
                        if (isDoubleClick) {
                            Log.d("TxtScreen", "双击屏幕，切换全屏，时间间隔: ${timeDiff}ms")
                            contentViewModel.onEvent(ContentUiEvent.OnDoubleClickScreen)
                            viewModel.onEvent(TxtEvent.OnDoubleClickScreen)
                        }
                    }
                }
            }
    ) {
        Log.d("TxtScreen", "[ReadyState] 渲染分支: isPagesReady=$isPagesReady")
        if (!isPagesReady) {
            Log.d("TxtScreen", "[ReadyState] 显示 PagingState（正在分页）")
            PagingState()
        } else {
            Log.d("TxtScreen", "[ReadyState] 显示 ReaderContent（阅读内容）")
            ReaderContent(
                fileInfo = fileInfo,
                isSwipeLayout = isSwipeLayout,
                contentViewModel = contentViewModel,
                viewModel = viewModel,
                ttsModel = ttsModel,
                onLongPressProgressInfo = { isShowLayoutSettingDialog = true },
                contentPadding = contentPadding,
                scope = scope
            )
        }
    }
    
    // ✅ 放在 Box 外部，完全独立于 Box 的重组，避免分页时 dialog 重组
    LayoutSettingDialogs(
        isShowLayoutSettingDialog = isShowLayoutSettingDialog,
        isSwipeLayout = isSwipeLayout,
        viewModel = viewModel,
        onDismiss = { isShowLayoutSettingDialog = false }
    )
}

/**
 * ✅ 分页中状态
 */
@Composable
private fun PagingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text(
            text = "正在分页...",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/**
 * ✅ 阅读器内容（分页完成后）
 */
@OptIn(FlowPreview::class)
@Composable
private fun ReaderContent(
    fileInfo: FileInfo,
    isSwipeLayout: Boolean,
    contentViewModel: ContentViewModel,
    viewModel: TxtViewModel,
    ttsModel: TtsViewModel,
    onLongPressProgressInfo: (String) -> Unit,
    contentPadding: PaddingValues,
    scope: CoroutineScope
) {
    val currentPage by viewModel.currentPage.collectAsState()
    var currentKeyword by remember { mutableStateOf("") }

    // ✅ 对话框状态管理 - 就近定义
    var isShowJumpToPageDialog by remember { mutableStateOf(false) }
    var isShowSearchDialog by remember { mutableStateOf(false) }

    var previewScale by remember { mutableFloatStateOf(1f) }
    val lineHeightFactor = 1.5f

    LaunchedEffect(Unit) {
        // 4. 监听预览缩放的变化
        snapshotFlow { previewScale }
            .debounce(1500) // 300ms 防抖，等待用户手指松开或停止缩放
            .collectLatest { finalScale ->
                // 5. 只有当用户停止操作后，才更新最终的 scale 状态
                // 这会触发 viewModel 发送 OnFontSizeChanged 事件
                val newFontSizeSp = (viewModel.currentFontSizeSp * finalScale).toInt()
                val newLineHeightSp = (newFontSizeSp * lineHeightFactor).toInt()
                viewModel.onEvent(TxtEvent.OnFontSizeChanged(newFontSizeSp))
                viewModel.onEvent(TxtEvent.OnLineHeightChanged(newLineHeightSp))
            }
    }

    Box(modifier = Modifier.fillMaxSize()
        .pointerInput(Unit) {
            detectTransformGestures(
                onGesture = { centroid, pan, zoom, rotation ->
                    previewScale *= zoom
                    previewScale = previewScale.coerceIn(0.8f, 2.0f)
                }
            )
        }
    ) {
        // ✅ 设置回调
        SetupCallbacks(
            contentViewModel = contentViewModel,
            onClickProgressInfo = { isShowJumpToPageDialog = true },
            onLongPressProgressInfo = onLongPressProgressInfo,
            onClickSearchButton = { isShowSearchDialog = !isShowSearchDialog }
        )
        
        // ✅ 更新进度文本
        UpdateProgressText(
            currentPage = currentPage,
            isSwipeLayout = isSwipeLayout,
            contentViewModel = contentViewModel,
            viewModel = viewModel
        )
        
        // ✅ 设置 TTS 回调
        SetupTtsCallbacks(
            viewModel = viewModel,
            ttsModel = ttsModel,
            scope = scope
        )
        
        // ✅ 阅读内容 - 直接在这里处理，避免额外传递参数
        TxtLayoutWrapper(
            isSwipeLayout = isSwipeLayout,
            viewModel = viewModel
        ) { page ->
            val pageContent = viewModel.getPageContent(page).trimEnd()
            val pageAnnotatedContent = if(isShowSearchDialog) {
                highLightText(pageContent, currentKeyword)
            } else {
                AnnotatedString(pageContent)
            }
            val currentDisplayFontSizeSp = (viewModel.currentFontSizeSp * previewScale).toInt()
            val currentDisplayLineHeightSp = (currentDisplayFontSizeSp * lineHeightFactor).toInt()
            PageContent(
                pageAnnotatedContent = pageAnnotatedContent,
                fontSize = currentDisplayFontSizeSp,
                lineHeight = currentDisplayLineHeightSp,
                contentPadding = contentPadding
            )
        }
        
        // ✅ 对话框
        JumpDialogs(
            isShowJumpToPageDialog = isShowJumpToPageDialog,
            isSwipeLayout = isSwipeLayout,
            currentPage = currentPage,
            viewModel = viewModel,
            onDismiss = { isShowJumpToPageDialog = false },
            scope = scope
        )
        
        SearchPanel(
            fileInfo = fileInfo,
            isShowSearchDialog = isShowSearchDialog,
            currentPage = currentPage,
            currentKeyword = currentKeyword,
            viewModel = viewModel,
            onVisibleChange = { isShowSearchDialog = it },
            onKeywordChange = { currentKeyword = it },
            scope = scope
        )
    }
}

/**
 * ✅ 设置 ContentViewModel 回调
 */
@Composable
private fun SetupCallbacks(
    contentViewModel: ContentViewModel,
    onClickProgressInfo: (String) -> Unit,
    onLongPressProgressInfo: (String) -> Unit,
    onClickSearchButton: () -> Unit
) {
    DisposableEffect(Unit) {
        contentViewModel.setOnClickProgressInfoCallback { onClickProgressInfo(it) }
        contentViewModel.setOnLongPressProgressInfoCallback { onLongPressProgressInfo(it) }
        contentViewModel.setOnClickSearchButtonCallback { onClickSearchButton() }

        onDispose {
            contentViewModel.setOnClickProgressInfoCallback(null)
            contentViewModel.setOnLongPressProgressInfoCallback(null)
            contentViewModel.setOnClickSearchButtonCallback(null)
        }
    }
}

/**
 * ✅ 更新进度文本
 */
@Composable
private fun UpdateProgressText(
    currentPage: Int,
    isSwipeLayout: Boolean,
    contentViewModel: ContentViewModel,
    viewModel: TxtViewModel
) {
    LaunchedEffect(currentPage, isSwipeLayout) {
        Log.d("TxtScreen", "[UpdateProgressText] 更新进度文本: currentPage=$currentPage, isSwipeLayout=$isSwipeLayout")
        if (isSwipeLayout) {
            val progressText = "${currentPage + 1}/${viewModel.getPagesCount()}"
            Log.d("TxtScreen", "[UpdateProgressText] 滑动模式进度: $progressText")
            contentViewModel.updateProgressText(progressText)
        } else {
            val progressPercent = (viewModel.getProgress() * 100).roundToInt()
            val progressText = "${progressPercent}%"
            Log.d("TxtScreen", "[UpdateProgressText] 滚动模式进度: $progressText")
            contentViewModel.updateProgressText(progressText)
        }
    }
}

/**
 * ✅ 设置 TTS 回调
 */
@Composable
private fun SetupTtsCallbacks(
    viewModel: TxtViewModel,
    ttsModel: TtsViewModel,
    scope: CoroutineScope
) {
    val currentPage by viewModel.currentPage.collectAsState()
    DisposableEffect(Unit) {
        Log.d("TxtScreen", "[SetupTtsCallbacks] 设置 TTS 回调")
        ttsModel.setOnRequestSpeechStartListener {
            Log.d("TxtScreen", "[SetupTtsCallbacks] 开始朗读, currentPage=$currentPage")
            val text = viewModel.getPageContent(currentPage)
            if (text.isNotBlank()) {
                Log.d("TxtScreen", "[SetupTtsCallbacks] 朗读文本长度: ${text.length}")
                ttsModel.speak(text, "txt_${currentPage}")
            } else {
                Log.w("TxtScreen", "[SetupTtsCallbacks] 页面内容为空，无法朗读")
            }
        }
        
        ttsModel.setOnSpeechDoneListener { utteranceId ->
            Log.d("TxtScreen", "[SetupTtsCallbacks] 朗读完成: utteranceId=$utteranceId")
            val lastPlayedPage = utteranceId?.substringAfter("_")?.toIntOrNull()
            val targetPage = if (lastPlayedPage != null && lastPlayedPage != currentPage) {
                currentPage
            } else {
                currentPage + 1
            }
            
            val totalPages = viewModel.getPagesCount()
            Log.d("TxtScreen", "[SetupTtsCallbacks] 目标页码: $targetPage, 总页数: $totalPages")
            if (targetPage in 0 until totalPages) {
                scope.launch {
                    if (targetPage != currentPage) {
                        Log.d("TxtScreen", "[SetupTtsCallbacks] 翻页: $currentPage -> $targetPage")
                        viewModel.goToPage(targetPage)
                    }
                    val text = viewModel.getPageContent(targetPage)
                    if (text.isNotBlank()) {
                        Log.d("TxtScreen", "[SetupTtsCallbacks] 继续朗读下一页, 文本长度: ${text.length}")
                        ttsModel.speak(text, "txt_$targetPage")
                    } else {
                        Log.w("TxtScreen", "[SetupTtsCallbacks] 下一页内容为空")
                    }
                }
            } else {
                Log.d("TxtScreen", "[SetupTtsCallbacks] 已到达最后一页")
            }
        }

        onDispose {
            Log.d("TxtScreen", "[SetupTtsCallbacks] 清除 TTS 回调")
            ttsModel.clearCallbacks()
        }
    }
}

/**
 * ✅ 跳转对话框
 */
@Composable
private fun JumpDialogs(
    isShowJumpToPageDialog: Boolean,
    isSwipeLayout: Boolean,
    currentPage: Int,
    viewModel: TxtViewModel,
    onDismiss: () -> Unit,
    scope: CoroutineScope
) {
    if (!isShowJumpToPageDialog) return
    
    if (isSwipeLayout) {
        JumpToPageDialog(
            currentPage = currentPage,
            totalPages = viewModel.getPagesCount(),
            onDismiss = onDismiss,
            onConfirm = {
                scope.launch { viewModel.goToPage(it) }
                onDismiss()
            }
        )
    } else {
        JumpToProgressDialog(
            progress = viewModel.getProgress(),
            onDismiss = onDismiss,
            onConfirm = {
                scope.launch { viewModel.goToPage(viewModel.findPageByProgress(it)) }
                onDismiss()
            }
        )
    }
}

/**
 * ✅ 布局设置对话框
 */
@Composable
private fun LayoutSettingDialogs(
    isShowLayoutSettingDialog: Boolean,
    isSwipeLayout: Boolean,
    viewModel: TxtViewModel,
    onDismiss: () -> Unit
) {
    if (!isShowLayoutSettingDialog) return

    val readingState by viewModel.readingState.collectAsStateWithLifecycle()
    LayoutSettingDialog(
        isSwipeLayout = isSwipeLayout,
        onSwipeLayoutChange = { newValue ->
            readingState?.let { currentState ->
                val newState = currentState.copy(isSwipeLayout = newValue)
                viewModel.saveProgress(newState)
            }
        },
        onDismiss = onDismiss
    )
}

/**
 * ✅ 搜索面板
 */
@Composable
private fun SearchPanel(
    fileInfo: FileInfo,
    isShowSearchDialog: Boolean,
    currentPage: Int,
    currentKeyword: String,
    viewModel: TxtViewModel,
    onVisibleChange: (Boolean) -> Unit,
    onKeywordChange: (String) -> Unit,
    scope: CoroutineScope
) {
    SlideInSearchPanel(
        visible = isShowSearchDialog,
        onVisibleChange = { onVisibleChange(it) },
        onClose = { onVisibleChange(false) },
        getCurrentPosition = { currentPage },
        onResultClick = {
            scope.launch {
                try {
                    val pageIndex = viewModel.findPageByCharOffset((it as SearchData.TxtSearchResult).charOffset)
                    viewModel.goToPage(pageIndex)
                } catch (e: Exception) {
                    Log.e("TxtScreen", "跳转页失败: ${e.message}")
                }
            }
        },
        onKeywordChange = onKeywordChange,
        searchExecutor = { keyword -> viewModel.search(keyword) },
        fileUri = fileInfo.uri  // ✅ 传递文件 URI，用于隔离搜索历史
    )
}

/**
 * ✅ 页面内容显示组件
 * 
 * 使用 SelectionContainer 包裹以支持文本选择
 *
 * @param pageAnnotatedContent 带样式的页面文本
 * @param fontSize 字体大小（sp）
 * @param lineHeight 行高（sp）
 * @param contentPadding 内容边距
 */
@Composable
fun PageContent(
    pageAnnotatedContent: AnnotatedString,
    fontSize: Int,
    lineHeight: Int,
    contentPadding: PaddingValues = PaddingValues()
) {
    SelectionContainer {
        Text(
            text = pageAnnotatedContent,
            fontSize = fontSize.sp,
            lineHeight = lineHeight.sp,
            modifier = Modifier
                .padding(contentPadding)

        )
    }
}

/**
 * ✅ 文本高亮函数
 * 
 * 在文本中查找关键词并添加高亮样式
 *
 * @param content 原始文本
 * @param keyword 要高亮的关键词
 * @return 带高亮样式的 AnnotatedString
 */
@Composable
fun highLightText(content: String, keyword: String): AnnotatedString {
    return if (keyword.isNotBlank()) {
        buildAnnotatedString {
            append(content)

            // 查找所有匹配的关键词并添加高亮样式
            var startIndex = 0
            while (startIndex <= content.length - keyword.length) {
                val matchIndex = content.indexOf(keyword, startIndex, ignoreCase = true)
                if (matchIndex == -1) break

                // 为匹配的关键词添加黄色背景高亮
                addStyle(
                    style = SpanStyle(
                        background = MaterialTheme.colorScheme.tertiary,
                        color = MaterialTheme.colorScheme.onTertiary
                    ),
                    start = matchIndex,
                    end = matchIndex + keyword.length
                )

                startIndex = matchIndex + keyword.length
            }
        }
    } else {
        buildAnnotatedString {
            append(content)
        }
    }
}

/**
 * ✅ TXT 布局包装器
 * 
 * 根据 isSwipeLayout 参数选择滑动或滚动布局
 *
 * @param modifier 修饰符
 * @param isSwipeLayout 是否为滑动布局
 * @param viewModel TXT ViewModel
 * @param pageContent 页面内容 Composable
 */
@Composable
fun TxtLayoutWrapper(
    modifier: Modifier = Modifier,
    isSwipeLayout: Boolean,
    viewModel: TxtViewModel,
    pageContent: @Composable (Int) -> Unit
) {
    val readingState by viewModel.readingState.collectAsStateWithLifecycle()
    val totalPages = viewModel.getPagesCount()
    val initialPage by remember(isSwipeLayout,totalPages){
        mutableIntStateOf(viewModel.findPageByCharOffset(readingState?.charOffset ?: 0))
    }
    Log.d("TxtScreen", "[TxtLayoutWrapper] 初始页: $initialPage")
    if (isSwipeLayout) {
        TxtSwipeLayout(
            modifier = modifier,
            initialPage = initialPage,
            viewModel = viewModel,
            itemContent = pageContent
        )
    } else {
        TxtScrollLayout(
            modifier = modifier,
            initialPage = initialPage,
            viewModel = viewModel,
            itemContent = pageContent
        )
    }
}

/**
 * ✅ TXT 滑动布局组件
 * 
 * 使用 HorizontalPager 实现水平翻页效果,支持手势滑动切换页面。
 *
 * @param modifier 修饰符
 * @param initialPage 初始页码
 * @param viewModel TXT ViewModel
 * @param itemContent 页面内容 Composable
 */
@Composable
fun TxtSwipeLayout(
    modifier: Modifier = Modifier,
    initialPage: Int,
    viewModel: TxtViewModel,
    itemContent: @Composable (Int) -> Unit,
){
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(
            0,
            viewModel.getPagesCount() - 1
        ),
        pageCount = { viewModel.getPagesCount() }
    )

    DisposableEffect(Unit) {
        viewModel.setOnGoToPageListener {
            scope.launch {
                Log.d("TxtSwipeLayout", "[TxtSwipeLayout] 跳转页: $it")
                pagerState.scrollToPage(it)
            }
        }
        onDispose {
            viewModel.setOnGoToPageListener(null)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        Log.d("TxtSwipeLayout", "[TxtSwipeLayout] PageChanged: ${pagerState.currentPage}")
        viewModel.onEvent(TxtEvent.OnPageChanged(pagerState.currentPage))
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 10
    ) { page ->

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            itemContent(page)
        }
    }
}

/**
 * ✅ TXT 滚动布局组件
 * 
 * 使用 LazyColumn 实现垂直滚动效果,支持快速滚动和位置记忆。
 * 通过 snapshotFlow 监听滚动位置变化,自动更新当前页码。
 *
 * @param modifier 修饰符
 * @param initialPage 初始页码
 * @param viewModel TXT ViewModel
 * @param itemContent 页面内容 Composable
 */
@Composable
fun TxtScrollLayout(
    modifier: Modifier = Modifier,
    initialPage: Int,
    viewModel: TxtViewModel,
    itemContent: @Composable (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val totalPages = viewModel.getPagesCount()
    // 创建 LazyListState 用于控制滚动
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage.coerceIn(0, totalPages - 1),
        initialFirstVisibleItemScrollOffset = 0
    )

    DisposableEffect(Unit) {
        viewModel.setOnGoToPageListener {
            scope.launch {
                Log.d("TxtScrollLayout", "[TxtScrollLayout] 跳转页: $it")
                lazyListState.scrollToItem(it)
            }
        }
        onDispose {
            viewModel.setOnGoToPageListener(null)
        }
    }

    // 使用 snapshotFlow 监听滚动位置变化
    LaunchedEffect(lazyListState) {
        snapshotFlow {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo

            if (visibleItems.isNotEmpty()) {
                val viewportCenter = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.height / 2

                val centerItem = visibleItems.minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    kotlin.math.abs(itemCenter - viewportCenter)
                }

                centerItem?.index
            } else {
                null
            }
        }
        .filterNotNull()
        .distinctUntilChanged()
        .collect { centerIndex ->
            Log.d("TxtScrollLayout", "[TxtScrollLayout] PageChanged: $centerIndex")
            viewModel.onEvent(TxtEvent.OnPageChanged(centerIndex))
        }
    }

    // 使用 LazyColumn 实现垂直滚动布局，提升性能
    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 0.dp)
    ) {
        items(
            count = totalPages,
            key = { index -> "txt_page_$index" }
        ) { page ->

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                itemContent(page)
            }
        }
    }
}