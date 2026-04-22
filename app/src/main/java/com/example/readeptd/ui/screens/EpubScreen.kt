package com.example.readeptd.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.example.readeptd.data.ReadingState
import com.example.readeptd.ui.views.EpubWebView
import com.example.readeptd.viewmodel.EpubUiState
import com.example.readeptd.viewmodel.EpubViewModel
import com.example.readeptd.viewmodel.TtsViewModel

@Composable
fun EpubScreen(
    fileInfo: FileInfo,
    ttsModel: TtsViewModel,
    modifier: Modifier = Modifier,
    viewModel: EpubViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 准备 EPUB 文件
    LaunchedEffect(fileInfo.uri) {
        viewModel.prepareEpubFile(fileInfo.uri.toUri(), fileInfo.fileName)
    }

    Column(modifier = modifier.fillMaxSize()) {
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
                // 获取上次阅读位置
                val savedCfi = viewModel.getCurrentReadingState()?.cfi
                Log.d("EpubScreen", "上次阅读位置 CFI: ${savedCfi ?: "(无，将显示首页)"}")
                
                // 准备完成，显示 WebView
                AndroidView(
                    factory = { context ->
                        EpubWebView(state.tempFilePath, context).apply {
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            
                            // 设置起始位置 CFI
                            setStartCfi(savedCfi)
                            
                            // 设置页面变化监听器，自动保存阅读进度
                            setOnPageChangedListener { epubLocation ->
                                Log.d("EpubScreen", "保存进度: $epubLocation")
                                // 并保存进度
                                viewModel.saveProgress(ReadingState.Epub(
                                    uri = fileInfo.uri,
                                    cfi = epubLocation.cfi,
                                    page = epubLocation.currentPage,
                                    totalPages = epubLocation.totalPages,
                                    progress = epubLocation.percentage
                                ))
                            }

                            setOnLoadCompleteListener {
                                Log.d("EpubScreen", "加载完成")
                            }

                            setOnErrorListener { errorMessage ->
                                Log.e("EpubScreen", "错误: $errorMessage")
                            }
                            
                            // 设置自动朗读回调
                            // 当 TTS 开始朗读时,获取当前页文本并开始朗读
                            ttsModel.setOnRequestSpeechStartListener {
                                Log.d("EpubScreen", "自动朗读开始,获取当前页文本")
                                getCurrentPageText { text ->
                                    Log.d("EpubScreen", "获取到文本: ${text.take(50)}, 是否为空: ${text.isBlank()}")
                                    if (text.isNotBlank()) {
                                        Log.d("EpubScreen", "调用 ttsModel.speak() 开始朗读")
                                        ttsModel.speak(text)
                                    } else {
                                        Log.w("EpubScreen", "文本为空,不调用 speak()")
                                    }
                                }
                            }
                            
                            // 当 TTS 朗读完成时,自动翻页并朗读下一页
                            ttsModel.setOnSpeechDoneListener { utteranceId ->
                                Log.d("EpubScreen", "自动朗读完成: $utteranceId, 准备翻页")
                                nextPage()
                                // 延迟等待页面加载,然后获取文本并朗读
                                postDelayed({
                                    getCurrentPageText { text ->
                                        if (text.isNotBlank()) {
                                            Log.d("EpubScreen", "获取到下一页文本,开始朗读")
                                            ttsModel.speak(text)
                                        } else {
                                            Log.w("EpubScreen", "下一页文本为空,停止自动朗读")
                                        }
                                    }
                                }, 500)
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
