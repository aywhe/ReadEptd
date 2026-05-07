package com.example.readeptd.utils

/**
 * 通用工具类
 * 提供纯 Kotlin 的实用函数（无 Android 依赖）
 */
object Utils {

    /**
     * 格式化文件大小
     * @param size 文件大小（字节）
     * @return 格式化后的字符串，如 "1.5 MB"
     */
    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }

    data class CharsParams(
        val avgCharsPerLine: Int = 0,
        val maxLinesPerPage: Int = 0
    )
    
    /**
     * 计算页面字符参数，注意单位统一
     * @param pageWidth 页面宽度（像素）
     * @param pageHeight 页面高度（像素）
     * @param fontSize 字体大小（像素）
     * @param lineHeight 行高（像素）
     * @param leftPadding 左边距（像素）
     * @param rightPadding 右边距（像素）
     * @param topPadding 上边距（像素）
     * @param bottomPadding 下边距（像素）
     * @return 一个 Pair 对象，包含每行平均字符数和每页最大行数
     */
    fun calculatePageCharsParams(
        pageWidth: Int,
        pageHeight: Int,
        fontSize: Int,
        lineHeight: Int,
        leftPadding: Int = 0,
        rightPadding: Int = 0,
        topPadding: Int = 0,
        bottomPadding: Int = 0
    ): CharsParams {

        val charAspectRatio = 1.0f
        val charSpacingFactor = 1.05f

        // 减去 padding 得到有效显示区域
        val effectiveWidth = (pageWidth - leftPadding - rightPadding).coerceAtLeast(1)
        val effectiveHeight = (pageHeight - topPadding - bottomPadding).coerceAtLeast(1)

        val effectiveCharWidth = fontSize * charAspectRatio * charSpacingFactor
        // 1. 计算每行平均字符数（像素相除，结果无单位）
        val avgCharsPerLine = (effectiveWidth.toFloat() / effectiveCharWidth).toInt()

        // 2. 计算每页最大行数（像素相除，结果无单位）
        val maxLinesPerPage = (effectiveHeight.toFloat() / lineHeight).toInt().coerceIn(10, 35) - 1
        return CharsParams(avgCharsPerLine, maxLinesPerPage)
    }
}