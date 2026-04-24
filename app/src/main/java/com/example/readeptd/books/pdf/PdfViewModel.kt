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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * PDF 阅读器 ViewModel
 * 继承自 BookViewModel，提供 PDF 文件特有的功能
 */
class PdfViewModel(
    application: Application
) : BookViewModel<ReadingState.Pdf>(application, ReadingState.Pdf::class.java) {

    // PDF 渲染器相关状态
    private var pdfRenderer: PdfRenderer? = null
    private val pageBitmaps = mutableMapOf<Int, Bitmap>()

    // 用于保护页面缓存的互斥锁，避免并发访问导致的问题
    private val pageCacheMutex = Mutex()

    // 当前页码（用于 UI 显示和进度保存）
    private val _currentPage = MutableStateFlow(0)
    private val _totalPages = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

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

    /**
     * 初始化 PDF 渲染器
     */
    fun initializeRenderer(filePath: String): Boolean {
        return try {
            cleanupRenderer()
            val file = File(filePath)
            val fileDescriptor =
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor)
            _totalPages.value = pdfRenderer?.pageCount ?: 0
            Log.d(TAG, "PDF 渲染器初始化成功，总页数: ${_totalPages.value}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "PDF 渲染器初始化失败", e)
            false
        }
    }

    /**
     * 清理渲染器资源
     */
    fun cleanupRenderer() {
        pageBitmaps.values.forEach { it.recycle() }
        pageBitmaps.clear()
        pdfRenderer?.close()
        pdfRenderer = null
        Log.d(TAG, "PDF 渲染器资源已释放")
    }

    /**
     * 渲染指定页面及其周围页面
     */
    fun renderPage(
        currentPage: Int,
        keepNeighbourNumber: Int = 1,
        bitMapWhScale: Int = 3,
        callback: (Bitmap?) -> Unit = {}
    ) {
        val renderer = pdfRenderer
        if (renderer != null && currentPage >= 0 && currentPage < renderer.pageCount) {
            // 检查是否已渲染，避免重复渲染
            if (!pageBitmaps.containsKey(currentPage)) {
                // 优先渲染当前页
                renderOnePage(renderer, currentPage, bitMapWhScale)
            }
            try {
                for (offset in 1..keepNeighbourNumber) {
                    val prevPage = currentPage - offset
                    val nextPage = currentPage + offset

                    // 渲染前一页（如果在范围内）
                    if (prevPage >= 0 && !pageBitmaps.containsKey(prevPage)) {
                        renderOnePage(renderer, prevPage, bitMapWhScale)
                    }

                    // 渲染后一页（如果在范围内）
                    if (nextPage < renderer.pageCount && !pageBitmaps.containsKey(nextPage)) {
                        renderOnePage(renderer, nextPage, bitMapWhScale)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "渲染页面 $currentPage 失败", e)
            }
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
    private fun renderOnePage(renderer: PdfRenderer, pageIndex: Int, bitMapWhScale: Int) {
        // 检查是否已渲染，防止重复渲染
        if (pageBitmaps.containsKey(pageIndex)) {
            return
        }

        try {
            val page = renderer.openPage(pageIndex)
            val bitmap = createBitmap(page.width * bitMapWhScale, page.height * bitMapWhScale)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            pageBitmaps[pageIndex] = bitmap
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
            Log.d(TAG, "页面 $pageIndex 文本预览: ${fullText.take(50)}")
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
        savePdfProgress(
            uri = currentFileUri ?: return,
            pageIndex = page
        )
    }

    /**
     * 获取初始页码（用于恢复阅读进度）
     */
    fun getInitialPage(): Int {
        val savedState = currentReadingState
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
     * 保存 PDF 阅读进度
     */
    fun savePdfProgress(
        uri: String,
        pageIndex: Int
    ) {
        val progress = if (_totalPages.value > 0) pageIndex.toFloat() / _totalPages.value else 0f
        val state = ReadingState.Pdf(
            uri = uri,
            page = pageIndex,
            totalPages = _totalPages.value,
            progress = progress,
            lastReadTime = System.currentTimeMillis()
        )
        saveProgress(state)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "PdfViewModel 清除，清理资源")
        cleanupRenderer()
    }

    override fun getViewModelName(): String {
        return "PdfViewModel"
    }

    companion object {
        private const val TAG = "PdfViewModel"
    }
}