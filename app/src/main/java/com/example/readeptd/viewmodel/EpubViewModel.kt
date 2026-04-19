package com.example.readeptd.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.readeptd.data.FileDataStore
import com.example.readeptd.data.ReadingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * EPUB 阅读器 UI 状态
 */
sealed interface EpubUiState {
    object Loading : EpubUiState
    data class Ready(val tempFilePath: String) : EpubUiState
    data class Error(val message: String) : EpubUiState
}

/**
 * EPUB 阅读器 ViewModel
 * 负责管理临时文件、阅读状态和阅读器状态
 */
class EpubViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val fileDataStore = FileDataStore(application)

    private val _uiState = MutableStateFlow<EpubUiState>(EpubUiState.Loading)
    val uiState: StateFlow<EpubUiState> = _uiState.asStateFlow()

    private var currentTempFile: File? = null
    private var processedUri: String? = null
    
    // 当前阅读状态
    private var currentReadingState: ReadingState.Epub? = null
    private var currentFileUri: String? = null

    /**
     * 准备 EPUB 文件：将 content URI 复制到临时文件，并加载阅读状态
     */
    fun prepareEpubFile(uri: Uri, fileName: String) {
        val uriString = uri.toString()
        currentFileUri = uriString
        
        // 如果已经处理过同一个 URI，且临时文件仍存在，则跳过
        if (processedUri == uriString && currentTempFile?.exists() == true) {
            Log.d("EpubViewModel", "文件已准备，跳过: $fileName")
            _uiState.value = EpubUiState.Ready(currentTempFile!!.absolutePath)
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d("EpubViewModel", "开始准备 EPUB 文件: $fileName")
                _uiState.value = EpubUiState.Loading

                // 清理旧的临时文件
                cleanupTempFile()

                // 创建临时文件
                val tempFile = File(
                    getApplication<Application>().cacheDir,
                    "epub_${System.currentTimeMillis()}_${fileName}"
                )

                // 复制文件内容
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("无法打开文件输入流")

                currentTempFile = tempFile
                processedUri = uriString
                Log.d("EpubViewModel", "临时文件创建成功: ${tempFile.absolutePath}")
                Log.d("EpubViewModel", "文件大小: ${tempFile.length()} bytes")
                
                // 加载上次的阅读状态
                loadLastReadingState(uriString)

                _uiState.value = EpubUiState.Ready(tempFile.absolutePath)

            } catch (e: Exception) {
                Log.e("EpubViewModel", "准备 EPUB 文件失败", e)
                _uiState.value = EpubUiState.Error("文件准备失败: ${e.message}")
            }
        }
    }

    /**
     * 清理临时文件
     */
    private fun cleanupTempFile() {
        currentTempFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                    Log.d("EpubViewModel", "旧临时文件已删除: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e("EpubViewModel", "删除临时文件失败", e)
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
        _uiState.value = EpubUiState.Loading
    }

    // ==================== 阅读状态管理 ====================
    
    /**
     * 加载上次的阅读状态
     */
    private suspend fun loadLastReadingState(uri: String) {
        try {
            val state = fileDataStore.getReadingState(uri)
            if (state is ReadingState.Epub) {
                currentReadingState = state
                Log.d(TAG, "恢复阅读状态: CFI=${state.cfi}, Page=${state.page}, Progress=${state.progress}")
            } else {
                currentReadingState = null
                Log.d(TAG, "未找到阅读状态，从头开始")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载阅读状态失败", e)
            currentReadingState = null
        }
    }
    
    /**
     * 保存阅读进度
     * @param cfi EPUB CFI 位置标识符
     * @param page 当前页码
     * @param totalPages 总页数
     * @param progress 阅读进度 (0.0-1.0)
     */
    fun saveProgress(cfi: String?, page: Int?, totalPages: Int?, progress: Float) {
        val uri = currentFileUri ?: return
        
        viewModelScope.launch {
            try {
                val state = ReadingState.Epub(
                    uri = uri,
                    cfi = cfi,
                    page = page,
                    totalPages = totalPages,
                    progress = progress
                )
                currentReadingState = state
                fileDataStore.saveReadingState(state)
                Log.d(TAG, "保存阅读状态: CFI=$cfi, Page=$page, Progress=$progress")
            } catch (e: Exception) {
                Log.e(TAG, "保存阅读状态失败", e)
            }
        }
    }
    
    /**
     * 获取当前阅读状态
     */
    fun getCurrentReadingState(): ReadingState.Epub? {
        return currentReadingState
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("EpubViewModel", "ViewModel 清除，清理临时文件")
        cleanupTempFile()
    }

    companion object {
        private const val TAG = "EpubViewModel"
    }
}

