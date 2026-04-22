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
    private var lineHeight: Int = 36
    private var fontSize: Int = 16
    
    // 暴露分页状态
    private val _pages = MutableStateFlow<List<TextChunk>>(emptyList())
    val pages: StateFlow<List<TextChunk>> = _pages.asStateFlow()
    
    // 当前页码
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    /**
     * 处理 UI 事件
     */
    fun onEvent(event: TxtEvent) {
        when (event) {
            is TxtEvent.OnViewSizeChanged -> handleViewSizeChanged(event.size)
            is TxtEvent.OnPageChanged -> handlePageChanged(event.pageIndex)
            is TxtEvent.OnFontSizeChanged -> handleFontSizeChanged(event.fontSize)
            is TxtEvent.OnLineHeightChanged -> handleLineHeightChanged(event.lineHeight)
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
            lineIndex = pageIndex,
            progress = if (_pages.value.isNotEmpty()) pageIndex.toFloat() / _pages.value.size else 0f
        )
    }

    /**
     * 处理字体大小变化
     */
    private fun handleFontSizeChanged(newFontSize: Int) {
        if (fontSize == newFontSize) return
        
        Log.d(TAG, "字体大小变化: $fontSize -> $newFontSize")
        fontSize = newFontSize
        
        // 字体变化后重新分页
        viewModelScope.launch {
            initPages()
        }
    }

    /**
     * 处理行距变化
     */
    private fun handleLineHeightChanged(newLineHeight: Int) {
        if (lineHeight == newLineHeight) return
        
        Log.d(TAG, "行距变化: $lineHeight -> $newLineHeight")
        lineHeight = newLineHeight
        
        // 行距变化后重新分页
        viewModelScope.launch {
            initPages()
        }
    }

    /**
     * 初始化分页
     */
    suspend fun initPages() {
        if (viewSize.width <= 0 || viewSize.height <= 0 || currentTempFile == null) {
            Log.d(TAG, "分页条件不满足: viewSize=$viewSize, file=${currentTempFile != null}")
            return
        }
        
        try {
            val charsParams = Utils.calculatePageCharsParams(viewSize.width, viewSize.height, fontSize, lineHeight)
            val avgCharsPerLine = charsParams.first
            val maxLinesPerPage = charsParams.second
            
            Log.d(TAG, "开始分页: 每页约 $maxLinesPerPage 行，每行约 $avgCharsPerLine 字符")
            
            // 使用临时可变列表
            val tempPages = mutableListOf<TextChunk>()
            
            val splitter = TextSplitter(avgCharsPerLine, maxLinesPerPage) { chunk ->
                tempPages.add(chunk)
            }
            
            textExtractor.extractTextRaw(currentTempFile?.toUri()).collect { line ->
                splitter.processLine(line)
            }
            splitter.flushRemaining()
            // 赋值不可变列表给 StateFlow
            _pages.value = tempPages.toList()
            
            Log.d(TAG, "分页完成，共 ${_pages.value.size} 页")
            
        } catch (e: Exception) {
            Log.e(TAG, "分页失败", e)
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
        charOffset: Long = 0,
        lineIndex: Int = 0,
        scrollPosition: Float = 0f,
        progress: Float = 0f
    ) {
        val state = ReadingState.Txt(
            uri = uri,
            charOffset = charOffset,
            lineIndex = lineIndex,
            scrollPosition = scrollPosition,
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
