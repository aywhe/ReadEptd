package com.example.readeptd.ui.screens

import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.example.readeptd.ui.views.EpubWebView
import com.example.readeptd.viewmodel.EpubUiEvent
import com.example.readeptd.viewmodel.EpubUiState
import com.example.readeptd.viewmodel.EpubViewModel

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
            ReaderView(
                filePath = state.filePath,
                bookTitle = state.bookTitle,
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

@Composable
private fun ReaderView(
    filePath: String,
    bookTitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 顶部信息栏
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = bookTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "EPUB 阅读器 · 左右滑动翻页",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        
        // EPUB WebView
        AndroidView(
            factory = { context ->
                EpubWebView(context).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    // 加载 EPUB 文件
                    loadEpub(filePath)
                    
                    // 设置监听器
                    setOnPageChangedListener { locationJson ->
                        Log.d("EpubScreen", "页面变化: $locationJson")
                    }
                    
                    setOnLoadCompleteListener { totalPages ->
                        Log.d("EpubScreen", "加载完成，共 $totalPages 页")
                    }
                    
                    setOnErrorListener { errorMessage ->
                        Log.e("EpubScreen", "错误: $errorMessage")
                    }
                }
            },
            update = { webView ->
                // 可以在这里处理更新逻辑
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
