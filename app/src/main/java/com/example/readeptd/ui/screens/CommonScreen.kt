package com.example.readeptd.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    var pageNumber by remember { mutableStateOf(currentPage.toString()) }

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
                    },
                    label = { Text("页码") },
                    placeholder = { Text("请输入页码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = pageNumber.toIntOrNull()?.let { it < 1 || it > totalPages } ?: false
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
    var onValueChanged = false
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "移动到进度")
        },
        text = {
            Column {
                Text(
                    text = "当前进度 ${(progressValue*100).toInt()}%",
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