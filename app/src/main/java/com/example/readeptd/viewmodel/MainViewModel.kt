package com.example.readeptd.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
}
