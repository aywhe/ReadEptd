package com.example.readeptd.bookmark

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readeptd.activity.ContentUiEvent
import com.example.readeptd.activity.ContentViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@Composable
fun BookmarkDialog(
    bookmarkData: BookmarkData,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    viewModel: BookmarkViewModel = viewModel()
){
    val scope = rememberCoroutineScope()
    val existBookmarks by viewModel.bookmarkRepository.findInPosition(bookmarkData).collectAsStateWithLifecycle(initialValue = emptyList())
    var text by remember { mutableStateOf(bookmarkData.note) }
    val maxLength = 50

    LaunchedEffect(existBookmarks) {
        if(existBookmarks.isNotEmpty()){
            text = existBookmarks.first().note
        }
    }

    AlertDialog(
        onDismissRequest = {
            // do nothing
        },
        title = {
            Text(text = if(existBookmarks.isEmpty()) "增加书签" else "修改书签")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { newValue ->
                        if(newValue.length <= maxLength) {
                            text = newValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 6,
                    label = { Text("备注") },
                    supportingText = {
                        Text(
                            text = "${text.length} / $maxLength",
                            color = if (text.length >= maxLength) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        },
        confirmButton = {
            if(existBookmarks.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        val newBookmark = existBookmarks.first().copyVal(note = text)
                        scope.launch {
                            viewModel.bookmarkRepository.removeBookmark(newBookmark.id)
                        }
                        onConfirm(text)
                    }
                ) {
                    Text("删除")
                }
            }
            Button(
                onClick = {
                    val newBookmark = if(existBookmarks.isNotEmpty()){
                        existBookmarks.first().copyVal(note = text)
                    }else {
                        bookmarkData.copyVal(note = text)
                    }
                    if(existBookmarks.isNotEmpty()){
                        scope.launch {
                            viewModel.bookmarkRepository.updateBookmark(newBookmark)
                        }
                    }else {
                        scope.launch {
                            viewModel.bookmarkRepository.addBookmark(newBookmark)
                        }
                    }
                    onConfirm(text)
                }
            ) {
                Text( if(existBookmarks.isEmpty()) "添加" else "修改")
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
fun BookmarkHint(
    modifier: Modifier = Modifier,
    contentViewModel: ContentViewModel,
    viewModel: BookmarkViewModel = viewModel(),
){
    val isBookmarked by contentViewModel.isBookmarked.collectAsStateWithLifecycle()
    var isShowBookmarkTip by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var delayJob by remember { mutableStateOf<Job?>(null) }
    val config by contentViewModel.configData.collectAsStateWithLifecycle()

    LaunchedEffect(isBookmarked) {
        delayJob?.cancel() // 取消之前的延迟任务
        if (isBookmarked) {
            delayJob = scope.launch {
                isShowBookmarkTip = true
                kotlinx.coroutines.delay(5000)
                isShowBookmarkTip = false
            }
        } else {
            isShowBookmarkTip = false
        }
    }
    AnimatedVisibility(
        visible = isShowBookmarkTip && config.isShowBookmarkHint,
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(), // 从左侧滑入并淡入
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()  // 向左侧滑出并淡出
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 72.dp)
                    .background(
                        MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                    )
                    .clickable(
                        onClick = {
                            contentViewModel.onEvent(ContentUiEvent.OnClickBookmark)
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "找到书签",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}