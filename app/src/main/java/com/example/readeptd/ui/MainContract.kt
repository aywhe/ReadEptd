package com.example.readeptd.ui

import android.net.Uri
import android.os.Bundle

/**
 * 文件信息数据类
 */
data class FileInfo(
    val uri: Uri,
    val fileName: String,
    val fileSize: Long = 0,
    val mimeType: String = "",
    val progress: Float? = null
) {
    companion object {
        private const val KEY_URI = "uri"
        private const val KEY_FILE_NAME = "file_name"
        private const val KEY_FILE_SIZE = "file_size"
        private const val KEY_MIME_TYPE = "mime_type"
        private const val KEY_PROGRESS = "progress"
        
        /**
         * 将 FileInfo 转换为 Bundle
         */
        fun FileInfo.toBundle(): Bundle {
            return Bundle().apply {
                putString(KEY_URI, uri.toString())
                putString(KEY_FILE_NAME, fileName)
                putLong(KEY_FILE_SIZE, fileSize)
                putString(KEY_MIME_TYPE, mimeType)
                if (progress != null) {
                    putFloat(KEY_PROGRESS, progress)
                }
            }
        }
        
        /**
         * 从 Bundle 创建 FileInfo
         */
        fun fromBundle(bundle: Bundle): FileInfo {
            return FileInfo(
                uri = Uri.parse(bundle.getString(KEY_URI) ?: ""),
                fileName = bundle.getString(KEY_FILE_NAME) ?: "",
                fileSize = bundle.getLong(KEY_FILE_SIZE, 0),
                mimeType = bundle.getString(KEY_MIME_TYPE) ?: "",
                progress = if (bundle.containsKey(KEY_PROGRESS)) {
                    bundle.getFloat(KEY_PROGRESS)
                } else {
                    null
                }
            )
        }
    }
}

/**
 * UI 状态密封类 - 代表屏幕的不同状态
 */
sealed interface MainUiState {
    object Loading : MainUiState
    data class Success(
        val readingFiles: List<FileInfo> = emptyList()
    ) : MainUiState
    data class Error(val error: String) : MainUiState
}

/**
 * UI 事件密封类 - 用户交互触发的事件
 */
sealed interface MainUiEvent {
    data class OnFilesSelected(val files: List<FileInfo>) : MainUiEvent
    data class RemoveFile(val index: Int) : MainUiEvent
    data class MoveFile(val fromIndex: Int, val toIndex: Int) : MainUiEvent
}
