package com.example.readeptd.bookmark

import com.example.readeptd.dao.BookmarkDao
import com.example.readeptd.dao.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookmarkRepository(private val bookmarkDao: BookmarkDao) {

    private fun bookmarkEntityToData(entity: BookmarkEntity): BookmarkData {
        return when(entity.mimeType){
            "application/pdf" -> BookmarkData.Pdf(
                id = entity.id,
                name = entity.bookId,
                fileUri = entity.fileUri,
                mimeType = entity.mimeType,
                note = entity.note,
                createdTime = entity.createdTime,
                lastModified = entity.lastModified,

                page = entity.position.toInt()
            )
            "text/plain" -> BookmarkData.Txt(
                id = entity.id,
                name = entity.bookId,
                fileUri = entity.fileUri,
                mimeType = entity.mimeType,
                note = entity.note,
                createdTime = entity.createdTime,
                lastModified = entity.lastModified,

                charOffset = entity.position.toLong()
            )
            "application/epub+zip" -> BookmarkData.Epub(
                id = entity.id,
                name = entity.bookId,
                fileUri = entity.fileUri,
                mimeType = entity.mimeType,
                note = entity.note,
                createdTime = entity.createdTime,
                lastModified = entity.lastModified,

                cfi = entity.position
            )
            else -> BookmarkData.Unknown(
                id = entity.id,
                name = entity.bookId,
                fileUri = entity.fileUri,
                mimeType = entity.mimeType,
                note = entity.note,
                createdTime = entity.createdTime,
                lastModified = entity.lastModified
            )
        }
    }

    private fun bookmarkDataToEntity(data: BookmarkData): BookmarkEntity {
        return BookmarkEntity(
            id = data.id,
            bookId = data.name,
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

    suspend fun addBookmark(bookmark: BookmarkData) {
        bookmarkDao.insertBookmark(bookmarkDataToEntity(bookmark))
    }

    suspend fun removeBookmark(id: Long) {
        bookmarkDao.deleteBookmark(id)
    }

    fun getBookmarksForFile(bookId: String): Flow<List<BookmarkData>> {
        return bookmarkDao.getBookmarksForBook(bookId)
            .map { entities -> entities.map { bookmarkEntityToData(it) } }
    }
}