package com.example.readeptd.data

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
    
    // ✅ 创建一个全局 CoroutineScope 用于 stateIn
    private val scope = CoroutineScope(Dispatchers.Default)
    
    // ==================== 全屏状态管理 ====================
    
    /**
     * 全屏状态缓存
     * Key: 文件 URI 或唯一标识
     * Value: 是否全屏
     */
    private val _fullScreenStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val fullScreenStates: StateFlow<Map<String, Boolean>> = _fullScreenStates.asStateFlow()
    
    /**
     * 获取指定文件的全屏状态 StateFlow（便于 Composable 中直接收集）
     * @param fileKey 文件 URI，可为 null，null 时返回默认值 false
     */
    fun fullScreenStateFlow(fileKey: String?): StateFlow<Boolean> {
        if (fileKey == null) {
            return MutableStateFlow(false).asStateFlow()
        }
        return _fullScreenStates
            .map { it[fileKey] ?: false }
            .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = false)
    }
    
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
     * Key: 文件 URI
     * Value: 包含横屏和竖屏的缩放信息
     */
    private val _pdfZoomInfoMap = MutableStateFlow<Map<String, PdfZoomInfo>>(emptyMap())
    val pdfZoomInfoMap: StateFlow<Map<String, PdfZoomInfo>> = _pdfZoomInfoMap.asStateFlow()
    
    /**
     * 获取指定文件的 PDF 缩放信息 StateFlow（便于 Composable 中直接收集）
     * @param fileKey 文件 URI，可为 null，null 时返回默认值 PdfZoomInfo()
     */
    fun pdfZoomInfoStateFlow(fileKey: String?): StateFlow<PdfZoomInfo> {
        if (fileKey == null) {
            return MutableStateFlow(PdfZoomInfo()).asStateFlow()
        }
        return _pdfZoomInfoMap
            .map { it[fileKey] ?: PdfZoomInfo() }
            .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = PdfZoomInfo())
    }
    
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
     * 更新指定文件在特定屏幕方向下的缩放比例
     * @param fileKey 文件 URI
     * @param isLandscape 是否为横屏
     * @param zoom 缩放比例
     */
    fun updatePdfZoom(fileKey: String, isLandscape: Boolean, zoom: Float) {
        val currentInfo = getPdfZoomInfo(fileKey)
        val updatedInfo = if (isLandscape) {
            currentInfo.copy(landscapeZoom = zoom)
        } else {
            currentInfo.copy(portraitZoom = zoom)
        }
        setPdfZoomInfo(fileKey, updatedInfo)
    }
    
    /**
     * 更新指定文件在特定屏幕方向下的偏移量
     * @param fileKey 文件 URI
     * @param isLandscape 是否为横屏
     * @param offset 偏移量
     */
    fun updatePdfOffset(fileKey: String, isLandscape: Boolean, offset: Offset) {
        val currentInfo = getPdfZoomInfo(fileKey)
        val updatedInfo = if (isLandscape) {
            currentInfo.copy(landscapeOffset = offset)
        } else {
            currentInfo.copy(portraitOffset = offset)
        }
        setPdfZoomInfo(fileKey, updatedInfo)
    }
    
    /**
     * 同时更新指定文件在特定屏幕方向下的缩放比例和偏移量
     * @param fileKey 文件 URI
     * @param isLandscape 是否为横屏
     * @param zoom 缩放比例
     * @param offset 偏移量
     */
    fun updatePdfZoomAndOffset(fileKey: String, isLandscape: Boolean, zoom: Float, offset: Offset) {
        val currentInfo = getPdfZoomInfo(fileKey)
        val updatedInfo = if (isLandscape) {
            currentInfo.copy(landscapeZoom = zoom, landscapeOffset = offset)
        } else {
            currentInfo.copy(portraitZoom = zoom, portraitOffset = offset)
        }
        setPdfZoomInfo(fileKey, updatedInfo)
    }
    
    /**
     * 清除指定文件的 PDF 缩放信息
     */
    fun clearPdfZoomInfo(fileKey: String) {
        _pdfZoomInfoMap.value = _pdfZoomInfoMap.value.toMutableMap().apply {
            remove(fileKey)
        }
    }

    // ==================== 上次阅读文件管理 ====================

    /**
     * 当前会话的上次阅读文件（不持久化，进程重启后丢失）
     */
    private val _lastReadingFile = MutableStateFlow<FileInfo?>(null)
    val lastReadingFile: StateFlow<FileInfo?> = _lastReadingFile.asStateFlow()

    /**
     * 获取上次阅读文件
     */
    fun getLastReadingFile(): FileInfo? {
        return _lastReadingFile.value
    }

    /**
     * 设置上次阅读文件
     */
    fun setLastReadingFile(fileInfo: FileInfo?) {
        _lastReadingFile.value = fileInfo
    }

    /**
     * 清除上次阅读文件
     */
    fun clearLastReadingFile() {
        _lastReadingFile.value = null
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
 * 同时保存横屏和竖屏两种状态的缩放信息
 */
data class PdfZoomInfo(
    // 竖屏状态
    val portraitZoom: Float = 1f,
    val portraitOffset: Offset = Offset.Zero,
    
    // 横屏状态
    val landscapeZoom: Float = 1f,
    val landscapeOffset: Offset = Offset.Zero
) {
    /**
     * 获取当前屏幕方向的缩放比例
     */
    fun getZoom(isLandscape: Boolean): Float {
        return if (isLandscape) landscapeZoom else portraitZoom
    }
    
    /**
     * 获取当前屏幕方向的偏移量
     */
    fun getOffset(isLandscape: Boolean): Offset {
        return if (isLandscape) landscapeOffset else portraitOffset
    }
}
