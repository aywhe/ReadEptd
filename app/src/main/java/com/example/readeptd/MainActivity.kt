package com.example.readeptd

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.ui.FileInfo
import com.example.readeptd.ui.MainUiEvent
import com.example.readeptd.ui.MainUiState
import com.example.readeptd.ui.theme.ReadEptdTheme
import com.example.readeptd.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReadEptdTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // 创建文件选择器 Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        uris?.let {
            val fileInfos = it.mapNotNull { uri ->
                try {
                    val cursor = context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE),
                        null,
                        null,
                        null
                    )
                    
                    val fileName = cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            it.getString(nameIndex)
                        } else {
                            uri.lastPathSegment ?: "Unknown"
                        }
                    } ?: uri.lastPathSegment ?: "Unknown"
                    
                    val fileSize = cursor?.use {
                        if (it.moveToFirst()) {
                            val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            it.getLong(sizeIndex)
                        } else {
                            0L
                        }
                    } ?: 0L
                    
                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    
                    FileInfo(
                        uri = uri,
                        fileName = fileName,
                        fileSize = fileSize,
                        mimeType = mimeType
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            
            if (fileInfos.isNotEmpty()) {
                viewModel.onEvent(MainUiEvent.OnFilesSelected(fileInfos))
            }
        }
    }
    
    when (val state = uiState) {
        is MainUiState.Loading -> LoadingScreen(modifier)
        is MainUiState.Success -> ContentScreen(
            selectedFiles = state.selectedFiles,
            onDragButtonClick = { filePickerLauncher.launch(getAllowedMimeTypes()) },
            onRemoveFile = { index -> viewModel.onEvent(MainUiEvent.RemoveFile(index)) },
            modifier = modifier
        )
        is MainUiState.Error -> ErrorScreen(
            error = state.error,
            modifier = modifier
        )
    }
}

/**
 * 获取允许的文件 MIME 类型
 */
private fun getAllowedMimeTypes(): Array<String> {
    return arrayOf(
        "text/plain",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/pdf",
        "application/epub+zip"
    )
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "加载中...")
    }
}

@Composable
fun ContentScreen(
    selectedFiles: List<FileInfo>,
    onDragButtonClick: () -> Unit,
    onRemoveFile: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (selectedFiles.isEmpty()) {
                Text(
                    text = "点击右下角按钮添加文件",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "已选择 ${selectedFiles.size} 个文件",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(selectedFiles.size) { index ->
                        FileItemCard(
                            fileInfo = selectedFiles[index],
                            onRemove = { onRemoveFile(index) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
        
        DraggableFloatingButton(
            onClick = onDragButtonClick
        )
    }
}

@Composable
fun ErrorScreen(
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
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(text = error)
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun DraggableFloatingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offset by remember { mutableStateOf(IntOffset.Zero) }
    
    Box(modifier = modifier.fillMaxSize()) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset { offset }
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offset += IntOffset(
                                dragAmount.x.toInt(),
                                dragAmount.y.toInt()
                            )
                        }
                    )
                }
                .size(56.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 文件列表项卡片
 */
@Composable
fun FileItemCard(
    fileInfo: FileInfo,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = fileInfo.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatFileSize(fileInfo.fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = fileInfo.mimeType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(
                onClick = onRemove
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
        else -> "${size / (1024 * 1024 * 1024)} GB"
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    ReadEptdTheme {
        ContentScreen(
            selectedFiles = emptyList(),
            onDragButtonClick = {},
            onRemoveFile = {}
        )
    }
}
