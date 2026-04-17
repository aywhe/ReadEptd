package com.example.readeptd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.example.readeptd.ui.FileInfo
import com.example.readeptd.ui.theme.ReadEptdTheme

class ContentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val fileInfo = intent.getBundleExtra("file_info")?.let { 
            FileInfo.fromBundle(it) 
        }
        
        setContent {
            ReadEptdTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Text(
                        text = "ContentActivity - ${fileInfo?.fileName ?: "无文件"}",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
