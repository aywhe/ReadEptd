package com.example.readeptd.books.epub

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * EPUB 页面信息数据类
 * 对应 epub.js relocated 事件返回的完整位置信息
 *
 * |---------------------------------------|--------|-----------------------|
 * | 成员                                   | 类型    | 含义 |
 * |---------------------------------------|--------|-----------------------|
 * | `start`                               | Object | 当前显示页面起始位置的信息 |
 * | `end`                                 | Object | 当前显示页面结束位置的信息 |
 * | `start.cfi` / `end.cfi`               | string | 起始/结束位置的 CFI 定位符 |
 * | `start.href` / `end.href`             | string | 所在章节的 HTML 文件名 |
 * | `start.index` / `end.index`           | number | 章节在 spine 中的索引（从1开始） |
 * | `start.location` / `end.location`     | number | 线性位置索引，对应 `book.locations` 数组的下标 |
 * | `start.percentage` / `end.percentage` | number | 起始/结束位置占全书的百分比（0~1） |
 * | `start.displayed.page`                | number | 当前页在章节内的页码 |
 * | `start.displayed.total`               | number | 当前章节的总页数 |
 * |---------------------------------------|--------|-----------------------|
 *
 */
data class EpubLocation(
    val start: Position,
    val end: Position,
    val rawJson: String = ""
) {
    data class Position(
        val index: Int,
        val href: String,
        val cfi: String,
        val displayed: Displayed,
        val location: Int,
        val percentage: Double
    ) {
        companion object {
            /**
             * 从 JSON 对象解析 Position
             */
            fun fromJson(positionJson: JSONObject?): Position {
                if (positionJson == null) {
                    return default()
                }

                val displayedJson = positionJson.optJSONObject("displayed")
                val displayed = Displayed.fromJson(displayedJson)

                return Position(
                    index = positionJson.optInt("index", 0),
                    href = positionJson.optString("href", ""),
                    cfi = positionJson.optString("cfi", ""),
                    displayed = displayed,
                    location = positionJson.optInt("location", 0),
                    percentage = positionJson.optDouble("percentage", 0.0)
                )
            }

            /**
             * 创建默认的 Position 对象
             */
            fun default(): Position {
                return Position(
                    index = 0,
                    href = "",
                    cfi = "",
                    displayed = Displayed.default(),
                    location = 0,
                    percentage = 0.0
                )
            }
        }
    }

    data class Displayed(
        val page: Int,
        val total: Int
    ) {
        companion object {
            /**
             * 从 JSON 对象解析 Displayed
             */
            fun fromJson(displayedJson: JSONObject?): Displayed {
                if (displayedJson == null) {
                    return default()
                }

                return Displayed(
                    page = displayedJson.optInt("page", 1),
                    total = displayedJson.optInt("total", 1)
                )
            }

            /**
             * 创建默认的 Displayed 对象
             */
            fun default(): Displayed {
                return Displayed(page = 0, total = 0)
            }
        }
    }

    companion object {
        /**
         * 从 JSON 字符串解析 EpubLocation
         */
        fun fromJson(jsonString: String): EpubLocation {
            val json = JSONObject(jsonString)

            val startJson = json.optJSONObject("start")
            val endJson = json.optJSONObject("end")

            val startPosition = Position.fromJson(startJson)
            val endPosition = Position.fromJson(endJson)

            return EpubLocation(
                start = startPosition,
                end = endPosition,
                rawJson = jsonString
            )
        }

        /**
         * 创建默认的 EpubLocation 对象
         */
        fun default(): EpubLocation {
            val defaultPosition = Position.default()
            return EpubLocation(
                start = defaultPosition,
                end = defaultPosition
            )
        }
    }
}

data class EpubClickInfo(
    val x: Float,
    val y: Float,
    val href: String
)

/**
 * EPUB 搜索结果数据类
 * 对应 epub.js search 返回的匹配项信息
 */
data class EpubSearchResult(
    val href: String = "",
    val sectionIndex: Int = -1,
    val cfi: String = "",
    val locInd: Int = -1,
    val excerpt: String = "",
    val chapterTitle: String = "",
    val idref: String = "",
    val matchIndex: Int = -1,
    val sectionBaseCfi: String = "",
    val position: Int = -1,
    val query: String = ""
) {
    companion object {
        /**
         * 从 JSON 字符串解析 EpubSearchResult
         */
        fun fromJson(jsonString: String): EpubSearchResult {
            val json = JSONObject(jsonString)

            return EpubSearchResult(
                href = json.optString("href", ""),
                sectionIndex = json.optInt("sectionIndex", -1),
                cfi = json.optString("cfi", ""),
                locInd = json.optInt("locInd", -1),
                excerpt = json.optString("excerpt", ""),
                chapterTitle = json.optString("chapterTitle", ""),
                idref = json.optString("idref", ""),
                matchIndex = json.optInt("matchIndex", -1),
                sectionBaseCfi = json.optString("sectionBaseCfi", ""),
                position = json.optInt("position", -1),
                query = json.optString("query", "")
            )
        }
    }
}

sealed interface EpubTheme {
    object Light : EpubTheme
    object Night : EpubTheme
    object EyeCare: EpubTheme
}

sealed interface EpubFlowMode {
    object Paginated : EpubFlowMode
    object Scrolled : EpubFlowMode
}

data class WebPaddingValues(
    val start: Int = 0,
    val top: Int = 0,
    val end: Int = 0,
    val bottom: Int = 0
) {
    constructor(all: Int = 0) : this(all, all, all, all)

    constructor(horizontal: Int = 0, vertical: Int = 0)
            : this(horizontal, vertical, horizontal, vertical)

    companion object {
        // webview使用的px是逻辑像素，对应Android层就是dp，所以不用转换
        fun fromPaddingValues(padding: PaddingValues): WebPaddingValues {
            return WebPaddingValues(
                start = padding.calculateStartPadding(LayoutDirection.Ltr).value.roundToInt(),
                top = padding.calculateTopPadding().value.roundToInt(),
                end = padding.calculateEndPadding(LayoutDirection.Ltr).value.roundToInt(),
                bottom = padding.calculateBottomPadding().value.roundToInt()
            )
        }
    }

    fun toPaddingValues(): PaddingValues {
        return PaddingValues(
            start = start.dp,
            top = top.dp,
            end = end.dp,
            bottom = bottom.dp
        )
    }

    fun toJson(): String {
        return JSONObject().apply {
            put("start", start)
            put("top", top)
            put("end", end)
            put("bottom", bottom)
        }.toString()
    }
}