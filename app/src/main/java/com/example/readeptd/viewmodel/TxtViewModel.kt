package com.example.readeptd.viewmodel

import android.app.Application
import com.example.readeptd.data.ReadingState

/**
 * TXT 阅读器 ViewModel
 * 继承自 BookViewModel，提供 TXT 文件特有的功能
 */
class TxtViewModel(
    application: Application
) : BookViewModel<ReadingState.Txt>(application, ReadingState.Txt::class.java) {

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
