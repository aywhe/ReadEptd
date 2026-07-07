package com.example.readeptd.books.pdf

import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.data.FileInfo
import com.example.readeptd.speech.TtsViewModel
import kotlinx.coroutines.launch
import com.example.readeptd.books.BookUiState
import com.example.readeptd.activity.ContentUiEvent
import com.example.readeptd.utils.JumpToPageDialog
import com.example.readeptd.activity.ContentViewModel
import com.example.readeptd.bookmark.BookmarkData
import com.example.readeptd.bookmark.BookmarkDialog
import com.example.readeptd.bookmark.BookmarkHint
import com.example.readeptd.bookmark.BookmarkListPanel
import com.example.readeptd.bookmark.BookmarkViewModel
import com.example.readeptd.search.SearchData
import com.example.readeptd.search.SlideInSearchPanel
import com.example.readeptd.utils.LayoutSettingDialog
import com.example.readeptd.utils.SlideHint
import kotlinx.coroutines.delay

@Composable
fun PdfScreen(
    fileInfo: FileInfo,
    contentViewModel: ContentViewModel,
    ttsModel: TtsViewModel,
    bookmarkViewModel: BookmarkViewModel = viewModel(),
    modifier: Modifier = Modifier,
    viewModel: PdfViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(fileInfo.uri) {
        viewModel.prepareBookFile(fileInfo.uri.toUri(), fileInfo.fileName)
        bookmarkViewModel.prepareBookFile(fileInfo.uri)
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is BookUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "加载中...",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            is BookUiState.Ready -> {
                PdfLazyViewer(
                    fileInfo = fileInfo,
                    contentViewModel = contentViewModel,
                    viewModel = viewModel,
                    ttsModel = ttsModel,
                    bookmarkViewModel = bookmarkViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }

            is BookUiState.Error -> {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfLazyViewer(
    fileInfo: FileInfo,
    contentViewModel: ContentViewModel,
    viewModel: PdfViewModel,
    ttsModel: TtsViewModel,
    bookmarkViewModel: BookmarkViewModel,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    val totalPages by viewModel.totalPages.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsState()
    val configuration = LocalConfiguration.current
    val config by contentViewModel.configData.collectAsStateWithLifecycle()
    
    // ✅ 使用 StateFlow 获取阅读状态
    val readingState by viewModel.readingState.collectAsStateWithLifecycle()
    
    // ✅ 从 readingState 中提取 isSwipeLayout，默认为 true
    val isSwipeLayout = readingState?.isSwipeLayout ?: true
    val isRtl = readingState?.isRtl ?: false

    // ✅ 根据当前屏幕方向获取对应的缩放状态
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    DisposableEffect(Unit) {
        // 初始化 PDF 渲染器
        viewModel.initializeRenderer()
        onDispose {
            viewModel.cleanupRenderer()
            Log.d("PdfLazyViewer", "资源已释放")
        }
    }
    
    val pdfState by viewModel.pdfState.collectAsStateWithLifecycle()
    
    when (pdfState) {
        is PdfState.Loading -> {
            // 显示加载状态
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "加载中...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        
        is PdfState.Ready -> {
            var containerSize by remember { mutableStateOf(IntSize.Zero) }
            var isShowJumpToPageDialog by remember { mutableStateOf(false) }
            var isShowLayoutSettingDialog by remember { mutableStateOf(false) }
            var isShowSearchDialog by remember { mutableStateOf(false) }
            var showNoTextHint by remember { mutableStateOf(false) }
            var isShowBookmarkDialog by remember { mutableStateOf(false) }
            var isShowBookmarkListPanel by remember { mutableStateOf(false) }
            val currentBookmarkDataList by bookmarkViewModel.findInPosition(
                BookmarkData.Pdf(
                    bookId = fileInfo.uri,
                    pageNumber = currentPage,
                    note = "[#${currentPage + 1}]"
                )
            ).collectAsStateWithLifecycle(emptyList())

            LaunchedEffect(currentBookmarkDataList, currentBookmarkDataList.size) {
                contentViewModel.updateBookmarkState(currentBookmarkDataList.isNotEmpty())
            }


            LaunchedEffect(currentPage) {
                Log.d("PdfLazyViewer", "当前页: $currentPage")
                contentViewModel.updateProgressText("${currentPage + 1}/$totalPages")
            }
            DisposableEffect(Unit) {
                contentViewModel.setOnClickProgressInfoCallback { progressText ->
                    if (totalPages > 0) {
                        isShowJumpToPageDialog = true
                    }
                }
                contentViewModel.setOnLongPressProgressInfoCallback { progressText ->
                    isShowLayoutSettingDialog = true
                }
                contentViewModel.setOnClickSearchButtonCallback {
                    isShowSearchDialog = !isShowSearchDialog
                }
                contentViewModel.setOnClickBookmarkCallback { isBookmarked ->
                    isShowBookmarkDialog = true
                }
                contentViewModel.setOnLongPressBookmarkCallback {
                    isShowBookmarkListPanel = true
                }

                ttsModel.setOnRequestSpeechStartListener {
                    val text = viewModel.getPageText(currentPage)
                    if (!text.isNullOrBlank()) {
                        ttsModel.speak(text, "pdf_${currentPage}")
                    } else {
                        showNoTextHint = true
                    }
                }

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

                    if (targetPage in 0 until totalPages) {
                        scope.launch {
                            // 如果需要翻页（目标页不是当前页），先滚动
                            if (targetPage != currentPage) {
                                viewModel.goToPage(targetPage)
                            }

                            // 朗读目标页
                            val text = viewModel.getPageText(targetPage)
                            if (!text.isNullOrBlank()) {
                                ttsModel.speak(text, "pdf_$targetPage")
                            }
                        }
                    }
                }

                onDispose {
                    ttsModel.clearCallbacks()
                    contentViewModel.setOnClickProgressInfoCallback(null)
                    contentViewModel.setOnLongPressProgressInfoCallback(null)
                    contentViewModel.setOnClickSearchButtonCallback(null)
                    contentViewModel.setOnClickBookmarkCallback(null)
                    contentViewModel.setOnLongPressBookmarkCallback(null)
                }
            }
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                LaunchedEffect( isSwipeLayout, isRtl) {
                    scale = 1f
                    offset = Offset.Zero
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            containerSize = coordinates.size
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    Log.d("PdfLazyViewer", "双击屏幕，切换全屏")
                                    contentViewModel.onEvent(ContentUiEvent.OnDoubleClickScreen)
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            delay(100)
                            detectTransformGestures(
                                onGesture = { centroid, pan, zoom, rotation ->
                                    scale *= zoom
                                    scale = scale.coerceIn(0.5f, 5f)
                                    offset += pan
                                }
                            )
                        }
                ) {
                    if (isSwipeLayout) {
                        PdfSwipeLayout(
                            contentViewModel = contentViewModel,
                            viewModel = viewModel,
                            isRtl = isRtl,
                            scale = scale,
                            offset = offset
                        )
                    } else {
                        PdfScrollLayout(
                            contentViewModel = contentViewModel,
                            viewModel = viewModel,
                            isRtl = isRtl,
                            scale = scale,
                            offset = offset
                        )
                    }
                }
                if (isShowJumpToPageDialog) {
                    JumpToPageDialog(
                        currentPage = currentPage,
                        totalPages = totalPages,
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
                }
                if (isShowLayoutSettingDialog) {
                    LayoutSettingDialog(
                        isSwipeLayout = isSwipeLayout,
                        onSwipeLayoutChange = { newValue ->
                            // ✅ 直接从 readingState 创建新状态并保存
                            readingState?.let { currentState ->
                                val newState = currentState.copy(isSwipeLayout = newValue)
                                viewModel.saveProgress(newState)
                            }
                        },
                        isRtl = isRtl,
                        onRtlChange = { newValue ->
                            readingState?.let { currentState ->
                                val newState = currentState.copy(isRtl = newValue)
                                viewModel.saveProgress(newState)
                            }
                        },
                        onDismiss = {
                            isShowLayoutSettingDialog = false
                        }
                    )
                }
                SlideInSearchPanel(
                    visible = isShowSearchDialog,
                    onVisibleChange = {
                        isShowSearchDialog = it
                    },
                    searchExecutor = { query ->
                        viewModel.search(query)
                    },
                    getDistanceToResult = {
                        val result = it as SearchData.PdfSearchResult
                        kotlin.math.abs(result.pageIndex - currentPage).toLong()
                    },
                    onResultClick = { result ->
                        scope.launch {
                            viewModel.goToPage((result as SearchData.PdfSearchResult).pageIndex)
                        }
                    },
                    onClose = {
                        isShowSearchDialog = false
                    },
                    fileUri = fileInfo.uri
                )

                LaunchedEffect(showNoTextHint) {
                    if(showNoTextHint) {
                        delay(2000)
                        showNoTextHint = false
                    }
                }

                SlideHint(
                    tips = "没有文本",
                    visible = showNoTextHint,
                    alignment = Alignment.TopStart,
                    padding = PaddingValues(top = 32.dp)
                )

                BookmarkHint(contentViewModel = contentViewModel)

                if(isShowBookmarkListPanel){
                    BookmarkListPanel(
                        viewModel = bookmarkViewModel,
                        onClose = {
                            isShowBookmarkListPanel = false
                        },
                        onBookmarkClick = { bookmarkData ->
                            scope.launch {
                                viewModel.goToPage((bookmarkData as BookmarkData.Pdf).pageNumber)
                            }
                        },
                        currentDistanceToBookmark = {
                            val result = it as BookmarkData.Pdf
                            kotlin.math.abs(result.pageNumber - currentPage).toLong()
                        }
                    )
                }

                if(isShowBookmarkDialog){
                    BookmarkDialog(
                        bookmarkData =
                            if(currentBookmarkDataList.isNotEmpty())
                                currentBookmarkDataList.first()
                            else
                                BookmarkData.Pdf(
                                    bookId = fileInfo.uri,
                                    pageNumber = currentPage,
                                    note = "[#${currentPage + 1}]"
                                ),
                        onDismiss = {
                            isShowBookmarkDialog = false
                        },
                        onConfirm = {
                            isShowBookmarkDialog = false
                        }
                    )
                }
            }
        }
        
        is PdfState.Error -> {
            // 显示错误状态
            val errorMessage = (pdfState as PdfState.Error).message
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "错误",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "加载失败",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PdfPageContent(
    page: Int,
    isSwipeLayout: Boolean = true,
    isRtl: Boolean = false,
    contentViewModel: ContentViewModel,
    viewModel: PdfViewModel,
    scale: Float = 1f,
    offset: Offset = Offset.Zero
){
    val colorMatrix = remember{
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }
    val config by contentViewModel.configData.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val screenWidthDp = configuration.screenWidthDp

    val preloadJob = remember(page) {
        scope.launch {
            viewModel.renderPageAsync(page, keepNeighbourNumber = 2)
        }
    }

//    LaunchedEffect(page) {
//        scope.launch {
//            viewModel.renderPage(page, 2)
//        }
//    }

    val bitmap by viewModel.getPageBitmapState(page).collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "PDF_Page_$page",
                colorFilter = if (config.isNightMode) {
                    ColorFilter.colorMatrix(colorMatrix)
                } else null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isSwipeLayout) {
                            Modifier.graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y)
                        } else {
                            Modifier
                        }
                    )
            )
        } else {
            Log.d("PdfLazyViewer", "页面 $page 的 bmp 为空")
            Box(
                modifier = if(isSwipeLayout) {
                    Modifier.fillMaxSize()
                } else {
                    if(isRtl){
                        Modifier.fillMaxWidth().height(screenHeightDp.dp)
                    }
                    else {
                        Modifier.fillMaxHeight().width(screenWidthDp.dp)
                    }
                },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

@Composable
fun PdfSwipeLayout(
    modifier: Modifier = Modifier,
    contentViewModel: ContentViewModel,
    viewModel: PdfViewModel,
    isRtl: Boolean = false,
    scale: Float,
    offset: Offset,
) {
    val totalPages by viewModel.totalPages.collectAsStateWithLifecycle()
    val initialPage = remember{ viewModel.getInitialPage() }
    val scope = rememberCoroutineScope()


    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { totalPages }
    )
    LaunchedEffect(pagerState.currentPage) {
        Log.d("PdfSwipeLayout", "当前页: ${pagerState.currentPage}")
        viewModel.onEvent(PdfEvent.OnPageChanged(pagerState.currentPage))
        // 使用 LinkedHashMap 实现 LRU 缓存，不需要手动清理过期页面
        //val currentPage = pagerState.currentPage
        //viewModel.cleanupUnusedPages(currentPage)
    }
    LaunchedEffect(Unit) {
        Log.d("PdfSwipeLayout", "设置页面跳转监听")
        viewModel.setOnGoToPageListener {
            scope.launch {
                pagerState.scrollToPage(it)
            }
        }
    }
    HorizontalPager(
        reverseLayout = isRtl,
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        PdfPageContent(
            page = page,
            isSwipeLayout = true,
            isRtl = isRtl,
            contentViewModel = contentViewModel,
            viewModel = viewModel,
            scale = scale,
            offset = offset
        )
    }
}

@Composable
fun PdfScrollLayout(
    modifier: Modifier = Modifier,
    contentViewModel: ContentViewModel,
    viewModel: PdfViewModel,
    isRtl: Boolean = false,
    scale: Float,
    offset: Offset
) {
    val totalPages by viewModel.totalPages.collectAsStateWithLifecycle()
    val initialPage = remember{ viewModel.getInitialPage() }
    val scope = rememberCoroutineScope()

    // 创建 LazyListState 用于控制滚动
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage,
        initialFirstVisibleItemScrollOffset = 0
    )

    // 监听页面跳转请求
    LaunchedEffect(Unit) {
        Log.d("PdfScrollLayout", "设置页面跳转监听")
        viewModel.setOnGoToPageListener { targetPage ->
            scope.launch {
                lazyListState.scrollToItem(targetPage)
            }
        }
    }

    // 监听滚动位置变化，更新当前页码（使用可见区域中间的页码）
    val centerPageIndex by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo

            if (visibleItems.isNotEmpty()) {
                // ✅ 根据滚动方向计算中心位置
                val viewportCenter = if (isRtl) {
                    // 横向滚动：使用宽度
                    layoutInfo.viewportStartOffset + layoutInfo.viewportSize.width / 2
                } else {
                    // 纵向滚动：使用高度
                    layoutInfo.viewportStartOffset + layoutInfo.viewportSize.height / 2
                }

                // 找到最接近视口中心的 item
                val centerItem = visibleItems.minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    kotlin.math.abs(itemCenter - viewportCenter)
                }

                centerItem?.index ?: 0
            } else {
                0
            }
        }
    }

    LaunchedEffect(centerPageIndex) {
        viewModel.onEvent(PdfEvent.OnPageChanged(centerPageIndex))
    }

    // 使用 LazyColumn 实现垂直滚动布局，提升性能
    Box(modifier = modifier.fillMaxSize()
        .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offset.x,
            translationY = offset.y,
        )
    ) {
        val item: @Composable (page:Int)-> Unit ={ page->
            PdfPageContent(
                page = page,
                isSwipeLayout = false,
                isRtl = isRtl,
                contentViewModel = contentViewModel,
                viewModel = viewModel
            )
        }
        if(isRtl) {
            LazyRow(
                reverseLayout =  true,
                state = lazyListState,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize()
            ) {
                items(totalPages,key = { it }) { page ->
                    item(page)
                }
            }
        } else {
            LazyColumn(
                state = lazyListState,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                items(totalPages,key = { it }) { page ->
                    item(page)
                }
            }
        }
    }
}