package com.example.readeptd.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.pdf.compose.PdfViewer
import androidx.pdf.compose.PdfViewerState
import com.example.readeptd.data.FileInfo
import com.example.readeptd.viewmodel.PdfUiState
import com.example.readeptd.viewmodel.PdfViewModel

@Composable
fun PdfScreen(
    fileInfo: FileInfo,
    modifier: Modifier = Modifier,
    viewModel: PdfViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pdfDocument by viewModel.pdfDocument.collectAsState()

    // 准备 PDF 文件
    LaunchedEffect(fileInfo.uri) {
        viewModel.preparePdfFile(fileInfo.uri.toUri(), fileInfo.fileName)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 根据状态显示不同内容
        when (val state = uiState) {
            is PdfUiState.Loading -> {
                // 加载中
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "加载中...",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            is PdfUiState.Ready -> {
                // ✅ 使用 Compose PdfViewer（支持双页布局）
                pdfDocument?.let { doc ->
                    val pdfState = remember { PdfViewerState() }
                    
                    PdfViewer(
                        pdfDocument = doc,
                        state = pdfState,  // ✅ 必需的 state 参数
                        
                        // 📖 布局配置 - 设置为单页垂直滚动（如需双页改为 pagesPerRow = 2）
                        pagesPerRow = 1,
                        horizontalPageSpacing = 8.dp,
                        verticalPageSpacing = 8.dp,
                        
                        // 🔍 缩放配置
                        minZoom = 0.5f,
                        maxZoom = 5.0f,
                        
                        // ✏️ 交互功能
                        isFormFillingEnabled = false,
                        isImageSelectionEnabled = true,
                        
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            is PdfUiState.Error -> {
                // 显示错误
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
