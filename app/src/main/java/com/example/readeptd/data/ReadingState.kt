package com.example.readeptd.data

import androidx.compose.ui.geometry.Offset
import org.json.JSONObject

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
     * 将阅读状态转换为 JSON 字符串
     */
    fun toJson(): String
    
    companion object {
        private const val KEY_MIME_TYPE = "mimeType"
        private const val KEY_URI = "uri"
        private const val KEY_LAST_READ_TIME = "lastReadTime"
        private const val KEY_PROGRESS = "progress"
        
        /**
         * 从 JSON 字符串恢复阅读状态
         */
        fun fromJson(jsonString: String): ReadingState {
            val jsonObject = JSONObject(jsonString)
            val mimeType = jsonObject.optString(KEY_MIME_TYPE, "application/octet-stream")
            val uri = jsonObject.getString(KEY_URI)
            val lastReadTime = jsonObject.optLong(KEY_LAST_READ_TIME, System.currentTimeMillis())
            val progress = jsonObject.optDouble(KEY_PROGRESS, 0.0).toFloat()
            
            return when {
                mimeType == "application/epub+zip" || mimeType.contains("epub") -> {
                    Epub(
                        uri = uri,
                        cfi = if (jsonObject.has(Epub.KEY_CFI)) jsonObject.getString(Epub.KEY_CFI) else null,
                        page = if (jsonObject.has(Epub.KEY_PAGE)) jsonObject.getInt(Epub.KEY_PAGE) else null,
                        totalPages = if (jsonObject.has(Epub.KEY_TOTAL_PAGES)) jsonObject.getInt(Epub.KEY_TOTAL_PAGES) else null,
                        progress = progress,
                        lastReadTime = lastReadTime
                    )
                }
                mimeType == "application/pdf" -> {
                    val zoom = jsonObject.optDouble(Pdf.KEY_ZOOM, 1.0).toFloat()
                    val zoomOffsetX = jsonObject.optDouble(Pdf.KEY_ZOOM_OFFSET_X, 0.0).toFloat()
                    val zoomOffsetY = jsonObject.optDouble(Pdf.KEY_ZOOM_OFFSET_Y, 0.0).toFloat()
                    
                    Pdf(
                        uri = uri,
                        page = jsonObject.optInt(Pdf.KEY_PAGE, 1),
                        totalPages = jsonObject.optInt(Pdf.KEY_TOTAL_PAGES, 1),
                        zoom = zoom,
                        zoomOffset = Offset(zoomOffsetX, zoomOffsetY),
                        progress = progress,
                        lastReadTime = lastReadTime
                    )
                }
                mimeType == "text/plain" -> {
                    Txt(
                        uri = uri,
                        charOffset = jsonObject.optLong(Txt.KEY_CHAR_OFFSET, 0),
                        progress = progress,
                        lastReadTime = lastReadTime
                    )
                }
                else -> {
                    Unknown(
                        uri = uri,
                        progress = progress,
                        lastReadTime = lastReadTime
                    )
                }
            }
        }
    }
    
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
    ) : ReadingState {
        companion object {
            const val KEY_CFI = "cfi"
            const val KEY_PAGE = "page"
            const val KEY_TOTAL_PAGES = "totalPages"
        }
        
        override fun toJson(): String {
            return JSONObject().apply {
                put(KEY_MIME_TYPE, mimeType)
                put(KEY_URI, uri)
                put(KEY_LAST_READ_TIME, lastReadTime)
                put(KEY_PROGRESS, progress)
                
                cfi?.let { put(KEY_CFI, it) }
                page?.let { put(KEY_PAGE, it) }
                totalPages?.let { put(KEY_TOTAL_PAGES, it) }
            }.toString()
        }
    }
    
    /**
     * PDF 格式的阅读状态
     * 基于页码和缩放比例
     */
    data class Pdf(
        override val uri: String,
        val page: Int = 1,                 // 当前页码
        val totalPages: Int = 1,           // 总页数
        val zoom: Float = 1f,
        val zoomOffset: Offset = Offset.Zero,
        override val progress: Float = 0f,
        override val lastReadTime: Long = System.currentTimeMillis(),
        override val mimeType: String = "application/pdf"
    ) : ReadingState {
        companion object {
            const val KEY_PAGE = "page"
            const val KEY_TOTAL_PAGES = "totalPages"
            const val KEY_ZOOM = "zoom"
            const val KEY_ZOOM_OFFSET_X = "zoomOffsetX"
            const val KEY_ZOOM_OFFSET_Y = "zoomOffsetY"
        }
        
        override fun toJson(): String {
            return JSONObject().apply {
                put(KEY_MIME_TYPE, mimeType)
                put(KEY_URI, uri)
                put(KEY_LAST_READ_TIME, lastReadTime)
                put(KEY_PROGRESS, progress)
                put(KEY_PAGE, page)
                put(KEY_TOTAL_PAGES, totalPages)
                put(KEY_ZOOM, zoom)
                put(KEY_ZOOM_OFFSET_X, zoomOffset.x)
                put(KEY_ZOOM_OFFSET_Y, zoomOffset.y)
            }.toString()
        }
    }
    
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
    ) : ReadingState {
        companion object {
            const val KEY_CHAR_OFFSET = "charOffset"
        }
        
        override fun toJson(): String {
            return JSONObject().apply {
                put(KEY_MIME_TYPE, mimeType)
                put(KEY_URI, uri)
                put(KEY_LAST_READ_TIME, lastReadTime)
                put(KEY_PROGRESS, progress)
                put(KEY_CHAR_OFFSET, charOffset)
            }.toString()
        }
    }
    
    /**
     * 未知或不支持格式的阅读状态
     * 仅记录基本信息
     */
    data class Unknown(
        override val uri: String,
        override val progress: Float = 0f,
        override val lastReadTime: Long = System.currentTimeMillis(),
        override val mimeType: String = "application/octet-stream"
    ) : ReadingState {
        override fun toJson(): String {
            return JSONObject().apply {
                put(KEY_MIME_TYPE, mimeType)
                put(KEY_URI, uri)
                put(KEY_LAST_READ_TIME, lastReadTime)
                put(KEY_PROGRESS, progress)
            }.toString()
        }
    }
}
