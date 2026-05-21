package com.example.readeptd.books.txt

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(FlowPreview::class)
@Composable
fun TxtScreen(
    fileInfo: FileInfo,
    contentViewModel: ContentViewModel,
    ttsModel: TtsViewModel,
    modifier: Modifier = Modifier,
    viewModel: TxtViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPagesReady by viewModel.isPagesReady.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // ✅ 对话框状态管理
    var isShowJumpToPageDialog by remember { mutableStateOf(false) }
    var isShowLayoutSettingDialog by remember { mutableStateOf(false) }
    var isShowSearchDialog by remember { mutableStateOf(false) }
    
    val config by contentViewModel.configData.collectAsStateWithLifecycle()
    val readingState by viewModel.readingState.collectAsStateWithLifecycle()
    val isSwipeLayout = readingState?.isSwipeLayout ?: true

    // 定义 padding（UI 层决定）
    val leftPaddingDp = 16
    val rightPaddingDp = 16
    val topPaddingDp = if(isSwipeLayout) 16 else 0
    val bottomPaddingDp = if(isSwipeLayout) 16 else 0
    val contentPadding = PaddingValues(
        start = leftPaddingDp.dp,
        end = rightPaddingDp.dp,
        top = topPaddingDp.dp,
        bottom = bottomPaddingDp.dp
    )

    // ✅ 创建防抖的 SharedFlow，用于减少 onSizeChanged 调用频率
    val sizeChangeFlow = remember {
        MutableSharedFlow<TxtEvent.OnViewMetricsChanged>(
            replay = 0,
            extraBufferCapacity = 1
        )
    }

    // ✅ 监听防抖后的尺寸变化事件
    LaunchedEffect(Unit) {
        sizeChangeFlow
            .debounce(500) // 500ms 防抖，等待布局稳定
            .collect { event ->
                viewModel.onEvent(event)
            }
    }

    // 准备 TXT 文件
    LaunchedEffect(fileInfo.uri) {
        viewModel.prepareBookFile(fileInfo.uri.toUri(), fileInfo.fileName)
    }
    
    // 监听屏幕旋转
    LaunchedEffect(configuration.orientation) {
        Log.d("TxtScreen", "屏幕方向变化: ${configuration.orientation}")
        viewModel.onEvent(TxtEvent.OnScreenOrientationChanged(configuration.orientation))
    }
    
    // 监听分页模式切换
    LaunchedEffect(isSwipeLayout) {
        Log.d("TxtScreen", "切换分页模式: $isSwipeLayout")
        if (isSwipeLayout) {
            viewModel.setSplitPagesMode(SplitPagesMode.ByLayoutSize)
        } else {
            viewModel.setSplitPagesMode(SplitPagesMode.ByCharsCount)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is BookUiState.Loading -> LoadingState()
            is BookUiState.Ready -> ReadyState(
                isPagesReady = isPagesReady,
                isSwipeLayout = isSwipeLayout,
                contentViewModel = contentViewModel,
                viewModel = viewModel,
                ttsModel = ttsModel,
                contentPadding = contentPadding,
                sizeChangeFlow = sizeChangeFlow,
                leftPaddingDp = leftPaddingDp,
                rightPaddingDp = rightPaddingDp,
                topPaddingDp = topPaddingDp,
                bottomPaddingDp = bottomPaddingDp,
                isShowJumpToPageDialog = isShowJumpToPageDialog,
                isShowLayoutSettingDialog = isShowLayoutSettingDialog,
                isShowSearchDialog = isShowSearchDialog,
                onJumpToPageDialogChange = { isShowJumpToPageDialog = it },
                onLayoutSettingDialogChange = { isShowLayoutSettingDialog = it },
                onSearchDialogChange = { isShowSearchDialog = it },
                readingState = readingState,
                scope = scope
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
@Composable
private fun ReadyState(
    isPagesReady: Boolean,
    isSwipeLayout: Boolean,
    contentViewModel: ContentViewModel,
    viewModel: TxtViewModel,
    ttsModel: TtsViewModel,
    contentPadding: PaddingValues,
    sizeChangeFlow: MutableSharedFlow<TxtEvent.OnViewMetricsChanged>,
    leftPaddingDp: Int,
    rightPaddingDp: Int,
    topPaddingDp: Int,
    bottomPaddingDp: Int,
    isShowJumpToPageDialog: Boolean,
    isShowLayoutSettingDialog: Boolean,
    isShowSearchDialog: Boolean,
    onJumpToPageDialogChange: (Boolean) -> Unit,
    onLayoutSettingDialogChange: (Boolean) -> Unit,
    onSearchDialogChange: (Boolean) -> Unit,
    readingState: ReadingState.Txt?,
    scope: CoroutineScope
) {
    var lastClickTime by remember { mutableStateOf(0L) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                scope.launch {
                    sizeChangeFlow.emit(
                        TxtEvent.OnViewMetricsChanged(
                            size = size,
                            leftPaddingDp = leftPaddingDp,
                            rightPaddingDp = rightPaddingDp,
                            topPaddingDp = topPaddingDp,
                            bottomPaddingDp = bottomPaddingDp
                        )
                    )
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
        if (!isPagesReady) {
            PagingState()
        } else {
            ReaderContent(
                isSwipeLayout = isSwipeLayout,
                contentViewModel = contentViewModel,
                viewModel = viewModel,
                ttsModel = ttsModel,
                contentPadding = contentPadding,
                isShowJumpToPageDialog = isShowJumpToPageDialog,
                isShowLayoutSettingDialog = isShowLayoutSettingDialog,
                isShowSearchDialog = isShowSearchDialog,
                onJumpToPageDialogChange = onJumpToPageDialogChange,
                onLayoutSettingDialogChange = onLayoutSettingDialogChange,
                onSearchDialogChange = onSearchDialogChange,
                readingState = readingState,
                scope = scope
            )
        }
    }
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
@Composable
private fun ReaderContent(
    isSwipeLayout: Boolean,
    contentViewModel: ContentViewModel,
    viewModel: TxtViewModel,
    ttsModel: TtsViewModel,
    contentPadding: PaddingValues,
    isShowJumpToPageDialog: Boolean,
    isShowLayoutSettingDialog: Boolean,
    isShowSearchDialog: Boolean,
    onJumpToPageDialogChange: (Boolean) -> Unit,
    onLayoutSettingDialogChange: (Boolean) -> Unit,
    onSearchDialogChange: (Boolean) -> Unit,
    readingState: ReadingState.Txt?,
    scope: CoroutineScope
) {
    val currentPage by viewModel.currentPage.collectAsState()
    var currentKeyword by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        // ✅ 设置回调
        SetupCallbacks(
            contentViewModel = contentViewModel,
            onJumpToPageDialogChange = onJumpToPageDialogChange,
            onLayoutSettingDialogChange = onLayoutSettingDialogChange,
            onSearchDialogChange = onSearchDialogChange
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
            currentPage = currentPage,
            viewModel = viewModel,
            ttsModel = ttsModel,
            scope = scope
        )
        
        // ✅ 阅读内容
        TxtLayoutWrapper(
            isSwipeLayout = isSwipeLayout,
            contentViewModel = contentViewModel,
            viewModel = viewModel
        ) { page ->
            Log.d("TxtScreen", "触发页面: $page")
            val pageContent = viewModel.getPageContent(page).trimEnd()
            val pageAnnotatedContent = highLightText(pageContent, currentKeyword)
            PageContent(
                pageAnnotatedContent = pageAnnotatedContent,
                fontSize = viewModel.currentFontSizeSp,
                lineHeight = viewModel.currentLineHeightSp,
                contentPadding = contentPadding
            )
        }
        
        // ✅ 对话框
        JumpDialogs(
            isShowJumpToPageDialog = isShowJumpToPageDialog,
            isSwipeLayout = isSwipeLayout,
            currentPage = currentPage,
            viewModel = viewModel,
            onDismiss = { onJumpToPageDialogChange(false) },
            scope = scope
        )
        
        LayoutSettingDialogs(
            isShowLayoutSettingDialog = isShowLayoutSettingDialog,
            isSwipeLayout = isSwipeLayout,
            readingState = readingState,
            viewModel = viewModel,
            onDismiss = { onLayoutSettingDialogChange(false) }
        )
        
        SearchPanel(
            isShowSearchDialog = isShowSearchDialog,
            currentPage = currentPage,
            currentKeyword = currentKeyword,
            viewModel = viewModel,
            onSearchDialogChange = onSearchDialogChange,
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
    onJumpToPageDialogChange: (Boolean) -> Unit,
    onLayoutSettingDialogChange: (Boolean) -> Unit,
    onSearchDialogChange: (Boolean) -> Unit
) {
    DisposableEffect(Unit) {
        contentViewModel.setOnClickProgressInfoCallback { onJumpToPageDialogChange(true) }
        contentViewModel.setOnLongPressProgressInfoCallback { onLayoutSettingDialogChange(true) }
        contentViewModel.setOnClickSearchButtonCallback { onSearchDialogChange(true) }

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
        if (isSwipeLayout) {
            contentViewModel.updateProgressText("${currentPage + 1}/${viewModel.getPagesCount()}")
        } else {
            contentViewModel.updateProgressText("${(viewModel.getProgress() * 100).roundToInt()}%")
        }
    }
}

/**
 * ✅ 设置 TTS 回调
 */
@Composable
private fun SetupTtsCallbacks(
    currentPage: Int,
    viewModel: TxtViewModel,
    ttsModel: TtsViewModel,
    scope: CoroutineScope
) {
    DisposableEffect(Unit) {
        ttsModel.setOnRequestSpeechStartListener {
            Log.d("TxtScreen", "开始朗读")
            val text = viewModel.getPageContent(currentPage)
            if (text.isNotBlank()) {
                ttsModel.speak(text, "txt_${currentPage}")
            }
        }
        
        ttsModel.setOnSpeechDoneListener { utteranceId ->
            val lastPlayedPage = utteranceId?.substringAfter("_")?.toIntOrNull()
            val targetPage = if (lastPlayedPage != null && lastPlayedPage != currentPage) {
                currentPage
            } else {
                currentPage + 1
            }
            
            val totalPages = viewModel.getPagesCount()
            if (targetPage in 0 until totalPages) {
                scope.launch {
                    if (targetPage != currentPage) {
                        viewModel.goToPage(targetPage)
                    }
                    val text = viewModel.getPageContent(targetPage)
                    if (text.isNotBlank()) {
                        ttsModel.speak(text, "txt_$targetPage")
                    }
                }
            }
        }

        onDispose {
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
    readingState: ReadingState.Txt?,
    viewModel: TxtViewModel,
    onDismiss: () -> Unit
) {
    if (!isShowLayoutSettingDialog) return
    
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
    isShowSearchDialog: Boolean,
    currentPage: Int,
    currentKeyword: String,
    viewModel: TxtViewModel,
    onSearchDialogChange: (Boolean) -> Unit,
    onKeywordChange: (String) -> Unit,
    scope: CoroutineScope
) {
    SlideInSearchPanel(
        visible = isShowSearchDialog,
        onVisibleChange = onSearchDialogChange,
        onClose = { onSearchDialogChange(false) },
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
        fileUri = "" // TODO: 需要传递文件 URI
    )
}

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

@Composable
fun TxtLayoutWrapper(
    modifier: Modifier = Modifier,
    isSwipeLayout: Boolean,
    contentViewModel: ContentViewModel,
    viewModel: TxtViewModel,
    pageContent: @Composable (Int) -> Unit
    ) {
        if (isSwipeLayout) {
            TxtSwipeLayout(
                modifier = modifier,
                viewModel = viewModel,
                itemContent = pageContent
            )
        } else {
            TxtScrollLayout(
                modifier = modifier,
                viewModel = viewModel,
                itemContent = pageContent
            )
        }
}

@Composable
fun TxtSwipeLayout(
    modifier: Modifier = Modifier,
    viewModel: TxtViewModel,
    itemContent: @Composable (Int) -> Unit,
){
    val readingState by viewModel.readingState.collectAsStateWithLifecycle()
    val initialPage = viewModel.findPageByCharOffset(readingState?.charOffset ?: 0)
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(
            0,
            viewModel.getPagesCount() - 1
        ),
        pageCount = { viewModel.getPagesCount() }
    )

    LaunchedEffect(Unit) {
        viewModel.setOnGoToPageListener {
            scope.launch {
                pagerState.scrollToPage(it)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        Log.d("TxtSwipeLayout", "TxtSwipeLayout PageChanged: ${pagerState.currentPage}")
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

@Composable
fun TxtScrollLayout(
    modifier: Modifier = Modifier,
    viewModel: TxtViewModel,
    itemContent: @Composable (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val totalPages = viewModel.getPagesCount()
    val readingState by viewModel.readingState.collectAsStateWithLifecycle()
    val initialPage = viewModel.findPageByCharOffset(readingState?.charOffset ?: 0)
    // 创建 LazyListState 用于控制滚动
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage.coerceIn(0, totalPages - 1),
        initialFirstVisibleItemScrollOffset = 0
    )

    LaunchedEffect(Unit) {
        viewModel.setOnGoToPageListener {
            scope.launch {
                lazyListState.scrollToItem(it)
            }
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
            Log.d("TxtScrollLayout", "TxtScrollLayout PageChanged: $centerIndex")
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