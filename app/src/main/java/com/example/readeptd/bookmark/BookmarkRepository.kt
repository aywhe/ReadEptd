package com.example.readeptd.bookmark

import android.util.Log
import com.example.readeptd.dao.BookmarkDao
import com.example.readeptd.dao.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 书签仓库类
 *
 * 负责管理书签数据的增删查改操作
 * 将数据库实体与应用层数据模型进行转换
 */
class BookmarkRepository(private val bookmarkDao: BookmarkDao) {

    /**
     * 将 BookmarkEntity 转换为 BookmarkData
     * 根据不同的 mimeType 创建对应的 BookmarkData 子类
     */
    private fun bookmarkEntityToData(entity: BookmarkEntity): BookmarkData {
        return when(entity.mimeType){
            "application/pdf" -> BookmarkData.Pdf(
                id = entity.id,
                bookId = entity.bookId,
                fileUri = entity.fileUri,
                mimeType = entity.mimeType,
                note = entity.note,
                createdTime = entity.createdTime,
                lastModified = entity.lastModified,

                page = entity.position.toInt()
            )
            "text/plain" -> BookmarkData.Txt(
                id = entity.id,
                bookId = entity.bookId,
                fileUri = entity.fileUri,
                mimeType = entity.mimeType,
                note = entity.note,
                createdTime = entity.createdTime,
                lastModified = entity.lastModified,

                charOffset = entity.position.toLong()
            )
            "application/epub+zip" -> BookmarkData.Epub(
                id = entity.id,
                bookId = entity.bookId,
                fileUri = entity.fileUri,
                mimeType = entity.mimeType,
                note = entity.note,
                createdTime = entity.createdTime,
                lastModified = entity.lastModified,

                cfi = entity.position
            )
            else -> BookmarkData.Unknown(
                id = entity.id,
                bookId = entity.bookId,
                fileUri = entity.fileUri,
                mimeType = entity.mimeType,
                note = entity.note,
                createdTime = entity.createdTime,
                lastModified = entity.lastModified
            )
        }
    }

    /**
     * 将 BookmarkData 转换为 BookmarkEntity
     * 根据不同的 BookmarkData 子类设置 position 字段
     */
    private fun bookmarkDataToEntity(data: BookmarkData): BookmarkEntity {
        return BookmarkEntity(
            id = data.id,
            bookId = data.bookId,
            fileUri = data.fileUri,
            mimeType = data.mimeType,
            note = data.note,
            createdTime = data.createdTime,
            lastModified = data.lastModified,
            position = when(data){
                is BookmarkData.Pdf -> data.page.toString()
                is BookmarkData.Txt -> data.charOffset.toString()
                is BookmarkData.Epub -> data.cfi
                is BookmarkData.Unknown -> ""
            }
        )
    }

    /**
     * 添加书签
     * @param bookmark 要添加的书签数据
     */
    suspend fun addBookmark(bookmark: BookmarkData) {
        Log.d("BookmarkRepository", "Adding bookmark: $bookmark")
        bookmarkDao.insertBookmark(bookmarkDataToEntity(bookmark))
    }

    /**
     * 删除书签
     * @param id 要删除的书签 ID
     */
    suspend fun removeBookmark(id: Long) {
        Log.d("BookmarkRepository", "Removing bookmark with ID: $id")
        bookmarkDao.deleteBookmark(id)
    }

    /**
     * 更新书签
     * @param bookmark 要更新的书签数据
     */
    suspend fun updateBookmark(bookmark: BookmarkData) {
        Log.d("BookmarkRepository", "Updating bookmark: $bookmark")
        val entity = bookmarkDataToEntity(bookmark)
        bookmarkDao.updateBookmark(entity)
    }

    /**
     * 获取指定书籍的所有书签
     * @param bookId 书籍 ID
     * @return 返回一个 Flow，发射该书籍的所有书签数据列表
     */
    fun getBookmarksForBook(bookId: String): Flow<List<BookmarkData>> {
        Log.d("BookmarkRepository", "Fetching bookmarks for book ID: $bookId")
        return bookmarkDao.getBookmarksForBook(bookId)
            .map { entities -> entities.map { bookmarkEntityToData(it) } }
    }

    /**
     * 查找指定位置的书签
     * @param bookmark 要查找的书签数据（包含书籍 ID 和位置）
     * @return 返回一个 Flow，发射匹配的书签数据列表
     */
    fun findInPosition(bookmark: BookmarkData): Flow<List<BookmarkData>> {
        val bookmarkEntity = bookmarkDataToEntity(bookmark)
        Log.d("BookmarkRepository", "Finding bookmarks in position: ${bookmarkEntity.position} for book ID: ${bookmarkEntity.bookId}")
        return bookmarkDao.findPosition(bookmarkEntity.bookId, bookmarkEntity.position)
            .map { entities ->
                entities.map { bookmarkEntityToData(it) }
            }
    }

    /**
     * 检查指定位置是否存在书签
     * @param bookmark 要检查的书签数据（包含书籍 ID 和位置）
     * @return 返回一个 Flow，发射布尔值，表示是否存在书签
     */
    fun existInPosition(bookmark: BookmarkData): Flow<Boolean> {
        val bookmarkEntity = bookmarkDataToEntity(bookmark)
        Log.d("BookmarkRepository", "Checking existence of bookmark in position: ${bookmarkEntity.position} for book ID: ${bookmarkEntity.bookId}")
        return bookmarkDao.findPosition(bookmarkEntity.bookId, bookmarkEntity.position)
            .map { entities ->
                entities.isNotEmpty()
            }
    }
}