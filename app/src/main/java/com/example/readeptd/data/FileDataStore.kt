package com.example.readeptd.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * DataStore 扩展属性,创建单例实例
 */
private val Context.fileListDataStore: DataStore<Preferences> by preferencesDataStore(name = "reading_files")
private val Context.readingStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "reading_states")

/**
 * 文件数据持久化管理器
 * 使用 Jetpack DataStore 保存和加载阅读文件列表
 */
class FileDataStore(private val context: Context) {
    
    companion object {
        private val READING_FILES_KEY = stringPreferencesKey("reading_files")
        private val READING_STATES_KEY = stringPreferencesKey("reading_states")
    }
    
    /**
     * 保存文件列表到 DataStore
     */
    suspend fun saveReadingFiles(files: List<FileInfo>) {
        context.fileListDataStore.edit { preferences ->
            val jsonArray = JSONArray()
            files.forEach { file ->
                val jsonObject = JSONObject().apply {
                    put("uri", file.uri)
                    put("fileName", file.fileName)
                    put("fileSize", file.fileSize)
                    put("mimeType", file.mimeType)
                }
                jsonArray.put(jsonObject)
            }
            preferences[READING_FILES_KEY] = jsonArray.toString()
        }
    }
    
    /**
     * 从 DataStore 加载文件列表
     */
    val readingFilesFlow: Flow<List<FileInfo>> = context.fileListDataStore.data.map { preferences ->
        val jsonString = preferences[READING_FILES_KEY]
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                val files = mutableListOf<FileInfo>()
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val fileInfo = FileInfo(
                        uri = jsonObject.getString("uri"),
                        fileName = jsonObject.getString("fileName"),
                        fileSize = jsonObject.getLong("fileSize"),
                        mimeType = jsonObject.getString("mimeType"),
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
        context.fileListDataStore.edit { preferences ->
            preferences.remove(READING_FILES_KEY)
        }
    }
    
    // ==================== 阅读状态管理 ====================
    
    /**
     * 保存阅读状态
     */
    suspend fun saveReadingState(state: ReadingState) {
        context.readingStateDataStore.edit { preferences ->
            val statesJson = preferences[READING_STATES_KEY] ?: "{}"
            val statesMap = parseStatesMap(statesJson)
            
            // 序列化并保存状态
            val stateJson = serializeReadingState(state)
            statesMap[state.uri] = stateJson
            
            preferences[READING_STATES_KEY] = JSONObject(statesMap).toString()
        }
    }
    
    /**
     * 获取指定 URI 的阅读状态
     */
    suspend fun getReadingState(uri: String): ReadingState? {
        val preferences = context.readingStateDataStore.data.first()
        val statesJson = preferences[READING_STATES_KEY] ?: return null
        
        return try {
            val statesMap = parseStatesMap(statesJson)
            val stateJson = statesMap[uri] ?: return null
            deserializeReadingState(stateJson)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 获取所有阅读状态
     */
    val allReadingStatesFlow: Flow<Map<String, ReadingState>> = 
        context.readingStateDataStore.data.map { preferences ->
            val statesJson = preferences[READING_STATES_KEY] ?: return@map emptyMap()
            
            try {
                val statesMap = parseStatesMap(statesJson)
                statesMap.mapValues { (_, stateJson) ->
                    try {
                        deserializeReadingState(stateJson)
                    } catch (e: Exception) {
                        null
                    }
                }.filterValues { it != null }.mapValues { it.value!! }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyMap()
            }
        }
    
    /**
     * 删除指定 URI 的阅读状态
     */
    suspend fun deleteReadingState(uri: String) {
        context.readingStateDataStore.edit { preferences ->
            val statesJson = preferences[READING_STATES_KEY] ?: return@edit
            
            try {
                val statesMap = parseStatesMap(statesJson)
                statesMap.remove(uri)
                preferences[READING_STATES_KEY] = JSONObject(statesMap).toString()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 清除所有阅读状态
     */
    suspend fun clearAllReadingStates() {
        context.readingStateDataStore.edit { preferences ->
            preferences.remove(READING_STATES_KEY)
        }
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 解析状态 Map
     */
    private fun parseStatesMap(jsonString: String): MutableMap<String, String> {
        return try {
            val jsonObject = JSONObject(jsonString)
            val map = mutableMapOf<String, String>()
            jsonObject.keys().forEach { key ->
                map[key] = jsonObject.getString(key)
            }
            map
        } catch (e: Exception) {
            emptyMap<String, String>().toMutableMap()
        }
    }
    
    /**
     * 序列化阅读状态为 JSON 字符串
     */
    private fun serializeReadingState(state: ReadingState): String {
        return JSONObject().apply {
            put("mimeType", state.mimeType)
            put("uri", state.uri)
            put("lastReadTime", state.lastReadTime)
            
            when (state) {
                is ReadingState.Epub -> {
                    if (state.cfi != null) put("cfi", state.cfi)
                    if (state.page != null) put("page", state.page)
                    if (state.totalPages != null) put("totalPages", state.totalPages)
                    put("progress", state.progress)
                }
                is ReadingState.Pdf -> {
                    put("page", state.page)
                    put("totalPages", state.totalPages)
                    put("zoom", state.zoom)
                    put("scrollOffset", state.scrollOffset)
                }
                is ReadingState.Txt -> {
                    put("charOffset", state.charOffset)
                    put("lineIndex", state.lineIndex)
                    put("scrollPosition", state.scrollPosition)
                }
                is ReadingState.Unknown -> {
                    // 无额外字段
                }
            }
        }.toString()
    }
    
    /**
     * 从 JSON 字符串反序列化为阅读状态
     */
    private fun deserializeReadingState(jsonString: String): ReadingState {
        val jsonObject = JSONObject(jsonString)
        val mimeType = jsonObject.optString("mimeType", "application/octet-stream")
        val uri = jsonObject.getString("uri")
        val lastReadTime = jsonObject.optLong("lastReadTime", System.currentTimeMillis())
        
        return when {
            mimeType == "application/epub+zip" || mimeType.contains("epub") -> ReadingState.Epub(
                uri = uri,
                cfi = if (jsonObject.has("cfi")) jsonObject.getString("cfi") else null,
                page = if (jsonObject.has("page")) jsonObject.getInt("page") else null,
                totalPages = if (jsonObject.has("totalPages")) jsonObject.getInt("totalPages") else null,
                progress = jsonObject.optDouble("progress", 0.0).toFloat(),
                lastReadTime = lastReadTime
            )
            mimeType == "application/pdf" -> ReadingState.Pdf(
                uri = uri,
                page = jsonObject.optInt("page", 1),
                totalPages = jsonObject.optInt("totalPages", 1),
                zoom = jsonObject.optDouble("zoom", 1.0).toFloat(),
                scrollOffset = jsonObject.optDouble("scrollOffset", 0.0).toFloat(),
                lastReadTime = lastReadTime
            )
            mimeType == "text/plain" -> ReadingState.Txt(
                uri = uri,
                charOffset = jsonObject.optLong("charOffset", 0),
                lineIndex = jsonObject.optInt("lineIndex", 0),
                scrollPosition = jsonObject.optDouble("scrollPosition", 0.0).toFloat(),
                lastReadTime = lastReadTime
            )
            else -> ReadingState.Unknown(
                uri = uri,
                lastReadTime = lastReadTime
            )
        }
    }
}
