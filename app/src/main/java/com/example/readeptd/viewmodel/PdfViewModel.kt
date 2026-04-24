package com.example.readeptd.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.readeptd.data.ReadingState
import com.example.readeptd.ui.PdfEvent
import com.example.readeptd.ui.TxtEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * PDF 阅读器 ViewModel
 * 继承自 BookViewModel，提供 PDF 文件特有的功能
 */
class PdfViewModel(
    application: Application
) : BookViewModel<ReadingState.Pdf>(application, ReadingState.Pdf::class.java) {

    // 当前页码（用于 UI 显示和进度保存）
    private val _currentPage = MutableStateFlow(0)
    private val _totalPages = MutableStateFlow(0)

    fun onEvent(event: PdfEvent) {
        when (event) {
            is PdfEvent.OnPageChanged -> handlePageChanged(event.pageIndex)
        }
    }

    fun setTotalPages(totalPages: Int) {
        _totalPages.value = totalPages
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
        return if (savedState != null && savedState.page > 0) {
            val page = (savedState.page).coerceIn(0, _totalPages.value - 1)
            Log.d(TAG, "恢复上次阅读进度: 第 ${page + 1} 页")
            page
        } else {
            Log.d(TAG, "没有保存的阅读状态，从第一页开始")
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

    override fun getViewModelName(): String {
        return "PdfViewModel"
    }

    companion object {
        private const val TAG = "PdfViewModel"
    }
}
