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
import com.example.readeptd.search.SearchData
import com.example.readeptd.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
    private var lineHeightSp: Int = 24
    private var fontSizeSp: Int = 16

    // Padding 设置（由 UI 层传入，单位：像素）
    private var leftPaddingDp: Int = 0
    private var rightPaddingDp: Int = 0
    private var topPaddingDp: Int = 0
    private var bottomPaddingDp: Int = 0

    private val charCountThreshold: Int = 512

    // ✅ 全文内容缓存（只加载一次）
    private var entireText: String? = null

    // ✅ 分页结果缓存：key -> List<TextChunk>
    // key 格式："layout:{avgCharsPerLine}:{maxLinesPerPage}" 或 "chars:{minChunkSize}"
    private val pagesCache = mutableMapOf<String, List<TextChunk>>()
    
    // ✅ 当前使用的分页 key
    private var currentKey: String? = null

    // 用于保证分页操作串行执行的互斥锁
    private val pagesMutex = Mutex()

    // 当前正在执行的分页任务，用于取消
    private var currentPageJob: Job? = null

    // 控制是否允许重新分页（全屏切换时暂时禁用）
    private var allowRePagination: Boolean = true

    // ✅ 防抖相关：用于 debounceTriggerRePagination
    private var debouncedRePaginationJob: Job? = null

    // ✅ 当前页码（不再依赖 _pages）
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    // ✅ 分页是否完成
    private val _isPagesReady = MutableStateFlow(false)
    val isPagesReady: StateFlow<Boolean> = _isPagesReady.asStateFlow()

    // 暴露字体大小和行距给 UI
    val currentFontSizeSp: Int get() = fontSizeSp
    val currentLineHeightSp: Int get() = lineHeightSp

    private var _onGoToPageListener: ((Int) -> Unit)? = null
    private var _SplitPagesMode: SplitPagesMode = SplitPagesMode.ByLayoutSize


    fun setSplitPagesMode(mode: SplitPagesMode) {
        Log.d(TAG, "[setSplitPagesMode] 调用: mode=$mode, 当前模式=$_SplitPagesMode, allowRePagination=$allowRePagination")
        if (_SplitPagesMode == mode) {
            Log.d(TAG, "[setSplitPagesMode] 分页模式未变化，跳过")
            return
        }
        
        Log.d(TAG, "[setSplitPagesMode] 分页模式变化: $_SplitPagesMode -> $mode")
        _SplitPagesMode = mode
        
        // ✅ 切换模式后触发重新分页
        if (allowRePagination) {
            Log.d(TAG, "[setSplitPagesMode] 允许重新分页，启动协程")
            viewModelScope.launch {
                rebuildPagesIfNeeded()
            }
        } else {
            Log.w(TAG, "[setSplitPagesMode] 不允许重新分页，跳过")
        }
    }
    
    /**
     * ✅ 获取当前分页列表
     */
    private fun getCurrentPages(): List<TextChunk> {
        //Log.d(TAG, "currentKey: $currentKey")
        return currentKey?.let { pagesCache[it] } ?: emptyList()
    }
    
    /**
     * ✅ 构建分页缓存 key
     */
    private fun buildCacheKey(): String {
        return when (_SplitPagesMode) {
            SplitPagesMode.ByLayoutSize -> {
                val charsParams = calculatePageCharsParams()
                "layout:${charsParams.avgCharsPerLine}:${charsParams.maxLinesPerPage}"
            }
            SplitPagesMode.ByCharsCount -> {
                "chars:$charCountThreshold"
            }
        }
    }
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
            is TxtEvent.OnDoubleClickScreen -> {
                // 这里先不用做什么
            }

            is TxtEvent.OnScreenOrientationChanged -> {
                allowRePagination = true
            }
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

        if (!sizeChanged && !paddingChanged) {
            Log.d(TAG, "[handleViewMetricsChanged] 视图指标未变化，跳过")
            return
        }

        Log.d(
            TAG,
            "[handleViewMetricsChanged] 视图指标变化: size=${size.width}x${size.height}, padding=($leftPaddingDp,$rightPaddingDp,$topPaddingDp,$bottomPaddingDp), sizeChanged=$sizeChanged, paddingChanged=$paddingChanged"
        )

        viewSize = size
        this.leftPaddingDp = leftPaddingDp
        this.rightPaddingDp = rightPaddingDp
        this.topPaddingDp = topPaddingDp
        this.bottomPaddingDp = bottomPaddingDp

        debounceTriggerRePagination()
    }

    /**
     * 触发重新分页（防抖）
     */
    private fun debounceTriggerRePagination(){
        // 取消之前的防抖任务
        debouncedRePaginationJob?.cancel()
        
        // 启动新的防抖任务，300ms 后执行
        debouncedRePaginationJob = viewModelScope.launch {
            delay(500)
            triggerRePagination()
        }
    }

    /**
     * 处理翻页事件
     */
    private fun handlePageChanged(pageIndex: Int) {
        val pages = getCurrentPages()
        if (pageIndex < 0 || pageIndex >= pages.size) {
            Log.w(TAG, "[handlePageChanged] 页码超出范围: pageIndex=$pageIndex, pages.size=${pages.size}")
            return
        }

        Log.d(TAG, "[handlePageChanged] 翻页到: $pageIndex, 总页数: ${pages.size}")
        _currentPage.value = pageIndex
        // 保存阅读进度
        updateTxtProgress(
            uri = currentFileUri ?: run {
                Log.w(TAG, "[handlePageChanged] currentFileUri 为空，无法保存进度")
                return
            },
            pageIndex = pageIndex
        )
    }

    /**
     * 处理字体大小变化
     */
    private fun handleFontSizeChanged(newFontSize: Int) {
        if (fontSizeSp == newFontSize) {
            Log.d(TAG, "[handleFontSizeChanged] 字体大小未变化: $newFontSize")
            return
        }

        Log.d(TAG, "[handleFontSizeChanged] 字体大小变化: $fontSizeSp -> $newFontSize")
        fontSizeSp = newFontSize
        triggerRePagination()
    }

    /**
     * 处理行距变化
     */
    private fun handleLineHeightChanged(newLineHeight: Int) {
        if (lineHeightSp == newLineHeight) {
            Log.d(TAG, "[handleLineHeightChanged] 行距未变化: $newLineHeight")
            return
        }

        Log.d(TAG, "[handleLineHeightChanged] 行距变化: $lineHeightSp -> $newLineHeight")
        lineHeightSp = newLineHeight
        triggerRePagination()
    }

    /**
     * ✅ 触发重新分页（统一入口）
     */
    private fun triggerRePagination() {
        Log.d(TAG, "[triggerRePagination] 调用: allowRePagination=$allowRePagination, viewSize=$viewSize")
        if (!allowRePagination) {
            Log.w(TAG, "[triggerRePagination] 跳过重新分页：allowRePagination=false")
            return
        }

        Log.d(TAG, "[triggerRePagination] 启动协程执行 rebuildPagesIfNeeded")
        viewModelScope.launch {
            rebuildPagesIfNeeded()
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
    private suspend fun rebuildPagesIfNeeded() {
        Log.d(TAG, "[rebuildPagesIfNeeded] 调用: viewSize=$viewSize, fontSizeSp=$fontSizeSp, lineHeightSp=$lineHeightSp")
        if (viewSize.width <= 0 || viewSize.height <= 0) {
            Log.w(TAG, "[rebuildPagesIfNeeded] 分页条件不满足: viewSize=$viewSize，直接返回")
            return
        }

        // ✅ 构建新的缓存 key
        val newKey = buildCacheKey()
        Log.d(TAG, "[rebuildPagesIfNeeded] 构建缓存 key: $newKey")
        
        // ✅ 如果缓存中已有该 key 的分页结果，直接使用
        if (pagesCache.containsKey(newKey)) {
            Log.d(TAG, "[rebuildPagesIfNeeded] 分页结果已缓存，key=$newKey，直接使用")
            _isPagesReady.value = true
            Log.d(TAG, "[rebuildPagesIfNeeded] 设置 _isPagesReady = true")
            if (currentKey != newKey) {
                Log.d(TAG, "[rebuildPagesIfNeeded] currentKey 变化: $currentKey -> $newKey，恢复阅读进度")
                currentKey = newKey
                restorePageFromProgress()
            }
            return
        }

        Log.d(TAG, "[rebuildPagesIfNeeded] 分页参数变化，准备重新分页: key=$newKey")
        
        // 取消之前正在执行的分页任务
        if (currentPageJob?.isActive == true) {
            Log.d(TAG, "[rebuildPagesIfNeeded] 取消之前的分页任务")
            currentPageJob?.cancel()
        }
        
        // 启动新的分页任务
        Log.d(TAG, "[rebuildPagesIfNeeded] 启动新的分页任务")
        currentPageJob = viewModelScope.launch {
            buildPages()
        }
    }

    /**
     * ✅ 统一的分页构建方法
     */
    private suspend fun buildPages() {
        Log.d(TAG, "[buildPages] 开始执行，获取锁")
        pagesMutex.withLock {
            Log.d(TAG, "[buildPages] 获取锁成功")
            val key = buildCacheKey()
            Log.d(TAG, "[buildPages] 构建 key: $key")
            
            // ✅ 双重检查：如果缓存中已有，直接使用
            if (pagesCache.containsKey(key)) {
                Log.d(TAG, "[buildPages] 双重检查：分页结果已缓存，key=$key")
                _isPagesReady.value = true
                Log.d(TAG, "[buildPages] 设置 _isPagesReady = true")
                if (currentKey != key) {
                    Log.d(TAG, "[buildPages] currentKey 变化: $currentKey -> $key，恢复阅读进度")
                    currentKey = key
                    restorePageFromProgress()
                }
                return@withLock
            }
            
            // 重置分页状态
            Log.d(TAG, "[buildPages] 重置分页状态，设置 _isPagesReady = false")
            _isPagesReady.value = false

            // 从 UI 状态中获取临时文件路径
            val currentState = uiState.value
            Log.d(TAG, "[buildPages] 当前 UI 状态: $currentState")
            if (currentState !is BookUiState.Ready) {
                Log.w(TAG, "[buildPages] 文件未准备好，当前状态: $currentState，直接返回")
                return@withLock
            }

            try {
                Log.d(TAG, "[buildPages] 开始加载全文内容")
                // ✅ 确保全文内容已加载
                ensureEntireTextLoaded(currentState.tempFilePath.toUri())
                
                val fullText = entireText ?: throw IllegalStateException("全文内容未加载")
                Log.d(TAG, "[buildPages] 全文内容长度: ${fullText.length}")
                
                // ✅ 根据分页模式创建 TextSplitter
                val tempPages = mutableListOf<TextChunk>()
                val splitter = when (_SplitPagesMode) {
                    SplitPagesMode.ByLayoutSize -> {
                        val charsParams = calculatePageCharsParams()
                        Log.d(TAG, "[buildPages] 使用布局尺寸分页: 每页约 ${charsParams.maxLinesPerPage} 行，每行约 ${charsParams.avgCharsPerLine} 字符")
                        TextSplitter(charsParams.avgCharsPerLine, charsParams.maxLinesPerPage) { chunk ->
                            tempPages.add(chunk)
                        }
                    }
                    SplitPagesMode.ByCharsCount -> {
                        Log.d(TAG, "[buildPages] 使用字符数分页: minChunkSize=$charCountThreshold")
                        TextSplitter(minChunkSize = charCountThreshold) { chunk ->
                            tempPages.add(chunk)
                        }
                    }
                }

                Log.d(TAG, "[buildPages] 开始处理全文")
                // ✅ 直接处理全文（无需再次读取文件）
                splitter.processFullText(fullText)
                splitter.flushRemaining()
                Log.d(TAG, "[buildPages] 全文处理完成，生成 ${tempPages.size} 页")

                // ✅ 存入缓存
                pagesCache[key] = tempPages.toList()
                currentKey = key
                Log.d(TAG, "[buildPages] 存入缓存，key=$key")

                // 标记分页完成
                _isPagesReady.value = true
                Log.d(TAG, "[buildPages] ✅ 分页完成，设置 _isPagesReady = true，共 ${getCurrentPages().size} 页")

                // 根据保存的阅读进度恢复页码
                Log.d(TAG, "[buildPages] 恢复阅读进度")
                restorePageFromProgress()

            } catch (e: Exception) {
                Log.e(TAG, "[buildPages] ❌ 分页失败", e)
            }
        }
        Log.d(TAG, "[buildPages] 执行完毕，释放锁")
    }

    /**
     * ✅ 确保全文内容已加载（只加载一次）
     */
    private suspend fun ensureEntireTextLoaded(uri: android.net.Uri) {
        if (entireText == null) {
            Log.d(TAG, "[ensureEntireTextLoaded] 开始加载全文内容, uri=$uri")
            entireText = textExtractor.readEntireText(uri)
            Log.d(TAG, "[ensureEntireTextLoaded] 全文内容加载完成，长度: ${entireText?.length}")
        } else {
            Log.d(TAG, "[ensureEntireTextLoaded] 全文内容已缓存，长度: ${entireText?.length}，跳过加载")
        }
    }
    
    /**
     * 根据保存的阅读进度恢复页码
     */
    private fun restorePageFromProgress() {
        val savedState = readingState.value
        val pages = getCurrentPages()
        Log.d(TAG, "[restorePageFromProgress] savedState=$savedState, pages.size=${pages.size}")
        if (savedState == null || pages.isEmpty()) {
            Log.d(TAG, "[restorePageFromProgress] 没有保存的阅读状态或页面为空，从第一页开始")
            _currentPage.value = 0
            return
        }

        val charOffset = savedState.charOffset
        Log.d(TAG, "[restorePageFromProgress] 读取到保存的字符偏移量: $charOffset, progress=${savedState.progress}")

        // 查找包含该字符偏移量的页面
        val targetPageIndex = findPageByCharOffset(charOffset)

        Log.d(TAG, "[restorePageFromProgress] 字符偏移量对应的页码: $targetPageIndex, 跳转到该页")
        _currentPage.value = targetPageIndex
        goToPage(targetPageIndex)
    }

    fun getProgress(): Float{
        val savedState = readingState.value
        val pages = getCurrentPages()
        if (savedState == null || pages.isEmpty()) {
            Log.d(TAG, "[getProgress] 没有保存状态或页面为空，返回 0f")
            return 0f
        }
        Log.d(TAG, "[getProgress] progress=${savedState.progress}")
        return savedState.progress
    }

    fun findPageByProgress(progress: Float): Int {
        val pages = getCurrentPages()
        if (pages.isEmpty()) return 0
        val charOffset = (progress.toDouble() * pages.last().endPos).toLong()
        return findPageByCharOffset(charOffset)
    }
    /**
     * 根据字符偏移量查找对应的页码
     */
    fun findPageByCharOffset(charOffset: Long): Int {
        val pages = getCurrentPages()
        if (pages.isEmpty()) {
            return 0
        }

        // 二分查找或直接遍历
        for ((index, page) in pages.withIndex()) {
            // 检查字符偏移量是否在当前页面范围内
            if (charOffset >= page.startPos && charOffset < page.endPos) {
                Log.d(TAG, "[findPageByCharOffset]] charOffset: $charOffset 找到 page: $index, startPos: ${page.startPos}, endPos: ${page.endPos}")
                return index
            }
        }

        Log.d(TAG, "[findPageByCharOffset] 未找到页码")
        // 如果没找到，返回最后一页或第一页
        return if (charOffset >= pages.last().endPos) {
            pages.size - 1
        } else {
            0
        }
    }

    /**
     * ✅ 获取指定页的内容（从全文中截取）
     */
    fun getPageContent(pageIndex: Int): String {
        val pages = getCurrentPages()
        if (pageIndex !in pages.indices) {
            Log.w(TAG, "[getPageContent] 页码超出范围: pageIndex=$pageIndex, 总页数: ${pages.size}")
            return ""
        }
        
        val entireText = this.entireText
        if (entireText == null) {
            Log.e(TAG, "[getPageContent] 全文内容未加载")
            return ""
        }
        
        val page = pages[pageIndex]
        if(page.content?.isNotEmpty() == true) {
            Log.d(TAG, "[getPageContent] 使用页码 $pageIndex 的缓存内容: ${page.content.take(30)} ...")
            return page.content
        } else {
            try {
                // ✅ 使用位置信息从全文中截取，确保不越界
                val safeStartPos =
                    page.startPos.toInt().coerceAtLeast(0).coerceAtMost(entireText.length)
                val safeEndPos =
                    page.endPos.toInt().coerceAtLeast(safeStartPos).coerceAtMost(entireText.length)

                val content = entireText.substring(safeStartPos, safeEndPos)
                Log.d(TAG, "[getPageContent] 从全文截取页码 $pageIndex 的内容: startPos=$safeStartPos, endPos=$safeEndPos, length=${content.length}")
                return content
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "[getPageContent] 截取页面内容失败: pageIndex=$pageIndex, startPos=${page.startPos}, endPos=${page.endPos}",
                    e
                )
                return ""
            }
        }
    }

    /**
     * 获取分页数量
     */
    fun getPagesCount(): Int {
        return getCurrentPages().size
    }

    /**
     * 更新 TXT 阅读进度（update 方式）
     * 基于当前状态更新指定字段，保留其他字段（如 isSwipeLayout）
     */
    fun updateTxtProgress(
        uri: String,
        pageIndex: Int
    ) {
        val pages = getCurrentPages()
        val progress =
            if (pages.isNotEmpty()) (pages[pageIndex].startPos.toDouble() / pages.last().endPos).toFloat() else 0f
            //if (pages.isNotEmpty()) pageIndex.toFloat() / pages.size else 0f
        val charOffset = pages.getOrNull(pageIndex)?.startPos ?: 0
        Log.d(TAG, "[updateTxtProgress] 保存进度: pageIndex=$pageIndex, progress=$progress, charOffset=$charOffset")
        
        // ✅ 获取当前状态，如果不存在则创建新状态
        val currentState = readingState.value
        Log.d(TAG, "[updateTxtProgress] 当前 readingState: $currentState")
        
        val newState = currentState?.let {
            // ✅ 基于当前状态更新，保留 isSwipeLayout 等其他字段
            it.copy(
                charOffset = charOffset,
                progress = progress,
                lastReadTime = System.currentTimeMillis()
            )
        } ?: run {
            // 如果没有当前状态，创建新状态（默认 isSwipeLayout = true）
            Log.d(TAG, "[updateTxtProgress] 没有当前状态，创建新状态")
            ReadingState.Txt(
                uri = uri,
                charOffset = charOffset,
                progress = progress,
                lastReadTime = System.currentTimeMillis(),
                isSwipeLayout = true
            )
        }
        
        Log.d(TAG, "[updateTxtProgress] 保存新状态: $newState")
        saveProgress(newState)
    }

    fun setOnGoToPageListener(listener: (Int) -> Unit) {
        _onGoToPageListener = listener
    }

    fun goToPage(pageIndex: Int) {
        val pages = getCurrentPages()
        if (pageIndex in pages.indices) {
            Log.d(TAG, "[goToPage] 跳转到页码: $pageIndex, 总页数: ${pages.size}")
            _currentPage.value = pageIndex
            _onGoToPageListener?.invoke(pageIndex)
        } else {
            Log.w(TAG, "[goToPage] 页码超出范围: pageIndex=$pageIndex, 总页数: ${pages.size}")
        }
    }

    fun search(
        keyword: String,
        startPage: Int = 0,
        previewCharsNeighborLeft: Int = 25,
        previewCharsNeighborRight: Int = 25,
        maxCountOnePage: Int = 10,
        searchSwitchStep: Int = 20
    ): Flow<SearchData.TxtSearchResult> = flow {
        if (keyword.isEmpty()) {
            return@flow
        }

        Log.d(TAG, "开始搜索关键词: $keyword, 起始页: $startPage, 切换步长: $searchSwitchStep")
        var allCount = 0
        
        val pages = getCurrentPages()
        val totalPages = pages.size
        if (totalPages == 0) return@flow
        
        // ✅ 确保全文内容已加载（用于搜索）
        val entireText = this@TxtViewModel.entireText
        if (entireText == null) {
            Log.e(TAG, "全文内容未加载，无法搜索")
            return@flow
        }
        
        // ✅ 双向交替搜索策略
        var forwardIndex = startPage  // 向前搜索的索引
        var backwardIndex = startPage - 1  // 向后搜索的索引（从 startPage-1 开始）
        var isForwardTurn = true  // 当前是否轮到向前搜索
        var stepCount = 0  // 当前方向已搜索的页数
        
        // ✅ 记录已搜索的页面，避免重复
        val searchedPages = mutableSetOf<Int>()
        
        while (forwardIndex < totalPages || backwardIndex >= 0) {
            // ✅ 检查协程是否被取消
            currentCoroutineContext().ensureActive()
            
            val currentPageIndex = if (isForwardTurn) {
                // 向前搜索
                if (forwardIndex >= totalPages) {
                    // 向前已到末尾，切换到向后
                    isForwardTurn = false
                    stepCount = 0
                    continue
                }
                forwardIndex++
            } else {
                // 向后搜索
                if (backwardIndex < 0) {
                    // 向后已到开头，切换到向前
                    isForwardTurn = true
                    stepCount = 0
                    continue
                }
                backwardIndex--
            }
            
            // 检查是否已搜索过（理论上不会，但保险起见）
            if (currentPageIndex in searchedPages) continue
            searchedPages.add(currentPageIndex)
            
            // ✅ 搜索当前页：从全文中截取页面内容
            val page = pages[currentPageIndex]
            val pageContent = getPageContent(currentPageIndex)
            var startIndex = 0
            var count = 0
            
            while (true) {
                // ✅ 内层循环也要检查取消
                currentCoroutineContext().ensureActive()
                
                val matchIndex = pageContent.indexOf(keyword, startIndex, ignoreCase = true)
                if (matchIndex == -1) break

                // ✅ 提取上下文预览
                val contextStart = (matchIndex - previewCharsNeighborLeft).coerceAtLeast(0)
                val contextEnd =
                    (matchIndex + keyword.length + previewCharsNeighborRight).coerceAtMost(
                        pageContent.length
                    )
                val previewContent = pageContent.substring(contextStart, contextEnd)

                // ✅ 计算字符偏移量
                val charOffset = page.startPos + matchIndex

                emit(
                    SearchData.TxtSearchResult(
                        keyword = keyword,
                        previewContent = previewContent,
                        pageIndex = currentPageIndex,
                        charOffset = charOffset,
                        charOffsetInPage = matchIndex,
                        displayName = "#${charOffset}",
                        sortKey = charOffset
                    )
                )

                startIndex = matchIndex + keyword.length
                count++
                allCount++

                // 每页最多 maxCountOnePage 个结果
                if (count >= maxCountOnePage) {
                    break
                }
            }
            
            // ✅ 检查是否需要切换方向
            stepCount++
            if (stepCount >= searchSwitchStep) {
                isForwardTurn = !isForwardTurn
                stepCount = 0
                Log.d(TAG, "搜索方向切换，已找到 $allCount 个结果")
            }
        }
        
        Log.d(TAG, "搜索完成，找到 $allCount 个结果")
    }.flowOn(Dispatchers.Default)  // ✅ 确保在后台线程执行

    override fun getViewModelName(): String {
        return "TxtViewModel"
    }

    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        currentPageJob?.cancel()
        currentPageJob = null
        
        // ✅ 清理防抖任务
        debouncedRePaginationJob?.cancel()
        debouncedRePaginationJob = null
        
        // ✅ 清理缓存
        entireText = null
        pagesCache.clear()
        currentKey = null
        
        Log.d(TAG, "清理资源")
    }

    companion object {
        private const val TAG = "TxtViewModel"
    }
}