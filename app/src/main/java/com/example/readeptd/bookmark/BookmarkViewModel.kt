package com.example.readeptd.bookmark

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.room.Room
import com.example.readeptd.dao.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class BookmarkViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val bookmarkRepository by lazy{BookmarkRepository(application)}
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
     * 获取指定书籍的所有书签
     * @param bookId 书籍 ID
     * @return 返回一个 Flow，发射该书籍的所有书签数据列表
     */
    fun getBookmarksForBook(bookId: String): Flow<List<BookmarkData>> {
        return bookmarkRepository.getBookmarksForBook(bookId)
    }

    /**
     * 删除指定书籍的所有书签
     * @param bookId 书籍 ID
     */
    suspend fun removeBookmarksForBook(bookId: String){
        bookmarkRepository.removeBookmarksForBook(bookId)
    }

    /**
     * 查找指定位置的书签
     * @param bookmark 要查找的书签数据（包含书籍 ID 和位置）
     * @return 返回一个 Flow，发射匹配的书签数据列表
     */
    fun findInPosition(bookmark: BookmarkData): Flow<List<BookmarkData>> {
        return bookmarkRepository.findInPosition(bookmark)
    }

    /**
     * 检查指定位置是否存在书签
     * @param bookmark 要检查的书签数据（包含书籍 ID 和位置）
     * @return 返回一个 Flow，发射布尔值，表示是否存在书签
     */
    fun existInPosition(bookmark: BookmarkData): Flow<Boolean> {
        Log.d("BookmarkViewModel", "Checking existence of bookmark in position: $bookmark")
        return findInPosition(bookmark)
            .map { entities ->
                entities.isNotEmpty()
            }
    }
}