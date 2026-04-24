package com.example.readeptd.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.readeptd.viewmodel.PdfViewModel
import com.example.readeptd.viewmodel.TtsViewModel
import kotlinx.coroutines.launch
import java.io.File
import androidx.core.graphics.createBitmap
import com.example.readeptd.ui.PdfEvent
import com.example.readeptd.viewmodel.BookUiState
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
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    val renderingPages = remember { mutableStateMapOf<Int, Boolean>() }
    val scope = rememberCoroutineScope()
    var isShowJumpToPageDialog by remember { mutableStateOf(false) }


    DisposableEffect(filePath) {
        try {
            val file = File(filePath)
            val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)
            pdfRenderer = renderer
        } catch (e: Exception) {
            Log.e("PdfLazyViewer", "加载 PDF 失败", e)
        }
        onDispose {
            pageBitmaps.values.forEach { it.recycle() }
            pageBitmaps.clear()
            pdfRenderer?.close()
            pdfRenderer = null
            Log.d("PdfLazyViewer", "资源已释放")
        }
    }

    var pageCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(pdfRenderer){
        if(pdfRenderer != null) {
            pageCount = pdfRenderer!!.pageCount
            viewModel.setTotalPages(pageCount)
        }
    }



    if(pdfRenderer != null && pdfRenderer!!.pageCount > 0){
        val pageCount = pdfRenderer!!.pageCount
        viewModel.setTotalPages(pageCount)
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var containerSize by remember { mutableStateOf(IntSize.Zero) }

        val initialPage = viewModel.getInitialPage()
        val pagerState = rememberPagerState(
            initialPage = initialPage,
            pageCount = { pageCount }
        )
        Log.d("PdfLazyViewer", "PDF 加载成功，页数: $pageCount, 初始页: $initialPage")
        LaunchedEffect(pagerState.currentPage) {
            Log.d("PdfLazyViewer", "当前页: ${pagerState.currentPage}")
            pdfRenderer?.let { renderer ->
                contentViewModel.updateProgressText("${pagerState.currentPage + 1}/$pageCount")
                viewModel.onEvent(PdfEvent.OnPageChanged(pagerState.currentPage))

                val currentPage = pagerState.currentPage
                renderAroundPage(renderer, currentPage, pageBitmaps, renderingPages)
                cleanupUnusedPages(currentPage, pageBitmaps, renderingPages)
            }
        }
        DisposableEffect(Unit) {
            contentViewModel.setOnClickProgressInfoCallback { progressText ->
                if(pdfRenderer != null) {
                    isShowJumpToPageDialog = true
                }
            }

            ttsModel.setOnRequestSpeechStartListener {
                pdfRenderer?.let { renderer ->
                    val text = getPageText(renderer, pagerState.currentPage)
                    if (!text.isNullOrBlank()) {
                        ttsModel.speak(text,"pdf_${pagerState.currentPage}")
                    }
                }
            }

            ttsModel.setOnSpeechDoneListener { utteranceId ->
                val nextPage = pagerState.currentPage + 1
                if (nextPage < pageCount) {
                    pdfRenderer?.let { renderer ->
                        val text = getPageText(renderer, nextPage)
                        if (!text.isNullOrBlank()) {
                            ttsModel.speak(text, "pdf_${pagerState.currentPage}")
                        }
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
                    val bitmap = pageBitmaps[page]
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
            if(isShowJumpToPageDialog){
                JumpToPageDialog(
                    currentPage = pagerState.currentPage,
                    totalPages = pageCount,
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

private fun cleanupUnusedPages(
    currentPage: Int,
    pageBitmaps: MutableMap<Int, Bitmap>,
    renderingPages: MutableMap<Int, Boolean>,
    keepNeighbourNumber: Int = 1,
) {
    val pagesToRemove = pageBitmaps.keys.filter { it !in (currentPage - keepNeighbourNumber..currentPage + keepNeighbourNumber) }
    pagesToRemove.forEach { pageIndex ->
        pageBitmaps.remove(pageIndex)?.recycle()
        renderingPages[pageIndex] =  false
        Log.d("PdfLazyViewer", "释放页面 $pageIndex 的 Bitmap")
    }
}

private fun renderAroundPage(
    renderer: PdfRenderer,
    currentPage: Int,
    pageBitmaps: MutableMap<Int, Bitmap>,
    renderingPages: MutableMap<Int, Boolean>,
    keepNeighbourNumber: Int = 1,
    bitMapWhScale: Int = 3
) {
    if (currentPage < 0 || currentPage >= renderer.pageCount) {
        return
    }
    if (pageBitmaps.containsKey(currentPage) || renderingPages[currentPage] == true) {
        Log.d("PdfLazyViewer", "页面 $currentPage 已渲染，不需要渲染")
    } else {
        try {
            for(index in (currentPage - keepNeighbourNumber).coerceIn(0, renderer.pageCount-1)
                    ..(currentPage + keepNeighbourNumber).coerceIn(0, renderer.pageCount-1)) {
                val page = renderer.openPage(index)
                val bitmap = createBitmap(page.width * bitMapWhScale, page.height * bitMapWhScale)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pageBitmaps[index] = bitmap
                renderingPages[index] = true
                Log.d(
                    "PdfLazyViewer",
                    "渲染页面 $index 完成 (${bitmap.width}x${bitmap.height})"
                )
            }
        } catch (e: Exception) {
            Log.e("PdfLazyViewer", "渲染页面 $currentPage 失败", e)
        } finally {
            renderingPages[currentPage] = false
        }
    }
}

private fun getPageText(renderer: PdfRenderer, pageIndex: Int): String? {
    return try {
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
            return null
        }
        val page = renderer.openPage(pageIndex)
        val textContents = page.getTextContents()
        Log.d("PdfLazyViewer", "获取页面 $pageIndex 文本 contents 数量为 ${textContents.size}")
        val fullText = textContents.joinToString(" ") { it.text ?: "" }
        Log.d("PdfLazyViewer", "获取页面 $pageIndex 的文本 ${fullText.take(50)}")
        page.close()
        fullText.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        Log.e("PdfLazyViewer", "获取页面 $pageIndex 文本失败", e)
        null
    }
}
