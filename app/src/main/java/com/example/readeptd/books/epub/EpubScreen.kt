package com.example.readeptd.books.epub

import android.util.Log
import android.widget.FrameLayout
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.data.FileInfo
import com.example.readeptd.books.BookUiState
import com.example.readeptd.books.epub.EpubViewModel
import com.example.readeptd.speech.TtsViewModel
import com.example.readeptd.utils.JumpToProgressDialog
import com.example.readeptd.viewmodel.ContentViewModel

@Composable
fun EpubScreen(
    fileInfo: FileInfo,
    contentViewModel: ContentViewModel,
    ttsModel: TtsViewModel,
    modifier: Modifier = Modifier,
    viewModel: EpubViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isShowJumpToProgressDialog by remember { mutableStateOf(false)}
    var location by remember { mutableStateOf(EpubLocation("", 0f, 0,0)) }
    // 准备 EPUB 文件
    LaunchedEffect(fileInfo.uri) {
        viewModel.prepareBookFile(fileInfo.uri.toUri(), fileInfo.fileName, "epub")
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 根据状态显示不同内容
        when (val state = uiState) {
            is BookUiState.Loading -> {
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
            is BookUiState.Ready -> {
                // 获取上次阅读位置
                val savedCfi = viewModel.getCurrentState()?.cfi
                Log.d("EpubScreen", "上次阅读位置 CFI: ${savedCfi ?: "(无，将显示首页)"}")

                var webView by remember { mutableStateOf<EpubWebView?>(null) }
                // 准备完成，显示 WebView
                AndroidView(
                    factory = { context ->
                        val newWebView = EpubWebView(state.tempFilePath, context)
                        newWebView.apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )

                            // 设置起始位置 CFI
                            setStartCfi(savedCfi)

                            // 设置页面变化监听器，自动保存阅读进度
                            setOnPageChangedListener { epubLocation ->
                                location = epubLocation
                                contentViewModel.updateProgressText("${(epubLocation.percentage*100).toInt()}%")
                                Log.d("EpubScreen", "保存进度: $epubLocation")
                                // 并保存进度
                                viewModel.saveEpubProgress(
                                    uri = fileInfo.uri,
                                    cfi = epubLocation.cfi,
                                    page = epubLocation.currentPage,
                                    totalPages = epubLocation.totalPages,
                                    progress = epubLocation.percentage
                                )
                            }

                            setOnLoadCompleteListener {
                                contentViewModel.setOnClickProgressInfoCallback {
                                    isShowJumpToProgressDialog = true
                                }
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
                                    Log.d("EpubScreen", "获取到文本: ${text.take(50)} ..., 是否为空: ${text.isBlank()}")
                                    if (text.isNotBlank()) {
                                        Log.d("EpubScreen", "调用 ttsModel.speak() 开始朗读")
                                        val cleanedText = text.replace("\\n", " ").trim()
                                        ttsModel.speak(cleanedText)
                                    } else {
                                        Log.w("EpubScreen", "文本为空,不调用 speak()")
                                    }
                                }
                            }

                            // 当 TTS 朗读完成时,自动翻页并朗读下一页
                            ttsModel.setOnSpeechDoneListener { utteranceId ->
                                Log.d("EpubScreen", "自动朗读完成: $utteranceId, 准备翻页")
                                nextPage{
                                    getCurrentPageText { text ->
                                        if (text.isNotBlank()) {
                                            Log.d("EpubScreen", "获取到下一页文本,开始朗读")
                                            val cleanedText = text.replace("\\n", " ").trim()
                                            ttsModel.speak(cleanedText)
                                        } else {
                                            Log.w("EpubScreen", "下一页文本为空,停止自动朗读")
                                        }
                                    }
                                }
                            }
                        }
                        webView = newWebView
                        newWebView
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
                if (isShowJumpToProgressDialog) {
                    JumpToProgressDialog(
                        progress = location.percentage,
                        onDismiss = {
                            isShowJumpToProgressDialog = false
                        },
                        onConfirm = { progress ->
                            Log.d("EpubScreen", "跳转到进度: $progress")
                            webView?.goToPercentage(progress.toDouble())
                            isShowJumpToProgressDialog = false
                        }
                    )
                }
            }
            is BookUiState.Error -> {
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
