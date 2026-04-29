package com.example.readeptd.search

import kotlinx.coroutines.flow.StateFlow

/**
 * 通用搜索 ViewModel 接口
 */
interface SearchableViewModel {
    val searchResults: StateFlow<List<SearchData.SearchResult>>
    val isSearching: StateFlow<Boolean>
    var currentKeyword: String

    fun search(keyword: String)
    fun clearSearch()
}
