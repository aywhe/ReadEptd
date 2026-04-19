package com.example.readeptd.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
 * 负责管理临时文件和阅读器状态
 */
class EpubViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<EpubUiState>(EpubUiState.Loading)
    val uiState: StateFlow<EpubUiState> = _uiState.asStateFlow()

    private var currentTempFile: File? = null
    private var processedUri: String? = null

    /**
     * 准备 EPUB 文件：将 content URI 复制到临时文件
     */
    fun prepareEpubFile(uri: Uri, fileName: String) {
        val uriString = uri.toString()
        
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
        _uiState.value = EpubUiState.Loading
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

