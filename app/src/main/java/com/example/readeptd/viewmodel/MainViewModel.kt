package com.example.readeptd.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.readeptd.ui.MainUiEvent
import com.example.readeptd.ui.MainUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * MainViewModel - 遵循 MVVM 单向数据流模式
 * 
 * 数据流向:
 * UI -> Event -> ViewModel -> State -> UI
 */
class MainViewModel : ViewModel() {
    
    // 私有可变状态流
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    
    // 公有不可变状态流，供 UI 观察
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    init {
        // 初始化时加载默认数据
        loadInitialData()
    }
    
    /**
     * 处理 UI 事件 - 单向数据流的入口
     */
    fun onEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.UpdateGreeting -> updateGreeting(event.name)
            is MainUiEvent.Refresh -> refreshData()
        }
    }
    
    /**
     * 加载初始数据
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Success("Hello Android!")
        }
    }
    
    /**
     * 更新问候语
     */
    private fun updateGreeting(name: String) {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            // 模拟异步操作
            try {
                _uiState.value = MainUiState.Success("Hello $name!")
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * 刷新数据
     */
    private fun refreshData() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            // 模拟刷新操作
            try {
                _uiState.value = MainUiState.Success("Hello Android! (Refreshed)")
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
