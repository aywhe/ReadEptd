package com.example.readeptd

import android.app.Application
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.contract.ContentUiEvent
import com.example.readeptd.contract.ContentUiState
import com.example.readeptd.data.FileInfo
import com.example.readeptd.books.epub.EpubScreen
import com.example.readeptd.ui.theme.ReadEptdTheme
import com.example.readeptd.utils.Utils
import com.example.readeptd.viewmodel.ContentViewModel
import com.example.readeptd.books.pdf.PdfScreen
import com.example.readeptd.speech.TtsEvent
import com.example.readeptd.books.txt.TxtScreen
import com.example.readeptd.speech.TtsViewModel
import com.example.readeptd.utils.TimerDialog

class ContentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val fileInfo = intent.getBundleExtra("file_info")?.let {
            FileInfo.fromBundle(it)
        }

        setContent {
            ReadEptdTheme {
                ContentScreen(
                    fileInfo = fileInfo
                )
            }
        }
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
        viewModel.setFullScreen(false)
    }

    val uiState by viewModel.uiState.collectAsState()
    val isSpeaking by ttsModel.isSpeaking.collectAsState()
    val ttsInitialized by ttsModel.isInitialized.collectAsState()
    val progressText by viewModel.progressText.collectAsState()
    val isFullScreen by viewModel.isFullScreen.collectAsState()

    // 控制状态栏显示/隐藏
    LaunchedEffect(isFullScreen) {
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


    Log.d("ContentActivity", "ContentScreen 重组, UI状态: ${uiState::class.simpleName}")

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
                            var isShowTimerDialog by remember { mutableStateOf(false) }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                if (isSpeaking) {
                                                    Log.d("ContentActivity", "停止朗读按钮被点击")
                                                    ttsModel.onEvent(TtsEvent.StopSpeaking)
                                                } else {
                                                    Log.d("ContentActivity", "请求开始自动朗读")
                                                    ttsModel.onEvent(TtsEvent.RequestAutoSpeak)
                                                }
                                            },
                                            onLongPress = {
                                                Log.d("ContentActivity", "长按按钮被点击")
                                                isShowTimerDialog = true
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
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
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
