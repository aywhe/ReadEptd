package com.example.readeptd.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
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
import java.io.File
import androidx.core.graphics.createBitmap

@Composable
fun PdfScreen(
    fileInfo: FileInfo,
    modifier: Modifier = Modifier,
    viewModel: PdfViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(fileInfo.uri) {
        viewModel.preparePdfFile(fileInfo.uri.toUri(), fileInfo.fileName)
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is PdfUiState.Loading -> {
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

            is PdfUiState.Ready -> {
                PdfLazyViewer(
                    filePath = state.tempFilePath,
                    modifier = Modifier.fillMaxSize()
                )
            }

            is PdfUiState.Error -> {
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

    LaunchedEffect(filePath) {
        try {
            val file = File(filePath)
            val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)
            
            pdfRenderer = renderer
            pageCount = renderer.pageCount
            
            Log.d("PdfLazyViewer", "PDF 加载成功，页数: $pageCount")
            
            renderPage(renderer, pagerState.currentPage, pageBitmaps, renderingPages)
            
            if (pagerState.currentPage + 1 < renderer.pageCount) {
                renderPage(renderer, pagerState.currentPage + 1, pageBitmaps, renderingPages)
            }
        } catch (e: Exception) {
            Log.e("PdfLazyViewer", "加载 PDF 失败", e)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        pdfRenderer?.let { renderer ->
            val currentPage = pagerState.currentPage
            
            renderPage(renderer, currentPage, pageBitmaps, renderingPages)
            
            if (currentPage > 0) {
                renderPage(renderer, currentPage - 1, pageBitmaps, renderingPages)
            }
            if (currentPage + 1 < pageCount) {
                renderPage(renderer, currentPage + 1, pageBitmaps, renderingPages)
            }
            
            val pagesToKeep = setOf(currentPage, currentPage - 1, currentPage + 1)
            val pagesToRemove = pageBitmaps.keys.filter { it !in pagesToKeep }
            pagesToRemove.forEach { pageIndex ->
                pageBitmaps.remove(pageIndex)?.recycle()
                Log.d("PdfLazyViewer", "释放页面 $pageIndex 的 Bitmap")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pageBitmaps.values.forEach { it.recycle() }
            pageBitmaps.clear()
            pdfRenderer?.close()
            Log.d("PdfLazyViewer", "资源已释放")
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
            
            Text(
                text = "${pagerState.currentPage + 1} / $pageCount",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        MaterialTheme.shapes.medium
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
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
        val bitmap = createBitmap(page.width * 2, page.height * 2)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        // 提取所有文本
        val textContents = page.getTextContents()

// 拼接成完整文本
        val fullText = textContents.joinToString(" ") { it.text ?: "" }

// 或者遍历处理每个文本块
        textContents.forEach { content ->
            Log.d("PDF", "文本: ${content.text}, 位置: ${content.bounds}")
        }
        page.close()

        pageBitmaps[pageIndex] = bitmap
        Log.d("PdfLazyViewer", "渲染页面 $pageIndex 完成 (${bitmap.width}x${bitmap.height})")
    } catch (e: Exception) {
        Log.e("PdfLazyViewer", "渲染页面 $pageIndex 失败", e)
    } finally {
        renderingPages[pageIndex] = false
    }
}
