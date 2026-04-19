package com.example.readeptd.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.pdf.PdfDocument
import androidx.pdf.SandboxedPdfLoader
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

    private val _pdfDocument = MutableStateFlow<PdfDocument?>(null)
    val pdfDocument: StateFlow<PdfDocument?> = _pdfDocument.asStateFlow()

    private var pdfLoader: SandboxedPdfLoader? = null
    private var currentPdfDocument: PdfDocument? = null
    private var currentTempFile: File? = null
    private var processedUri: String? = null

    /**
     * 准备 PDF 文件：将 content URI 复制到临时文件并加载
     */
    fun preparePdfFile(uri: Uri, fileName: String) {
        val uriString = uri.toString()
        
        // 如果已经处理过同一个 URI，且临时文件仍存在，则跳过
        if (processedUri == uriString && currentTempFile?.exists() == true) {
            Log.d("PdfViewModel", "文件已准备，跳过: $fileName")
            _uiState.value = PdfUiState.Ready(currentTempFile!!.absolutePath)
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d("PdfViewModel", "开始准备 PDF 文件: $fileName")
                _uiState.value = PdfUiState.Loading

                // 清理旧的临时文件和 PDF 文档
                cleanupTempFile()
                cleanupPdfDocument()

                // 创建临时文件
                val tempFile = File(
                    getApplication<Application>().cacheDir,
                    "pdf_${System.currentTimeMillis()}_${fileName}"
                )

                // 复制文件内容
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("无法打开文件输入流")

                currentTempFile = tempFile
                processedUri = uriString
                Log.d("PdfViewModel", "临时文件创建成功: ${tempFile.absolutePath}")
                Log.d("PdfViewModel", "文件大小: ${tempFile.length()} bytes")

                // 创建 PDF 加载器
                if (pdfLoader == null) {
                    pdfLoader = SandboxedPdfLoader(getApplication<Application>())
                }

                // 从临时文件加载 PDF 文档
                val pdfDocument = pdfLoader!!.openDocument(Uri.fromFile(tempFile), null)
                currentPdfDocument = pdfDocument
                _pdfDocument.value = pdfDocument  // ✅ 暴露给 Compose
                
                Log.d("PdfViewModel", "PDF 加载成功")
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
     * 清理 PDF 文档资源
     */
    private fun cleanupPdfDocument() {
        currentPdfDocument?.let { document ->
            try {
                document.close()
                Log.d("PdfViewModel", "旧 PDF 文档已关闭")
            } catch (e: Exception) {
                Log.e("PdfViewModel", "关闭 PDF 文档失败", e)
            }
        }
        currentPdfDocument = null
        _pdfDocument.value = null  // ✅ 清除 StateFlow
    }

    /**
     * 获取当前 PDF 文档
     */
    fun getPdfDocument(): PdfDocument? {
        return currentPdfDocument
    }

    /**
     * 重置状态（用于重新加载）
     */
    fun reset() {
        cleanupTempFile()
        cleanupPdfDocument()
        processedUri = null
        _uiState.value = PdfUiState.Loading
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("PdfViewModel", "ViewModel 清除，清理资源")
        cleanupTempFile()
        cleanupPdfDocument()
        pdfLoader = null
    }

    companion object {
        private const val TAG = "PdfViewModel"
    }
}
