package com.example.readeptd.data

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用级内存数据存储
 * 用于跨 Activity/ViewModel 共享不需要持久化的临时数据
 * 
 * 特点：
 * - 单例模式，应用生命周期内常驻内存
 * - 使用 StateFlow 支持响应式更新
 * - 线程安全
 * - 进程重启后数据会丢失（符合设计预期）
 */
object AppMemoryStore {
    
    // ==================== 全屏状态管理 ====================
    
    /**
     * 全屏状态缓存
     * Key: 文件 URI 或唯一标识
     * Value: 是否全屏
     */
    private val _fullScreenStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val fullScreenStates: StateFlow<Map<String, Boolean>> = _fullScreenStates.asStateFlow()
    
    /**
     * 获取指定文件的全屏状态
     */
    fun isFullScreen(fileKey: String): Boolean {
        return _fullScreenStates.value[fileKey] ?: false
    }
    
    /**
     * 设置指定文件的全屏状态
     */
    fun setFullScreen(fileKey: String, isFullScreen: Boolean) {
        _fullScreenStates.value = _fullScreenStates.value.toMutableMap().apply {
            this[fileKey] = isFullScreen
        }
    }
    
    /**
     * 切换指定文件的全屏状态
     */
    fun toggleFullScreen(fileKey: String) {
        val currentState = isFullScreen(fileKey)
        setFullScreen(fileKey, !currentState)
    }
    
    /**
     * 清除指定文件的全屏状态
     */
    fun clearFullScreen(fileKey: String) {
        _fullScreenStates.value = _fullScreenStates.value.toMutableMap().apply {
            remove(fileKey)
        }
    }
    
    // ==================== PDF 缩放信息管理 ====================
    
    /**
     * PDF 缩放信息缓存
     * Key: 文件 URI 或唯一标识
     * Value: 缩放信息（缩放比例 + 偏移量）
     */
    private val _pdfZoomInfoMap = MutableStateFlow<Map<String, PdfZoomInfo>>(emptyMap())
    val pdfZoomInfoMap: StateFlow<Map<String, PdfZoomInfo>> = _pdfZoomInfoMap.asStateFlow()
    
    /**
     * 获取指定文件的 PDF 缩放信息
     */
    fun getPdfZoomInfo(fileKey: String): PdfZoomInfo {
        return _pdfZoomInfoMap.value[fileKey] ?: PdfZoomInfo()
    }
    
    /**
     * 设置指定文件的 PDF 缩放信息
     */
    fun setPdfZoomInfo(fileKey: String, zoomInfo: PdfZoomInfo) {
        _pdfZoomInfoMap.value = _pdfZoomInfoMap.value.toMutableMap().apply {
            this[fileKey] = zoomInfo
        }
    }
    
    /**
     * 更新指定文件的缩放比例
     */
    fun updatePdfZoom(fileKey: String, zoom: Float) {
        val currentInfo = getPdfZoomInfo(fileKey)
        setPdfZoomInfo(fileKey, currentInfo.copy(zoom = zoom))
    }
    
    /**
     * 更新指定文件的偏移量
     */
    fun updatePdfOffset(fileKey: String, offset: Offset) {
        val currentInfo = getPdfZoomInfo(fileKey)
        setPdfZoomInfo(fileKey, currentInfo.copy(offset = offset))
    }
    
    /**
     * 清除指定文件的 PDF 缩放信息
     */
    fun clearPdfZoomInfo(fileKey: String) {
        _pdfZoomInfoMap.value = _pdfZoomInfoMap.value.toMutableMap().apply {
            remove(fileKey)
        }
    }
    
    // ==================== 通用数据缓存 ====================
    
    /**
     * 通用字符串数据缓存
     * 可用于存储任意临时字符串数据
     */
    private val _stringData = MutableStateFlow<Map<String, String>>(emptyMap())
    val stringData: StateFlow<Map<String, String>> = _stringData.asStateFlow()
    
    fun getStringData(key: String): String? {
        return _stringData.value[key]
    }
    
    fun setStringData(key: String, value: String) {
        _stringData.value = _stringData.value.toMutableMap().apply {
            this[key] = value
        }
    }
    
    fun clearStringData(key: String) {
        _stringData.value = _stringData.value.toMutableMap().apply {
            remove(key)
        }
    }
    
    // ==================== 批量清理 ====================
    
    /**
     * 清除指定文件的所有缓存数据
     */
    fun clearFileCache(fileKey: String) {
        clearFullScreen(fileKey)
        clearPdfZoomInfo(fileKey)
    }
    
    /**
     * 清除所有缓存数据（谨慎使用）
     */
    fun clearAll() {
        _fullScreenStates.value = emptyMap()
        _pdfZoomInfoMap.value = emptyMap()
        _stringData.value = emptyMap()
    }
}

/**
 * PDF 缩放信息数据类
 */
data class PdfZoomInfo(
    val zoom: Float = 1f,
    val offset: Offset = Offset.Zero
)