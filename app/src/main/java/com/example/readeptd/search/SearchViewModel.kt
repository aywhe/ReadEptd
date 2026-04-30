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

    // ✅ 搜索是否正在进行
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

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
            _isSearching.value = true  // ✅ 标记搜索开始
            _searchResults.value = emptyList()
            _currentIndex.value = -1

            // 使用 mutableStateListOf 还是 StateFlow？
            // 如果你坚持用 StateFlow，这里依然需要累积列表
            val results = mutableListOf<SearchData.SearchResult>()
            var lastUpdateCount = 0
            val updateInterval = 50  // ✅ 增加到 50，减少 UI 更新频率

            try {
                // ✅ 调用传入的搜索函数并收集结果
                searchFun(keyword).collect { result ->
                    results.add(result)
                    
                    // ✅ 优化：每积累 50 个结果才更新一次 UI
                    if (results.size - lastUpdateCount >= updateInterval) {
                        _searchResults.value = results.toList()
                        lastUpdateCount = results.size
                    }
                }
            } catch (e: Exception) {
                // 处理搜索过程中可能出现的异常（如文件读取错误）
                e.printStackTrace()
            } finally {
                // ✅ 统一排序并更新最终结果
                if (results.isNotEmpty()) {
                    val sortedResults = results.sortedBy { it.sortKey }
                    searchCache[keyword] = sortedResults
                    _searchResults.value = sortedResults
                    _currentIndex.value = 0
                } else {
                    // ✅ 确保无结果时也清空列表
                    _searchResults.value = emptyList()
                }
                
                _isSearching.value = false  // ✅ 标记搜索结束
            }
        }
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        searchCache.clear()
    }
    
    fun clearResults() {
        _searchResults.value = emptyList()
        _currentIndex.value = -1
    }

    /**
     * 清除指定关键词的缓存
     */
    fun clearCache(keyword: String) {
        searchCache.remove(keyword)
    }

    /**
     * 导航到上一项
     * @param bySortKey 是否按 sortKey 分组跳转（跳转到上一个不同的 sortKey）
     */
    fun navigateToPrevious(bySortKey: Boolean = false) {
        val currentIdx = _currentIndex.value
        val results = _searchResults.value
        if (results.isEmpty()) return

        if (!bySortKey) {
            // ✅ 普通模式：逐项循环导航
            _currentIndex.value = if (currentIdx <= 0) results.size - 1 else currentIdx - 1
        } else {
            // ✅ 按 sortKey 模式：跳转到上一个不同的 sortKey
            val currentSortKey = if (currentIdx >= 0 && currentIdx < results.size) {
                results[currentIdx].sortKey
            } else null

            var targetIndex = currentIdx
            var found = false
            
            // 向前搜索第一个不同 sortKey 的项目
            for (i in 1..results.size) {
                val idx = if (currentIdx - i >= 0) currentIdx - i else results.size + (currentIdx - i)
                if (currentSortKey == null || results[idx].sortKey != currentSortKey) {
                    targetIndex = idx
                    found = true
                    break
                }
            }
            
            if (found) {
                _currentIndex.value = targetIndex
            }
        }
    }

    /**
     * 导航到下一项
     * @param bySortKey 是否按 sortKey 分组跳转（跳转到下一个不同的 sortKey）
     */
    fun navigateToNext(bySortKey: Boolean = false) {
        val currentIdx = _currentIndex.value
        val results = _searchResults.value
        if (results.isEmpty()) return

        if (!bySortKey) {
            // ✅ 普通模式：逐项循环导航
            _currentIndex.value = if (currentIdx >= results.size - 1) 0 else currentIdx + 1
        } else {
            // ✅ 按 sortKey 模式：跳转到下一个不同的 sortKey
            val currentSortKey = if (currentIdx >= 0 && currentIdx < results.size) {
                results[currentIdx].sortKey
            } else null

            var targetIndex = currentIdx
            var found = false
            
            // 向后搜索第一个不同 sortKey 的项目
            for (i in 1..results.size) {
                val idx = (currentIdx + i) % results.size
                if (currentSortKey == null || results[idx].sortKey != currentSortKey) {
                    targetIndex = idx
                    found = true
                    break
                }
            }
            
            if (found) {
                _currentIndex.value = targetIndex
            }
        }
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

    /**
     * 找到距离指定位置最近的搜索结果索引
     * @param currentPosition 当前位置（页码/字符偏移等，与 sortKey 同类型）
     * @return 最近的搜索结果索引，如果没有结果返回 -1
     */
    fun findClosestResultIndex(currentPosition: Int): Int {
        val results = _searchResults.value
        if (results.isEmpty()) return -1
        
        var closestIndex = 0
        var minDistance = Int.MAX_VALUE
        
        results.forEachIndexed { index, result ->
            val distance = kotlin.math.abs(result.sortKey - currentPosition)
            if (distance < minDistance) {
                minDistance = distance
                closestIndex = index
            }
        }
        
        return closestIndex
    }
}
