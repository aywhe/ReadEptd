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
import androidx.compose.foundation.pager.PagerState
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
import com.example.readeptd.viewmodel.PdfUiState
import com.example.readeptd.viewmodel.PdfViewModel
import com.example.readeptd.viewmodel.TtsViewModel
import kotlinx.coroutines.launch
import java.io.File
import androidx.core.graphics.createBitmap

@Composable
fun PdfScreen(
    fileInfo: FileInfo,
    ttsModel: TtsViewModel,
    modifier: Modifier = Modifier,
    viewModel: PdfViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(fileInfo.uri) {
        viewModel.preparePdfFile(fileInfo.uri.toUri(), fileInfo.fileName)
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is PdfUiState.Loading -> LoadingView()
            is PdfUiState.Ready -> PdfLazyViewer(
                filePath = state.tempFilePath,
                ttsModel = ttsModel,
                modifier = Modifier.fillMaxSize()
            )
            is PdfUiState.Error -> ErrorView(message = state.message)
        }
    }
}

@Composable
private fun LoadingView() {
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

@Composable
private fun ErrorView(message: String) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfLazyViewer(
    filePath: String,
    ttsModel: TtsViewModel,
    modifier: Modifier = Modifier
) {
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { pageCount })
    
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    val renderingPages = remember { mutableStateMapOf<Int, Boolean>() }
    
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(filePath) {
        loadPdfRenderer(filePath)?.let { renderer ->
            pdfRenderer = renderer
            pageCount = renderer.pageCount
            
            Log.d("PdfLazyViewer", "PDF 加载成功，页数: $pageCount")
            
            renderPage(renderer, pagerState.currentPage, pageBitmaps, renderingPages)
            
            if (pagerState.currentPage + 1 < renderer.pageCount) {
                renderPage(renderer, pagerState.currentPage + 1, pageBitmaps, renderingPages)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        pdfRenderer?.let { renderer ->
            preloadPages(renderer, pagerState.currentPage, pageCount, pageBitmaps, renderingPages)
            cleanupUnusedPages(pagerState.currentPage, pageCount, pageBitmaps)
        }
    }

    DisposableEffect(Unit) {
        setupTtsCallbacks(ttsModel, pdfRenderer, pagerState, pageCount, scope)
        
        onDispose {
            ttsModel.clearCallbacks()
            releaseAllResources(pageBitmaps, pdfRenderer)
        }
    }

    if (pageCount > 0) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .onGloballyPositioned { coordinates ->
                    containerSize = coordinates.size
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        handleTransformGesture(pan, zoom, scale, offset, containerSize) { newScale, newOffset ->
                            scale = newScale
                            offset = newOffset
                        }
                    }
                }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                PageContent(
                    page = page,
                    pageBitmaps = pageBitmaps,
                    scale = scale,
                    offset = offset
                )
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

private fun loadPdfRenderer(filePath: String): PdfRenderer? {
    return try {
        val file = File(filePath)
        val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(fileDescriptor)
    } catch (e: Exception) {
        Log.e("PdfLazyViewer", "加载 PDF 失败", e)
        null
    }
}

private fun preloadPages(
    renderer: PdfRenderer,
    currentPage: Int,
    pageCount: Int,
    pageBitmaps: MutableMap<Int, Bitmap>,
    renderingPages: MutableMap<Int, Boolean>
) {
    renderPage(renderer, currentPage, pageBitmaps, renderingPages)
    
    if (currentPage > 0) {
        renderPage(renderer, currentPage - 1, pageBitmaps, renderingPages)
    }
    if (currentPage + 1 < pageCount) {
        renderPage(renderer, currentPage + 1, pageBitmaps, renderingPages)
    }
}

private fun cleanupUnusedPages(
    currentPage: Int,
    pageCount: Int,
    pageBitmaps: MutableMap<Int, Bitmap>
) {
    val pagesToKeep = setOf(currentPage, currentPage - 1, currentPage + 1)
    val pagesToRemove = pageBitmaps.keys.filter { it !in pagesToKeep }
    pagesToRemove.forEach { pageIndex ->
        pageBitmaps.remove(pageIndex)?.recycle()
        Log.d("PdfLazyViewer", "释放页面 $pageIndex 的 Bitmap")
    }
}

private fun setupTtsCallbacks(
    ttsModel: TtsViewModel,
    pdfRenderer: PdfRenderer?,
    pagerState: PagerState,
    pageCount: Int,
    scope: kotlinx.coroutines.CoroutineScope
) {
    ttsModel.setOnRequestSpeechStartListener {
        pdfRenderer?.let { renderer ->
            val text = getPageText(renderer, pagerState.currentPage)
            if (!text.isNullOrBlank()) {
                ttsModel.speak(text)
            }
        }
    }
    
    ttsModel.setOnSpeechDoneListener { utteranceId ->
        scope.launch {
            val nextPage = pagerState.currentPage + 1
            if (nextPage < pageCount) {
                pagerState.scrollToPage(nextPage)
                pdfRenderer?.let { renderer ->
                    val text = getPageText(renderer, nextPage)
                    if (!text.isNullOrBlank()) {
                        ttsModel.speak(text)
                    }
                }
            }
        }
    }
}

private fun releaseAllResources(
    pageBitmaps: MutableMap<Int, Bitmap>,
    pdfRenderer: PdfRenderer?
) {
    pageBitmaps.values.forEach { it.recycle() }
    pageBitmaps.clear()
    pdfRenderer?.close()
    Log.d("PdfLazyViewer", "资源已释放")
}

private fun handleTransformGesture(
    pan: Offset,
    zoom: Float,
    currentScale: Float,
    currentOffset: Offset,
    containerSize: IntSize,
    onUpdate: (Float, Offset) -> Unit
) {
    val newScale = (currentScale * zoom).coerceIn(0.5f, 5f)
    val maxXOffset = if (newScale > 1f) containerSize.width * (newScale - 1) / 2 else 0f
    val maxYOffset = if (newScale > 1f) containerSize.height * (newScale - 1) / 2 else 0f
    val newOffset = Offset(
        (currentOffset.x + pan.x).coerceIn(-maxXOffset, maxXOffset),
        (currentOffset.y + pan.y).coerceIn(-maxYOffset, maxYOffset)
    )
    onUpdate(newScale, newOffset)
}

@Composable
private fun PageContent(
    page: Int,
    pageBitmaps: Map<Int, Bitmap>,
    scale: Float,
    offset: Offset
) {
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

private fun renderPage(
    renderer: PdfRenderer,
    pageIndex: Int,
    pageBitmaps: MutableMap<Int, Bitmap>,
    renderingPages: MutableMap<Int, Boolean>
) {
    if (pageBitmaps.containsKey(pageIndex) || renderingPages[pageIndex] == true) {
        return
    }
    
    if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
        return
    }
    
    renderingPages[pageIndex] = true
    
    try {
        val page = renderer.openPage(pageIndex)
        val bitmap = createBitmap(page.width * 3, page.height * 3)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        pageBitmaps[pageIndex] = bitmap
        Log.d("PdfLazyViewer", "渲染页面 $pageIndex 完成 (${bitmap.width}x${bitmap.height})")
    } catch (e: Exception) {
        Log.e("PdfLazyViewer", "渲染页面 $pageIndex 失败", e)
    } finally {
        renderingPages[pageIndex] = false
    }
}

private fun getPageText(renderer: PdfRenderer, pageIndex: Int): String? {
    return try {
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
            return null
        }
        val page = renderer.openPage(pageIndex)
        val textContents = page.getTextContents()
        val fullText = textContents.joinToString(" ") { it.text ?: "" }
        page.close()
        fullText.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        Log.e("PdfLazyViewer", "获取页面 $pageIndex 文本失败", e)
        null
    }
}
