package com.example.readeptd.bookmark

import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

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
                TextField(
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
                            color = if (text.length >= maxLength) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
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