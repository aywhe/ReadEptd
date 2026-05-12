package com.example.readeptd.activity

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.readeptd.data.AppMemoryStore
import com.example.readeptd.data.ConfigureData
import com.example.readeptd.data.FileDataStore
import com.example.readeptd.data.FileInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContentViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val fileDataStore = FileDataStore(application)
    
    // ✅ 应用配置缓存（类似 MainViewModel）
    private val _configData = MutableStateFlow(ConfigureData())
    val configData: StateFlow<ConfigureData> = _configData.asStateFlow()

    private val _uiState = MutableStateFlow<ContentUiState>(ContentUiState.Loading)
    val uiState: StateFlow<ContentUiState> = _uiState.asStateFlow()
    private val _progressText = MutableStateFlow("-%")
    val progressText: StateFlow<String> = _progressText.asStateFlow()
    private var _onClickProgressInfoCallback: ((String) -> Unit)? = null
    private var _onClickSearchButtonCallback: (() -> Unit)? = null

    // ✅ 当前文件 URI（用于关联 AppMemoryStore 中的全屏状态）
    private var currentFileUri: String? = null

    init {
        Log.d("ContentViewModel", "ViewModel 创建: ${this.hashCode()}")
        // ✅ 启动配置监听
        loadConfigData()
        //_configData.value = _configData.value.copy(isSwipeLayout = false)
        // 注意：数据需要通过外部传入，而不是从 SavedStateHandle 获取
        // 因为我们是使用 Intent 传递的数据
    }

    /**
     * ✅ 加载配置数据（持续监听 DataStore 变化）
     */
    private fun loadConfigData() {
        viewModelScope.launch {
            fileDataStore.configFlow.collect { config ->
                _configData.value = config
                Log.d("ContentViewModel", "配置已更新: isNightMode=${config.isNightMode}, isDynamicColor=${config.isDynamicColor}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _onClickProgressInfoCallback = null
        _onClickSearchButtonCallback = null
        Log.d("ContentViewModel", "ViewModel 清除: ${this.hashCode()}")
    }

    fun onEvent(event: ContentUiEvent) {
        when (event) {
            is ContentUiEvent.Initialize -> handleInitialize(event.fileInfo)
            is ContentUiEvent.OnClickProgressInfo -> {
                _onClickProgressInfoCallback?.invoke(event.progressText)
            }
            is ContentUiEvent.OnClickSearchButton -> {
                _onClickSearchButtonCallback?.invoke()
            }
            is ContentUiEvent.OnDoubleClickScreen -> {
                // ✅ 使用 AppMemoryStore 管理全屏状态
                currentFileUri?.let { uri ->
                    AppMemoryStore.toggleFullScreen(uri)
                    Log.d("ContentViewModel", "切换全屏状态: $uri -> ${AppMemoryStore.isFullScreen(uri)}")
                }
            }
            is ContentUiEvent.OnScreenOrientationChanged ->{
                // ✅ 屏幕旋转时重置全屏状态
                currentFileUri?.let { uri ->
                    //AppMemoryStore.setFullScreen(uri, false)
                    Log.d("ContentViewModel", "屏幕旋转，重置全屏状态: $uri")
                }
            }
        }
    }

    fun setOnClickProgressInfoCallback(callback: ((String) -> Unit)?) {
        _onClickProgressInfoCallback = callback
    }

    fun setOnClickSearchButtonCallback(callback: (() -> Unit)?) {
        _onClickSearchButtonCallback = callback
    }

    /**
     * 更新进度信息
     */
    fun updateProgressText(progressText: String) {
        Log.d("ContentViewModel", "更新进度信息: $progressText")
        _progressText.value = progressText
    }

    /**
     * ✅ 获取当前文件的全屏状态（从 AppMemoryStore 读取）
     */
    fun getIsFullScreen(): Boolean {
        return currentFileUri?.let { uri ->
            AppMemoryStore.isFullScreen(uri)
        } ?: false
    }

    /**
     * 处理初始化事件
     */
    private fun handleInitialize(fileInfo: FileInfo?) {
        viewModelScope.launch {
            try {
                // 先重置为 Loading 状态，确保 UI 显示加载中
                _uiState.value = ContentUiState.Loading
                
                if (fileInfo != null) {
                    // ✅ 保存当前文件 URI，用于关联全屏状态
                    currentFileUri = fileInfo.uri
                    Log.d("ContentViewModel", "成功加载文件信息: ${fileInfo.fileName}, URI: ${fileInfo.uri}")
                    _uiState.value = ContentUiState.Success(fileInfo)
                } else {
                    Log.e("ContentViewModel", "未找到文件信息")
                    _uiState.value = ContentUiState.Error("未找到文件信息")
                }
            } catch (e: Exception) {
                Log.e("ContentViewModel", "加载文件信息失败", e)
                _uiState.value = ContentUiState.Error("加载文件失败: ${e.message}")
            }
        }
    }
}
