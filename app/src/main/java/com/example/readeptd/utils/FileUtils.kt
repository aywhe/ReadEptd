package com.example.readeptd.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri

/**
 * 文件和 URI 工具类
 * 提供文件操作和 URI 权限管理的实用函数
 */
object FileUtils {

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
            Log.d("FileUtils", "已获取 URI 持久化读取权限: $uri")
        } catch (e: Exception) {
            Log.e("FileUtils", "获取 URI 持久化权限失败: $uri", e)
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
            Log.d("FileUtils", "已释放 URI 持久化读取权限: $uri")
        } catch (e: Exception) {
            Log.e("FileUtils", "释放 URI 持久化权限失败: $uri", e)
        }
    }
}
