package com.example.readeptd.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 跳转到指定页码的对话框
 * @param currentPage 当前页码, 从 0 开始
 * @param totalPages 总页数
 * @param onDismiss 取消回调
 * @param onConfirm 确定回调, 输入页码
 */
@Composable
fun JumpToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var pageNumber by remember { mutableStateOf((currentPage+1).toString()) }
    var sliderPosition by remember { mutableFloatStateOf(1.0f*currentPage/totalPages*100f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "跳转到页面")
        },
        text = {
            Column {
                Text(
                    text = "当前在第 ${currentPage + 1} 页，共 $totalPages 页",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = pageNumber,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            pageNumber = input
                        }
                        sliderPosition = input.toIntOrNull()?.let { it.toFloat()/totalPages*100f } ?: 0f
                    },
                    label = { Text("页码") },
                    placeholder = { Text("请输入页码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = pageNumber.toIntOrNull()?.let { it < 1 || it > totalPages } ?: false
                )
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        sliderPosition = it
                        pageNumber = (sliderPosition/100f*totalPages+1).toInt().coerceIn(1, totalPages).toString()
                    },
                    valueRange = 0f..100f,
                    steps = 99,  // 100 个值需要 99 个间隔
                    modifier = Modifier.fillMaxWidth()
                )
                if (pageNumber.toIntOrNull()?.let { it < 1 || it > totalPages } == true) {
                    Text(
                        text = "请输入 1 到 $totalPages 之间的数字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    pageNumber.toIntOrNull()?.let { page ->
                        if (page in 1..totalPages) {
                            onConfirm(page - 1)
                        }
                    }
                },
                enabled = pageNumber.toIntOrNull()?.let { it in 1..totalPages } == true
            ) {
                Text("跳转")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 跳转到指定进度的对话框
 * @param progress 进度 0-1
 * @param onDismiss 取消回调
 * @param onConfirm 确定回调, 参数为进度 0-1
 */
@Composable
fun JumpToProgressDialog(
    progress: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var progressValue by remember { mutableFloatStateOf(progress) }
    var sliderPosition by remember { mutableFloatStateOf(progress*100) }
    var onValueChanged by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "移动到进度")
        },
        text = {
            Column {
                Text(
                    text = "当前进度 ${(sliderPosition).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        sliderPosition = it
                        onValueChanged = true
                    },
                    valueRange = 0f..100f,
                    steps = 99,  // 100 个值需要 99 个间隔
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(sliderPosition/100f)
                },
                enabled = onValueChanged
            ) {
                Text("跳转")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun TimerDialog(
    currentRemainingMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    onStopTimer: () -> Unit,
    maxMinutes: Int = 120
) {
    val initialMinutes = (currentRemainingMillis / 1000f / 60f).coerceIn(0f, maxMinutes.toFloat())
    var sliderPosition by remember {
        mutableFloatStateOf(initialMinutes)
    }
    
    LaunchedEffect(currentRemainingMillis) {
        val newMinutes = (currentRemainingMillis / 1000f / 60f).coerceIn(0f, maxMinutes.toFloat())
        sliderPosition = newMinutes
    }

    val minMinutes = 0


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("定时关闭朗读") },
        text = {
            Column {
                Text(
                    text = if ((sliderPosition).toInt() > 0) {
                        "剩余时间：${(sliderPosition).toInt()}分钟"
                    } else {
                        "设置时间：0 分钟"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(24.dp))

                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        sliderPosition = it
                    },
                    valueRange = minMinutes.toFloat()..maxMinutes.toFloat(),
                    steps = (maxMinutes - minMinutes),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${minMinutes}分钟",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${maxMinutes}分钟",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val selectedMillis = (sliderPosition * 60 * 1000).toLong()
                    onConfirm(selectedMillis.coerceIn(0L, maxMinutes.toLong() * 60 * 1000))
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }

                // 只有当有正在运行的定时器时才显示停止按钮
                if (currentRemainingMillis > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onStopTimer,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    )
}