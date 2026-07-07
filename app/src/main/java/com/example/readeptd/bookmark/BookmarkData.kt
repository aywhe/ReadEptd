package com.example.readeptd.bookmark

/**
 * 书签基类
 * 不同的文件格式有不同的书签属性
 */
sealed interface BookmarkData: Comparable<BookmarkData> {
    val id: Long
    val bookId: String
    val note: String
    val createdTime: Long

    fun isInPosition(other:BookmarkData): Boolean
    fun copyVal(
        id: Long = this.id,
        bookId: String = this.bookId,
        note: String = this.note,
        createdTime: Long = this.createdTime,
    ): BookmarkData

    override fun compareTo(other: BookmarkData): Int {
        return 0
    }
    /**
     * EPUB 格式的书签
     * 支持 CFI
     */
    data class Epub(
        override val id: Long = 0,
        override val bookId: String,
        override val note: String = "",
        override val createdTime: Long = System.currentTimeMillis(),
        val cfi: String,
        val loc: Long
    ) : BookmarkData {
        override fun isInPosition(other:BookmarkData): Boolean{
            return false
        }
        override fun copyVal(
            id: Long,
            bookId: String,
            note: String,
            createdTime: Long,
        ): BookmarkData {
            return Epub(
                id = id,
                bookId = bookId,
                note = note,
                createdTime = createdTime,
                cfi = this.cfi,
                loc = this.loc
            )
        }
        override fun compareTo(other: BookmarkData): Int{
            return if(other is BookmarkData.Epub) (loc - other.loc).toInt() else 0
        }
    }

    /**
     * PDF 格式的书签
     * 基于页码
     */
    data class Pdf(
        override val id: Long = 0,
        override val bookId: String,
        override val note: String = "",
        override val createdTime: Long = System.currentTimeMillis(),
        val pageNumber: Int
    ) : BookmarkData{
        override fun isInPosition(other:BookmarkData): Boolean{
            return other is BookmarkData.Pdf && other.pageNumber == pageNumber
        }
        override fun copyVal(
            id: Long,
            bookId: String,
            note: String,
            createdTime: Long,
        ): BookmarkData {
            return Pdf(
                id = id,
                bookId = bookId,
                note = note,
                createdTime = createdTime,
                pageNumber = this.pageNumber
            )
        }
        override fun compareTo(other: BookmarkData): Int{
            return if(other is BookmarkData.Pdf) pageNumber - other.pageNumber else 0
        }
    }

    /**
     * TXT 纯文本格式的书签
     * 基于字符偏移量
     */
    data class Txt(
        override val id: Long = 0,
        override val bookId: String,
        override val note: String = "",
        override val createdTime: Long = System.currentTimeMillis(),
        val startPos: Long,
        val endPos: Long
    ) : BookmarkData{
        override fun isInPosition(other:BookmarkData): Boolean{
            return other is BookmarkData.Txt
                    && startPos >= other.startPos
                    && startPos < other.endPos
        }
        override fun copyVal(
            id: Long,
            bookId: String,
            note: String,
            createdTime: Long,
        ): BookmarkData {
            return Txt(
                id = id,
                bookId = bookId,
                note = note,
                createdTime = createdTime,
                startPos = this.startPos,
                endPos = this.endPos
            )
        }
        override fun compareTo(other: BookmarkData): Int{
            return if(other is BookmarkData.Txt) (startPos - other.startPos).toInt() else 0
        }
    }
}
