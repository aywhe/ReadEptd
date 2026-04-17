package com.example.readeptd.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.readeptd.ui.FileInfo
import com.example.readeptd.ui.MainUiEvent
import com.example.readeptd.ui.MainUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    init {
        Log.d("MainViewModel", "ViewModel 创建: ${this.hashCode()}")
        loadInitialData()
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d("MainViewModel", "ViewModel 清除: ${this.hashCode()}")
    }
    
    fun onEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.OnFilesSelected -> handleFilesSelected(event.files)
            is MainUiEvent.RemoveFile -> removeFile(event.index)
            is MainUiEvent.MoveFile -> moveFile(event.fromIndex, event.toIndex)
        }
    }
    
    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Success()
        }
    }

    private fun handleFilesSelected(files: List<FileInfo>) {
        viewModelScope.launch {
            Log.d("MainViewModel", "收到 ${files.size} 个文件")
            val currentState = _uiState.value
            Log.d("MainViewModel", "当前状态类型: ${currentState::class.simpleName}")
            if (currentState is MainUiState.Success) {
                Log.d("MainViewModel", "当前已有 ${currentState.selectedFiles.size} 个文件")
                val existingFiles = currentState.selectedFiles.toMutableList()
                val newFiles = mutableListOf<FileInfo>()
                
                files.forEach { file ->
                    val existingIndex = existingFiles.indexOfFirst { it.uri == file.uri }
                    
                    if (existingIndex != -1) {
                        existingFiles.removeAt(existingIndex)
                        Log.d("MainViewModel", "文件已存在，移动到末尾: ${file.fileName}")
                    } else {
                        Log.d("MainViewModel", "新文件: ${file.fileName}")
                    }
                    
                    newFiles.add(file)
                }
                
                val updatedFiles = existingFiles + newFiles
                
                Log.d("MainViewModel", "准备更新状态，新文件数: ${updatedFiles.size}")
                _uiState.value = currentState.copy(
                    selectedFiles = updatedFiles
                )
                Log.d("MainViewModel", "状态已更新，UI应该刷新")
            }
        }
    }
    
    private fun removeFile(index: Int) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is MainUiState.Success) {
                if (index in currentState.selectedFiles.indices) {
                    val updatedFiles = currentState.selectedFiles.toMutableList().apply {
                        removeAt(index)
                    }
                    _uiState.value = currentState.copy(
                        selectedFiles = updatedFiles
                    )
                    Log.d("MainViewModel", "文件已删除，剩余 ${updatedFiles.size} 个")
                } else {
                    Log.e("MainViewModel", "无效的文件索引: $index")
                }
            }
        }
    }
    
    private fun moveFile(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is MainUiState.Success) {
                if (fromIndex in currentState.selectedFiles.indices && 
                    toIndex in currentState.selectedFiles.indices) {
                    val updatedFiles = currentState.selectedFiles.toMutableList().apply {
                        add(toIndex, removeAt(fromIndex))
                    }
                    _uiState.value = currentState.copy(
                        selectedFiles = updatedFiles
                    )
                    Log.d("MainViewModel", "文件从 $fromIndex 移动到 $toIndex")
                } else {
                    Log.e("MainViewModel", "无效的文件索引: from=$fromIndex, to=$toIndex")
                }
            }
        }
    }
}
