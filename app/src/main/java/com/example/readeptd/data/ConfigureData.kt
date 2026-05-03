package com.example.readeptd.data

/**
 * 应用配置数据类
 * 
 * 添加新配置项的步骤：
 * 1. 在此类中添加新属性（建议提供默认值）
 * 2. 在 FileDataStore.saveConfigure() 中添加序列化逻辑
 * 3. 在 FileDataStore.configureFlow 中添加反序列化逻辑
 * 
 * 示例：
 * data class ConfigureData(
 *     val showTtsNotification: Boolean = true,
 *     val newConfig: String = "default"  // 新增配置
 * )
 */
data class ConfigureData(
    val showTtsNotification: Boolean = true
)
