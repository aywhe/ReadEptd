package com.example.readeptd.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class EpubUiState {
    object Loading : EpubUiState()
    data class Success(
        val filePath: String,
        val bookTitle: String = "未知书籍"
    ) : EpubUiState()
    data class Error(val message: String) : EpubUiState()
}

sealed class EpubUiEvent {
    data class LoadEpub(val fileUri: Uri) : EpubUiEvent()
    data class ChangePage(val pageIndex: Int) : EpubUiEvent()
}

class EpubViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow<EpubUiState>(EpubUiState.Loading)
    val uiState: StateFlow<EpubUiState> = _uiState.asStateFlow()
    
    private var currentPageIndex = 0
    private var currentFilePath: String? = null
    
    init {
        Log.d("EpubViewModel", "ViewModel 创建: ${this.hashCode()}")
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d("EpubViewModel", "ViewModel 清除: ${this.hashCode()}")
        // 清理资源
        if (_uiState.value is EpubUiState.Success) {
            // 可以在这里添加清理逻辑
        }
    }
    
    fun onEvent(event: EpubUiEvent) {
        when (event) {
            is EpubUiEvent.LoadEpub -> handleLoadEpub(event.fileUri)
            is EpubUiEvent.ChangePage -> handleChangePage(event.pageIndex)
        }
    }
    
    /**
     * 处理加载 EPUB 文件
     */
    private fun handleLoadEpub(fileUri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value = EpubUiState.Loading
                
                val context = getApplication<Application>()
                
                // 将 URI 复制到临时文件（如果需要）
                val filePath = if (fileUri.scheme == "content") {
                    copyUriToTempFile(context, fileUri)
                } else {
                    fileUri.path
                }
                
                if (filePath != null && File(filePath).exists()) {
                    currentFilePath = filePath
                    currentPageIndex = 0
                    
                    // 从文件名提取书名
                    val bookTitle = File(filePath).nameWithoutExtension
                    
                    _uiState.value = EpubUiState.Success(
                        filePath = filePath,
                        bookTitle = bookTitle
                    )
                    Log.d("EpubViewModel", "成功加载 EPUB: $bookTitle, 路径: $filePath")
                } else {
                    _uiState.value = EpubUiState.Error("无法访问 EPUB 文件")
                }
            } catch (e: Exception) {
                Log.e("EpubViewModel", "加载 EPUB 文件失败", e)
                _uiState.value = EpubUiState.Error("打开文件失败: ${e.message}")
            }
        }
    }
    
    /**
     * 处理页面切换
     */
    private fun handleChangePage(pageIndex: Int) {
        currentPageIndex = pageIndex
        Log.d("EpubViewModel", "切换到第 ${pageIndex + 1} 页")
    }
    
    /**
     * 获取当前页码
     */
    fun getCurrentPageIndex(): Int {
        return currentPageIndex
    }
    
    /**
     * 将 URI 复制到临时文件
     */
    private fun copyUriToTempFile(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val cacheFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.epub")
                inputStream.copyTo(cacheFile.outputStream())
                cacheFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
