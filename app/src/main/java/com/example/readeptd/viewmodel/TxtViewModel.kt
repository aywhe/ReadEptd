package com.example.readeptd.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.ui.unit.IntSize
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.example.readeptd.data.ReadingState
import com.example.readeptd.parser.TxtExtractor
import com.example.readeptd.utils.Utils
import com.example.readeptd.parser.TextSplitter
import com.example.readeptd.parser.TextChunk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.readeptd.ui.TxtEvent

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
    
    // 暴露分页状态
    private val _pages = MutableStateFlow<List<TextChunk>>(emptyList())
    val pages: StateFlow<List<TextChunk>> = _pages.asStateFlow()
    
    // 当前页码
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()
    
    // 初始页码（用于恢复阅读进度）
    private val _initialPage = MutableStateFlow(0)
    val initialPage: StateFlow<Int> = _initialPage.asStateFlow()
    
    // 暴露字体大小和行距给 UI
    val currentFontSizeSp: Int get() = fontSizeSp
    val currentLineHeightSp: Int get() = lineHeightSp

    /**
     * 处理 UI 事件
     */
    fun onEvent(event: TxtEvent) {
        when (event) {
            is TxtEvent.OnViewSizeChanged -> handleViewSizeChanged(event.size)
            is TxtEvent.OnPageChanged -> handlePageChanged(event.pageIndex)
            is TxtEvent.OnFontSizeChanged -> handleFontSizeChanged(event.fontSize)
            is TxtEvent.OnLineHeightChanged -> handleLineHeightChanged(event.lineHeight)
            is TxtEvent.OnPaddingChanged -> handlePaddingChanged(
                event.leftPaddingDp,
                event.rightPaddingDp,
                event.topPaddingDp,
                event.bottomPaddingDp
            )
        }
    }

    /**
     * 处理 UI 尺寸变化
     */
    private fun handleViewSizeChanged(size: IntSize) {
        if (viewSize == size) return
        
        Log.d(TAG, "UI 尺寸变化: ${size.width}x${size.height}")
        viewSize = size
        
        // 尺寸变化后重新分页
        viewModelScope.launch {
            initPages()
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
        
        // 字体变化后重新分页
        viewModelScope.launch {
            initPages()
        }
    }

    /**
     * 处理行距变化
     */
    private fun handleLineHeightChanged(newLineHeight: Int) {
        if (lineHeightSp == newLineHeight) return
        
        Log.d(TAG, "行距变化: $lineHeightSp -> $newLineHeight")
        lineHeightSp = newLineHeight
        
        // 行距变化后重新分页
        viewModelScope.launch {
            initPages()
        }
    }

    /**
     * 处理 Padding 变化
     */
    private fun handlePaddingChanged(
        leftPaddingDp: Int,
        rightPaddingDp: Int,
        topPaddingDp: Int,
        bottomPaddingDp: Int
    ) {
        this.leftPaddingDp = leftPaddingDp
        this.rightPaddingDp = rightPaddingDp
        this.topPaddingDp = topPaddingDp
        this.bottomPaddingDp = bottomPaddingDp
        
        Log.d(TAG, "Padding 变化: left=${this@TxtViewModel.leftPaddingDp}, right=${this@TxtViewModel.rightPaddingDp}, top=${this@TxtViewModel.topPaddingDp}, bottom=${this@TxtViewModel.bottomPaddingDp}")
        
        // Padding 变化后重新分页
        viewModelScope.launch {
            initPages()
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
     * 初始化分页
     */
    suspend fun initPages() {
        if (viewSize.width <= 0 || viewSize.height <= 0) {
            Log.d(TAG, "分页条件不满足: viewSize=$viewSize")
            return
        }
        
        // 从 UI 状态中获取临时文件路径
        val currentState = uiState.value
        if (currentState !is BookUiState.Ready) {
            Log.d(TAG, "文件未准备好，当前状态: $currentState")
            return
        }
        
        try {
            // 这里
            val charsParams = this.calculatePageCharsParams()
            
            Log.d(TAG, "开始分页: 每页约 $charsParams.maxLinesPerPage 行，每行约 $charsParams.avgCharsPerLine 字符")
            
            // 使用临时可变列表
            val tempPages = mutableListOf<TextChunk>()
            
            val splitter = TextSplitter(charsParams.avgCharsPerLine, charsParams.maxLinesPerPage) { chunk ->
                tempPages.add(chunk)
            }
            
            textExtractor.extractTextRaw(currentState.tempFilePath.toUri()).collect { line ->
                splitter.processLine(line)
            }
            
            // 处理剩余内容
            splitter.flushRemaining()
            
            // 赋值不可变列表给 StateFlow
            _pages.value = tempPages.toList()
            
            Log.d(TAG, "分页完成，共 ${_pages.value.size} 页")
            
            // 根据保存的阅读进度恢复页码
            restorePageFromProgress()
            
        } catch (e: Exception) {
            Log.e(TAG, "分页失败", e)
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
