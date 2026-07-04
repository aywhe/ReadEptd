package com.example.readeptd.bookmark

/**
 * 书签基类
 * 不同的文件格式有不同的书签属性
 */
sealed interface BookmarkData {
    val id: Long
    val name: String
    val fileUri: String
    val mimeType: String
    val note: String?
    val createdTime: Long
    val lastModified: Long

    /**
     * EPUB 格式的书签
     * 支持 CFI
     */
    data class Epub(
        override val id: Long,
        override val fileUri: String,
        override val name: String,
        override val mimeType: String = "application/epub+zip",
        override val note: String? = null,
        override val createdTime: Long = System.currentTimeMillis(),
        override val lastModified: Long = System.currentTimeMillis(),

        val cfi: String
    ) : BookmarkData

    /**
     * PDF 格式的书签
     * 基于页码
     */
    data class Pdf(
        override val id: Long,
        override val fileUri: String,
        override val name: String,
        override val mimeType: String = "application/pdf",
        override val note: String? = null,
        override val createdTime: Long = System.currentTimeMillis(),
        override val lastModified: Long = System.currentTimeMillis(),

        val page: Int
    ) : BookmarkData

    /**
     * TXT 纯文本格式的书签
     * 基于字符偏移量
     */
    data class Txt(
        override val id: Long,
        override val fileUri: String,
        override val name: String,
        override val mimeType: String = "text/plain",
        override val note: String? = null,
        override val createdTime: Long = System.currentTimeMillis(),
        override val lastModified: Long = System.currentTimeMillis(),

        val charOffset: Long,          // 字符偏移量
    ) : BookmarkData

    /**
     * 未知或不支持格式
     * 仅记录基本信息
     */
    data class Unknown(
        override val id: Long,
        override val fileUri: String,
        override val name: String,
        override val mimeType: String = "application/octet-stream",
        override val note: String? = null,
        override val createdTime: Long = System.currentTimeMillis(),
        override val lastModified: Long = System.currentTimeMillis(),
    ) : BookmarkData
}
