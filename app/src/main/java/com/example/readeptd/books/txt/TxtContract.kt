package com.example.readeptd.books.txt

import androidx.compose.ui.unit.IntSize

/**
 * TXT 阅读器 UI 事件
 */
sealed interface TxtEvent {

    /**
     * 视图尺寸和边距变化事件
     * @param size 视图尺寸
     * @param leftPaddingDp 左边距（dp）
     * @param rightPaddingDp 右边距（dp）
     * @param topPaddingDp 上边距（dp）
     * @param bottomPaddingDp 下边距（dp）
     */
    data class OnViewMetricsChanged(
        val size: IntSize,
        val leftPaddingDp: Int,
        val rightPaddingDp: Int,
        val topPaddingDp: Int,
        val bottomPaddingDp: Int
    ) : TxtEvent


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

    object OnDoubleClickScreen: TxtEvent
    data  class OnScreenOrientationChanged(val orientation: Int): TxtEvent

}


sealed interface SplitPagesMode {
    object ByLayoutSize : SplitPagesMode
    object ByCharsCount : SplitPagesMode
}