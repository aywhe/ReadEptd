package com.example.readeptd.search

import android.app.Application
import android.util.Log
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
    
    // ✅ 3. 当前正在搜索的关键词
    private var currentSearchingKeyword: String? = null
    
    // ✅ 4. 最后搜索完成的关键词（用于判断是否执行过搜索）
    private val _lastSearchedKeyword = MutableStateFlow("")
    val lastSearchedKeyword: StateFlow<String> = _lastSearchedKeyword.asStateFlow()

    // ✅ 5. 搜索结果缓存：关键词 -> 结果列表 (使用 LRU 缓存,最多保留5个)
    private val searchCache = object : LinkedHashMap<String, List<SearchData.SearchResult>>(5, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SearchData.SearchResult>>?): Boolean {
            return size > 5  // 超过5个就移除最久未使用的
        }
    }

    // ✅ 6. 搜索历史记录（使用 LRU Map，key=keyword, value=时间戳）
    private val historyKeywordsMap = object : LinkedHashMap<String, Long>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > 20  // 超过20个就移除最久未使用的
        }
    }

    private var _onClickHistoryKeyword: ((String) -> Unit)? = null

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
            currentSearchingKeyword = null
            _lastSearchedKeyword.value = ""
            return
        }

        // ✅ 添加到搜索历史（LRU 自动管理）
        addToHistory(keyword)

        // ✅ 检查缓存，如果已存在则直接返回
        val cachedResults = searchCache[keyword]
        if (cachedResults != null) {
            _searchResults.value = cachedResults
            _currentIndex.value = if (cachedResults.isNotEmpty()) 0 else -1
            currentSearchingKeyword = null
            _lastSearchedKeyword.value = keyword
            return
        }

        // ✅ 取消上一次的搜索任务
        searchJob?.cancel()

        // ✅ 记录当前正在搜索的关键词（在启动新协程之前设置）
        currentSearchingKeyword = keyword

        // ✅ 启动新的协程
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = emptyList()
            _currentIndex.value = -1

            // 使用 mutableStateListOf 还是 StateFlow？
            // 如果你坚持用 StateFlow，这里依然需要累积列表
            val results = mutableListOf<SearchData.SearchResult>()
            var lastUpdateTime = System.currentTimeMillis()
            var lastUpdateCount = 0
            val updateIntervalMs = 2000L
            val updateItemCount = 20

            try {
                // ✅ 调用传入的搜索函数并收集结果
                searchFun(keyword).collect { result ->
                    results.add(result)

                    val currentTime = System.currentTimeMillis()
                    val countDiff = results.size - lastUpdateCount
                    val timeDiff = currentTime - lastUpdateTime
                    
                    // ✅ 任一条件满足即更新：数量达到间隔 OR 时间超过间隔
                    if (countDiff >= updateItemCount || timeDiff >= updateIntervalMs) {
                        _searchResults.value = results.toList()
                        lastUpdateTime = currentTime
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
                
                // ✅ 设置最后搜索的关键词（无论有无结果）
                _lastSearchedKeyword.value = keyword
                
                // ✅ 只有在当前关键词仍然是这个 keyword 时才清除（避免新搜索覆盖）
                if (currentSearchingKeyword == keyword) {
                    currentSearchingKeyword = null
                }
                _isSearching.value = false
            }
        }
    }

    /**
     * 添加关键词到历史记录（LRU 自动管理）
     */
    private fun addToHistory(keyword: String) {
        // ✅ LinkedHashMap 会自动将访问过的 key 移到末尾（accessOrder=true）
        historyKeywordsMap[keyword] = System.currentTimeMillis()
    }

    /**
     * 停止搜索
     */
    fun stopSearching() {
        // ✅ 在取消 Job 之前，先保存当前关键词（避免竞态条件）
        val keywordToClear = currentSearchingKeyword
        
        // ✅ 取消搜索任务
        searchJob?.cancel()
        searchJob = null
        
        // ✅ 使用保存的关键词清理缓存（允许重新搜索）
        if (keywordToClear != null) {
            clearCache(keywordToClear)
        }
        
        // ✅ 重置状态
        currentSearchingKeyword = null
        _isSearching.value = false
        
        // ✅ 注意：不清空 _searchResults 和 _lastSearchedKeyword，保留已搜索到的部分结果供用户查看
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
     * 清空搜索历史
     */
    fun clearHistory() {
        historyKeywordsMap.clear()
    }

    /**
     * 删除单条历史记录
     */
    fun removeHistoryKeyword(keyword: String) {
        historyKeywordsMap.remove(keyword)
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
     * 获取搜索历史关键词列表（按访问时间倒序，最新的在前）
     */
    fun getKeywords(): List<String>{
        return historyKeywordsMap.keys.toList().reversed()
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

    fun setOnClickHistoryKeyword(onClickHistoryKeyword: ((String) -> Unit)?) {
        _onClickHistoryKeyword = onClickHistoryKeyword
        Log.d("SearchViewModel", "setOnClickHistoryKeyword: ${onClickHistoryKeyword != null}")
    }
    fun onEvent(event: SearchEvent) {
        Log.d("SearchViewModel", "onEvent: $event, _onClickHistoryKeyword is null: ${_onClickHistoryKeyword == null}")
        when (event) {
            is SearchEvent.onClickHistoryKeyword -> {
                _onClickHistoryKeyword?.invoke(event.keyword)
            }
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
        var minDistance = Long.MAX_VALUE
        
        results.forEachIndexed { index, result ->
            val distance = kotlin.math.abs(result.sortKey - currentPosition)
            if (distance < minDistance) {
                minDistance = distance
                closestIndex = index
            }
        }
        
        return closestIndex
    }

    override fun onCleared() {
        super.onCleared()
        stopSearching()  // ✅ 清理时停止搜索
        clearCache()
        clearResults()
        clearHistory()
        _onClickHistoryKeyword = null
        Log.d("SearchViewModel", "onCleared")
    }
}
