package com.example.readeptd.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.data.FileInfo
import com.example.readeptd.ui.TxtEvent
import com.example.readeptd.viewmodel.BookUiState
import com.example.readeptd.viewmodel.TxtViewModel
import com.example.readeptd.viewmodel.TtsViewModel
import kotlinx.coroutines.launch

@Composable
fun TextScreen(
    fileInfo: FileInfo,
    ttsModel: TtsViewModel,
    modifier: Modifier = Modifier,
    viewModel: TxtViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val initialPage by viewModel.initialPage.collectAsState()
    val isPagesReady by viewModel.isPagesReady.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 定义 padding（UI 层决定）
    val leftPaddingDp = 16
    val rightPaddingDp = 16
    val topPaddingDp = 16
    val bottomPaddingDp = 16
    val contentPadding = PaddingValues(
        start = leftPaddingDp.dp,
        end = rightPaddingDp.dp,
        top = topPaddingDp.dp,
        bottom = bottomPaddingDp.dp
    )

    // 准备 TXT 文件
    LaunchedEffect(fileInfo.uri) {
        viewModel.prepareBookFile(fileInfo.uri.toUri(), fileInfo.fileName, "txt")
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size ->
                            viewModel.onEvent(
                                TxtEvent.OnViewMetricsChanged(
                                    size = size,
                                    leftPaddingDp = leftPaddingDp,
                                    rightPaddingDp = rightPaddingDp,
                                    topPaddingDp = topPaddingDp,
                                    bottomPaddingDp = bottomPaddingDp
                                )
                            )
                        }
                ) {
                    if (!isPagesReady) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "正在分页...",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            val pagerState = rememberPagerState(
                                initialPage = initialPage.coerceIn(
                                    0,
                                    viewModel.getPagesCount() - 1
                                ),
                                pageCount = { viewModel.getPagesCount() }
                            )

                            LaunchedEffect(initialPage) {
                                if (initialPage >= 0 && initialPage < viewModel.getPagesCount()) {
                                    pagerState.scrollToPage(initialPage)
                                }
                            }

                            LaunchedEffect(pagerState.currentPage) {
                                viewModel.onEvent(TxtEvent.OnPageChanged(pagerState.currentPage))
                            }

                            DisposableEffect(Unit) {
                                // 设置自动朗读回调
                                // 当 TTS 开始朗读时,获取当前页文本并开始朗读
                                ttsModel.setOnRequestSpeechStartListener {
                                    val text = viewModel.getPageContent(pagerState.currentPage)
                                    if (text.isNotBlank()) {
                                        ttsModel.speak(text)
                                    }
                                }
                                // 当 TTS 朗读完成时,自动翻页并朗读下一页
                                ttsModel.setOnSpeechDoneListener { utteranceId ->
                                    scope.launch {
                                        pagerState.scrollToPage(pagerState.currentPage + 1)
                                        val text = viewModel.getPageContent(pagerState.currentPage)
                                        if (text.isNotBlank()) {
                                            ttsModel.speak(text)
                                        }
                                    }
                                }
                                onDispose {
                                    ttsModel.clearCallbacks()
                                }
                            }


                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 10
                            ) { page ->
                                val pageContent = viewModel.getPageContent(page)
                                PageContent(
                                    pageContent = pageContent,
                                    fontSize = viewModel.currentFontSizeSp,
                                    lineHeight = viewModel.currentLineHeightSp,
                                    contentPadding = contentPadding
                                )
                            }
                        }
                    }
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

@Composable
fun PageContent(
    pageContent: String,
    fontSize: Int,
    lineHeight: Int,
    contentPadding: PaddingValues = PaddingValues()
) {
    Text(
        text = pageContent,
        fontSize = fontSize.sp,
        lineHeight = lineHeight.sp,
        modifier = Modifier.padding(contentPadding)
    )
}
