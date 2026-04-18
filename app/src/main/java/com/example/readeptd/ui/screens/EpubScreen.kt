package com.example.readeptd.ui.screens

import android.util.Log
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
import com.example.readeptd.data.FileInfo
import com.example.readeptd.ui.views.EpubWebView

@Composable
fun EpubScreen(
    fileInfo: FileInfo,
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
                text = fileInfo.fileName,
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
                EpubWebView(fileInfo.uri.path ?: "",context).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
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
            onRelease = { webView ->
                // 关键：清理资源，防止内存泄漏
                webView.destroy()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
