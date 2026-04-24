package com.example.readeptd.data

/**
 * 阅读状态基类
 * 不同的文件格式有不同的阅读状态属性
 */
sealed interface ReadingState {
    val uri: String
    val lastReadTime: Long
    val mimeType: String
    val progress: Float
    
    /**
     * EPUB 格式的阅读状态
     * 支持 CFI 定位和页码导航
     */
    data class Epub(
        override val uri: String,
        val cfi: String? = null,           // EPUB CFI 定位符
        val page: Int? = null,             // 当前页码
        val totalPages: Int? = null,       // 总页数
        override val progress: Float = 0f,          // 阅读进度 0.0-1.0
        override val lastReadTime: Long = System.currentTimeMillis(),
        override val mimeType: String = "application/epub+zip"
    ) : ReadingState
    
    /**
     * PDF 格式的阅读状态
     * 基于页码和缩放比例
     */
    data class Pdf(
        override val uri: String,
        val page: Int = 1,                 // 当前页码
        val totalPages: Int = 1,           // 总页数
        override val progress: Float = 0f,
        override val lastReadTime: Long = System.currentTimeMillis(),
        override val mimeType: String = "application/pdf"
    ) : ReadingState
    
    /**
     * TXT 纯文本格式的阅读状态
     * 基于字符偏移量或行号
     */
    data class Txt(
        override val uri: String,
        val charOffset: Long = 0,          // 字符偏移量
        override val progress: Float = 0f,
        override val lastReadTime: Long = System.currentTimeMillis(),
        override val mimeType: String = "text/plain"
    ) : ReadingState
    
    /**
     * 未知或不支持格式的阅读状态
     * 仅记录基本信息
     */
    data class Unknown(
        override val uri: String,
        override val progress: Float = 0f,
        override val lastReadTime: Long = System.currentTimeMillis(),
        override val mimeType: String = "application/octet-stream"
    ) : ReadingState
}
