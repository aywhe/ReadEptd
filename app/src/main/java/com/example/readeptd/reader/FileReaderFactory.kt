package com.example.readeptd.reader

import android.content.Context
import android.net.Uri
/**
 * 文件阅读器工厂
 * 根据文件 MIME 类型创建对应的阅读器实例
 * 支持动态注册新的阅读器实现，便于扩展其他文件格式
 */
object FileReaderFactory {

    private val readers = mutableListOf<FileReader>(
        EpubFileReader()
        // 未来可以添加更多阅读器，例如：
        // PdfFileReader(),
        // TxtFileReader(),
        // DocxFileReader()
    )

    /**
     * 根据 MIME 类型获取对应的阅读器
     * @param mimeType 文件的 MIME 类型
     * @return 匹配的阅读器实例，如果没有找到则返回 null
     */
    fun getReader(mimeType: String): FileReader? {
        return readers.find { it.supportsMimeType(mimeType) }
    }

    /**
     * 注册新的文件阅读器
     * @param reader 要注册的阅读器实例
     */
    fun registerReader(reader: FileReader) {
        if (!readers.contains(reader)) {
            readers.add(reader)
        }
    }

    /**
     * 注销文件阅读器
     * @param reader 要注销的阅读器实例
     */
    fun unregisterReader(reader: FileReader) {
        readers.remove(reader)
    }

    /**
     * 获取所有已注册的阅读器
     */
    fun getAllReaders(): List<FileReader> {
        return readers.toList()
    }
}