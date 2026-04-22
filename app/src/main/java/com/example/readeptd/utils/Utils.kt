package com.example.readeptd.utils

import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import android.content.Context

/**
 * URI 工具类
 * 提供 URI 相关的实用函数
 */
object Utils {

    /**
     * 检查 URI 指向的资源是否仍然存在且可访问
     * @param context 上下文
     * @param uri URI 字符串
     * @return 如果资源存在且可访问返回 true，否则返回 false
     */
    fun uriExists(context: Context, uri: String): Boolean {
        return try {
            context.contentResolver.query(uri.toUri(), null, null, null, null)?.use { cursor ->
                cursor.count > 0
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
    
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
    
    /**
     * 获取 URI 的持久化读取权限
     * @param context 上下文
     * @param uri URI 字符串
     */
    fun takePersistableUriPermission(context: Context, uri: String) {
        try {
            val uriObj = uri.toUri()
            context.contentResolver.takePersistableUriPermission(
                uriObj,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Log.d("Utils", "已获取 URI 持久化读取权限: $uri")
        } catch (e: Exception) {
            Log.e("Utils", "获取 URI 持久化权限失败: $uri", e)
        }
    }
    
    /**
     * 释放 URI 的持久化读取权限
     * @param context 上下文
     * @param uri URI 字符串
     */
    fun releasePersistableUriPermission(context: Context, uri: String) {
        try {
            val uriObj = uri.toUri()
            context.contentResolver.releasePersistableUriPermission(
                uriObj,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Log.d("Utils", "已释放 URI 持久化读取权限: $uri")
        } catch (e: Exception) {
            Log.e("Utils", "释放 URI 持久化权限失败: $uri", e)
        }
    }
    fun calculatePageCharsParams(
        pageWidth: Int,
        pageHeight: Int,
        fontSize: Int,
        lineHeight: Int
    ): Pair<Int, Int> {

        val charAspectRatio = 1.0f
        val charSpacingFactor = 1.05f

        val effectiveCharWidth = fontSize * charAspectRatio * charSpacingFactor
        // 1. 计算每行平均字符数（像素相除，结果无单位）
        val avgCharsPerLine = (pageWidth.toFloat() / effectiveCharWidth).toInt()

        // 2. 计算每页最大行数（像素相除，结果无单位）
        val maxLinesPerPage = (pageHeight.toFloat() / lineHeight).toInt().coerceIn(10, 35) - 1
        return Pair(avgCharsPerLine, maxLinesPerPage)
    }
}