package com.example.readeptd.books.epub

import android.app.Application
import com.example.readeptd.books.BookViewModel
import com.example.readeptd.data.ReadingState

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

    override fun getViewModelName(): String {
        return "EpubViewModel"
    }

    companion object {
        private const val TAG = "EpubViewModel"
    }
}