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
    val mimeType: String = ""
) {
    
    companion object {
        private const val KEY_URI = "uri"
        private const val KEY_FILE_NAME = "file_name"
        private const val KEY_FILE_SIZE = "file_size"
        private const val KEY_MIME_TYPE = "mime_type"
        
        /**
         * 将 FileInfo 转换为 Bundle，用于 Activity 间传递
         */
        fun FileInfo.toBundle(): Bundle {
            return Bundle().apply {
                putString(KEY_URI, uri.toString())
                putString(KEY_FILE_NAME, fileName)
                putLong(KEY_FILE_SIZE, fileSize)
                putString(KEY_MIME_TYPE, mimeType)
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
            )
        }
    }
}
