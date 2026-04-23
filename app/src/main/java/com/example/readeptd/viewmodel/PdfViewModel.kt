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
 * PDF 阅读器 UI 状态
 */
sealed interface PdfUiState {
    object Loading : PdfUiState
    data class Ready(val tempFilePath: String) : PdfUiState
    data class Error(val message: String) : PdfUiState
}

/**
 * PDF 阅读器 ViewModel
 * 负责管理临时文件、PDF 文档的加载和生命周期
 */
class PdfViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<PdfUiState>(PdfUiState.Loading)
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    private var currentTempFile: File? = null
    private var processedUri: String? = null

    /**
     * 准备 PDF 文件：将 content URI 复制到临时文件
     */
    fun preparePdfFile(uri: Uri, fileName: String) {
        val uriString = uri.toString()
        
        if (processedUri == uriString && currentTempFile?.exists() == true) {
            Log.d("PdfViewModel", "文件已准备，跳过: $fileName")
            _uiState.value = PdfUiState.Ready(currentTempFile!!.absolutePath)
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d("PdfViewModel", "开始准备 PDF 文件: $fileName")
                _uiState.value = PdfUiState.Loading

                cleanupTempFile()

                val tempFile = File(
                    getApplication<Application>().cacheDir,
                    "pdf_${System.currentTimeMillis()}_${fileName}"
                )

                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("无法打开文件输入流")

                currentTempFile = tempFile
                processedUri = uriString
                Log.d("PdfViewModel", "临时文件创建成功: ${tempFile.absolutePath}")
                Log.d("PdfViewModel", "文件大小: ${tempFile.length()} bytes")

                _uiState.value = PdfUiState.Ready(tempFile.absolutePath)

            } catch (e: Exception) {
                Log.e("PdfViewModel", "准备 PDF 文件失败", e)
                _uiState.value = PdfUiState.Error("文件准备失败: ${e.message}")
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
                    Log.d("PdfViewModel", "旧临时文件已删除: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e("PdfViewModel", "删除临时文件失败", e)
            }
        }
        currentTempFile = null
    }

    /**
     * 重置状态（用于重新加载）
     */
    fun reset() {
        cleanupTempFile()
        processedUri = null
        _uiState.value = PdfUiState.Loading
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("PdfViewModel", "ViewModel 清除，清理资源")
        cleanupTempFile()
    }

    companion object {
        private const val TAG = "PdfViewModel"
    }
}
