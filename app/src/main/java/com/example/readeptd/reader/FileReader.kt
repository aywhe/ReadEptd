package com.example.readeptd.reader

import android.content.Context
import android.net.Uri

/**
 * 文件阅读器接口 - 支持多种文件格式的扩展
 */
interface FileReader {
    /**
     * 判断是否支持该文件类型
     * @param mimeType 文件的 MIME 类型
     * @return 是否支持
     */
    fun supportsMimeType(mimeType: String): Boolean

    /**
     * 打开并显示文件
     * @param context Android 上下文
     * @param fileUri 文件 URI
     */
    fun openFile(context: Context, fileUri: Uri)

    /**
     * 关闭文件，释放资源
     */
    fun closeFile()
}