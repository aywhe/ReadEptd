package com.example.readeptd.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.readeptd.ui.ContentUiEvent
import com.example.readeptd.ui.ContentUiState
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

    init {
        Log.d("ContentViewModel", "ViewModel 创建: ${this.hashCode()}")
        loadFileInfo()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("ContentViewModel", "ViewModel 清除: ${this.hashCode()}")
    }

    fun onEvent(event: ContentUiEvent) {
        when (event) {
            is ContentUiEvent.LoadFileContent -> handleLoadFileContent()
        }
    }

    private fun loadFileInfo() {
        viewModelScope.launch {
            try {
                val fileInfo = savedStateHandle.get<FileInfo>("file_info")

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

    private fun handleLoadFileContent() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is ContentUiState.Success) {
                Log.d("ContentViewModel", "开始加载文件内容: ${currentState.fileInfo.fileName}")

                // TODO: 在这里实现文件内容加载逻辑
                // 根据不同的文件类型（TXT、PDF、EPUB等）进行解析

                Log.d("ContentViewModel", "文件内容加载完成")
            }
        }
    }
}