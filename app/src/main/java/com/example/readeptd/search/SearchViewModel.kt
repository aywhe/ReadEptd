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

    // ✅ 当前选中的搜索结果索引
    private val _currentIndex = MutableStateFlow<Int>(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    // ✅ 2. 用于取消上一次搜索的 Job
    private var searchJob: Job? = null

    // ✅ 3. 搜索结果缓存：关键词 -> 结果列表
    private val searchCache = mutableMapOf<String, List<SearchData.SearchResult>>()

    /**
     * 执行搜索
     */
    fun onSearch(
        keyword: String,
        searchFun: ((String) -> Flow<SearchData.SearchResult>)?
    ) {
        // 如果没有提供搜索函数，或者关键词为空，则清空结果并返回
        if (searchFun == null || keyword.isBlank()) {
            _searchResults.value = emptyList()
            _currentIndex.value = -1
            return
        }

        // ✅ 检查缓存，如果已存在则直接返回
        val cachedResults = searchCache[keyword]
        if (cachedResults != null) {
            _searchResults.value = cachedResults
            _currentIndex.value = if (cachedResults.isNotEmpty()) 0 else -1
            return
        }

        // ✅ 取消上一次的搜索任务
        searchJob?.cancel()

        // ✅ 启动新的协程
        searchJob = viewModelScope.launch {
            _searchResults.value = emptyList()
            _currentIndex.value = -1

            // 使用 mutableStateListOf 还是 StateFlow？
            // 如果你坚持用 StateFlow，这里依然需要累积列表
            val results = mutableListOf<SearchData.SearchResult>()
            var lastUpdateCount = 0
            val updateInterval = 20

            try {
                // ✅ 调用传入的搜索函数并收集结果
                searchFun(keyword).collect { result ->
                    results.add(result)
                    
                    // ✅ 优化：每积累 20 个结果才更新一次 UI
                    if (results.size - lastUpdateCount >= updateInterval) {
                        _searchResults.value = results.toList()
                        lastUpdateCount = results.size
                    }
                }
            } catch (e: Exception) {
                // 处理搜索过程中可能出现的异常（如文件读取错误）
                e.printStackTrace()
            } finally {
                // ✅ 确保最后剩余的结果也能显示出来
                if (results.size > lastUpdateCount) {
                    _searchResults.value = results.toList()
                }
                
                // ✅ 缓存搜索结果
                if (results.isNotEmpty()) {
                    searchCache[keyword] = results.toList()
                    _currentIndex.value = 0
                }
            }
        }
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        searchCache.clear()
    }

    /**
     * 清除指定关键词的缓存
     */
    fun clearCache(keyword: String) {
        searchCache.remove(keyword)
    }

    /**
     * 导航到上一项
     */
    fun navigateToPrevious() {
        val currentIdx = _currentIndex.value
        val results = _searchResults.value
        if (results.isEmpty()) return

        _currentIndex.value = if (currentIdx <= 0) results.size - 1 else currentIdx - 1
    }

    /**
     * 导航到下一项
     */
    fun navigateToNext() {
        val currentIdx = _currentIndex.value
        val results = _searchResults.value
        if (results.isEmpty()) return

        _currentIndex.value = if (currentIdx >= results.size - 1) 0 else currentIdx + 1
    }

    /**
     * 获取当前选中的结果
     */
    fun getCurrentResult(): SearchData.SearchResult? {
        val idx = _currentIndex.value
        val results = _searchResults.value
        return if (idx in results.indices) results[idx] else null
    }

    /**
     * 设置当前选中的索引
     */
    fun setCurrentIndex(index: Int) {
        val results = _searchResults.value
        if (index in results.indices) {
            _currentIndex.value = index
        }
    }
}
