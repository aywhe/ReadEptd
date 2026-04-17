package com.example.readeptd.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.readeptd.ui.FileInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * DataStore 扩展属性，创建单例实例
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reading_files")

/**
 * 文件数据持久化管理器
 * 使用 Jetpack DataStore 保存和加载阅读文件列表
 */
class FileDataStore(private val context: Context) {
    
    companion object {
        private val READING_FILES_KEY = stringPreferencesKey("reading_files")
    }
    
    /**
     * 保存文件列表到 DataStore
     */
    suspend fun saveReadingFiles(files: List<FileInfo>) {
        context.dataStore.edit { preferences ->
            val jsonArray = JSONArray()
            files.forEach { file ->
                val jsonObject = JSONObject().apply {
                    put("uri", file.uri.toString())
                    put("fileName", file.fileName)
                    put("fileSize", file.fileSize)
                    put("mimeType", file.mimeType)
                    if (file.totalPage != null) {
                        put("totalPage", file.totalPage)
                    } else {
                        put("totalPage", JSONObject.NULL)
                    }
                    if (file.currentPage != null) {
                        put("currentPage", file.currentPage)
                    } else {
                        put("currentPage", JSONObject.NULL)
                    }
                }
                jsonArray.put(jsonObject)
            }
            preferences[READING_FILES_KEY] = jsonArray.toString()
        }
    }
    
    /**
     * 从 DataStore 加载文件列表
     */
    val readingFilesFlow: Flow<List<FileInfo>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[READING_FILES_KEY]
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                val files = mutableListOf<FileInfo>()
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val fileInfo = FileInfo(
                        uri = Uri.parse(jsonObject.getString("uri")),
                        fileName = jsonObject.getString("fileName"),
                        fileSize = jsonObject.getLong("fileSize"),
                        mimeType = jsonObject.getString("mimeType"),
                        totalPage = if (jsonObject.isNull("totalPage")) {
                            null
                        } else {
                            jsonObject.getInt("totalPage")
                        },
                        currentPage = if (jsonObject.isNull("currentPage")) {
                            null
                        } else {
                            jsonObject.getInt("currentPage")
                        }
                    )
                    files.add(fileInfo)
                }
                
                files
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    /**
     * 清除所有保存的文件数据
     */
    suspend fun clearReadingFiles() {
        context.dataStore.edit { preferences ->
            preferences.remove(READING_FILES_KEY)
        }
    }
}
