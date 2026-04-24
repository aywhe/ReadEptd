package com.example.readeptd.books.pdf

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.data.FileInfo
import com.example.readeptd.speech.TtsViewModel
import kotlinx.coroutines.launch
import com.example.readeptd.books.BookUiState
import com.example.readeptd.utils.JumpToPageDialog
import com.example.readeptd.viewmodel.ContentViewModel

@Composable
fun PdfScreen(
    fileInfo: FileInfo,
    contentViewModel: ContentViewModel,
    ttsModel: TtsViewModel,
    modifier: Modifier = Modifier,
    viewModel: PdfViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(fileInfo.uri) {
        viewModel.prepareBookFile(fileInfo.uri.toUri(), fileInfo.fileName, "pdf")
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
    
    val totalPages by viewModel.totalPages.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()

    DisposableEffect(filePath) {
        // 初始化 PDF 渲染器
        viewModel.initializeRenderer(filePath)
        onDispose {
            viewModel.cleanupRenderer()
            Log.d("PdfLazyViewer", "资源已释放")
        }
    }

    if (totalPages > 0) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var containerSize by remember { mutableStateOf(IntSize.Zero) }

        val initialPage = viewModel.getInitialPage()
        val pagerState = rememberPagerState(
            initialPage = initialPage,
            pageCount = { totalPages }
        )
        Log.d("PdfLazyViewer", "PDF 加载成功，页数: $totalPages, 初始页: $initialPage")
        
        LaunchedEffect(pagerState.currentPage) {
            Log.d("PdfLazyViewer", "当前页: ${pagerState.currentPage}")
            contentViewModel.updateProgressText("${pagerState.currentPage + 1}/$totalPages")
            viewModel.onEvent(PdfEvent.OnPageChanged(pagerState.currentPage))

            val currentPage = pagerState.currentPage
            viewModel.renderAroundPage(currentPage)
            viewModel.cleanupUnusedPages(currentPage)
        }
        DisposableEffect(Unit) {
            contentViewModel.setOnClickProgressInfoCallback { progressText ->
                if (totalPages > 0) {
                    isShowJumpToPageDialog = true
                }
            }

            ttsModel.setOnRequestSpeechStartListener {
                val text = viewModel.getPageText(pagerState.currentPage)
                if (!text.isNullOrBlank()) {
                    ttsModel.speak(text, "pdf_${pagerState.currentPage}")
                }
            }

            ttsModel.setOnSpeechDoneListener { utteranceId ->
                val nextPage = pagerState.currentPage + 1
                if (nextPage < totalPages) {
                    val text = viewModel.getPageText(nextPage)
                    if (!text.isNullOrBlank()) {
                        ttsModel.speak(text, "pdf_${pagerState.currentPage}")
                    }
                    scope.launch {
                        pagerState.scrollToPage(nextPage)
                    }
                }
            }
            onDispose {
            }
        }
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .onGloballyPositioned { coordinates ->
                    containerSize = coordinates.size
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        val maxXOffset = if (scale > 1f) containerSize.width * (scale - 1) / 2 else 0f
                        val maxYOffset = if (scale > 1f) containerSize.height * (scale - 1) / 2 else 0f
                        offset = Offset(
                            (offset.x + pan.x).coerceIn(-maxXOffset, maxXOffset),
                            (offset.y + pan.y).coerceIn(-maxYOffset, maxYOffset)
                        )
                    }
                }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val bitmap = viewModel.getPageBitmap(page)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "PDF 第 ${page + 1} 页",
                            modifier = Modifier
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        )
                    } else {
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
            if (isShowJumpToPageDialog) {
                JumpToPageDialog(
                    currentPage = pagerState.currentPage,
                    totalPages = totalPages,
                    onDismiss = {
                        isShowJumpToPageDialog = false
                    },
                    onConfirm = {
                        scope.launch {
                            pagerState.scrollToPage(it)
                        }
                        isShowJumpToPageDialog = false
                    }
                )
            }
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
