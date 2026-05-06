package com.example.readeptd.books.epub

import org.json.JSONObject

/**
 * EPUB 页面信息数据类
 * 对应 epub.js relocated 事件返回的完整位置信息
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
        val percentage: Float
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
                    percentage = positionJson.optDouble("percentage", 0.0).toFloat()
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
                    percentage = 0f
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