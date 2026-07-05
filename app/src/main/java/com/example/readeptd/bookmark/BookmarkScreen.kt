package com.example.readeptd.bookmark

import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun BookmarkDialog(
    bookmarkData: BookmarkData,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
){
    AlertDialog(
        onDismissRequest = {
            // do nothing
        },
        title = {
            Text(text = "增加书签")
        },
        text = {
            Column {
                var text by remember { mutableStateOf(bookmarkData.note) }
                val maxLength = 200

                TextField(
                    value = text,
                    onValueChange = { newValue ->
                        if (newValue.length <= maxLength) {
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
                            color = if (text.length > maxLength) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm("")
                }
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}