package com.example.readeptd.books.epub

import android.app.Application
import android.util.Log
import com.example.readeptd.books.BookViewModel
import com.example.readeptd.data.ReadingState
import com.example.readeptd.search.SearchData
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.ArrayDeque
import java.util.Queue
import kotlin.String

/**
 * EPUB 阅读器 ViewModel
 * 继承自 BookViewModel，提供 EPUB 文件特有的功能
 */
class EpubViewModel(
    application: Application
) : BookViewModel<ReadingState.Epub>(application, ReadingState.Epub::class.java) {

    private val _currentLocation = MutableStateFlow(EpubLocation.default())
    val currentLocation: StateFlow<EpubLocation> = _currentLocation.asStateFlow()

    fun updateLocation(location: EpubLocation) {
        _currentLocation.value = location
    }

    /**
     * 更新 EPUB 阅读进度（update 方式）
     * 基于当前状态更新指定字段，保留其他字段（如 isSwipeLayout）
     */
    fun updateEpubProgress(
        uri: String,
        cfi: String? = null,
        page: Int? = null,
        totalPages: Int? = null,
        progress: Float = 0f
    ) {
        // ✅ 获取当前状态，如果不存在则创建新状态
        val currentState = readingState.value
        
        val newState = currentState?.let {
            // ✅ 基于当前状态更新，保留 isSwipeLayout 等其他字段
            it.copy(
                cfi = cfi,
                page = page,
                totalPages = totalPages,
                progress = progress,
                lastReadTime = System.currentTimeMillis()
            )
        } ?: run {
            // 如果没有当前状态，创建新状态（默认 isSwipeLayout = true）
            ReadingState.Epub(
                uri = uri,
                cfi = cfi,
                page = page,
                totalPages = totalPages,
                progress = progress,
                lastReadTime = System.currentTimeMillis(),
                isSwipeLayout = true
            )
        }
        
        saveProgress(newState)
    }

    /**
     * 搜索 EPUB 文本内容
     * @param keyword 搜索关键词
     * @return Flow<SearchData.EpubSearchResult> 搜索结果流
     */
    fun search(keyword: String, epubWebView: EpubWebView?): Flow<SearchData.EpubSearchResult> = callbackFlow {
        if (epubWebView == null) {
            Log.e(TAG, "EPUB WebView 未初始化")
            close()
            return@callbackFlow
        }

        Log.d(TAG, "开始搜索关键词: '$keyword'")

        // ✅ 设置搜索结果回调
        epubWebView.search(
            keyword = keyword,
            resultCallback = { result ->
                if(result != null) {
                    val searchResult = SearchData.EpubSearchResult(
                        keyword = result.query,
                        previewContent = result.excerpt,
                        chapterTitle = result.chapterTitle,
                        href = result.href,
                        cfi = result.cfi,
                        sortKey = (result.sectionIndex * 1000 + result.matchIndex).toLong()
                    )
                    // ✅ 发送结果到 Flow
                    trySend(searchResult)
                } else {
                    Log.e(TAG, "搜索结果为空")
                }
            },
            completedCallback = {
                Log.d(TAG, "搜索完成")
                close()  // ✅ 搜索完成，关闭 Flow
            }
        )

        // ✅ 当 Flow 被取消时（例如用户取消搜索），清理资源
        awaitClose {
            Log.d(TAG, "搜索被取消")
        }
    }

    private val highlightedCFIs: Queue<String> = ArrayDeque()

    fun removeAllHighlights(epubWebView: EpubWebView?) {
        if (epubWebView == null) {
            Log.w(TAG, "EPUB WebView 未初始化，无法执行清除操作")
            return
        }

        // 移除所有已存在的高亮
        while (highlightedCFIs.isNotEmpty()) {
            val oldCfi = highlightedCFIs.poll()
            if (oldCfi != null) {
                epubWebView.highlight(oldCfi, true)
            }
        }
    }
    
    /**
     * 清除所有高亮并添加新的高亮
     * @param cfi 要高亮的 CFI 位置
     * @param epubWebView EPUB WebView 实例
     */
    fun highlightSingle(cfi: String, epubWebView: EpubWebView?) {
        if (epubWebView == null) {
            Log.w(TAG, "EPUB WebView 未初始化，无法执行高亮操作")
            return
        }

        removeAllHighlights(epubWebView)
        
        // 添加新的高亮
        epubWebView.highlight(cfi, false)
        highlightedCFIs.add(cfi)
        Log.d(TAG, "添加高亮: $cfi")
    }

    override fun getViewModelName(): String {
        return "EpubViewModel"
    }

    companion object {
        private const val TAG = "EpubViewModel"
    }
}