package com.example.readeptd.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.viewmodel.TtsViewModel

/**
 * TTS 测试界面
 * 演示如何使用 TTS 服务
 */
@Composable
fun TtsTestScreen(
    ttsViewModel: TtsViewModel = viewModel()
) {
    var text by remember { mutableStateOf("你好，这是一个文本转语音的测试。") }
    val isInitialized by ttsViewModel.isInitialized.collectAsState()
    val isSpeaking by ttsViewModel.isSpeaking.collectAsState()
    val speechRate by ttsViewModel.speechRate.collectAsState()
    val pitch by ttsViewModel.pitch.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            text = "TTS 文本转语音",
            style = MaterialTheme.typography.headlineMedium
        )

        // 状态显示
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("初始化状态: ${if (isInitialized) "✓ 已就绪" else "✗ 未就绪"}")
                Text("朗读状态: ${if (isSpeaking) "正在朗读" else "空闲"}")
                Text("语速: $speechRate")
                Text("音调: $pitch")
            }
        }

        // 文本输入
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("输入要朗读的文本") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        // 控制按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { ttsViewModel.speak(text) },
                enabled = isInitialized && text.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isSpeaking) "重新朗读" else "开始朗读")
            }

            Button(
                onClick = { ttsViewModel.stop() },
                enabled = isSpeaking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("停止")
            }
        }

        // 语速调节
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("语速调节", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = speechRate,
                    onValueChange = { ttsViewModel.setSpeechRate(it) },
                    valueRange = 0.5f..2.0f,
                    steps = 14
                )
                Text("当前语速: ${String.format("%.1f", speechRate)}x")
            }
        }

        // 音调调节
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("音调调节", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = pitch,
                    onValueChange = { ttsViewModel.setPitch(it) },
                    valueRange = 0.5f..1.5f,
                    steps = 9
                )
                Text("当前音调: ${String.format("%.1f", pitch)}x")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 提示信息
        if (!isInitialized) {
            Text(
                text = "TTS 引擎正在初始化...",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
