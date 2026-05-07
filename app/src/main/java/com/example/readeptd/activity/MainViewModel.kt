package com.example.readeptd.activity

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.readeptd.data.ConfigureData
import com.example.readeptd.data.FileDataStore
import com.example.readeptd.data.FileInfo
import com.example.readeptd.data.ReadingState
import com.example.readeptd.data.TempFileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val fileDataStore = FileDataStore(application)
    
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    private val _readingStates = MutableStateFlow<Map<String, ReadingState>>(emptyMap())
    val readingStates: StateFlow<Map<String, ReadingState>> = _readingStates.asStateFlow()

    private val _lastReadingFile = MutableStateFlow<FileInfo?>(null)
    val lastReadingFile: StateFlow<FileInfo?> = _lastReadingFile.asStateFlow()

    // ✅ 配置数据缓存（类似 readingStates）
    private val _configData = MutableStateFlow<ConfigureData>(ConfigureData())
    val configData: StateFlow<ConfigureData> = _configData.asStateFlow()

    init {
        Log.d("MainViewModel", "ViewModel 创建: ${this.hashCode()}")
        loadInitialData()
        loadLastReadingFile()
        loadConfigData()  // ✅ 新增：加载配置数据
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d("MainViewModel", "ViewModel 清除: ${this.hashCode()}")
        // 如果真的能正常退出应用的话，就删除所有临时文件，避免占用空间
        cleanupOrphanedTempFiles(emptyList())
    }
    
    fun onEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.OnFilesSelected -> handleFilesSelected(event.files)
            is MainUiEvent.RemoveFile -> removeFile(event.index)
            is MainUiEvent.MoveFile -> moveFile(event.fromIndex, event.toIndex)
            is MainUiEvent.GoToContentActivity -> {
                _lastReadingFile.value = event.fileInfo
                saveLastReadingFile(event.fileInfo)
            }
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
    
    /**
     * ✅ 加载配置数据（持续监听 DataStore 变化）
     */
    private fun loadConfigData() {
        viewModelScope.launch {
            fileDataStore.configFlow.collect { config ->
                _configData.value = config
                Log.d("MainViewModel", "配置已更新: isNightMode=${config.isNightMode}, isDynamicColor=${config.isDynamicColor}")
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
                    
                    // 如果删除的是上次阅读的文件，清空 lastReadingFile
                    if (_lastReadingFile.value?.uri == removedFile.uri) {
                        _lastReadingFile.value = null
                        saveLastReadingFile(null)
                        Log.d("MainViewModel", "已清空上次阅读文件")
                    }
                    
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
            val deleted = TempFileManager.deleteTempFile(
                getApplication(),
                fileInfo.uri,
                fileInfo.fileName
            )
            
            if (deleted) {
                Log.d("MainViewModel", "已删除临时文件: ${fileInfo.fileName}")
            } else {
                Log.e("MainViewModel", "删除临时文件失败: ${fileInfo.fileName}")
            }
        }
    }
    
    private fun cleanupOrphanedTempFiles(currentFiles: List<FileInfo>) {
        viewModelScope.launch {
            val cleanedCount = TempFileManager.cleanupOrphanedFiles(
                getApplication(),
                currentFiles
            )
            
            Log.d("MainViewModel", "孤儿文件清理完成，共清理 $cleanedCount 个文件")
        }
    }
    
    /**
     * 加载上次打开的文件
     */
    private fun loadLastReadingFile() {
        viewModelScope.launch {
            try {
                val lastFile = fileDataStore.getLastReadingFile()
                _lastReadingFile.value = lastFile
                Log.d("MainViewModel", "加载上次阅读文件: ${lastFile?.fileName}")
            } catch (e: Exception) {
                Log.e("MainViewModel", "加载上次阅读文件失败", e)
            }
        }
    }
    
    /**
     * 保存上次打开的文件
     */
    private fun saveLastReadingFile(fileInfo: FileInfo?) {
        viewModelScope.launch {
            try {
                fileDataStore.saveLastReadingFile(fileInfo)
                Log.d("MainViewModel", "保存上次阅读文件: ${fileInfo?.fileName}")
            } catch (e: Exception) {
                Log.e("MainViewModel", "保存上次阅读文件失败", e)
            }
        }
    }

    /**
     * ✅ 更新应用配置（立即更新缓存，异步保存）
     */
    fun updateConfig(update: ConfigureData.() -> ConfigureData) {
        // ✅ 先立即更新内存（UI 立即响应）
        val currentConfig = _configData.value
        val newConfig = currentConfig.update()
        _configData.value = newConfig
        
        Log.d("MainViewModel", "配置已更新（内存）: isNightMode=${newConfig.isNightMode}, isDynamicColor=${newConfig.isDynamicColor}")
        
        // ✅ 再异步保存到磁盘
        viewModelScope.launch {
            try {
                fileDataStore.saveConfig(newConfig)
                Log.d("MainViewModel", "配置已保存到磁盘")
            } catch (e: Exception) {
                Log.e("MainViewModel", "保存配置失败，回滚", e)
                // 如果保存失败，回滚配置
                _configData.value = currentConfig
            }
        }
    }

}
