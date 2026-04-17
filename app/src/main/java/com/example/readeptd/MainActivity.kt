package com.example.readeptd

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.ui.FileInfo
import com.example.readeptd.ui.MainUiEvent
import com.example.readeptd.ui.MainUiState
import com.example.readeptd.ui.theme.ReadEptdTheme
import com.example.readeptd.viewmodel.MainViewModel
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableItem

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
    
    Log.d("MainActivity", "MainScreen 重组, UI状态: ${uiState::class.simpleName}")
    if (uiState is MainUiState.Success) {
        val successState = uiState as MainUiState.Success
        Log.d("MainActivity", "当前选中文件数: ${successState.selectedFiles.size}")
    }
    
    // 创建文件选择器 Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        Log.d("MainActivity", "文件选择器回调触发")
        Log.d("MainActivity", "URIs: ${uris?.size ?: 0} 个")
        
        uris?.let {
            Log.d("MainActivity", "开始处理 ${it.size} 个 URI")
            val fileInfos = it.mapNotNull { uri ->
                try {
                    Log.d("MainActivity", "处理 URI: $uri")
                    
                    var fileName = uri.lastPathSegment ?: "Unknown"
                    var fileSize = 0L
                    
                    context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                fileName = cursor.getString(nameIndex)
                            }
                            
                            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (sizeIndex != -1) {
                                fileSize = cursor.getLong(sizeIndex)
                            }
                        }
                    }
                    
                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    
                    Log.d("MainActivity", "文件信息 - 名称: $fileName, 大小: $fileSize, 类型: $mimeType")
                    
                    FileInfo(
                        uri = uri,
                        fileName = fileName,
                        fileSize = fileSize,
                        mimeType = mimeType
                    )
                } catch (e: Exception) {
                    Log.e("MainActivity", "处理文件失败: ${e.message}", e)
                    e.printStackTrace()
                    null
                }
            }
            
            Log.d("MainActivity", "成功解析 ${fileInfos.size} 个文件")
            
            if (fileInfos.isNotEmpty()) {
                Log.d("MainActivity", "调用 ViewModel 事件: OnFilesSelected")
                viewModel.onEvent(MainUiEvent.OnFilesSelected(fileInfos))
            } else {
                Log.w("MainActivity", "没有成功解析任何文件")
            }
        } ?: run {
            Log.w("MainActivity", "用户取消了文件选择")
        }
    }
    
    when (val state = uiState) {
        is MainUiState.Loading -> LoadingScreen(modifier)
        is MainUiState.Success -> ContentScreen(
            files = state.selectedFiles,
            onDragButtonClick = { filePickerLauncher.launch(getAllowedMimeTypes()) },
            onRemoveFile = { index -> viewModel.onEvent(MainUiEvent.RemoveFile(index)) },
            onMoveFile = { from, to -> viewModel.onEvent(MainUiEvent.MoveFile(from, to)) },
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
    files: List<FileInfo>,
    onDragButtonClick: () -> Unit,
    onRemoveFile: (Int) -> Unit,
    onMoveFile: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (files.isNotEmpty()) files.size - 1 else 0, // 反向布局时显示最后一个项目
        initialFirstVisibleItemScrollOffset = 0
    )
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            onMoveFile(from.index, to.index)
        }
    )
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (files.isEmpty()) {
                Text(
                    text = "点击右下角按钮添加文件",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    reverseLayout = true,
                    state = lazyListState,
                    modifier = Modifier.weight(1f)
                ) {
                    items(
                        count = files.size,
                        key = { index -> files[index].uri.toString() }
                    ) { index ->
                        ReorderableItem(
                            state = reorderableState,
                            key = files[index].uri.toString()
                        ) { isDragging ->
                            val animatedScale by animateFloatAsState(
                                targetValue = if (isDragging) 1.05f else 1f,
                                label = "scale"
                            )
                            FileItemCard(
                                fileInfo = files[index],
                                onRemove = { onRemoveFile(index) },
                                isDragging = isDragging,
                                modifier = Modifier
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .scale(animatedScale)
                                    .draggableHandle(
                                        dragGestureDetector = DragGestureDetector.LongPress,
                                        onDragStopped = {

                                        }
                                    )
                            )
                        }
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
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
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
            files = emptyList(),
            onDragButtonClick = {},
            onRemoveFile = {},
            onMoveFile = { _, _ -> }
        )
    }
}
