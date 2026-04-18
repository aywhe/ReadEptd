package com.example.readeptd.ui.screens

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.viewmodel.EpubUiEvent
import com.example.readeptd.viewmodel.EpubUiState
import com.example.readeptd.viewmodel.EpubViewModel
import io.hamed.htepubreadr.ui.view.EpubView
import io.hamed.htepubreadr.util.EpubUtil

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpubScreen(
    fileUri: android.net.Uri,
    modifier: Modifier = Modifier,
    viewModel: EpubViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(fileUri) {
        viewModel.onEvent(EpubUiEvent.LoadEpub(fileUri))
    }
    
    when (val state = uiState) {
        is EpubUiState.Loading -> LoadingView(modifier)
        is EpubUiState.Success -> {
            val totalPages = state.totalPages
            val pagerState = rememberPagerState(initialPage = viewModel.getCurrentPageIndex()) { totalPages }
            
            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage != viewModel.getCurrentPageIndex()) {
                    viewModel.onEvent(EpubUiEvent.ChangePage(pagerState.currentPage))
                }
            }
            
            ReaderView(
                epubReader = state.epubReader,
                bookEntity = state.bookEntity,
                totalPages = totalPages,
                pagerState = pagerState,
                modifier = modifier
            )
        }
        is EpubUiState.Error -> ErrorView(state.message, modifier)
    }
}

@Composable
private fun LoadingView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text("正在加载 EPUB...", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun ErrorView(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("错误", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderView(
    epubReader: io.hamed.htepubreadr.component.EpubReaderComponent,
    bookEntity: io.hamed.htepubreadr.entity.BookEntity,
    totalPages: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "第 ${pagerState.currentPage + 1} / $totalPages 章",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = bookEntity.name ?: "未知书籍",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
        
        HorizontalPager(
            beyondViewportPageCount = 10,
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            AndroidView(
                factory = { ctx ->
                    EpubView(ctx).apply {
                        setBaseUrl(epubReader.absolutePath)
                        loadChapter(page, epubReader)
                        setOnHrefClickListener { href -> Log.d("EpubScreen", "点击链接: $href") }
                    }
                },
                update = { epubView ->
                    if (page == pagerState.currentPage) {
                        epubView.loadChapter(page, epubReader)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun EpubView.loadChapter(index: Int, epubReader: io.hamed.htepubreadr.component.EpubReaderComponent) {
    try {
        val allPages = epubReader.make(context)?.pagePathList ?: return
        if (index in allPages.indices) {
            setUp(EpubUtil.getHtmlContent(allPages[index]))
        }
    } catch (e: Exception) {
        Log.e("EpubView", "加载章节失败: $index", e)
    }
}
