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
            is MainUiEvent.UpdateGreeting -> updateGreeting(event.name)
            is MainUiEvent.Refresh -> refreshData()
            is MainUiEvent.OnButtonClick -> handleButtonClick()
            is MainUiEvent.OnFilesSelected -> handleFilesSelected(event.files)
        }
    }
    
    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Success("Hello Android!")
        }
    }
    
    private fun updateGreeting(name: String) {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            try {
                _uiState.value = MainUiState.Success("Hello $name!")
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    private fun refreshData() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            try {
                _uiState.value = MainUiState.Success("Hello Android! (Refreshed)")
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    private fun handleButtonClick() {
        viewModelScope.launch {
            Log.d("MainViewModel", "浮动按钮被点击")
        }
    }
    
    private fun handleFilesSelected(files: List<FileInfo>) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is MainUiState.Success) {
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
                
                _uiState.value = currentState.copy(
                    selectedFiles = updatedFiles,
                    message = "已选择 ${updatedFiles.size} 个文件"
                )
                Log.d("MainViewModel", "文件列表已更新，共 ${updatedFiles.size} 个")
            }
        }
    }
}
