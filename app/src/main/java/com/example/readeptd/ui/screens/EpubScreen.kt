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
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.data.FileInfo
import com.example.readeptd.ui.views.EpubWebView
import com.example.readeptd.viewmodel.EpubUiState
import com.example.readeptd.viewmodel.EpubViewModel

@Composable
fun EpubScreen(
    fileInfo: FileInfo,
    modifier: Modifier = Modifier,
    viewModel: EpubViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 准备 EPUB 文件
    LaunchedEffect(fileInfo.uri) {
        viewModel.prepareEpubFile(fileInfo.uri.toUri(), fileInfo.fileName)
    }

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
        }

        // 根据状态显示不同内容
        when (val state = uiState) {
            is EpubUiState.Loading -> {
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
            is EpubUiState.Ready -> {
                // 准备完成，显示 WebView
                AndroidView(
                    factory = { context ->
                        EpubWebView(state.tempFilePath, context).apply {
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            
                            // 设置页面变化监听器，自动保存阅读进度
                            setOnPageChangedListener { locationJson ->
                                Log.d("EpubScreen", "页面变化: $locationJson")
                                // TODO: 解析 locationJson 并保存进度
                                // 示例：viewModel.saveProgress(cfi, page, totalPages, progress)
                            }

                            setOnLoadCompleteListener { totalPages ->
                                Log.d("EpubScreen", "加载完成，共 $totalPages 页")
                                // TODO: 恢复阅读进度
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
                        Log.d("EpubScreen", "AndroidView 销毁")
                        // 必需手动销毁 WebView 以释放资源，奇怪的生命周期
                        webView.destroy()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            is EpubUiState.Error -> {
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
