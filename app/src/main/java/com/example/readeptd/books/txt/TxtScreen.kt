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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.derivedStateOf
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
    var isShowJumpToPageDialog by remember { mutableStateOf(false) }
    var isShowLayoutSettingDialog by remember { mutableStateOf(false) }
    var isShowSearchDialog by remember { mutableStateOf(false) }
    val config by contentViewModel.configData.collectAsStateWithLifecycle()
    
    // ✅ 使用 readingState Flow 获取 isSwipeLayout
    val readingState by viewModel.readingState.collectAsStateWithLifecycle()
    // ✅ 只在 readingState 加载完成后才使用实际值，否则使用默认值（但不触发分页）
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
    // 监听屏幕旋转，恢复重新分页功能
    LaunchedEffect(configuration.orientation) {
        Log.d("TxtScreen", "屏幕方向变化: ${configuration.orientation}")
        viewModel.onEvent(TxtEvent.OnScreenOrientationChanged(configuration.orientation))
    }
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        when (val state = uiState) {
            is BookUiState.Loading -> {
                // 加载中
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

            is BookUiState.Ready -> {
                var lastClickTime by remember { mutableStateOf(0L)}
                var readingStateReady by remember { mutableStateOf(false) }
                // ✅ 只在 readingState 加载完成后才设置分页模式，避免无效分页
                LaunchedEffect(readingState) {
                    if (readingState != null && (!readingStateReady)) {
                        val mode = if (readingState!!.isSwipeLayout) {
                            SplitPagesMode.ByLayoutSize
                        } else {
                            SplitPagesMode.ByCharsCount
                        }
                        readingStateReady = true
                        Log.d("TxtScreen", "readingState 已加载，设置分页模式: $mode")
                        viewModel.setSplitPagesMode(mode)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size ->
                            // ✅ 发送事件到防抖 Flow，而不是直接调用 ViewModel
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
                    } else {
                        // 分页成功
                        val currentPage by viewModel.currentPage.collectAsState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            DisposableEffect(Unit) {
                                contentViewModel.setOnClickProgressInfoCallback { progressText ->
                                    isShowJumpToPageDialog = true
                                }
                                contentViewModel.setOnLongPressProgressInfoCallback { progressText ->
                                    isShowLayoutSettingDialog = true
                                }
                                contentViewModel.setOnClickSearchButtonCallback {
                                    isShowSearchDialog = !isShowSearchDialog
                                }

                                onDispose {
                                    contentViewModel.setOnClickProgressInfoCallback(null)
                                    contentViewModel.setOnLongPressProgressInfoCallback(null)
                                    contentViewModel.setOnClickSearchButtonCallback(null)
                                }
                            }
                            LaunchedEffect(currentPage, isSwipeLayout) {
                                if(isSwipeLayout){
                                    contentViewModel.updateProgressText(
                                        "${currentPage + 1}/${viewModel.getPagesCount()}"
                                    )
                                } else {
                                    contentViewModel.updateProgressText(
                                        "${(viewModel.getProgress() * 100).roundToInt()}%"
                                    )
                                }
                            }

                            DisposableEffect(Unit) {
                                // 设置自动朗读回调
                                // 当 TTS 开始朗读时,获取当前页文本并开始朗读
                                ttsModel.setOnRequestSpeechStartListener {
                                    Log.d("TxtScreen", "开始朗读")
                                    val text = viewModel.getPageContent(currentPage)
                                    if (text.isNotBlank()) {
                                        ttsModel.speak(text, "txt_${currentPage}")
                                    }
                                }
                                // 当 TTS 朗读完成时,自动翻页并朗读下一页
                                ttsModel.setOnSpeechDoneListener { utteranceId ->
                                    val lastPlayedPage = utteranceId?.substringAfter("_")?.toIntOrNull()

                                    // 判断是否需要调整页码：如果用户手动翻页了，从当前页开始朗读
                                    val targetPage = if (lastPlayedPage != null && lastPlayedPage != currentPage) {
                                        // 用户手动翻页，从当前页继续
                                        currentPage
                                    } else {
                                        // 正常顺序播放，朗读下一页
                                        currentPage + 1
                                    }
                                    
                                    val totalPages = viewModel.getPagesCount()
                                    if (targetPage in 0 until totalPages) {
                                        scope.launch {
                                            // 如果需要翻页（目标页不是当前页），先滚动
                                            if (targetPage != currentPage) {
                                                viewModel.goToPage(targetPage)
                                            }

                                            // 朗读目标页
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

                            var currentKeyword by remember { mutableStateOf("") }
                            TxtLayoutWrapper(
                                isSwipeLayout = isSwipeLayout,
                                contentViewModel = contentViewModel,
                                viewModel = viewModel
                            ){ page ->
                                Log.d("TxtScreen", "当前页: $page")
                                // 注意：TextChunk保留不同TextChunk之间的换行信息，但是显示的时候，每个页面独立，不需要拼接，所以删除末尾的换行信息
                                val pageContent = viewModel.getPageContent(page).trimEnd()
                                val pageAnnotatedContent =
                                    if (isShowSearchDialog) highLightText(
                                        pageContent,
                                        currentKeyword
                                    )
                                    else highLightText(pageContent, "")
                                PageContent(
                                    pageAnnotatedContent = pageAnnotatedContent,
                                    fontSize = viewModel.currentFontSizeSp,
                                    lineHeight = viewModel.currentLineHeightSp,
                                    contentPadding = contentPadding
                                )
                            }
                            if(isShowJumpToPageDialog){
                                if(isSwipeLayout) {
                                    JumpToPageDialog(
                                        currentPage = currentPage,
                                        totalPages = viewModel.getPagesCount(),
                                        onDismiss = {
                                            isShowJumpToPageDialog = false
                                        },
                                        onConfirm = {
                                            scope.launch {
                                                viewModel.goToPage(it)
                                            }
                                            isShowJumpToPageDialog = false
                                        }
                                    )
                                } else {
                                    JumpToProgressDialog(
                                        progress = viewModel.getProgress(),
                                        onDismiss = {
                                            isShowJumpToPageDialog = false
                                        },
                                        onConfirm = {
                                            scope.launch {
                                                viewModel.goToPage(viewModel.findPageByProgress(it))
                                            }
                                            isShowJumpToPageDialog = false
                                        }
                                    )
                                }
                            }
                            if(isShowLayoutSettingDialog){
                                LayoutSettingDialog(
                                    isSwipeLayout = isSwipeLayout,
                                    onSwipeLayoutChange = { newValue ->
                                        // ✅ 直接从 readingState 创建新状态并保存
                                        viewModel.readingState.value?.let { currentState ->
                                            val newState = currentState.copy(isSwipeLayout = newValue)
                                            viewModel.saveProgress(newState)
                                        }
                                    },
                                    onDismiss = {
                                        isShowLayoutSettingDialog = false
                                    }
                                )
                            }
                            SlideInSearchPanel(
                                initialVisible = isShowSearchDialog,
                                onClose =  {isShowSearchDialog =  false},
                                getCurrentPosition = {currentPage},
                                onResultClick = {
                                    scope.launch {
                                        try{
                                            val pageIndex = viewModel.findPageByCharOffset((it as SearchData.TxtSearchResult).charOffset)
                                            viewModel.goToPage(pageIndex)
                                        } catch (e: Exception){
                                            Log.e("TxtScreen", "跳转页失败: ${e.message}")
                                        }
                                    }
                                },
                                onKeywordChange = {currentKeyword = it},
                                searchExecutor = {keyword -> viewModel.search(keyword) }
                            )
                        }
                    }
                }
            }

            is BookUiState.Error -> {
                // 显示错误
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
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