package com.example.readeptd.books.pdf

/**
 * TXT 阅读器 UI 事件
 */
sealed interface PdfEvent {

    /**
     * 翻页事件
     * @param pageIndex 目标页码
     */
    data class OnPageChanged(val pageIndex: Int) : PdfEvent

}