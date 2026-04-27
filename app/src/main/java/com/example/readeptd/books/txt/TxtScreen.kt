package com.example.readeptd.books.txt

import android.util.Log
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.data.FileInfo
import com.example.readeptd.books.BookUiState
import com.example.readeptd.contract.ContentUiEvent
import com.example.readeptd.speech.TtsViewModel
import com.example.readeptd.utils.JumpToPageDialog
import com.example.readeptd.viewmodel.ContentViewModel
import kotlinx.coroutines.launch

@Composable
fun TxtScreen(
    fileInfo: FileInfo,
    contentViewModel: ContentViewModel,
    ttsModel: TtsViewModel,
    modifier: Modifier = Modifier,
    viewModel: TxtViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val initialPage by viewModel.initialPage.collectAsState()
    val isPagesReady by viewModel.isPagesReady.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isShowJumpToPageDialog by remember { mutableStateOf(false) }

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
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    Log.d("TxtScreen", "双击屏幕，切换全屏")
                                    contentViewModel.onEvent(ContentUiEvent.OnDoubleClickScreen)
                                }
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
                            LaunchedEffect(Unit) {
                                contentViewModel.setOnClickProgressInfoCallback { progressText ->
                                    isShowJumpToPageDialog = true
                                }
                            }

                            LaunchedEffect(pagerState.currentPage) {
                                viewModel.onEvent(TxtEvent.OnPageChanged(pagerState.currentPage))
                                contentViewModel.updateProgressText(
                                    "${pagerState.currentPage + 1}/${viewModel.getPagesCount()}"
                                )
                            }

                            DisposableEffect(Unit) {
                                // 设置自动朗读回调
                                // 当 TTS 开始朗读时,获取当前页文本并开始朗读
                                ttsModel.setOnRequestSpeechStartListener {
                                    Log.d("TxtScreen", "开始朗读")
                                    val text = viewModel.getPageContent(pagerState.currentPage)
                                    if (text.isNotBlank()) {
                                        ttsModel.speak(text, "txt_${pagerState.currentPage}")
                                    }
                                }
                                // 当 TTS 朗读完成时,自动翻页并朗读下一页
                                ttsModel.setOnSpeechDoneListener { utteranceId ->
                                    val lastPlayedPage = utteranceId?.substringAfter("_")?.toIntOrNull()
                                    val currentPage = pagerState.currentPage
                                    
                                    // 判断是否需要调整页码：如果用户手动翻页了，从当前页开始朗读
                                    val targetPage = if (lastPlayedPage != null && lastPlayedPage != currentPage) {
                                        // 用户手动翻页，从当前页继续
                                        currentPage
                                    } else {
                                        // 正常顺序播放，朗读下一页
                                        currentPage + 1
                                    }
                                    
                                    val totalPages = viewModel.getPagesCount()
                                    if (targetPage in 0 until totalPages) {
                                        scope.launch {
                                            // 如果需要翻页（目标页不是当前页），先滚动
                                            if (targetPage != currentPage) {
                                                pagerState.scrollToPage(targetPage)
                                            }
                                            
                                            // 朗读目标页
                                            val text = viewModel.getPageContent(targetPage)
                                            if (text.isNotBlank()) {
                                                ttsModel.speak(text, "txt_$targetPage")
                                            }
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
                                Log.d("TxtScreen", "当前页: $page")
                                val pageContent = viewModel.getPageContent(page)
                                PageContent(
                                    pageContent = pageContent,
                                    fontSize = viewModel.currentFontSizeSp,
                                    lineHeight = viewModel.currentLineHeightSp,
                                    contentPadding = contentPadding
                                )
                            }
                            if(isShowJumpToPageDialog){
                                JumpToPageDialog(
                                    currentPage = pagerState.currentPage,
                                    totalPages = viewModel.getPagesCount(),
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
    SelectionContainer {
        Text(
            text = pageContent,
            fontSize = fontSize.sp,
            lineHeight = lineHeight.sp,
            modifier = Modifier.padding(contentPadding)
        )
    }
}
