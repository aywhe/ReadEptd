package com.example.readeptd.activity

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.readeptd.data.FileInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContentViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ContentUiState>(ContentUiState.Loading)
    val uiState: StateFlow<ContentUiState> = _uiState.asStateFlow()
    private val _progressText = MutableStateFlow("")
    val progressText: StateFlow<String> = _progressText.asStateFlow()
    private var _onClickProgressInfoCallback: ((String) -> Unit)? = null
    private var _onClickSearchButtonCallback: (() -> Unit)? = null
    private val _isFullScreen = MutableStateFlow(false)
    val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    init {
        Log.d("ContentViewModel", "ViewModel 创建: ${this.hashCode()}")
        // 注意：数据需要通过外部传入，而不是从 SavedStateHandle 获取
        // 因为我们是使用 Intent 传递的数据
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
                _isFullScreen.value = !_isFullScreen.value
            }
            is ContentUiEvent.OnScreenOrientationChanged ->{
                _isFullScreen.value = false
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
     * 处理初始化事件
     */
    private fun handleInitialize(fileInfo: FileInfo?) {
        viewModelScope.launch {
            try {
                // 先重置为 Loading 状态，确保 UI 显示加载中
                _uiState.value = ContentUiState.Loading
                
                if (fileInfo != null) {
                    Log.d("ContentViewModel", "成功加载文件信息: ${fileInfo.fileName}")
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
