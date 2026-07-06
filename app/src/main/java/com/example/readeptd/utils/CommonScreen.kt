package com.example.readeptd.utils

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

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
    var pageNumber by remember { mutableStateOf((currentPage + 1).toString()) }
    var sliderPosition by remember { mutableFloatStateOf(1.0f * currentPage / totalPages * 100f) }

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
                        sliderPosition =
                            input.toIntOrNull()?.let { it.toFloat() / totalPages * 100f } ?: 0f
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
                        pageNumber =
                            (sliderPosition / 100f * totalPages + 1).toInt().coerceIn(1, totalPages)
                                .toString()
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
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
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
    var sliderPosition by remember { mutableFloatStateOf(progress * 100) }
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
                    onConfirm(sliderPosition / 100f)
                },
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
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
    var inputMinutes by remember {
        mutableStateOf(initialMinutes.toInt().toString())
    }

    val minMinutes = 0

    LaunchedEffect(sliderPosition) {
        val minutes = sliderPosition.toInt()
        if (inputMinutes != minutes.toString()) {
            inputMinutes = minutes.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("定时关闭朗读") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val remainingSeconds = (currentRemainingMillis / 1000).toInt()
                    val minutes = remainingSeconds / 60
                    val seconds = remainingSeconds % 60
                        
                    if (currentRemainingMillis > 0) {
                        Text(
                            text = String.format(Locale.getDefault(), "剩余时间 %02d:%02d", minutes, seconds),
                            style = MaterialTheme.typography.bodyLarge
                        )

                        TextButton(
                            onClick = onStopTimer,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(0.dp) // 清除默认内边距
                        ) {
                            Text("停止")
                        }
                    } else {
                        Text(
                            text = "未设置定时",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = inputMinutes,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.all { it.isDigit() }) {
                            inputMinutes = input
                            val minutes = input.toIntOrNull()
                            if (minutes != null && minutes in minMinutes..maxMinutes) {
                                sliderPosition = minutes.toFloat()
                            }
                        }
                    },
                    label = { Text("分钟") },
                    placeholder = { Text("请输入分钟数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = inputMinutes.toIntOrNull()?.let { it !in minMinutes..maxMinutes }
                        ?: false
                )

                if (inputMinutes.toIntOrNull()?.let { it !in minMinutes..maxMinutes } == true) {
                    Text(
                        text = "请输入 $minMinutes 到 $maxMinutes 之间的数字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        sliderPosition = it
                        inputMinutes = it.toInt().toString()
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
                    onConfirm(
                        selectedMillis.coerceIn(
                            minMinutes.toLong() * 60 * 1000,
                            maxMinutes.toLong() * 60 * 1000
                        )
                    )
                },
                enabled = inputMinutes.toIntOrNull()?.let { it in minMinutes..maxMinutes } == true
            ) {
                Text("确定")
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
fun LayoutSettingDialog(
    isSwipeLayout: Boolean,
    onSwipeLayoutChange: (Boolean) -> Unit = {},
    isRtl: Boolean? = null,
    onRtlChange: (Boolean) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    var isSwipeLayoutState by remember { mutableStateOf(isSwipeLayout) }
    var isRtlState by remember { mutableStateOf(isRtl) }
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("布局设置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 滑动布局开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("分页")
                    Switch(
                        checked = isSwipeLayoutState,
                        onCheckedChange = { newValue ->
                            isSwipeLayoutState = newValue
                            onSwipeLayoutChange(newValue)
                        }
                    )
                }
                if (isRtlState != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("文言文")
                        Switch(
                            checked = isRtlState == true,
                            onCheckedChange = { newValue ->
                                isRtlState = newValue
                                onRtlChange(newValue)
                            }
                        )
                    }
                }
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

@Composable
fun SlideHint(
    tips: String,
    visible: Boolean,
    alignment: Alignment = Alignment.TopStart,
    padding: PaddingValues = PaddingValues(top = 32.dp),
    onClick: ()->Unit = {}
){
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(), // 从左侧滑入并淡入
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()  // 向左侧滑出并淡出
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(alignment)
                    .padding(padding)
                    .background(
                        MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                    )
                    .clickable(
                        onClick = {
                            onClick()
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    text = tips,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}