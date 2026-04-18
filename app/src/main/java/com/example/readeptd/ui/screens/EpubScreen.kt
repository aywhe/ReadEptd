package com.example.readeptd.ui.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.hamed.htepubreadr.component.EpubReaderComponent
import io.hamed.htepubreadr.entity.BookEntity
import io.hamed.htepubreadr.ui.view.EpubView
import io.hamed.htepubreadr.util.EpubUtil
import java.io.File

@Composable
fun EpubScreen(
    fileUri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var epubReader by remember { mutableStateOf<EpubReaderComponent?>(null) }
    var bookEntity by remember { mutableStateOf<BookEntity?>(null) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var isReady by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    DisposableEffect(fileUri) {
        try {
            val filePath = copyUriToTempFile(context, fileUri)
            
            if (filePath != null) {
                epubReader = EpubReaderComponent(filePath)
                val loadedBookEntity = epubReader?.make(context)
                bookEntity = loadedBookEntity
                
                if (loadedBookEntity != null && loadedBookEntity.pagePathList.isNotEmpty()) {
                    isReady = true
                    currentPageIndex = 0
                } else {
                    errorMessage = "无法解析 EPUB 文件或文件为空"
                }
            } else {
                errorMessage = "无法获取文件路径"
            }
        } catch (e: Exception) {
            Log.e("EpubScreen", "打开 EPUB 文件失败", e)
            errorMessage = "打开文件失败: ${e.message}"
        }
        
        onDispose {
            epubReader = null
            bookEntity = null
            isReady = false
            errorMessage = null
            currentPageIndex = 0
        }
    }
    
    when {
        errorMessage != null -> {
            ErrorView(errorMessage!!, modifier)
        }
        isReady && epubReader != null && bookEntity != null -> {
            ReaderView(
                epubReader = epubReader!!,
                bookEntity = bookEntity!!,
                currentPageIndex = currentPageIndex,
                onPageChange = { newIndex ->
                    currentPageIndex = newIndex
                },
                modifier = modifier
            )
        }
        else -> {
            LoadingView(modifier)
        }
    }
}

@Composable
private fun LoadingView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text(
            text = "正在加载 EPUB...",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun ErrorView(errorMessage: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "错误",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ReaderView(
    epubReader: EpubReaderComponent,
    bookEntity: BookEntity,
    currentPageIndex: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalPages = bookEntity.pagePathList.size
    
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                EpubView(ctx).apply {
                    setBaseUrl(epubReader.absolutePath)
                    loadChapter(currentPageIndex, epubReader)
                    setOnHrefClickListener { href ->
                        Log.d("EpubScreen", "点击链接: $href")
                    }
                }
            },
            update = { epubView ->
                epubView.loadChapter(currentPageIndex, epubReader)
            },
            modifier = Modifier.fillMaxSize()
        )
        
        NavigationBar(
            currentPage = currentPageIndex,
            totalPages = totalPages,
            bookTitle = bookEntity.name ?: "未知书籍",
            onPreviousClick = {
                if (currentPageIndex > 0) {
                    onPageChange(currentPageIndex - 1)
                }
            },
            onNextClick = {
                if (currentPageIndex < totalPages - 1) {
                    onPageChange(currentPageIndex + 1)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        )
    }
}

private fun EpubView.loadChapter(index: Int, epubReader: EpubReaderComponent) {
    try {
        val allPages = epubReader.make(context)?.pagePathList ?: return
        if (index in allPages.indices) {
            val content = EpubUtil.getHtmlContent(allPages[index])
            setUp(content)
        }
    } catch (e: Exception) {
        Log.e("EpubView", "加载章节失败: $index", e)
    }
}

@Composable
private fun NavigationBar(
    currentPage: Int,
    totalPages: Int,
    bookTitle: String,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(8.dp).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousClick, enabled = currentPage > 0) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "上一章"
            )
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "第 ${currentPage + 1} / $totalPages 章",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = bookTitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
        
        IconButton(onClick = onNextClick, enabled = currentPage < totalPages - 1) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "下一章"
            )
        }
    }
}

private fun copyUriToTempFile(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val cacheFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.epub")
            inputStream.copyTo(cacheFile.outputStream())
            cacheFile.absolutePath
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
