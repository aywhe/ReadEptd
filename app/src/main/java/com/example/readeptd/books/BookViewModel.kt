package com.example.readeptd.books

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.readeptd.data.FileDataStore
import com.example.readeptd.data.ReadingState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * 书籍阅读器 UI 状态基类
 */
sealed interface BookUiState {
    object Loading : BookUiState
    data class Ready(val tempFilePath: String) : BookUiState
    data class Error(val message: String) : BookUiState
}

/**
 * 书籍阅读器 ViewModel 基类
 * 提供通用的文件管理、阅读状态管理功能
 * 
 * @param T 具体的 ReadingState 类型
 */
abstract class BookViewModel<T : ReadingState>(
    application: Application,
    private val stateClass: Class<T>
) : AndroidViewModel(application) {

    private val fileDataStore = FileDataStore(application)

    private val _uiState = MutableStateFlow<BookUiState>(BookUiState.Loading)
    val uiState: StateFlow<BookUiState> = _uiState.asStateFlow()

    protected var currentTempFile: File? = null
    private var processedUri: String? = null
    
    // 当前阅读状态（使用泛型）
    protected var currentReadingState: T? = null
    protected var currentFileUri: String? = null
    
    // 用于防抖的 Flow
    private val _pendingReadingState = MutableStateFlow<T?>(null)

    /**
     * 准备书籍文件：将 content URI 复制到临时文件，并加载阅读状态
     */
    fun prepareBookFile(uri: Uri, fileName: String, filePrefix: String = "book") {
        val uriString = uri.toString()
        currentFileUri = uriString
        
        // 如果已经处理过同一个 URI，且临时文件仍存在，则跳过
        if (processedUri == uriString && currentTempFile?.exists() == true) {
            Log.d(getViewModelName(), "文件已准备，跳过: $fileName")
            _uiState.value = BookUiState.Ready(currentTempFile!!.absolutePath)
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d(getViewModelName(), "开始准备文件: $fileName")
                _uiState.value = BookUiState.Loading

                // 清理旧的临时文件
                cleanupTempFile()

                // 创建临时文件
                val tempFile = File(
                    getApplication<Application>().cacheDir,
                    "${filePrefix}_${System.currentTimeMillis()}_${fileName}"
                )

                // 复制文件内容
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("无法打开文件输入流")

                currentTempFile = tempFile
                processedUri = uriString
                Log.d(getViewModelName(), "临时文件创建成功: ${tempFile.absolutePath}")
                Log.d(getViewModelName(), "文件大小: ${tempFile.length()} bytes")
                
                // 加载上次的阅读状态
                loadLastReadingState(uriString)

                _uiState.value = BookUiState.Ready(tempFile.absolutePath)

            } catch (e: Exception) {
                Log.e(getViewModelName(), "准备文件失败", e)
                _uiState.value = BookUiState.Error("文件准备失败: ${e.message}")
            }
        }
    }

    /**
     * 清理临时文件
     */
    protected fun cleanupTempFile() {
        currentTempFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                    Log.d(getViewModelName(), "旧临时文件已删除: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(getViewModelName(), "删除临时文件失败", e)
            }
        }
        currentTempFile = null
    }

    /**
     * 重置状态（用于重新加载）
     */
    fun reset() {
        cleanupTempFile()
        currentReadingState = null
        _uiState.value = BookUiState.Loading
    }

    // ==================== 阅读状态管理 ====================
    
    /**
     * 加载上次的阅读状态
     */
    private suspend fun loadLastReadingState(uri: String) {
        try {
            val state = fileDataStore.getReadingState(uri)
            // 检查状态类型是否匹配
            if (state != null && stateClass.isInstance(state)) {
                @Suppress("UNCHECKED_CAST")
                currentReadingState = state as T
                Log.d(getViewModelName(), "恢复阅读状态: $state")
            } else {
                currentReadingState = null
                Log.d(getViewModelName(), "未找到阅读状态或类型不匹配，从头开始")
            }
        } catch (e: Exception) {
            Log.e(getViewModelName(), "加载阅读状态失败", e)
            currentReadingState = null
        }
    }
    
    /**
     * 保存阅读进度（带防抖）
     * 不会立即写入，而是在停止更新 1 秒后自动保存
     */
    @OptIn(FlowPreview::class)
    fun saveProgress(readingState: T) {
        // 更新内存中的状态
        currentReadingState = readingState
        currentFileUri = readingState.uri
        
        // 发送到防抖 Flow
        _pendingReadingState.value = readingState
    }
    
    /**
     * 初始化防抖保存
     * 在 ViewModel 创建时启动，监听状态变化并延迟保存
     */
    init {
        viewModelScope.launch {
            // 防抖：1 秒内没有新的更新才执行保存
            _pendingReadingState
                .debounce(1000)  // 1 秒防抖
                .collect { readingState ->
                    readingState?.let {
                        persistReadingState(it)
                    }
                }
        }
    }
    
    /**
     * 实际执行持久化操作
     */
    private suspend fun persistReadingState(readingState: T) {
        try {
            fileDataStore.saveReadingState(readingState)
            Log.d(getViewModelName(), "持久化阅读状态: $readingState")
        } catch (e: Exception) {
            Log.e(getViewModelName(), "持久化阅读状态失败", e)
        }
    }
    
    /**
     * 立即保存（用于退出时）
     */
    fun saveProgressImmediately() {
        currentReadingState?.let {
            viewModelScope.launch {
                persistReadingState(it)
            }
        }
    }
    
    /**
     * 获取当前阅读状态
     */
    fun getCurrentState(): T? {
        return currentReadingState
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(getViewModelName(), "ViewModel 清除，清理临时文件")
        
        // 退出前立即保存最后一次状态
        saveProgressImmediately()
        
        cleanupTempFile()
    }

    /**
     * 获取 ViewModel 名称（用于日志）
     */
    protected abstract fun getViewModelName(): String

    companion object {
        private const val TAG = "BookViewModel"
    }
}

