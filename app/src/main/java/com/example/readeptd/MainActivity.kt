package com.example.readeptd

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.data.FileInfo
import com.example.readeptd.data.FileInfo.Companion.toBundle
import com.example.readeptd.ui.MainUiEvent
import com.example.readeptd.ui.MainUiState
import com.example.readeptd.ui.theme.ReadEptdTheme
import com.example.readeptd.viewmodel.MainViewModel
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalMaterial3Api::class)
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
        Log.d("MainActivity", "当前选中文件数: ${successState.readingFiles.size}")
    }

    // 创建文件选择器 Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        Log.d("MainActivity", "文件选择器回调触发")
        Log.d("MainActivity", "URIs: ${uris?.size ?: 0} 个")

        uris?.let {
            Log.d("MainActivity", "开始处理 ${it.size} 个 URI")
            
            // 获取持久化 URI 权限（仅读取）
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            it.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    Log.d("MainActivity", "已获取持久化读取权限: $uri")
                } catch (e: Exception) {
                    Log.e("MainActivity", "获取持久化权限失败: $uri", e)
                }
            }
            
            val fileInfos = it.mapNotNull { uri ->
                try {
                    Log.d("MainActivity", "处理 URI: $uri")

                    var fileName = uri.lastPathSegment ?: "Unknown"
                    var fileSize = 0L

                    context.contentResolver.query(
                        uri,
                        arrayOf(
                            android.provider.OpenableColumns.DISPLAY_NAME,
                            android.provider.OpenableColumns.SIZE
                        ),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex =
                                cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                fileName = cursor.getString(nameIndex)
                            }

                            val sizeIndex =
                                cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (sizeIndex != -1) {
                                fileSize = cursor.getLong(sizeIndex)
                            }
                        }
                    }

                    val mimeType = context.contentResolver.getType(uri) ?: ""

                    Log.d(
                        "MainActivity",
                        "文件信息 - 名称: $fileName, 大小: $fileSize, 类型: $mimeType"
                    )

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

    // 获取文件数量用于显示在标题栏
    val fileCount = if (uiState is MainUiState.Success) {
        (uiState as MainUiState.Success).readingFiles.size
    } else {
        0
    }

    var showMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "ReadEptd ($fileCount)")
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多选项"
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("关于") },
                                onClick = {
                                    showMenu = false
                                    showAboutDialog = true
                                }
                            )
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is MainUiState.Loading -> LoadingScreen(
                modifier = Modifier.padding(innerPadding)
            )

            is MainUiState.Success -> ContentScreen(
                files = state.readingFiles,
                onDragButtonClick = { filePickerLauncher.launch(getAllowedMimeTypes()) },
                onRemoveFile = { index ->
                    val fileToRemove = state.readingFiles[index]
                    try {
                        context.contentResolver.releasePersistableUriPermission(
                            fileToRemove.uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        Log.d("MainActivity", "已释放 URI 读取权限: ${fileToRemove.uri}")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "释放 URI 权限失败", e)
                    }
                    viewModel.onEvent(MainUiEvent.RemoveFile(index))
                },
                onMoveFile = { from, to -> viewModel.onEvent(MainUiEvent.MoveFile(from, to)) },
                modifier = Modifier.padding(innerPadding)
            )

            is MainUiState.Error -> ErrorScreen(
                error = state.error,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于 ReadEptd") },
            text = {
                val versionName = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) {
                    "未知版本"
                }

                Text(
                    text = """
                        ReadEptd - 智能听书助手 v$versionName
                        
                        主要功能：
                        • 支持 TXT、DOCX、PDF、EPUB 格式
                        • 文字转语音朗读
                        • 定时关闭功能
                        • 自动保存阅读进度
                        • 关键词搜索功能
                        
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false }
                ) {
                    Text("确定")
                }
            }
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
    var isMovingFile by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (files.isNotEmpty()) files.size - 1 else 0, // 反向布局时显示最后一个项目
        initialFirstVisibleItemScrollOffset = 0
    )
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            isMovingFile = true
            onMoveFile(from.index, to.index)
        }
    )
    val scope = rememberCoroutineScope()
    LaunchedEffect(files.lastOrNull()?.uri.toString()) {
        if (files.isNotEmpty()
            && !isMovingFile
        ) {
            scope.launch {
                Log.d(
                    "MainActivity",
                    "last item changed, scrolling to last item: ${files.last().uri}"
                )
                lazyListState.animateScrollToItem(files.size - 1)
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (files.isEmpty()) {
                Text(
                    text = "点击右下角按钮添加文件",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .animateContentSize(),
                    reverseLayout = true,
                    state = lazyListState,
                    verticalArrangement = Arrangement.Top,
                    userScrollEnabled = true
                ) {
                    items(
                        count = files.size,
                        key = { index -> files[index].uri.toString() }
                    ) { index ->
                        ReorderableItem(
                            state = reorderableState,
                            key = files[index].uri.toString(),
                            modifier = Modifier.fillMaxWidth()
                        ) { isDragging ->
                            val animatedScale by animateFloatAsState(
                                targetValue = if (isDragging) 1.02f else 1f,
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
                                        onDragStopped = { isMovingFile = false }
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
 * 检查 URI 指向的资源是否仍然存在且可访问
 */
fun Context.uriExists(uri: Uri): Boolean {
    return try {
        // 尝试查询元数据，如果能查到至少一列，说明文件存在
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            cursor.count > 0
        } ?: false
    } catch (e: Exception) {
        false
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
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteButton by remember { mutableStateOf(false) }
    var isFileAccessible by remember { mutableStateOf<Boolean?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(fileInfo.uri) {
        scope.launch {
            isFileAccessible = context.uriExists(fileInfo.uri)
        }
    }

    LaunchedEffect(isDragging) {
        if (isDragging) {
            showDeleteButton = true
        } else {
            kotlinx.coroutines.delay(5000)
            showDeleteButton = false
        }
    }
    
    Card(
        onClick = {
            if (isFileAccessible == true && !isDragging) {
                val intent = Intent(context, ContentActivity::class.java).apply {
                    putExtra("file_info", fileInfo.toBundle())
                }
                context.startActivity(intent)
            }
        },
        enabled = isFileAccessible == true,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isFileAccessible == false -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                isDragging -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(4.dp)
                ) {
                    Text(
                        text = fileInfo.fileName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 显示文件大小和 MIME 类型
                        Text(
                            text = formatFileSize(fileInfo.fileSize),
                            style = MaterialTheme.typography.bodySmall,
                            //color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = fileInfo.mimeType,
                            style = MaterialTheme.typography.bodySmall,
                            //color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // 显示阅读进度
                        fileInfo.progress?.let { progress ->
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                //color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isFileAccessible == false) {
                            Text(
                                text = "文件不存在",
                                style = MaterialTheme.typography.bodySmall,
                                //color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = showDeleteButton,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    // 根据 showDeleteButton 状态控制删除按钮显示
                    IconButton(
                        onClick = { showConfirmDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除"
                        )
                    }
                }
            }
            
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "确认删除",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = "确定要删除 \"${fileInfo.fileName}\" 吗？",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemove()
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
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
