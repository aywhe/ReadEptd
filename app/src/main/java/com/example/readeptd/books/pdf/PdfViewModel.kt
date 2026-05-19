package com.example.readeptd.books.pdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.lifecycle.viewModelScope
import com.example.readeptd.books.BookViewModel
import com.example.readeptd.data.ReadingState
import com.example.readeptd.search.SearchData
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.currentCoroutineContext
import java.io.File

/**
 * PDF 状态密封接口
 * 用于跟踪 PDF 文件的加载和就绪状态
 */
sealed interface PdfState {
    /**
     * 加载中状态
     */
    object Loading : PdfState
    
    /**
     * 就绪状态 - PDF 已成功加载并可以显示
     */
    object Ready : PdfState
    
    /**
     * 错误状态
     *
     * @param message 错误信息
     */
    data class Error(val message: String) : PdfState
}

/**
 * PDF 阅读器 ViewModel
 * 继承自 BookViewModel，提供 PDF 文件特有的功能
 */
class PdfViewModel(
    application: Application
) : BookViewModel<ReadingState.Pdf>(application, ReadingState.Pdf::class.java) {

    // PDF 渲染器相关状态
    private var pdfRenderer: PdfRenderer? = null
    
    // ✅ PDF 状态流
    private val _pdfState = MutableStateFlow<PdfState>(PdfState.Loading)
    val pdfState: StateFlow<PdfState> = _pdfState.asStateFlow()

    // ✅ 使用 LinkedHashMap 实现 LRU 缓存,增加到15页以改善滚动体验
    private val pageBitmaps = object : LinkedHashMap<Int, Bitmap>(30, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?): Boolean {
            if (size > 30) {
                val pageIndex = eldest?.key
                eldest?.value?.recycle()
                
                // ✅ 同步更新 StateFlow，通知 UI bitmap 已被回收
                if (pageIndex != null && _pageBitmapStates.containsKey(pageIndex)) {
                    _pageBitmapStates[pageIndex]?.value = null // 因为是flow，不要直接remove，不然ui会丢失监听
                }
                
                Log.d(TAG, "LRU缓存已满,回收页面 ${pageIndex} 的Bitmap")
                return true
            }
            return false
        }
    }

    // 用于保护页面缓存的互斥锁，避免并发访问导致的问题
    private val pageCacheMutex = Mutex()

    // 当前页码（用于 UI 显示和进度保存）
    private val _currentPage = MutableStateFlow(0)
    private val _totalPages = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()
    private var _onGoToPageListener: ((Int) -> Unit)? = null

    // ✅ 页面渲染状态流：key 为 pageIndex，value 为对应的 Bitmap
    private val _pageBitmapStates = mutableMapOf<Int, MutableStateFlow<Bitmap?>>()
    
    /**
     * 获取指定页面的位图状态流
     */
    fun getPageBitmapState(pageIndex: Int): StateFlow<Bitmap?> {
        Log.d(TAG, "获取页面 ${pageIndex} 的flow")
        return _pageBitmapStates.getOrPut(pageIndex) { MutableStateFlow(null) }
    }

    /**
     * 获取指定页面的位图（供 UI 调用）
     */
    fun getPageBitmap(pageIndex: Int): Bitmap? {
        return pageBitmaps[pageIndex]
    }

    fun onEvent(event: PdfEvent) {
        when (event) {
            is PdfEvent.OnPageChanged -> handlePageChanged(event.pageIndex)
        }
    }

    fun setOnGoToPageListener(listener: (Int) -> Unit) {
        _onGoToPageListener = listener
    }

    fun goToPage(pageIndex: Int) {
        if (pageIndex >= 0 && pageIndex < _totalPages.value) {
            _currentPage.value = pageIndex
            _onGoToPageListener?.invoke(pageIndex)
        }
    }

    /**
     * 初始化 PDF 渲染器
     *
     * @param filePath PDF 文件路径
     * @return 是否初始化成功
     */
    fun initializeRenderer(filePath: String): Boolean {
        return try {
            _pdfState.value = PdfState.Loading
            cleanupRenderer()
            val file = File(filePath)
            val fileDescriptor =
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor)
            _totalPages.value = pdfRenderer?.pageCount ?: 0
            
            // ✅ 获取上次阅读的页码
            val initialPage = getInitialPage()
            
            // ✅ 先渲染上次阅读的页面
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    renderPage(initialPage, keepNeighbourNumber = 1)
                    // ✅ 渲染完成后设置为 Ready 状态
                    _pdfState.value = PdfState.Ready
                    Log.d(TAG, "PDF 渲染器初始化成功，总页数: ${_totalPages.value}，已渲染初始页: $initialPage")
                } catch (e: Exception) {
                    Log.e(TAG, "渲染初始页面失败", e)
                    // 即使渲染失败也设置为 Ready，让用户可以看到 PDF
                    _pdfState.value = PdfState.Ready
                }
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "PDF 渲染器初始化失败", e)
            _pdfState.value = PdfState.Error("PDF 渲染器初始化失败: ${e.message}")
            false
        }
    }

    /**
     * 清理渲染器资源
     */
    fun cleanupRenderer() {
        // ✅ 重置 PDF 状态为 Loading
        _pdfState.value = PdfState.Loading
        
        // ✅ 先清空所有 StateFlow，避免 UI 使用已回收的 bitmap
        _pageBitmapStates.values.forEach { it.value = null }
        
        // 回收所有 bitmap
        pageBitmaps.values.forEach { it.recycle() }
        pageBitmaps.clear()
        
        // 清空状态流映射
        _pageBitmapStates.clear()
        
        pdfRenderer?.close()
        pdfRenderer = null
        Log.d(TAG, "PDF 渲染器资源已释放")
    }

    /**
     * 渲染指定页面及其周围页面
     */
    suspend fun renderPage(
        currentPage: Int,
        keepNeighbourNumber: Int = 2,
        bitMapWhScale: Int = 3,
        maxScaleSize: Int = 2400,
        callback: (Bitmap?) -> Unit = {}
    ) {
        val renderer = pdfRenderer
        if (renderer != null) {
            try {
                if (currentPage >= 0 && currentPage < renderer.pageCount) {
                    // 检查是否已渲染，避免重复渲染
                    if (!pageBitmaps.containsKey(currentPage)) {
                        // 优先渲染当前页
                        renderOnePage(renderer, currentPage, bitMapWhScale, maxScaleSize)
                    } else {
                        Log.d(TAG, "页面 ${currentPage} 存在，无需渲染")
                    }
                } else {
                    Log.e(TAG, "页面索引越界")
                }
                for (offset in 1..keepNeighbourNumber) {
                    val prevPage = currentPage - offset
                    val nextPage = currentPage + offset

                    // 渲染前一页（如果在范围内）
                    if (prevPage >= 0 && prevPage < renderer.pageCount && !pageBitmaps.containsKey(prevPage)) {
                        renderOnePage(renderer, prevPage, bitMapWhScale, maxScaleSize)
                    }

                    // 渲染后一页（如果在范围内）
                    if (nextPage >= 0 && nextPage < renderer.pageCount && !pageBitmaps.containsKey(nextPage)) {
                        renderOnePage(renderer, nextPage, bitMapWhScale, maxScaleSize)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "渲染页面 $currentPage 失败", e)
            }
        } else {
            Log.e(TAG, "PDF 渲染器未初始化")
        }
        if (pageBitmaps.containsKey(currentPage)) {
            callback(pageBitmaps[currentPage])
        } else {
            callback(null)
        }
    }

    /**
     * 渲染单个页面（内部辅助函数，需在锁内调用）
     */
    private suspend fun renderOnePage(renderer: PdfRenderer, pageIndex: Int, bitMapWhScale: Int, maxScaleSize: Int = 2400) {
        // 检查是否已渲染，防止重复渲染
        if (pageBitmaps.containsKey(pageIndex)) {
            Log.d(TAG, "页面 ${pageIndex} 已存在，无需重复渲染")
            return
        }

        
        try {
            val page = renderer.openPage(pageIndex)
            var actualScale = bitMapWhScale
            if(bitMapWhScale <= 0){
                actualScale = 1
            }
            
            val scaledWidth = page.width * actualScale
            val scaledHeight = page.height * actualScale
            
            val finalScale = if (scaledWidth > maxScaleSize || scaledHeight > maxScaleSize) {
                val widthRatio = maxScaleSize.toFloat() / page.width
                val heightRatio = maxScaleSize.toFloat() / page.height
                minOf(widthRatio, heightRatio)
            } else {
                actualScale.toFloat()
            }
            
            val bitmap = createBitmap((page.width * finalScale).toInt(), (page.height * finalScale).toInt())
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            pageCacheMutex.withLock {
                pageBitmaps[pageIndex] = bitmap
                // ✅ 更新 StateFlow，通知 UI
                // 不要重新创建stateflow，不然ui会丢失监听
                _pageBitmapStates.getOrPut(pageIndex){ MutableStateFlow(bitmap)}.value = bitmap
//                // 两种写法都可以，上面更简洁
//                if(_pageBitmapStates.containsKey(pageIndex)){
//                    _pageBitmapStates[pageIndex]?.value = bitmap
//                } else {
//                    _pageBitmapStates.put(pageIndex, MutableStateFlow(bitmap))
//                }
            }
            Log.d(TAG, "渲染页面 $pageIndex 完成 (${bitmap.width}x${bitmap.height})")
        } catch (e: Exception) {
            Log.e(TAG, "渲染页面 $pageIndex 失败", e)
        }
    }

    /**
     * 清理不需要的页面缓存
     */
    fun cleanupUnusedPages(currentPage: Int, keepNeighbourNumber: Int = 1) {
        val pagesToRemove = pageBitmaps.keys.filter {
            it !in (currentPage - keepNeighbourNumber..currentPage + keepNeighbourNumber)
        }
        pagesToRemove.forEach { pageIndex ->
            pageBitmaps.remove(pageIndex)?.recycle()
            // ✅ 同步清理 StateFlow，避免内存泄漏
            // 不删除stateflow
            _pageBitmapStates[pageIndex]?.value = null
            Log.d(TAG, "释放页面 $pageIndex 的 Bitmap")
        }
    }

    /**
     * 获取指定页面的文本内容（用于 TTS）
     */
    fun getPageText(pageIndex: Int): String? {
        val renderer = pdfRenderer ?: return null
        return try {
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                return null
            }
            val page = renderer.openPage(pageIndex)
            val textContents = page.getTextContents()
            Log.d(TAG, "获取页面 $pageIndex 文本, contents 数量为 ${textContents.size}")
            val fullText = textContents.joinToString(" ") { it.text ?: "" }
            Log.d(TAG, "页面 $pageIndex 文本预览: ${fullText.take(50)} ...")
            page.close()
            fullText.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "获取页面 $pageIndex 文本失败", e)
            null
        }
    }

    /**
     * 更新当前页码并保存进度
     */
    fun handlePageChanged(page: Int) {
        if (page < 0 || page >= _totalPages.value) return

        Log.d(TAG, "翻页到: $page")
        _currentPage.value = page

        // 保存阅读进度
        updatePdfProgress(
            uri = currentFileUri ?: return,
            pageIndex = page
        )
    }

    /**
     * 获取初始页码（用于恢复阅读进度）
     */
    fun getInitialPage(): Int {
        val savedState = readingState.value
        return if (savedState != null && savedState.page >= 0) {
            val page = savedState.page.coerceIn(0, _totalPages.value - 1)
            Log.d(TAG, "恢复上次阅读进度: $page")
            page
        } else {
            Log.d(TAG, "没有保存的阅读状态，从页码 0 开始")
            0
        }
    }

    /**
     * 更新 PDF 阅读进度（update 方式）
     * 基于当前状态更新指定字段，保留其他字段（如 isSwipeLayout）
     */
    fun updatePdfProgress(
        uri: String,
        pageIndex: Int
    ) {
        // ✅ 获取当前状态，如果不存在则创建新状态
        val currentState = readingState.value

        val progress = if (_totalPages.value > 0) pageIndex.toFloat() / _totalPages.value else 0f

        val newState = currentState?.let {
            // ✅ 基于当前状态更新，保留 isSwipeLayout 等其他字段
            it.copy(
                page = pageIndex,
                totalPages = _totalPages.value,
                progress = progress,
                lastReadTime = System.currentTimeMillis()
            )
        } ?: run {
            // 如果没有当前状态，创建新状态（默认 isSwipeLayout = true）
            ReadingState.Pdf(
                uri = uri,
                page = pageIndex,
                totalPages = _totalPages.value,
                progress = progress,
                lastReadTime = System.currentTimeMillis(),
                isSwipeLayout = true
            )
        }

        saveProgress(newState)
    }

    /**
     * 搜索 PDF 文本内容（双向交替搜索）
     * @param keyword 搜索关键词
     * @param startPage 起始页码（从当前页开始）
     * @param previewCharsNeighborLeft 预览上下文左侧字符数
     * @param previewCharsNeighborRight 预览上下文右侧字符数
     * @param maxCountOnePage 每页最多返回的结果数
     * @param searchSwitchStep 搜索方向切换步长
     * @return Flow<SearchData.PdfSearchResult> 搜索结果流
     */
    fun search(
        keyword: String,
        startPage: Int = 0,
        previewCharsNeighborLeft: Int = 25,
        previewCharsNeighborRight: Int = 25,
        maxCountOnePage: Int = 10,
        searchSwitchStep: Int = 20
    ): Flow<SearchData.PdfSearchResult> = flow {
        if (keyword.isEmpty()) {
            return@flow
        }

        val renderer = pdfRenderer
        if (renderer == null) {
            Log.e(TAG, "PDF 渲染器未初始化")
            return@flow
        }

        Log.d(TAG, "开始搜索关键词: '$keyword', 起始页: $startPage, 切换步长: $searchSwitchStep")
        var allCount = 0

        val totalPages = renderer.pageCount
        if (totalPages == 0) return@flow

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

            // ✅ 获取当前页文本并搜索
            try {
                val pageText = getPageText(currentPageIndex)
                if (!pageText.isNullOrBlank()) {
                    var startIndex = 0
                    var count = 0

                    // 在当前页中查找所有匹配
                    while (true) {
                        // ✅ 内层循环也要检查取消
                        currentCoroutineContext().ensureActive()
                        
                        val matchIndex = pageText.indexOf(keyword, startIndex, ignoreCase = true)
                        if (matchIndex == -1) break

                        // ✅ 提取上下文预览
                        val contextStart = (matchIndex - previewCharsNeighborLeft).coerceAtLeast(0)
                        val contextEnd =
                            (matchIndex + keyword.length + previewCharsNeighborRight).coerceAtMost(
                                pageText.length
                            )
                        val previewContent = pageText.substring(contextStart, contextEnd)

                        emit(
                            SearchData.PdfSearchResult(
                                keyword = keyword,
                                previewContent = previewContent,
                                pageIndex = currentPageIndex
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
                }
            } catch (e: Exception) {
                // ✅ 如果是取消异常，直接抛出
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }
                Log.e(TAG, "搜索第 $currentPageIndex 页时出错", e)
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

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "PdfViewModel 清除，清理资源")
        _onGoToPageListener = null
        cleanupRenderer()
    }

    override fun getViewModelName(): String {
        return "PdfViewModel"
    }

    companion object {
        private const val TAG = "PdfViewModel"
    }
}