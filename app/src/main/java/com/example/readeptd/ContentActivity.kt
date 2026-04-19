package com.example.readeptd

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.ui.ContentUiEvent
import com.example.readeptd.ui.ContentUiState
import com.example.readeptd.data.FileInfo
import com.example.readeptd.ui.screens.EpubScreen
import com.example.readeptd.ui.theme.ReadEptdTheme
import com.example.readeptd.utils.Utils
import com.example.readeptd.viewmodel.ContentViewModel
import androidx.core.net.toUri

class ContentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val fileInfo = intent.getBundleExtra("file_info")?.let { 
            FileInfo.fromBundle(it) 
        }
        
        setContent {
            ReadEptdTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ContentScreen(
                        fileInfo = fileInfo,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun ContentScreen(
    fileInfo: FileInfo?,
    modifier: Modifier = Modifier,
    viewModel: ContentViewModel = viewModel()
) {
    // 在首次组合或 fileInfo 变化时加载文件信息
    // 使用 fileInfo?.uri 作为 key，确保不同文件能正确触发
    LaunchedEffect(fileInfo?.uri) {
        viewModel.onEvent(ContentUiEvent.Initialize(fileInfo))
    }

    val uiState by viewModel.uiState.collectAsState()

    Log.d("ContentActivity", "ContentScreen 重组, UI状态: ${uiState::class.simpleName}")

    when (val state = uiState) {
        is ContentUiState.Loading -> LoadingContentScreen(modifier = modifier)
        is ContentUiState.Success -> FileContentScreen(
            fileInfo = state.fileInfo,
            modifier = modifier
        )
        is ContentUiState.Error -> ErrorContentScreen(
            error = state.error,
            modifier = modifier
        )
    }
}

@Composable
fun LoadingContentScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text(
            text = "加载中...",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
fun FileContentScreen(
    fileInfo: FileInfo,
    modifier: Modifier = Modifier
) {
    // 根据 mimeType 分发到不同的 Screen
    when (fileInfo.mimeType) {
        "application/epub+zip" -> {
            EpubScreen(
                fileInfo = fileInfo,
                modifier = modifier
            )
        }
        // 未来可以添加更多格式支持
        // "application/pdf" -> PdfScreen(fileInfo.uri, modifier)
        // "text/plain" -> TextScreen(fileInfo.uri, modifier)
        else -> {
            // 不支持的格式，显示文件信息
            UnsupportedFormatScreen(
                fileInfo = fileInfo,
                modifier = modifier
            )
        }
    }
}

@Composable
fun ErrorContentScreen(
    error: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "发生错误",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun UnsupportedFormatScreen(
    fileInfo: FileInfo,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "文件名: ${fileInfo.fileName}",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "文件大小: ${Utils.formatFileSize(fileInfo.fileSize)}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = "文件类型: ${fileInfo.mimeType}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp)
        )

        Text(
            text = "不支持的文件格式",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ContentScreenPreview() {
    ReadEptdTheme {
        FileContentScreen(
            fileInfo = FileInfo(
                uri = "content://test",
                fileName = "测试文件.txt",
                fileSize = 1024000,
                mimeType = "text/plain"
            )
        )
    }
}
