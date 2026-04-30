package com.example.readeptd.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 通用搜索 ViewModel 接口
 */
class SearchViewModel(
    application: Application
) : AndroidViewModel(application) {

    // ✅ 1. 定义 StateFlow：UI 层观察这个流
    // 初始值为空列表
    private val _searchResults = MutableStateFlow<List<SearchData.SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchData.SearchResult>> = _searchResults.asStateFlow()

    // ✅ 2. 用于取消上一次搜索的 Job
    private var searchJob: Job? = null

    /**
     * 执行搜索
     */
    fun onSearch(keyword: String, searchFun:((String)->Flow<SearchData.SearchResult>)? = null) {
        // 如果关键词为空，直接清空结果
        if (keyword.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        // ✅ 3. 取消上一次的搜索任务（关键！防止并发冲突）
        searchJob?.cancel()

        // ✅ 4. 启动新的协程
        searchJob = viewModelScope.launch {
            // 先清空旧结果，准备接收新结果
            _searchResults.value = emptyList()

            // 创建一个临时列表来累积结果
            val results = mutableListOf<SearchData.SearchResult>()

            // 收集 TxtViewModel 产生的流
            searchFun?.invoke(keyword)?.collect { result ->
                // 每找到一个结果，就加入列表
                results.add(result)

                // ✅ 5. 实时更新 StateFlow
                // 注意：StateFlow 需要赋值一个新的 List 对象才能触发 UI 更新
                _searchResults.value = results.toList()
            }
        }
    }
}

