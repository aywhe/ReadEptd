package com.example.readeptd

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.example.readeptd.data.ConfigureData
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.activity.MainUiEvent
import com.example.readeptd.activity.MainUiState
import com.example.readeptd.activity.MainViewModel
import com.example.readeptd.data.AppMemoryStore
import com.example.readeptd.data.FileInfo
import com.example.readeptd.ui.theme.ReadEptdTheme
import com.example.readeptd.utils.FileUtils
import com.example.readeptd.utils.SystemUiUtils
import com.example.readeptd.utils.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableItem

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val config by viewModel.configData.collectAsStateWithLifecycle()
            
            // ✅ 根据夜间模式设置状态栏和导航栏颜色
            LaunchedEffect(config.isNightMode) {
                SystemUiUtils.updateSystemBarColors(window, config.isNightMode)
            }
            
            ReadEptdTheme(
                darkTheme = config.isNightMode,
                dynamicColor = config.isDynamicColor
            ) {
                MainScreen(viewModel = viewModel)
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
                    FileUtils.takePersistableUriPermission(context, uri.toString())
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
                            OpenableColumns.DISPLAY_NAME,
                            OpenableColumns.SIZE
                        ),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex =
                                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                fileName = cursor.getString(nameIndex)
                            }

                            val sizeIndex =
                                cursor.getColumnIndex(OpenableColumns.SIZE)
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
                        uri = uri.toString(),
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
    var showSettingDialog by remember { mutableStateOf(false) }
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
                                text = { Text("设置") },
                                onClick = {
                                    showMenu = false
                                    showSettingDialog = true
                                }
                            )
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

            is MainUiState.Success -> {
                ContentScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )

                DraggableFloatingButton(
                    onClick = { filePickerLauncher.launch(getAllowedMimeTypes())  }
                )
            }

            is MainUiState.Error -> ErrorScreen(
                error = state.error,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    if (showSettingDialog){
        SettingsDialog(
            onDismiss = { showSettingDialog = false },
            viewModel = viewModel
        )
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
                        • 支持 TXT、PDF、EPUB 格式
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
        //"application/msword",
        //"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
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
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val readingStates by viewModel.readingStates.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    val files = if (uiState is MainUiState.Success) {
        (uiState as MainUiState.Success).readingFiles
    } else {
        emptyList()
    }
    
    // ✅ 按需加载阅读状态：当有文件时才启动监听
    LaunchedEffect(files.isNotEmpty()) {
        if (files.isNotEmpty()) {
            viewModel.loadReadingStates()
        }
    }
    
    var isMovingFile by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (files.isNotEmpty()) files.size - 1 else 0, // 反向布局时显示最后一个项目
        initialFirstVisibleItemScrollOffset = 0
    )
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            isMovingFile = true
            viewModel.onEvent(MainUiEvent.MoveFile(from.index, to.index))
        }
    )
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(files.lastOrNull()?.uri) {
        if (files.isNotEmpty() && !isMovingFile) {
            scope.launch {
                Log.d(
                    "MainActivity",
                    "last item changed, scrolling to last item: ${files.last().uri}"
                )
                lazyListState.animateScrollToItem(files.size - 1)
            }
        }
    }

    // ✅ 直接从 AppMemoryStore 读取（会话级别）
    val lastReadingFile by AppMemoryStore.lastReadingFile.collectAsStateWithLifecycle()
    
    fun goToContentActivity(fileInfo: FileInfo?) {
        if (fileInfo == null) {
            return
        }
        val intent = Intent(context, ContentActivity::class.java)
        intent.putExtra("file_info", fileInfo.toBundle())
        viewModel.onEvent(MainUiEvent.GoToContentActivity(fileInfo))
        Log.d("MainActivity", "go to content activity: ${fileInfo.fileName}")
        context.startActivity(intent)
    }

    Box(modifier =
        modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(lastReadingFile) {
                    // 只有存在上次阅读文件时才启用手势
                    if (lastReadingFile != null) {
                        var totalDragAmount = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                totalDragAmount = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                totalDragAmount += dragAmount
                                change.consume()
                            },
                            onDragEnd = {
                                // 在手势结束时判断总滑动距离是否达到阈值
                                if (totalDragAmount < -50f) {
                                    goToContentActivity(lastReadingFile)
                                }
                            },
                            onDragCancel = {
                                totalDragAmount = 0f
                            }
                        )
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
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
                        key = { index -> files[index].uri }
                    ) { index ->
                        ReorderableItem(
                            state = reorderableState,
                            key = files[index].uri,
                            modifier = Modifier.fillMaxWidth()
                        ) { isDragging ->
                            val animatedScale by animateFloatAsState(
                                targetValue = if (isDragging) 1.02f else 1f,
                                label = "scale"
                            )
                            
                            // 从收集的状态中获取进度
                            val progress = readingStates[files[index].uri]?.progress
                            
                            FileItemCard(
                                fileInfo = files[index],
                                onClick = {fileInfo ->
                                    goToContentActivity(fileInfo)
                                },
                                onRemove = { 
                                    FileUtils.releasePersistableUriPermission(context, files[index].uri)
                                    viewModel.onEvent(MainUiEvent.RemoveFile(index))
                                },
                                isDragging = isDragging,
                                progress = progress,
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
            contentColor = MaterialTheme.colorScheme.onPrimary,
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
    onClick: (FileInfo) -> Unit,
    onRemove: () -> Unit,
    isDragging: Boolean = false,
    progress: Float? = null,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteButton by remember { mutableStateOf(false) }
    var isFileAccessible by remember { mutableStateOf<Boolean?>(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(fileInfo.uri) {
        scope.launch {
            isFileAccessible = FileUtils.uriExists(context, fileInfo.uri)
        }
    }

    LaunchedEffect(isDragging) {
        if (isDragging) {
            showDeleteButton = true
        } else {
            delay(5000)
            showDeleteButton = false
        }
    }
    
    Card(
        onClick = {
            if (isFileAccessible == true && !isDragging) {
                onClick(fileInfo)
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
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
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
                            text = Utils.formatFileSize(fileInfo.fileSize),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = fileInfo.mimeType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // 显示阅读进度
                        progress?.let { progress ->
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    ReadEptdTheme {
        ContentScreen(
            viewModel = viewModel()
        )
    }
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val config by viewModel.configData.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("设置") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 夜间模式开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("夜间模式")
                    Switch(
                        checked = config.isNightMode,
                        onCheckedChange = { 
                            viewModel.updateConfig { copy(isNightMode = it) }
                        }
                    )
                }

                // 动态颜色开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("跟随系统主题色")
                    Switch(
                        checked = config.isDynamicColor,
                        onCheckedChange = { 
                            viewModel.updateConfig { copy(isDynamicColor = it) }
                        }
                    )
                }

                // 滑动布局开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("左右分页")
                    Switch(
                        checked = config.isSwipeLayout,
                        onCheckedChange = { 
                            viewModel.updateConfig { copy(isSwipeLayout = it) }
                        }
                    )
                }

                HorizontalDivider()

                // TTS 设置按钮
                Button(
                    onClick = {
                        try {
                            val intent = Intent("com.android.settings.TTS_SETTINGS")
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                            Log.d("MainActivity", "已打开 TTS 设置页面")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "无法打开 TTS 设置：${e.message}", e)
                        } 
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("设置 TTS")
                }

                Text(
                    text = "点击“设置 TTS”可跳转到系统文字转语音设置页面，选择您喜欢的 TTS 引擎（如讯飞、百度等）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onDismiss() },
            ) {
                Text("关闭")
            }
        }
    )

}
