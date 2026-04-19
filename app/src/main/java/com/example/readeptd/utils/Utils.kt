package com.example.readeptd.utils

import android.content.Context
import androidx.core.net.toUri

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
            // 尝试查询元数据，如果能查到至少一列，说明文件存在
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
}