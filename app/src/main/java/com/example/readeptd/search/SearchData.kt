package com.example.readeptd.search

class SearchData {
    /**
     * 通用搜索结果接口
     */
    interface SearchResult {
        val keyword: String           // 搜索关键词
        val previewContent: String           // 上下文预览
        val displayName: String       // 显示名称（如"第5页"、"第三章"）
        val sortKey: Long              // 排序键（用于结果排序）
    }

    /**
     * TXT 搜索结果
     */
    data class TxtSearchResult(
        override val keyword: String,
        override val previewContent: String,
        val pageIndex: Int,           // 页码
        val charOffset: Long,          // 字符偏移
        val charOffsetInPage: Int,     // 在该页中的字符位置
        override val displayName: String = "第 ${pageIndex + 1} 页",
        override val sortKey: Long = charOffset
    ) : SearchResult

    /**
     * EPUB 搜索结果
     */
    data class EpubSearchResult(
        override val keyword: String,
        override val previewContent: String,
        val chapterTitle: String,     // 章节标题
        val href: String,             // 章节链接
        val cfi: String,              // CFI 位置
        override val displayName: String = chapterTitle,
        override val sortKey: Long = 0  // EPUB 按文档顺序
    ) : SearchResult

    /**
     * PDF 搜索结果
     */
    data class PdfSearchResult(
        override val keyword: String,
        override val previewContent: String,
        val pageIndex: Int,           // 页码
        override val displayName: String = "第 ${pageIndex + 1} 页",
        override val sortKey: Long = pageIndex.toLong()
    ) : SearchResult
}