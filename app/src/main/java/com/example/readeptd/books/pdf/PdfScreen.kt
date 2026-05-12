package com.example.readeptd.books.pdf

import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.data.FileInfo
import com.example.readeptd.speech.TtsViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import com.example.readeptd.books.BookUiState
import com.example.readeptd.activity.ContentUiEvent
import com.example.readeptd.utils.JumpToPageDialog
import com.example.readeptd.activity.ContentViewModel
import com.example.readeptd.data.AppMemoryStore
import com.example.readeptd.search.SearchData
import com.example.readeptd.search.SlideInSearchPanel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce

@Composable
fun PdfScreen(
    fileInfo: FileInfo,
    contentViewModel: ContentViewModel,
    ttsModel: TtsViewModel,
    modifier: Modifier = Modifier,
    viewModel: PdfViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(fileInfo.uri) {
        viewModel.prepareBookFile(fileInfo.uri.toUri(), fileInfo.fileName)
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
                    filePath = state.tempFilePath,
                    contentViewModel = contentViewModel,
                    viewModel = viewModel,
                    ttsModel = ttsModel,
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
    filePath: String,
    contentViewModel: ContentViewModel,
    viewModel: PdfViewModel,
    ttsModel: TtsViewModel,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isShowJumpToPageDialog by remember { mutableStateOf(false) }

    val totalPages by viewModel.totalPages.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsState()
    val configuration = LocalConfiguration.current

    // ✅ 根据当前屏幕方向获取对应的缩放状态
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    DisposableEffect(filePath) {
        // 初始化 PDF 渲染器
        viewModel.initializeRenderer(filePath)
        onDispose {
            viewModel.cleanupRenderer()
            Log.d("PdfLazyViewer", "资源已释放")
        }
    }

    if (totalPages > 0) {
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        var isShowSearchDialog by remember { mutableStateOf(false) }

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
            contentViewModel.setOnClickSearchButtonCallback {
                isShowSearchDialog = !isShowSearchDialog
            }

            ttsModel.setOnRequestSpeechStartListener {
                val text = viewModel.getPageText(currentPage)
                if (!text.isNullOrBlank()) {
                    ttsModel.speak(text, "pdf_${currentPage}")
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
            }
        }
        Box(modifier = modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
        ) {
            // ✅ 从 AppMemoryStore 获取当前文件的缩放信息（包含横竖屏两种状态）
            val fileUri = viewModel.getCurrentState()?.uri ?: ""

            val pdfZoomInfo by AppMemoryStore.pdfZoomInfoStateFlow(fileUri).collectAsStateWithLifecycle()
            // ✅ 使用 rememberSaveable 或者直接用 mutableStateOf，初始值从 AppMemoryStore 获取
            // 注意：这里的关键是 LaunchedEffect 会在 pdfZoomInfo 变化时同步更新
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            // ✅ 【关键】监听 pdfZoomInfo 和 isLandscape 的变化，同步更新本地状态
            // 这会在以下情况触发：
            // 1. 初次加载时，pdfZoomInfo 从 AppMemoryStore 获取数据
            // 2. 离开再返回时，pdfZoomInfo 更新为保存的值
            // 3. 屏幕方向改变时，isLandscape 变化
            LaunchedEffect(pdfZoomInfo, isLandscape) {
                val newScale = pdfZoomInfo.getZoom(isLandscape)
                val newOffset = pdfZoomInfo.getOffset(isLandscape)

                // 只有当值真正不同时才更新，避免循环更新
                if (scale != newScale || offset != newOffset) {
                    scale = newScale
                    offset = newOffset
                    Log.d("PdfLazyViewer", "从 AppMemoryStore 恢复缩放状态: zoom=$newScale, offset=$newOffset")
                }
            }
            // ✅ 创建用于防抖的 StateFlow
            val zoomUpdateFlow = remember { MutableStateFlow<Pair<Float, Offset>>(Pair(scale, offset)) }
            // ✅ 当本地 scale/offset 变化时，更新到 Flow（不直接写入 AppMemoryStore）
            LaunchedEffect(scale, offset) {
                zoomUpdateFlow.value = Pair(scale, offset)
            }
            // ✅ 使用防抖机制，延迟写入 AppMemoryStore
            LaunchedEffect(fileUri, isLandscape) {
                @OptIn(FlowPreview::class)
                zoomUpdateFlow
                    .debounce(500)  // 500ms 防抖，用户停止操作后才保存
                    .collect { (zoom, off) ->
                        if (fileUri.isNotEmpty()) {
                            AppMemoryStore.updatePdfZoomAndOffset(
                                fileUri,
                                isLandscape,
                                zoom,
                                off
                            )
                            Log.d("PdfLazyViewer", "防抖保存缩放状态: zoom=$zoom, offset=$off")
                        }
                    }
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
                                Log.d("PdfScreen", "双击屏幕，切换全屏")
                                contentViewModel.onEvent(ContentUiEvent.OnDoubleClickScreen)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                Log.d("PdfScreen", "长按开始，scale=$scale")
                            },
                            onDrag = { change, dragAmount ->
                                if (scale > 1f) {
                                    offset += dragAmount
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                Log.d("PdfScreen", "拖拽结束")
                            },
                            onDragCancel = {
                                Log.d("PdfScreen", "拖拽取消")
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures(
                            onGesture = { centroid, pan, zoom, rotation ->
                                if (zoom != 1f) {
                                    scale *= zoom
                                    Log.d("PdfScreen", "缩放: scale=$scale")
                                } else if (scale > 1f) {
                                    offset += pan
                                }
                            }
                        )
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    )
            ) {
                val config by contentViewModel.configData.collectAsStateWithLifecycle()
                if(config.isSwipeLayout){
                    SwipeLayout(
                        contentViewModel = contentViewModel,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ScrollLayout(
                        contentViewModel = contentViewModel,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
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
            SlideInSearchPanel(
                initialVisible = isShowSearchDialog,
                searchExecutor = { query ->
                    viewModel.search(query)
                },
                getCurrentPosition = {
                    currentPage
                },
                onResultClick = { result ->
                    scope.launch {
                        viewModel.goToPage((result as SearchData.PdfSearchResult).pageIndex)
                    }
                },
                onClose = {
                    isShowSearchDialog = false
                }
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text(
                text = "正在渲染...",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun SwipeLayout(
    modifier: Modifier = Modifier,
    contentViewModel: ContentViewModel,
    viewModel: PdfViewModel,

) {
    val totalPages by viewModel.totalPages.collectAsStateWithLifecycle()
    val initialPage = viewModel.getInitialPage()
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    Log.d("PdfLazyViewer", "PDF 加载成功，页数: $totalPages, 初始页: $initialPage")

    // 收集配置信息，获取夜间模式状态
    val config by contentViewModel.configData.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { totalPages }
    )
    LaunchedEffect(pagerState.currentPage) {
        Log.d("PdfLazyViewer", "当前页: ${pagerState.currentPage}")
        viewModel.onEvent(PdfEvent.OnPageChanged(pagerState.currentPage))
        val currentPage = pagerState.currentPage
        viewModel.cleanupUnusedPages(currentPage)
    }
    LaunchedEffect(Unit) {
        viewModel.setOnGoToPageListener {
            scope.launch {
                pagerState.scrollToPage(it)
            }
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val contentAlignment =
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Alignment.TopCenter
            } else {
                Alignment.Center
            }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = contentAlignment,
        ) {
            val scrollState = rememberScrollState()
            val modifierImage: Modifier =
                if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    Modifier.fillMaxSize().verticalScroll(scrollState)
                } else {
                    Modifier.fillMaxSize()
                }
            viewModel.renderPage(page, 1)
            val bitmap = viewModel.getPageBitmap(page)
            if (bitmap != null) {
                Image(
                    modifier = modifierImage,
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PDF_Page_$page",
                    contentScale = ContentScale.FillWidth,
                    colorFilter = if (config.isNightMode) {
                        ColorFilter.colorMatrix(
                            ColorMatrix(
                                floatArrayOf(
                                    -1f, 0f, 0f, 0f, 255f,
                                    0f, -1f, 0f, 0f, 255f,
                                    0f, 0f, -1f, 0f, 255f,
                                    0f, 0f, 0f, 1f, 0f
                                )
                            )
                        )
                    } else null
                )
            } else {
                Log.d("PdfLazyViewer", "PDF page $page bmp is null")
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ScrollLayout(
    modifier: Modifier = Modifier,
    contentViewModel: ContentViewModel,
    viewModel: PdfViewModel
){

}