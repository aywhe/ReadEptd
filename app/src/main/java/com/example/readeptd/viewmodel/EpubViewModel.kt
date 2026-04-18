package com.example.readeptd.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.hamed.htepubreadr.component.EpubReaderComponent
import io.hamed.htepubreadr.entity.BookEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class EpubUiState {
    object Loading : EpubUiState()
    data class Success(
        val epubReader: EpubReaderComponent,
        val bookEntity: BookEntity,
        val totalPages: Int
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
                val filePath = copyUriToTempFile(context, fileUri)
                
                if (filePath != null) {
                    val epubReader = EpubReaderComponent(filePath)
                    val bookEntity = epubReader.make(context)
                    
                    if (bookEntity != null && bookEntity.pagePathList.isNotEmpty()) {
                        currentPageIndex = 0
                        _uiState.value = EpubUiState.Success(
                            epubReader = epubReader,
                            bookEntity = bookEntity,
                            totalPages = bookEntity.pagePathList.size
                        )
                        Log.d("EpubViewModel", "成功加载 EPUB: ${bookEntity.name}, 共 ${bookEntity.pagePathList.size} 章")
                    } else {
                        _uiState.value = EpubUiState.Error("无法解析 EPUB 文件或文件为空")
                    }
                } else {
                    _uiState.value = EpubUiState.Error("无法获取文件路径")
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
        val currentState = _uiState.value
        if (currentState is EpubUiState.Success) {
            if (pageIndex in 0 until currentState.totalPages) {
                currentPageIndex = pageIndex
                Log.d("EpubViewModel", "切换到第 ${pageIndex + 1} 章")
            }
        }
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
