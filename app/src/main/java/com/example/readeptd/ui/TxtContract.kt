package com.example.readeptd.ui

import androidx.compose.ui.unit.IntSize

/**
 * TXT 阅读器 UI 事件
 */
sealed interface TxtEvent {

    /**
     * UI 尺寸变化事件
     * @param size 新的视图尺寸
     */
    data class OnViewSizeChanged(val size: IntSize) : TxtEvent

    /**
     * 翻页事件
     * @param pageIndex 目标页码
     */
    data class OnPageChanged(val pageIndex: Int) : TxtEvent

    /**
     * 字体大小调整事件
     * @param fontSize 新的字体大小（sp）
     */
    data class OnFontSizeChanged(val fontSize: Int) : TxtEvent

    /**
     * 行距调整事件
     * @param lineHeight 新的行距（sp）
     */
    data class OnLineHeightChanged(val lineHeight: Int) : TxtEvent

    /**
     * Padding 设置事件
     */
    data class OnPaddingChanged(
        val leftPaddingDp: Int,
        val rightPaddingDp: Int,
        val topPaddingDp: Int,
        val bottomPaddingDp: Int
    ) : TxtEvent
}