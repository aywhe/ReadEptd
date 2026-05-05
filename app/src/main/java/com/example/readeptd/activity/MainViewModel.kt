package com.example.readeptd.activity

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.readeptd.data.FileDataStore
import com.example.readeptd.data.FileInfo
import com.example.readeptd.data.ReadingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import com.example.readeptd.utils.Utils
import androidx.core.net.toUri

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val fileDataStore = FileDataStore(application)
    
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    private val _readingStates = MutableStateFlow<Map<String, ReadingState>>(emptyMap())
    val readingStates: StateFlow<Map<String, ReadingState>> = _readingStates.asStateFlow()

    init {
        Log.d("MainViewModel", "ViewModel 创建: ${this.hashCode()}")
        loadInitialData()
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d("MainViewModel", "ViewModel 清除: ${this.hashCode()}")
        cleanupOrphanedTempFiles(emptyList())
    }
    
    fun onEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.OnFilesSelected -> handleFilesSelected(event.files)
            is MainUiEvent.RemoveFile -> removeFile(event.index)
            is MainUiEvent.MoveFile -> moveFile(event.fromIndex, event.toIndex)
        }
    }
    
    /**
     * 查询指定文件的阅读进度
     */
    fun getProgress(uri: String): Float? {
        return _readingStates.value[uri]?.progress
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // 从 DataStore 加载保存的文件列表
            fileDataStore.readingFilesFlow.collect { savedFiles ->
                Log.d("MainViewModel", "从 DataStore 加载了 ${savedFiles.size} 个文件")
                _uiState.value = MainUiState.Success(
                    readingFiles = savedFiles
                )
                
                cleanupOrphanedTempFiles(savedFiles)
            }
        }
    }
    
    fun loadReadingStates() {
        viewModelScope.launch {
            fileDataStore.allReadingStatesFlow.collect { states ->
                _readingStates.value = states
                Log.d("MainViewModel", "更新了阅读状态缓存，共 ${states.size} 个")
            }
        }
    }
    
    private fun handleFilesSelected(files: List<FileInfo>) {
        viewModelScope.launch {
            Log.d("MainViewModel", "收到 ${files.size} 个文件")
            val currentState = _uiState.value
            Log.d("MainViewModel", "当前状态类型: ${currentState::class.simpleName}")
            if (currentState is MainUiState.Success) {
                Log.d("MainViewModel", "当前已有 ${currentState.readingFiles.size} 个文件")
                val existingFiles = currentState.readingFiles.toMutableList()
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
                    readingFiles = updatedFiles
                )
                Log.d("MainViewModel", "状态已更新，UI应该刷新")
                
                // 保存到 DataStore
                saveReadingFiles(updatedFiles)
            }
        }
    }
    
    private fun removeFile(index: Int) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is MainUiState.Success) {
                if (index in currentState.readingFiles.indices) {
                    val removedFile = currentState.readingFiles[index]
                    
                    val updatedFiles = currentState.readingFiles.toMutableList().apply {
                        removeAt(index)
                    }
                    _uiState.value = currentState.copy(
                        readingFiles = updatedFiles
                    )
                    Log.d("MainViewModel", "文件已删除，剩余 ${updatedFiles.size} 个")
                    
                    // 保存到 DataStore
                    saveReadingFiles(updatedFiles)
                    
                    fileDataStore.deleteReadingState(removedFile.uri)
                    Log.d("MainViewModel", "已删除阅读状态: ${removedFile.fileName}")
                    
                    deleteTempFileForRemovedFile(removedFile)
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
                if (fromIndex in currentState.readingFiles.indices &&
                    toIndex in currentState.readingFiles.indices) {
                    val updatedFiles = currentState.readingFiles.toMutableList().apply {
                        add(toIndex, removeAt(fromIndex))
                    }
                    _uiState.value = currentState.copy(
                        readingFiles = updatedFiles
                    )
                    Log.d("MainViewModel", "文件从 $fromIndex 移动到 $toIndex")
                    
                    // 保存到 DataStore
                    saveReadingFiles(updatedFiles)
                } else {
                    Log.e("MainViewModel", "无效的文件索引: from=$fromIndex, to=$toIndex")
                }
            }
        }
    }
    
    /**
     * 保存文件列表到 DataStore
     */
    private fun saveReadingFiles(files: List<FileInfo>) {
        viewModelScope.launch {
            try {
                fileDataStore.saveReadingFiles(files)
                Log.d("MainViewModel", "成功保存 ${files.size} 个文件到 DataStore")
            } catch (e: Exception) {
                Log.e("MainViewModel", "保存文件失败", e)
            }
        }
    }

    private fun deleteTempFileForRemovedFile(fileInfo: FileInfo) {
        viewModelScope.launch {
            try {
                val tempFileName = Utils.generateTempFileName(fileInfo.uri, fileInfo.fileName)
                
                val tempFile = File(
                    getApplication<Application>().cacheDir,
                    tempFileName
                )
                
                if (tempFile.exists()) {
                    tempFile.delete()
                    Log.d("MainViewModel", "已删除临时文件: ${fileInfo.fileName}")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "删除临时文件失败: ${fileInfo.fileName}", e)
            }
        }
    }
    
    private fun cleanupOrphanedTempFiles(currentFiles: List<FileInfo>) {
        viewModelScope.launch {
            try {
                val cacheDir = getApplication<Application>().cacheDir
                val tempFiles = cacheDir.listFiles { file ->
                    file.name.startsWith("book_")
                }
                
                val validFileNames = currentFiles.map { fileInfo ->
                    Utils.generateTempFileName(fileInfo.uri, fileInfo.fileName)
                }.toSet()
                
                tempFiles?.forEach { file ->
                    if (file.name !in validFileNames) {
                        file.delete()
                        Log.d("MainViewModel", "清理孤儿临时文件: ${file.name}")
                    }
                }
                
                Log.d("MainViewModel", "孤儿文件清理完成")
            } catch (e: Exception) {
                Log.e("MainViewModel", "清理孤儿文件失败", e)
            }
        }
    }
}
