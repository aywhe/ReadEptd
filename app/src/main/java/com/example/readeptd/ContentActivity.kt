package com.example.readeptd

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.activity.ContentUiEvent
import com.example.readeptd.activity.ContentUiState
import com.example.readeptd.activity.ContentViewModel
import com.example.readeptd.data.FileInfo
import com.example.readeptd.books.epub.EpubScreen
import com.example.readeptd.ui.theme.ReadEptdTheme
import com.example.readeptd.utils.SystemUiUtils
import com.example.readeptd.books.pdf.PdfScreen
import com.example.readeptd.speech.TtsEvent
import com.example.readeptd.books.txt.TxtScreen
import com.example.readeptd.data.AppMemoryStore
import com.example.readeptd.speech.TtsViewModel
import com.example.readeptd.utils.TimerDialog
import com.example.readeptd.utils.Utils
import kotlin.math.roundToInt

class ContentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 保持屏幕常亮，防止阅读时自动息屏
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val fileInfo = intent.getBundleExtra("file_info")?.let {
            FileInfo.fromBundle(it)
        }

        setContent {
            val viewModel: ContentViewModel = viewModel()
            val config by viewModel.configData.collectAsStateWithLifecycle()

            // ✅ 根据夜间模式设置状态栏和导航栏颜色
            LaunchedEffect(config.isNightMode) {
                SystemUiUtils.updateSystemBarColors(window, config.isNightMode)
            }

            ReadEptdTheme(
                darkTheme = config.isNightMode,
                dynamicColor = config.isDynamicColor
            ) {
                ContentScreen(
                    fileInfo = fileInfo,
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清除屏幕常亮标志
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentScreen(
    fileInfo: FileInfo?,
    modifier: Modifier = Modifier,
    viewModel: ContentViewModel = viewModel(),
    ttsModel: TtsViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current

    // 在首次组合或 fileInfo 变化时加载文件信息
    // 使用 fileInfo?.uri 作为 key，确保不同文件能正确触发
    LaunchedEffect(fileInfo?.uri) {
        viewModel.onEvent(ContentUiEvent.Initialize(fileInfo))
    }
    // 监听屏幕旋转，恢复重新分页功能
    LaunchedEffect(configuration.orientation) {
        Log.d("ContentActivity", "屏幕方向变化: ${configuration.orientation}")
        viewModel.onEvent(ContentUiEvent.OnScreenOrientationChanged(configuration.orientation))
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isShowTimerDialog by remember { mutableStateOf(false) }
    var isShowToolTipInFullScreen by remember { mutableStateOf(true) }

    // ✅ 直接传入可能为 null 的 uri，AppMemoryStore 内部处理
    val isFullScreen by AppMemoryStore.fullScreenStateFlow(fileInfo?.uri).collectAsStateWithLifecycle()

    // 控制状态栏显示/隐藏
    LaunchedEffect(isFullScreen) {
        if(isFullScreen){
            // 进入全屏时默认显示工具提示，用户可以选择隐藏
            isShowToolTipInFullScreen = true
        }
        val window = (context as? ComponentActivity)?.window
        if (window != null) {
            if (isFullScreen) {
                // 隐藏状态栏（无动画）
                WindowInsetsControllerCompat(window, view).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.statusBars())
                }
                // 强制立即应用，禁用动画
                window.decorView.post {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
                    )
                }
            } else {
                // 显示状态栏（无动画）
                WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.statusBars())
                // 强制立即应用，禁用动画
                window.decorView.post {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if(!isFullScreen) {
                TopAppBar(
                    title = {
                        Text(
                            text = when (val state = uiState) {
                                is ContentUiState.Success -> state.fileInfo.fileName
                                else -> "阅读"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (context is ComponentActivity) {
                                context.finish()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        ToolTip(
                            viewModel = viewModel,
                            ttsModel = ttsModel,
                            onLongPressSpeak = {
                                isShowTimerDialog = true
                            }
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        if (isShowTimerDialog) {
            val remainingTimeMillis by ttsModel.remainingMillisTime.collectAsState()
            TimerDialog(
                currentRemainingMillis = remainingTimeMillis,
                onDismiss = {
                    isShowTimerDialog = false
                },
                onConfirm = { millis ->
                    ttsModel.onEvent(TtsEvent.StartCountDownTimer(millis))
                    isShowTimerDialog = false
                },
                onStopTimer = {
                    ttsModel.onEvent(TtsEvent.RemoveCountDownTimer)
                    isShowTimerDialog = false
                },
            )
        }
        when (val state = uiState) {
            is ContentUiState.Loading -> LoadingContentScreen(
                modifier = modifier.padding(innerPadding)
            )

            is ContentUiState.Success -> FileContentScreen(
                fileInfo = state.fileInfo,
                contentViewModel = viewModel,
                ttsModel = ttsModel,
                modifier = modifier.padding(innerPadding)
            )

            is ContentUiState.Error -> ErrorContentScreen(
                error = state.error,
                modifier = modifier.padding(innerPadding)
            )
        }

        if(isFullScreen && isShowToolTipInFullScreen) {
            DraggableFloatingToolTip(
                modifier = modifier.padding(innerPadding),
                onDismiss = { isShowToolTipInFullScreen = false },
                onLongPressSpeak =  { isShowTimerDialog = true },
                viewModel = viewModel,
                ttsModel = ttsModel
            )
        }
    }
}

@Composable
fun ToolTip(
    modifier: Modifier = Modifier,
    isDragTool: Boolean = false,
    onLongPressSpeak: () -> Unit =  {},
    viewModel: ContentViewModel,
    ttsModel: TtsViewModel
){
    val isSpeaking by ttsModel.isSpeaking.collectAsState()
    val ttsInitialized by ttsModel.isInitialized.collectAsState()
    val progressText by viewModel.progressText.collectAsStateWithLifecycle()
    if (progressText.isNotBlank()) {
        TextButton(
            onClick = {
                viewModel.onEvent(
                    ContentUiEvent.OnClickProgressInfo(
                        progressText
                    )
                )
            }
        ) {
            Text(
                text = progressText,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
    if (ttsInitialized) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            viewModel.onEvent(ContentUiEvent.OnClickSearchButton)
                        }
                    )
                }
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(start = 4.dp, end = 16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (isSpeaking) {
                                Log.d("ContentActivity", "停止朗读按钮被点击")
                                ttsModel.stop()
                            } else {
                                Log.d("ContentActivity", "请求开始自动朗读")
                                ttsModel.onEvent(TtsEvent.RequestAutoSpeak)
                            }
                        },
                        onLongPress = {
                            Log.d("ContentActivity", "长按按钮被点击")
                            onLongPressSpeak()
                        }
                    )
                }
        ) {
            Icon(
                imageVector = if (isSpeaking) Icons.Default.HeadsetOff else Icons.Default.Headset,
                contentDescription = if (isSpeaking) "停止朗读" else "开始朗读",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DraggableFloatingToolTip(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onLongPressSpeak: () -> Unit = {},
    viewModel: ContentViewModel,
    ttsModel: TtsViewModel
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val screenWidthPx = with(density) { screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { screenHeightDp.dp.toPx() }

    val iconSizeDp = 48.dp
    val cornerRadiusDp = 12.dp
    val collapsedIconSizeDp = 12.dp
    val iconSizePx = with(density) { iconSizeDp.toPx() }.roundToInt()
    val collapsedIconSizePx = with(density) { collapsedIconSizeDp.toPx() }.roundToInt()

    var offset by remember {
        mutableStateOf(
            IntOffset(
                (screenWidthPx - iconSizePx * 1.5f).roundToInt(),
                (screenHeightPx * 0.1f).roundToInt()
            )
        )
    }

    val buttonCenterX = offset.x + iconSizePx / 2
    val isButtonOnRightSide = buttonCenterX > screenWidthPx / 2

    var isCollapsed by remember { mutableStateOf(false) }
    var showTip by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val surfaceColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    val onSurfaceColor = MaterialTheme.colorScheme.onPrimaryContainer

    // 自动贴边逻辑
    var isFirstRun by remember { mutableStateOf(true) }
    LaunchedEffect(isCollapsed) {
        if (isFirstRun) {
            isFirstRun = false
            return@LaunchedEffect
        }
        val targetX = if(isButtonOnRightSide){
            if (isCollapsed) {
                screenWidthPx.toInt() - collapsedIconSizePx
            } else {
                 screenWidthPx.toInt() - iconSizePx
            }
        } else {
            0
        }
        offset = IntOffset(targetX, offset.y)
    }

    // 确认对话框
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("隐藏悬浮球") },
            text = { Text("是否隐藏悬浮球？") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onDismiss()
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                }) {
                    Text("取消")
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { offset }
                .padding(0.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            if (isCollapsed) {
                                isCollapsed = false
                            }
                            isDragging = true
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offset += IntOffset(
                                dragAmount.x.toInt(),
                                dragAmount.y.toInt()
                            )
                            // 限制垂直拖动范围
                            offset = IntOffset(
                                offset.x,
                                offset.y.coerceIn(0, (screenHeightPx - iconSizePx).toInt())
                            )
                        },
                        onDragEnd = {
                            isDragging = false
                            // 根据位置判断是否折叠
                            val snapThresholdPx = iconSizePx / 2
                            if (!isCollapsed) {
                                if (offset.x < snapThresholdPx || 
                                    offset.x + iconSizePx / 2 > screenWidthPx - snapThresholdPx) {
                                    isCollapsed = true
                                }
                            }
                        }
                    )
                }
        ) {
            // 工具提示内容
            if (showTip && !isCollapsed) {
                var cachedIsButtonOnRightSide by remember { mutableStateOf(isButtonOnRightSide) }

                LaunchedEffect(isDragging, isButtonOnRightSide) {
                    if (!isDragging) {
                        cachedIsButtonOnRightSide = isButtonOnRightSide
                    }
                }
                val toolTipModifier =
                    if (cachedIsButtonOnRightSide) {
                        Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                placeable.placeRelative(x = -placeable.width, y = 0)
                            }
                        }
                    } else {
                        Modifier.offset(x = iconSizeDp)
                    }.animateContentSize()
                
                val toolTipShape = 
                    RoundedCornerShape(
                        topStart = if (cachedIsButtonOnRightSide) cornerRadiusDp else 0.dp,
                        topEnd = if (cachedIsButtonOnRightSide) 0.dp else cornerRadiusDp,
                        bottomStart = if (cachedIsButtonOnRightSide) cornerRadiusDp else 0.dp,
                        bottomEnd = if (cachedIsButtonOnRightSide) 0.dp else cornerRadiusDp
                    )
                
                Surface(
                    shape = toolTipShape,
                    color = surfaceColor,
                    modifier = toolTipModifier
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolTip(
                            isDragTool = true,
                            onLongPressSpeak = onLongPressSpeak,
                            viewModel = viewModel,
                            ttsModel = ttsModel
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            showConfirmDialog = true
                                        }
                                    )
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 主按钮
            val buttonShape = when {
                isCollapsed -> RoundedCornerShape(
                    topStart = if (isButtonOnRightSide) cornerRadiusDp else 0.dp,
                    topEnd = if (isButtonOnRightSide) 0.dp else cornerRadiusDp,
                    bottomStart = if (isButtonOnRightSide) cornerRadiusDp else 0.dp,
                    bottomEnd = if (isButtonOnRightSide) 0.dp else cornerRadiusDp
                )
                showTip -> RoundedCornerShape(
                    topStart = if (isButtonOnRightSide) 0.dp else cornerRadiusDp,
                    topEnd = if (isButtonOnRightSide) cornerRadiusDp else 0.dp,
                    bottomStart = if (isButtonOnRightSide) 0.dp else cornerRadiusDp,
                    bottomEnd = if (isButtonOnRightSide) cornerRadiusDp else 0.dp
                )
                else -> CircleShape
            }

            val buttonWidth = if (isCollapsed) collapsedIconSizeDp else iconSizeDp

            Surface(
                shape = buttonShape,
                color = surfaceColor,
                modifier = Modifier.size(buttonWidth, iconSizeDp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            if (isCollapsed) {
                                isCollapsed = false
                            } else {
                                showTip = !showTip
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = if (showTip) "关闭工具栏" else "打开工具栏",
                        tint = onSurfaceColor,
                        modifier = Modifier.size(
                            if (isCollapsed) collapsedIconSizeDp else iconSizeDp * 2 / 3,
                            iconSizeDp * 2 / 3
                        )
                    )
                }
            }
        }
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
    contentViewModel: ContentViewModel,
    ttsModel: TtsViewModel,
    modifier: Modifier = Modifier
) {
    // 根据 mimeType 分发到不同的 Screen
    when (fileInfo.mimeType) {
        "application/epub+zip" -> {
            EpubScreen(
                fileInfo = fileInfo,
                ttsModel = ttsModel,
                contentViewModel = contentViewModel,
                modifier = modifier
            )
        }
        "text/plain" -> {
            TxtScreen(
                fileInfo = fileInfo,
                contentViewModel = contentViewModel,
                ttsModel = ttsModel,
                modifier = modifier
            )
        }
        "application/pdf" -> {
            PdfScreen(
                fileInfo = fileInfo,
                contentViewModel = contentViewModel,
                ttsModel = ttsModel,
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
