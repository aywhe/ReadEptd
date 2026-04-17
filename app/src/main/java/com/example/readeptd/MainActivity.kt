package com.example.readeptd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
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

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    when (val state = uiState) {
        is MainUiState.Loading -> LoadingScreen(modifier)
        is MainUiState.Success -> ContentScreen(
            message = state.message,
            onRefreshClick = { viewModel.onEvent(MainUiEvent.Refresh) },
            onUpdateClick = { viewModel.onEvent(MainUiEvent.UpdateGreeting("Compose")) },
            onButtonClick = { viewModel.onEvent(MainUiEvent.OnButtonClick) },
            modifier = modifier
        )
        is MainUiState.Error -> ErrorScreen(
            error = state.error,
            onRetryClick = { viewModel.onEvent(MainUiEvent.Refresh) },
            modifier = modifier
        )
    }
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
    message: String,
    onRefreshClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
        
        DraggableFloatingButton(
            onClick = onButtonClick
        )
    }
}

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

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    ReadEptdTheme {
        ContentScreen(
            message = "Hello Android!",
            onRefreshClick = {},
            onUpdateClick = {},
            onButtonClick = {}
        )
    }
}
