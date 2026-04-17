package com.example.readeptd.data

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri

/**
 * 文件信息数据类
 * 用于表示文档文件的元数据
 */
data class FileInfo(
    val uri: Uri,
    val fileName: String,
    val fileSize: Long = 0,
    val mimeType: String = "",
    val totalPage: Int? = null,
    val currentPage: Int? = null
) {
    /**
     * 计算阅读进度（0.0 - 1.0）
     */
    val progress: Float?
        get() = if (totalPage != null && totalPage > 0) {
            currentPage?.div(totalPage.toFloat())
        } else {
            null
        }
    
    companion object {
        private const val KEY_URI = "uri"
        private const val KEY_FILE_NAME = "file_name"
        private const val KEY_FILE_SIZE = "file_size"
        private const val KEY_MIME_TYPE = "mime_type"
        private const val KEY_TOTAL_PAGE = "total_page"
        private const val KEY_CURRENT_PAGE = "current_page"
        
        /**
         * 将 FileInfo 转换为 Bundle，用于 Activity 间传递
         */
        fun FileInfo.toBundle(): Bundle {
            return Bundle().apply {
                putString(KEY_URI, uri.toString())
                putString(KEY_FILE_NAME, fileName)
                putLong(KEY_FILE_SIZE, fileSize)
                putString(KEY_MIME_TYPE, mimeType)
                if (totalPage != null) {
                    putInt(KEY_TOTAL_PAGE, totalPage)
                }
                if (currentPage != null) {
                    putInt(KEY_CURRENT_PAGE, currentPage)
                }
            }
        }
        
        /**
         * 从 Bundle 恢复 FileInfo
         */
        fun fromBundle(bundle: Bundle): FileInfo {
            return FileInfo(
                uri = (bundle.getString(KEY_URI) ?: "").toUri(),
                fileName = bundle.getString(KEY_FILE_NAME) ?: "",
                fileSize = bundle.getLong(KEY_FILE_SIZE, 0),
                mimeType = bundle.getString(KEY_MIME_TYPE) ?: "",
                totalPage = if (bundle.containsKey(KEY_TOTAL_PAGE)) {
                    bundle.getInt(KEY_TOTAL_PAGE)
                } else {
                    null
                },
                currentPage = if (bundle.containsKey(KEY_CURRENT_PAGE)) {
                    bundle.getInt(KEY_CURRENT_PAGE)
                } else {
                    null
                }
            )
        }
    }
}
