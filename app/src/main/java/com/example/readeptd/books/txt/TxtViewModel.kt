package com.example.readeptd.books.txt

import android.app.Application
import android.util.Log
import androidx.compose.ui.unit.IntSize
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.example.readeptd.books.BookUiState
import com.example.readeptd.books.BookViewModel
import com.example.readeptd.data.ReadingState
import com.example.readeptd.parser.TextChunk
import com.example.readeptd.parser.TextSplitter
import com.example.readeptd.parser.TxtExtractor
import com.example.readeptd.utils.Utils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * TXT 阅读器 ViewModel
 * 继承自 BookViewModel，提供 TXT 文件特有的功能
 */
class TxtViewModel(
    application: Application
) : BookViewModel<ReadingState.Txt>(application, ReadingState.Txt::class.java) {

    private val textExtractor = TxtExtractor(application)
    private var viewSize: IntSize = IntSize(0, 0)
    private var lineHeightSp: Int = 36
    private var fontSizeSp: Int = 16

    // Padding 设置（由 UI 层传入，单位：像素）
    private var leftPaddingDp: Int = 0
    private var rightPaddingDp: Int = 0
    private var topPaddingDp: Int = 0
    private var bottomPaddingDp: Int = 0

    // 用于保证分页操作串行执行的互斥锁
    private val pagesMutex = Mutex()

    // 当前正在执行的分页任务，用于取消
    private var currentPageJob: Job? = null

    // 缓存上一次的分页参数，用于判断是否需要重新分页
    private var lastCharsParams: Utils.CharsParams? = null

    // 暴露分页状态
    private val _pages = MutableStateFlow<List<TextChunk>>(emptyList())
    val pages: StateFlow<List<TextChunk>> = _pages.asStateFlow()

    // 当前页码
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    // 初始页码（用于恢复阅读进度）
    private val _initialPage = MutableStateFlow(0)
    val initialPage: StateFlow<Int> = _initialPage.asStateFlow()

    // 分页是否完成（独立于 BookUiState）
    private val _isPagesReady = MutableStateFlow(false)
    val isPagesReady: StateFlow<Boolean> = _isPagesReady.asStateFlow()

    // 暴露字体大小和行距给 UI
    val currentFontSizeSp: Int get() = fontSizeSp
    val currentLineHeightSp: Int get() = lineHeightSp

    /**
     * 处理 UI 事件
     */
    fun onEvent(event: TxtEvent) {
        when (event) {
            is TxtEvent.OnViewMetricsChanged -> handleViewMetricsChanged(
                event.size,
                event.leftPaddingDp,
                event.rightPaddingDp,
                event.topPaddingDp,
                event.bottomPaddingDp
            )
            is TxtEvent.OnPageChanged -> handlePageChanged(event.pageIndex)
            is TxtEvent.OnFontSizeChanged -> handleFontSizeChanged(event.fontSize)
            is TxtEvent.OnLineHeightChanged -> handleLineHeightChanged(event.lineHeight)
        }
    }

    /**
     * 处理视图尺寸和边距变化
     */
    private fun handleViewMetricsChanged(
        size: IntSize,
        leftPaddingDp: Int,
        rightPaddingDp: Int,
        topPaddingDp: Int,
        bottomPaddingDp: Int
    ) {
        val sizeChanged = viewSize != size
        val paddingChanged = this.leftPaddingDp != leftPaddingDp ||
                            this.rightPaddingDp != rightPaddingDp ||
                            this.topPaddingDp != topPaddingDp ||
                            this.bottomPaddingDp != bottomPaddingDp

        if (!sizeChanged && !paddingChanged) return

        Log.d(TAG, "视图指标变化: size=${size.width}x${size.height}, padding=($leftPaddingDp,$rightPaddingDp,$topPaddingDp,$bottomPaddingDp)")

        viewSize = size
        this.leftPaddingDp = leftPaddingDp
        this.rightPaddingDp = rightPaddingDp
        this.topPaddingDp = topPaddingDp
        this.bottomPaddingDp = bottomPaddingDp

        viewModelScope.launch {
            reinitPagesIfNeeded()
        }
    }

    /**
     * 处理翻页事件
     */
    private fun handlePageChanged(pageIndex: Int) {
        if (pageIndex < 0 || pageIndex >= _pages.value.size) return

        Log.d(TAG, "翻页到: $pageIndex")
        _currentPage.value = pageIndex

        // 保存阅读进度
        saveTxtProgress(
            uri = currentFileUri ?: return,
            pageIndex = pageIndex
        )
    }

    /**
     * 处理字体大小变化
     */
    private fun handleFontSizeChanged(newFontSize: Int) {
        if (fontSizeSp == newFontSize) return

        Log.d(TAG, "字体大小变化: $fontSizeSp -> $newFontSize")
        fontSizeSp = newFontSize

        viewModelScope.launch {
            reinitPagesIfNeeded()
        }
    }

    /**
     * 处理行距变化
     */
    private fun handleLineHeightChanged(newLineHeight: Int) {
        if (lineHeightSp == newLineHeight) return

        Log.d(TAG, "行距变化: $lineHeightSp -> $newLineHeight")
        lineHeightSp = newLineHeight

        viewModelScope.launch {
            reinitPagesIfNeeded()
        }
    }

    private fun calculatePageCharsParams(): Utils.CharsParams {
        val context = getApplication<Application>()
        val density = context.resources.displayMetrics.density
        val scaledDensity = context.resources.displayMetrics.scaledDensity

        // 统一转换为像素单位
        val pageWidthPx = viewSize.width
        val pageHeightPx = viewSize.height
        val fontSizePx = (fontSizeSp * scaledDensity).toInt()
        val lineHeightPx = (lineHeightSp * scaledDensity).toInt()
        val leftPaddingPx = (leftPaddingDp * density).toInt()
        val rightPaddingPx = (rightPaddingDp * density).toInt()
        val topPaddingPx = (topPaddingDp * density).toInt()
        val bottomPaddingPx = (bottomPaddingDp * density).toInt()

        return Utils.calculatePageCharsParams(
            pageWidth = pageWidthPx,
            pageHeight = pageHeightPx,
            fontSize = fontSizePx,
            lineHeight = lineHeightPx,
            leftPadding = leftPaddingPx,
            rightPadding = rightPaddingPx,
            topPadding = topPaddingPx,
            bottomPadding = bottomPaddingPx
        )
    }

    /**
     * 根据参数变化判断是否需要重新分页
     */
    private suspend fun reinitPagesIfNeeded() {
        if (viewSize.width <= 0 || viewSize.height <= 0) {
            Log.d(TAG, "分页条件不满足: viewSize=$viewSize")
            return
        }

        val newCharsParams = calculatePageCharsParams()

        // 如果分页参数没有变化，不需要重新分页
        if (lastCharsParams != null &&
            lastCharsParams?.avgCharsPerLine == newCharsParams.avgCharsPerLine &&
            lastCharsParams?.maxLinesPerPage == newCharsParams.maxLinesPerPage) {
            Log.d(TAG, "分页参数未变化，跳过重新分页: $newCharsParams")
            return
        }

        Log.d(TAG, "分页参数变化: $lastCharsParams -> $newCharsParams，开始重新分页")

        // 取消之前的分页任务
        currentPageJob?.cancel()

        // 启动新的分页任务
        currentPageJob = viewModelScope.launch {
            initPages()
        }
    }

    /**
     * 初始化分页（保证串行执行，避免并发问题）
     */
    suspend fun initPages() {
        // 重置分页状态
        _isPagesReady.value = false

        // 使用 Mutex 保证同一时间只有一个分页任务在执行
        pagesMutex.withLock {
            if (viewSize.width <= 0 || viewSize.height <= 0) {
                Log.d(TAG, "分页条件不满足: viewSize=$viewSize")
                return@withLock
            }

            // 从 UI 状态中获取临时文件路径
            val currentState = uiState.value
            if (currentState !is BookUiState.Ready) {
                Log.d(TAG, "文件未准备好，当前状态: $currentState")
                return@withLock
            }

            try {
                val charsParams = this.calculatePageCharsParams()

                Log.d(TAG, "开始分页: 每页约 ${charsParams.maxLinesPerPage} 行，每行约 ${charsParams.avgCharsPerLine} 字符")

                // 使用临时可变列表
                val tempPages = mutableListOf<TextChunk>()

                val splitter = TextSplitter(
                    charsParams.avgCharsPerLine,
                    charsParams.maxLinesPerPage
                ) { chunk ->
                    tempPages.add(chunk)
                }

                textExtractor.extractTextRaw(currentState.tempFilePath.toUri()).collect { line ->
                    splitter.processLine(line)
                }

                // 处理剩余内容
                splitter.flushRemaining()

                // 赋值不可变列表给 StateFlow
                _pages.value = tempPages.toList()

                // 更新缓存的分页参数
                lastCharsParams = charsParams

                // 标记分页完成
                _isPagesReady.value = true

                Log.d(TAG, "分页完成，共 ${_pages.value.size} 页")

                // 根据保存的阅读进度恢复页码
                restorePageFromProgress()

            } catch (e: Exception) {
                Log.e(TAG, "分页失败", e)
            }
        }
    }

    /**
     * 根据保存的阅读进度恢复页码
     */
    private fun restorePageFromProgress() {
        val savedState = currentReadingState
        if (savedState == null || _pages.value.isEmpty()) {
            Log.d(TAG, "没有保存的阅读状态或页面为空，从第一页开始")
            _initialPage.value = 0
            _currentPage.value = 0
            return
        }

        val charOffset = savedState.charOffset
        Log.d(TAG, "尝试恢复到字符偏移量: $charOffset")

        // 查找包含该字符偏移量的页面
        val targetPageIndex = findPageByCharOffset(charOffset)

        Log.d(TAG, "恢复到页码: $targetPageIndex")
        _initialPage.value = targetPageIndex
        _currentPage.value = targetPageIndex
    }

    /**
     * 根据字符偏移量查找对应的页码
     */
    private fun findPageByCharOffset(charOffset: Long): Int {
        if (_pages.value.isEmpty()) return 0

        // 二分查找或直接遍历
        for ((index, page) in _pages.value.withIndex()) {
            // 检查字符偏移量是否在当前页面范围内
            if (charOffset >= page.startPos && charOffset < page.endPos) {
                return index
            }
        }

        // 如果没找到，返回最后一页或第一页
        return if (charOffset >= _pages.value.last().endPos) {
            _pages.value.size - 1
        } else {
            0
        }
    }

    /**
     * 获取指定页的内容
     */
    fun getPageContent(pageIndex: Int): String {
        return if (pageIndex in _pages.value.indices) {
            _pages.value[pageIndex].content
        } else ""
    }

    /**
     * 获取分页数量
     */
    fun getPagesCount(): Int{
        return _pages.value.size
    }

    /**
     * 保存 TXT 阅读进度
     */
    fun saveTxtProgress(
        uri: String,
        pageIndex: Int
    ) {
        val progress = if (_pages.value.isNotEmpty()) pageIndex.toFloat() / _pages.value.size else 0f
        val charOffset = _pages.value.getOrNull(pageIndex)?.startPos ?: 0
        val state = ReadingState.Txt(
            uri = uri,
            charOffset = charOffset,
            progress = progress,
            lastReadTime = System.currentTimeMillis()
        )
        saveProgress(state)
    }

    override fun getViewModelName(): String {
        return "TxtViewModel"
    }

    companion object {
        private const val TAG = "TxtViewModel"
    }
}