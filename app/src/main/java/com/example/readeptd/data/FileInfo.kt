package com.example.readeptd.data

import android.os.Bundle
import org.json.JSONObject

/**
 * 文件信息数据类
 * 用于表示文档文件的元数据
 */
data class FileInfo(
    val uri: String,
    val fileName: String,
    val fileSize: Long = 0,
    val mimeType: String = ""
) {
    
    /**
     * 将 FileInfo 转换为 JSON 字符串
     */
    fun toJson(): String {
        return JSONObject().apply {
            put(KEY_URI, uri)
            put(KEY_FILE_NAME, fileName)
            put(KEY_FILE_SIZE, fileSize)
            put(KEY_MIME_TYPE, mimeType)
        }.toString()
    }
    
    /**
     * 将 FileInfo 转换为 Bundle
     */
    fun toBundle(): Bundle {
        return Bundle().apply {
            putString(KEY_URI, uri)
            putString(KEY_FILE_NAME, fileName)
            putLong(KEY_FILE_SIZE, fileSize)
            putString(KEY_MIME_TYPE, mimeType)
        }
    }
    
    companion object {
        private const val KEY_URI = "uri"
        private const val KEY_FILE_NAME = "file_name"
        private const val KEY_FILE_SIZE = "file_size"
        private const val KEY_MIME_TYPE = "mime_type"
        
        /**
         * 从 JSON 字符串恢复 FileInfo
         */
        fun fromJson(jsonString: String): FileInfo {
            val jsonObject = JSONObject(jsonString)
            return FileInfo(
                uri = jsonObject.getString(KEY_URI),
                fileName = jsonObject.getString(KEY_FILE_NAME),
                fileSize = jsonObject.getLong(KEY_FILE_SIZE),
                mimeType = jsonObject.getString(KEY_MIME_TYPE)
            )
        }
        
        /**
         * 从 Bundle 恢复 FileInfo
         */
        fun fromBundle(bundle: Bundle): FileInfo {
            return FileInfo(
                uri = bundle.getString(KEY_URI) ?: "",
                fileName = bundle.getString(KEY_FILE_NAME) ?: "",
                fileSize = bundle.getLong(KEY_FILE_SIZE, 0),
                mimeType = bundle.getString(KEY_MIME_TYPE) ?: "",
            )
        }
    }
}
