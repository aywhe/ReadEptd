package com.example.readeptd.data

import org.json.JSONObject

/**
 * 应用配置数据类
 * 用于保存和管理应用的全局配置项
 */
data class ConfigureData(
    val isNightMode: Boolean = false,
    val isDynamicColor: Boolean = true,
    val autoNightMode: Boolean = false,
    val autoNightStartTime: String = "20:00",
    val autoNightEndTime: String = "06:00",
) {
    
    /**
     * 将配置转换为 JSON 字符串
     */
    fun toJson(): String {
        return JSONObject().apply {
            put(KEY_IS_NIGHT_MODE, isNightMode)
            put(KEY_IS_DYNAMIC_COLOR, isDynamicColor)
            put(KEY_AUTO_NIGHT_MODE, autoNightMode)
            put(KEY_AUTO_NIGHT_START_TIME, autoNightStartTime)
            put(KEY_AUTO_NIGHT_END_TIME, autoNightEndTime)
        }.toString()
    }
    
    companion object {
        private const val KEY_IS_NIGHT_MODE = "is_night_mode"
        private const val KEY_IS_DYNAMIC_COLOR = "is_dynamic_color"
        private const val KEY_AUTO_NIGHT_MODE = "auto_night_mode"
        private const val KEY_AUTO_NIGHT_START_TIME = "auto_night_start_time"
        private const val KEY_AUTO_NIGHT_END_TIME = "auto_night_end_time"
        
        /**
         * 从 JSON 字符串恢复配置
         */
        fun fromJson(jsonString: String): ConfigureData {
            val jsonObject = JSONObject(jsonString)
            return ConfigureData(
                isNightMode = jsonObject.optBoolean(KEY_IS_NIGHT_MODE, false),
                isDynamicColor = jsonObject.optBoolean(KEY_IS_DYNAMIC_COLOR, true),
                autoNightMode = jsonObject.optBoolean(KEY_AUTO_NIGHT_MODE, false),
                autoNightStartTime = jsonObject.optString(KEY_AUTO_NIGHT_START_TIME, "20:00"),
                autoNightEndTime = jsonObject.optString(KEY_AUTO_NIGHT_END_TIME, "06:00")
            )
        }
        
        /**
         * 默认配置
         */
        fun default(): ConfigureData {
            return ConfigureData()
        }
    }
}
