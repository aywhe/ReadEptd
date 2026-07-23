package com.example.readeptd.books.epub

import android.content.res.Configuration
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.data.FileInfo
import com.example.readeptd.books.BookUiState
import com.example.readeptd.activity.ContentUiEvent
import com.example.readeptd.speech.TtsViewModel
import com.example.readeptd.utils.JumpToProgressDialog
import com.example.readeptd.activity.ContentViewModel
import com.example.readeptd.bookmark.BookmarkData
import com.example.readeptd.bookmark.BookmarkDialog
import com.example.readeptd.bookmark.BookmarkHint
import com.example.readeptd.bookmark.BookmarkListPanel
import com.example.readeptd.bookmark.BookmarkViewModel
import com.example.readeptd.data.AppMemoryStore
import com.example.readeptd.data.ReadingState
import com.example.readeptd.search.SearchData
import com.example.readeptd.search.SlideInSearchPanel
import com.example.readeptd.utils.LayoutSettingDialog
import kotlinx.coroutines.launch

@Composable
fun EpubScreen(
    fileInfo: FileInfo,
    contentViewModel: ContentViewModel,
    ttsModel: TtsViewModel,
    bookmarkViewModel: BookmarkViewModel = viewModel(),
    modifier: Modifier = Modifier,
    viewModel: EpubViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    //val currentLocation by viewModel.currentLocation.collectAsState()
    var currentLocation by remember { mutableStateOf(EpubLocation.default()) }

    LaunchedEffect(fileInfo.uri) {
        viewModel.prepareBookFile(fileInfo.uri)
        bookmarkViewModel.prepareBookFile(fileInfo.uri)
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
                // ✅ 使用 readingState Flow 获取上次阅读位置
                val readingState by viewModel.readingState.collectAsStateWithLifecycle()
                val savedCfi = readingState?.cfi
                val isSwipeLayout = readingState?.isSwipeLayout ?: true
                Log.d("EpubScreen", "上次阅读位置 CFI: ${savedCfi ?: "(无，将显示首页)"}")
                var isShowSearchDialog by remember { mutableStateOf(false) }
                var isShowJumpToProgressDialog by remember { mutableStateOf(false)}
                var isShowLayoutSettingDialog by remember { mutableStateOf(false)}

                var webView by remember { mutableStateOf<EpubWebView?>(null) }
                var currentKeyword by remember { mutableStateOf("") }
                val config by contentViewModel.configData.collectAsStateWithLifecycle()
                //var scale by remember { mutableStateOf(1f) }
                var isShowBookmarkDialog by remember { mutableStateOf(false) }
                var isShowBookmarkListPanel by remember { mutableStateOf(false) }

                val isFullScreen by AppMemoryStore.fullScreenStateFlow(fileInfo.uri).collectAsStateWithLifecycle()
                val safeCutLayoutPaddingValues = WindowInsets.displayCutout.asPaddingValues()

                val currentBookmarkDataList by bookmarkViewModel.findInPosition(
                    BookmarkData.Epub(
                        bookId = fileInfo.uri,
                        start = BookmarkData.Epub.Position(
                            cfi = currentLocation.start.cfi,
                            loc = currentLocation.start.location,
                            percentage = currentLocation.start.percentage
                        ),
                        end = BookmarkData.Epub.Position(
                            cfi = currentLocation.end.cfi,
                            loc = currentLocation.end.location,
                            percentage = currentLocation.end.percentage
                        ),
                        note = "[#${(currentLocation.start.percentage * 100).toInt()}%#${currentLocation.start.location}]"
                    )
                ).collectAsStateWithLifecycle(emptyList())

                LaunchedEffect(currentBookmarkDataList, currentBookmarkDataList.size) {
                    contentViewModel.updateBookmarkState(currentBookmarkDataList.isNotEmpty())
                }

                LaunchedEffect(isFullScreen){
                    webView?.setFullScreen(isFullScreen && configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                }

                LaunchedEffect(isSwipeLayout) {
                    webView?.setFlowMode(
                        when(isSwipeLayout) {
                            true -> EpubFlowMode.Paginated
                            false -> EpubFlowMode.Scrolled
                        }
                    )
                    webView?.setStartCfi(savedCfi)
                    webView?.startEpubWebsite()
                }
                Box(modifier = Modifier.fillMaxSize()
                ) {
                    // 准备完成，显示 WebView
                    AndroidView(
                        factory = { context ->
                            val newWebView = EpubWebView(state.filePath, context)
                            newWebView.apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                                )

                                // 设置起始位置 CFI
                                setStartCfi(savedCfi)
                                setTheme(
                                    when(config.isNightMode) {
                                        true -> EpubTheme.Night
                                        false -> when(config.isDynamicColor) {
                                            true -> EpubTheme.Light
                                            false -> EpubTheme.EyeCare
                                        }
                                    }
                                )
                                setFlowMode(
                                    when(isSwipeLayout) {
                                        true -> EpubFlowMode.Paginated
                                        false -> EpubFlowMode.Scrolled
                                    }
                                )
                                setFontSize(viewModel.currentFontSizePx)
                                setSafeCutLayoutPadding(
                                    WebPaddingValues.fromPaddingValues(safeCutLayoutPaddingValues)
                                )

                                setOnFontSizeChangedListener { newFontSizePx->
                                    Log.d("EpubScreen", "字体大小变化: $newFontSizePx px")
                                    viewModel.updateFontSize(newFontSizePx)
                                }

                                // 设置页面变化监听器，自动保存阅读进度
                                setOnPageChangedListener { epubLocation ->
                                    currentLocation = epubLocation
                                    //viewModel.updateLocation(epubLocation)
                                    contentViewModel.updateProgressText("${(epubLocation.start.percentage * 100).toInt()}%")
                                    Log.d("EpubScreen", "保存进度: $epubLocation")
                                    // 并保存进度
                                    viewModel.updateEpubProgress(
                                        uri = fileInfo.uri,
                                        cfi = epubLocation.start.cfi,
                                        page = epubLocation.start.displayed.page,
                                        totalPages = epubLocation.start.displayed.total,
                                        progress = epubLocation.start.percentage
                                    )
                                }

                                setOnLoadCompleteListener {
                                    webView?.setFullScreen(isFullScreen && configuration.orientation == Configuration.ORIENTATION_PORTRAIT)

                                    contentViewModel.setOnClickProgressInfoCallback {
                                        toggleNavPanel()
                                    }
                                    contentViewModel.setOnLongPressProgressInfoCallback {
                                        isShowLayoutSettingDialog = true
                                    }
                                    contentViewModel.setOnClickSearchButtonCallback {
                                        isShowSearchDialog = !isShowSearchDialog
                                    }
                                    contentViewModel.setOnClickBookmarkCallback { isShowBookmarkDialog = true }
                                    contentViewModel.setOnLongPressBookmarkCallback { isShowBookmarkListPanel = true }
                                    Log.d("EpubScreen", "加载完成")
                                }

                                setOnDoubleClickListener {
                                    Log.d("EpubScreen", "双击")
                                    contentViewModel.onEvent(ContentUiEvent.OnDoubleClickScreen)
                                }

                                setOnClickJumpToProgressListener {
                                    isShowJumpToProgressDialog = true
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
                            ttsModel.clearCallbacks()
                            contentViewModel.clearCallBacks()
                            webView.destroy()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    if (isShowJumpToProgressDialog) {
                        JumpToProgressDialog(
                            progress = currentLocation.start.percentage.toFloat(),
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
                    if(isShowLayoutSettingDialog){
                        LayoutSettingDialog(
                            isSwipeLayout = isSwipeLayout,
                            onSwipeLayoutChange = { newValue ->
                                // ✅ 直接从 readingState 创建新状态并保存
                                if(readingState == null){
                                    viewModel.saveProgress(ReadingState.Epub(fileInfo.uri, isSwipeLayout = newValue))
                                } else {
                                    readingState?.let { currentState ->
                                        val newState = currentState.copy(isSwipeLayout = newValue)
                                        viewModel.saveProgress(newState)
                                    }
                                }
                            },
                            onDismiss = {
                                isShowLayoutSettingDialog = false
                            }
                        )
                    }
                    
                    SlideInSearchPanel(
                        visible = isShowSearchDialog,
                        onVisibleChange =  {isShowSearchDialog = it},
                        onClose = {
                            isShowSearchDialog = false
                            webView?.removeAllHighlights()
                        },
                        searchExecutor = { query ->
                            viewModel.search(query, epubWebView = webView)  // ✅ 调用 ViewModel 的搜索函数
                        },
                        onResultClick = { result ->
                            val epubResult = (result as SearchData.EpubSearchResult)
                            webView?.highlightSingle(epubResult.cfi)
                            webView?.goToLocation(epubResult.cfi)
                        },
                        getDistanceToResult = {
                            val result = (it as SearchData.EpubSearchResult)
                            kotlin.math.abs(currentLocation.end.location - result.locInd).toLong()
                        },
                        onKeywordChange = { keyword -> currentKeyword = keyword},
                        fileUri = fileInfo.uri  // ✅ 传递文件 URI，用于隔离搜索历史
                    )

                    BookmarkHint(contentViewModel = contentViewModel)

                    if(isShowBookmarkListPanel){
                        BookmarkListPanel(
                            viewModel = bookmarkViewModel,
                            onClose = {
                                isShowBookmarkListPanel = false
                            },
                            onBookmarkClick = { bookmarkData ->
                                scope.launch {
                                    try {
                                        val cfi = (bookmarkData as BookmarkData.Epub).start.cfi
                                        webView?.goToLocation(cfi)
                                    } catch (e: Exception) {
                                        Log.e("TxtScreen", "跳转页失败: ${e.message}")
                                    }
                                }
                            },
                            currentDistanceToBookmark = {
                                kotlin.math.abs((((it as BookmarkData.Epub).start.percentage - currentLocation.start.percentage) * 1000000).toLong())
                            }
                        )
                    }

                    if(isShowBookmarkDialog){
                        BookmarkDialog(
                            bookmarkData =
                                if(currentBookmarkDataList.isNotEmpty())
                                    currentBookmarkDataList.first()
                                else
                                    BookmarkData.Epub(
                                        bookId = fileInfo.uri,
                                        start = BookmarkData.Epub.Position(
                                            cfi = currentLocation.start.cfi,
                                            loc = currentLocation.start.location,
                                            percentage = currentLocation.start.percentage
                                        ),
                                        end = BookmarkData.Epub.Position(
                                            cfi = currentLocation.end.cfi,
                                            loc = currentLocation.end.location,
                                            percentage = currentLocation.end.percentage
                                        ),
                                        note = "[#${(currentLocation.start.percentage * 100).toInt()}%#${currentLocation.start.location}]"
                                    ),
                            onDismiss = {
                                isShowBookmarkDialog = false
                            },
                            onAfterConfirm = {
                                isShowBookmarkDialog = false
                            },
                            onAfterDelete = {
                                isShowBookmarkDialog = false
                            }
                        )
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
