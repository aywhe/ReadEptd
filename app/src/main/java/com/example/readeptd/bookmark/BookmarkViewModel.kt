package com.example.readeptd.bookmark

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
class BookmarkViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val bookmarkRepository by lazy { BookmarkRepository(application) }

    // 用 StateFlow 管理当前文件 URI
    private val _currentFileUri = MutableStateFlow("")

    // 当 currentFileUri 变化时，自动切换查询
    @OptIn(ExperimentalCoroutinesApi::class)
    val bookmarks: StateFlow<List<BookmarkData>> = _currentFileUri
        .filter { it.isNotEmpty() }  // 过滤空字符串
        .flatMapLatest { uri ->
            bookmarkRepository.getBookmarksForBook(uri)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun prepareBookFile(uri: String) {
        _currentFileUri.value = uri
    }

    /**
     * 添加书签
     * @param bookmark 要添加的书签数据
     */
    suspend fun addBookmark(bookmark: BookmarkData) {
        return bookmarkRepository.addBookmark(bookmark)
    }

    /**
     * 删除书签
     * @param id 要删除的书签 ID
     */
    suspend fun removeBookmark(id: Long) {
        bookmarkRepository.removeBookmark(id)
    }

    /**
     * 更新书签
     * @param bookmark 要更新的书签数据
     */
    suspend fun updateBookmark(bookmark: BookmarkData) {
        bookmarkRepository.updateBookmark(bookmark)
    }

    /**
     * 删除指定书籍的所有书签
     */
    suspend fun removeAllBookmarks(){
        bookmarkRepository.removeBookmarksForBook(_currentFileUri.value)
    }

    /**
     * 查找指定位置的书签
     * @param bookmark 要查找的书签数据（包含书籍 ID 和位置）
     * @return 返回一个 Flow，发射匹配的书签数据列表
     */
    fun findInPosition(bookmark: BookmarkData): Flow<List<BookmarkData>> {
        Log.d("BookmarkViewModel", "Look for existence of bookmark in position: $bookmark")
        return bookmarks.map { entities ->
            entities.filter { it.isInPosition(bookmark) }
        }
    }


    /**
     * 检查指定位置是否存在书签
     * @param bookmark 要检查的书签数据（包含书籍 ID 和位置）
     * @return 返回一个 Flow，发射布尔值，表示是否存在书签
     */
    fun existInPosition(bookmark: BookmarkData): Flow<Boolean> {
        Log.d("BookmarkViewModel", "Checking existence of bookmark in position: $bookmark")
        return bookmarks.map { entities ->
            entities.any { it.isInPosition(bookmark) }
        }
    }
}