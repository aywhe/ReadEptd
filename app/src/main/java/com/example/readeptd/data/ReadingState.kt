package com.example.readeptd.data

import org.json.JSONObject

/**
 * 阅读状态基类
 * 不同的文件格式有不同的阅读状态属性
 */
sealed interface ReadingState {
    val uri: String
    val lastReadTime: Long
    val mimeType: String
    val progress: Double
    val isSwipeLayout: Boolean
    val isRtl: Boolean

    /**
     * 将阅读状态转换为 JSON 字符串
     */
    fun toJson(): String

    companion object {
        private const val KEY_MIME_TYPE = "mimeType"
        private const val KEY_URI = "uri"
        private const val KEY_LAST_READ_TIME = "lastReadTime"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_IS_SWIPE_LAYOUT = "isSwipeLayout"
        private const val KEY_IS_RTL = "isRtl"

        /**
         * 从 JSON 字符串恢复阅读状态
         */
        fun fromJson(jsonString: String): ReadingState {
            val jsonObject = JSONObject(jsonString)
            val mimeType = jsonObject.optString(KEY_MIME_TYPE, "application/octet-stream")
            val uri = jsonObject.getString(KEY_URI)
            val lastReadTime = jsonObject.optLong(KEY_LAST_READ_TIME, System.currentTimeMillis())
            val progress = jsonObject.optDouble(KEY_PROGRESS, 0.0)
            val isSwipeLayout = jsonObject.optBoolean(KEY_IS_SWIPE_LAYOUT, true)
            val isRtl = jsonObject.optBoolean(KEY_IS_RTL, false)

            return when {
                mimeType == "application/epub+zip" || mimeType.contains("epub") -> {
                    Epub.fromJson(jsonObject, uri, lastReadTime, progress, isSwipeLayout, isRtl)
                }

                mimeType == "application/pdf" -> {
                    Pdf.fromJson(jsonObject, uri, lastReadTime, progress, isSwipeLayout, isRtl)
                }

                mimeType == "text/plain" -> {
                    Txt.fromJson(jsonObject, uri, lastReadTime, progress, isSwipeLayout, isRtl)
                }

                else -> {
                    Unknown(uri, progress, lastReadTime, mimeType, isSwipeLayout, isRtl)
                }
            }
        }

        // 提取公共字段到单独的方法，供各子类调用
        fun createBaseJsonObject(readingState: ReadingState): JSONObject = JSONObject().apply {
            put(KEY_MIME_TYPE, readingState.mimeType)
            put(KEY_URI, readingState.uri)
            put(KEY_LAST_READ_TIME, readingState.lastReadTime)
            put(KEY_PROGRESS, readingState.progress)
            put(KEY_IS_SWIPE_LAYOUT, readingState.isSwipeLayout)
            put(KEY_IS_RTL, readingState.isRtl)
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
        override val progress: Double = 0.0,          // 阅读进度 0.0-1.0
        override val lastReadTime: Long = System.currentTimeMillis(),
        override val mimeType: String = "application/epub+zip",
        override val isSwipeLayout: Boolean = true,
        override val isRtl: Boolean = false
    ) : ReadingState {
        companion object {
            const val KEY_CFI = "cfi"
            const val KEY_PAGE = "page"
            const val KEY_TOTAL_PAGES = "totalPages"

            fun fromJson(
                jsonObject: JSONObject,
                uri: String,
                lastReadTime: Long,
                progress: Double,
                isSwipeLayout: Boolean,
                isRtl: Boolean
            ): Epub {
                return Epub(
                    uri = uri,
                    cfi = if (jsonObject.has(KEY_CFI)) jsonObject.getString(KEY_CFI) else null,
                    page = if (jsonObject.has(KEY_PAGE)) jsonObject.getInt(KEY_PAGE) else null,
                    totalPages = if (jsonObject.has(KEY_TOTAL_PAGES)) jsonObject.getInt(KEY_TOTAL_PAGES) else null,
                    progress = progress,
                    lastReadTime = lastReadTime,
                    isSwipeLayout = isSwipeLayout,
                    isRtl = isRtl
                )
            }
        }

        override fun toJson(): String {
            val baseObj = ReadingState.Companion.createBaseJsonObject(this)
            cfi?.let { baseObj.put(KEY_CFI, it) }
            page?.let { baseObj.put(KEY_PAGE, it) }
            totalPages?.let { baseObj.put(KEY_TOTAL_PAGES, it) }
            return baseObj.toString()
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
        override val progress: Double = 0.0,
        override val lastReadTime: Long = System.currentTimeMillis(),
        override val mimeType: String = "application/pdf",
        override val isSwipeLayout: Boolean = true,
        override val isRtl: Boolean = false
    ) : ReadingState {
        companion object {
            const val KEY_PAGE = "page"
            const val KEY_TOTAL_PAGES = "totalPages"

            fun fromJson(
                jsonObject: JSONObject,
                uri: String,
                lastReadTime: Long,
                progress: Double,
                isSwipeLayout: Boolean,
                isRtl: Boolean
            ): Pdf {
                return Pdf(
                    uri = uri,
                    page = jsonObject.optInt(KEY_PAGE, 1),
                    totalPages = jsonObject.optInt(KEY_TOTAL_PAGES, 1),
                    progress = progress,
                    lastReadTime = lastReadTime,
                    isSwipeLayout = isSwipeLayout,
                    isRtl = isRtl
                )
            }
        }

        override fun toJson(): String {
            val baseObj = ReadingState.Companion.createBaseJsonObject(this)
            baseObj.put(KEY_PAGE, page)
            baseObj.put(KEY_TOTAL_PAGES, totalPages)
            return baseObj.toString()
        }
    }

    /**
     * TXT 纯文本格式的阅读状态
     * 基于字符偏移量或行号
     */
    data class Txt(
        override val uri: String,
        val charOffset: Long = 0,          // 字符偏移量
        override val progress: Double = 0.0,
        override val lastReadTime: Long = System.currentTimeMillis(),
        override val mimeType: String = "text/plain",
        override val isSwipeLayout: Boolean = true,
        override val isRtl: Boolean = false
    ) : ReadingState {
        companion object {
            const val KEY_CHAR_OFFSET = "charOffset"

            fun fromJson(
                jsonObject: JSONObject,
                uri: String,
                lastReadTime: Long,
                progress: Double,
                isSwipeLayout: Boolean,
                isRtl: Boolean
            ): Txt {
                return Txt(
                    uri = uri,
                    charOffset = jsonObject.optLong(KEY_CHAR_OFFSET, 0),
                    progress = progress,
                    lastReadTime = lastReadTime,
                    isSwipeLayout = isSwipeLayout,
                    isRtl = isRtl
                )
            }
        }

        override fun toJson(): String {
            val baseObj = ReadingState.Companion.createBaseJsonObject(this)
            baseObj.put(KEY_CHAR_OFFSET, charOffset)
            return baseObj.toString()
        }
    }

    /**
     * 未知或不支持格式的阅读状态
     * 仅记录基本信息
     */
    data class Unknown(
        override val uri: String,
        override val progress: Double = 0.0,
        override val lastReadTime: Long = System.currentTimeMillis(),
        override val mimeType: String = "application/octet-stream",
        override val isSwipeLayout: Boolean = true,
        override val isRtl: Boolean = false
    ) : ReadingState {
        override fun toJson(): String {
            val baseObj = ReadingState.Companion.createBaseJsonObject(this)
            return baseObj.toString()
        }
    }
}
