package com.example.readeptd.reader

import android.content.Context
import android.net.Uri
import com.folioreader.FolioReader

/**
 * EPUB 文件阅读器实现
 * 使用 FolioReader 库显示 EPUB 格式文件
 */
class EpubFileReader : FileReader {

    private var folioReader: FolioReader? = null

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType == "application/epub+zip" ||
               mimeType.endsWith(".epub", ignoreCase = true)
    }

    override fun openFile(context: Context, fileUri: Uri) {
        try {
            folioReader = FolioReader.get()

            val filePath = getFilePathFromUri(context, fileUri)

            if (filePath != null) {
                folioReader?.openBook(filePath)
            } else {
                throw Exception("无法获取文件路径")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("打开 EPUB 文件失败: ${e.message}")
        }
    }

    override fun closeFile() {
        folioReader?.closeBook()
        folioReader = null
    }

    /**
     * 从 URI 获取文件路径
     * 将 content URI 复制到缓存目录并返回文件路径
     */
    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
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
}