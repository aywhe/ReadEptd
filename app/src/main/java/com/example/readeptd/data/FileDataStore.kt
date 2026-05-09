package com.example.readeptd.data

import android.content.Context
import android.util.Log
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
private val Context.configDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_config")

/**
 * 文件数据持久化管理器
 * 使用 Jetpack DataStore 保存和加载阅读文件列表
 */
class FileDataStore(private val context: Context) {
    
    companion object {
        private val READING_FILES_KEY = stringPreferencesKey("reading_files")
        private val READING_STATES_KEY = stringPreferencesKey("reading_states")
        private val CONFIG_DATA_KEY = stringPreferencesKey("config_data")
    }
    
    /**
     * 保存文件列表到 DataStore
     */
    suspend fun saveReadingFiles(files: List<FileInfo>) {
        context.fileListDataStore.edit { preferences ->
            val jsonArray = JSONArray()
            files.forEach { file ->
                jsonArray.put(file.toJson())
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
                    files.add(FileInfo.fromJson(jsonArray.getString(i)))
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
            
            // 使用 ReadingState 自身的序列化方法
            statesMap[state.uri] = state.toJson()
            
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
            ReadingState.fromJson(stateJson)
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
                        ReadingState.fromJson(stateJson)
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
    
    // ==================== 应用配置管理 ====================
    
    /**
     * 保存应用配置
     */
    suspend fun saveConfig(config: ConfigureData) {
        context.configDataStore.edit { preferences ->
            preferences[CONFIG_DATA_KEY] = config.toJson()
        }
    }
    
    /**
     * 获取应用配置（Flow 形式，自动响应变化）
     */
    val configFlow: Flow<ConfigureData> = context.configDataStore.data.map { preferences ->
        val jsonString = preferences[CONFIG_DATA_KEY]
        Log.d("FileDataStore", "加载配置 - jsonString: $jsonString")
        if (jsonString != null) {
            try {
                val config = ConfigureData.fromJson(jsonString)
                Log.d("FileDataStore", "从存储加载配置成功: isNightMode=${config.isNightMode}, isDynamicColor=${config.isDynamicColor}")
                config
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("FileDataStore", "解析配置失败，使用默认配置", e)
                ConfigureData()
            }
        } else {
            Log.d("FileDataStore", "未找到配置数据，使用默认配置")
            ConfigureData()
        }
    }
    
    /**
     * 获取应用配置（一次性读取）
     */
    suspend fun getConfig(): ConfigureData {
        return configFlow.first()
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
}
