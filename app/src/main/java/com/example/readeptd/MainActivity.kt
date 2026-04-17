package com.example.readeptd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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

/**
 * 主屏幕 - 遵循 MVVM 单向数据流
 * 
 * UI 层职责:
 * 1. 观察 ViewModel 的状态 (StateFlow)
 * 2. 根据状态渲染 UI
 * 3. 将用户交互转换为事件发送给 ViewModel
 */
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    // 收集 StateFlow 作为 Compose State
    val uiState by viewModel.uiState.collectAsState()
    
    // 根据状态渲染不同的 UI
    when (val state = uiState) {
        is MainUiState.Loading -> LoadingScreen(modifier)
        is MainUiState.Success -> ContentScreen(
            message = state.message,
            onRefreshClick = { viewModel.onEvent(MainUiEvent.Refresh) },
            onUpdateClick = { viewModel.onEvent(MainUiEvent.UpdateGreeting("Compose")) },
            modifier = modifier
        )
        is MainUiState.Error -> ErrorScreen(
            error = state.error,
            onRetryClick = { viewModel.onEvent(MainUiEvent.Refresh) },
            modifier = modifier
        )
    }
}

/**
 * 加载状态界面
 */
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

/**
 * 内容显示界面
 */
@Composable
fun ContentScreen(
    message: String,
    onRefreshClick: () -> Unit,
    onUpdateClick: () -> Unit,
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
            text = message,
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onRefreshClick) {
            Text(text = "刷新")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(onClick = onUpdateClick) {
            Text(text = "更新问候语")
        }
    }
}

/**
 * 错误状态界面
 */
@Composable
fun ErrorScreen(
    error: String,
    onRetryClick: () -> Unit,
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
        
        Button(onClick = onRetryClick) {
            Text(text = "重试")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    ReadEptdTheme {
        ContentScreen(
            message = "Hello Android!",
            onRefreshClick = {},
            onUpdateClick = {}
        )
    }
}