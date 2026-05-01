package com.example.readeptd.books.epub

import android.app.Application
import android.util.Log
import com.example.readeptd.books.BookViewModel
import com.example.readeptd.data.ReadingState
import com.example.readeptd.search.SearchData
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import kotlin.String

/**
 * EPUB 阅读器 ViewModel
 * 继承自 BookViewModel，提供 EPUB 文件特有的功能
 */
class EpubViewModel(
    application: Application
) : BookViewModel<ReadingState.Epub>(application, ReadingState.Epub::class.java) {

    /**
     * 保存 EPUB 阅读进度
     */
    fun saveEpubProgress(
        uri: String,
        cfi: String? = null,
        page: Int? = null,
        totalPages: Int? = null,
        progress: Float = 0f
    ) {
        val state = ReadingState.Epub(
            uri = uri,
            cfi = cfi,
            page = page,
            totalPages = totalPages,
            progress = progress,
            lastReadTime = System.currentTimeMillis()
        )
        saveProgress(state)
    }

    /**
     * 搜索 EPUB 文本内容
     * @param keyword 搜索关键词
     * @return Flow<SearchData.EpubSearchResult> 搜索结果流
     */
    fun search(keyword: String, webView: EpubWebView?): Flow<SearchData.EpubSearchResult> = callbackFlow {
        if (webView == null) {
            Log.e(TAG, "EPUB WebView 未初始化")
            close()
            return@callbackFlow
        }

        Log.d(TAG, "开始搜索关键词: '$keyword'")

        // ✅ 设置搜索结果回调
        webView.search(
            keyword = keyword,
            resultCallback = { result ->
                if(result != null) {
                    val searchResult = SearchData.EpubSearchResult(
                        keyword = result.query,
                        previewContent = result.excerpt,
                        chapterTitle = result.chapterTitle,
                        href = result.href,
                        cfi = result.cfi,
                        sortKey = result.sectionIndex * 1000 + result.matchIndex
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

    override fun getViewModelName(): String {
        return "EpubViewModel"
    }

    companion object {
        private const val TAG = "EpubViewModel"
    }
}