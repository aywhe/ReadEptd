package com.example.readeptd.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import org.mozilla.universalchardet.UniversalDetector
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.io.File

/**
 * TXT 文件文本提取器
 */
class TxtExtractor(private val context: Context) {
    
    fun extractTextRaw(uri: Uri): Flow<String> = flow {
        try {
            val inputStream = openInputStream(uri) ?: throw IllegalStateException("无法打开文件输入流")
            
            inputStream.use { stream ->
                val detectedCharset = detectFileEncoding(stream)
                
                val combinedStream = SequenceInputStream(
                    ByteArrayInputStream(detectedData),
                    stream
                )
                
                BufferedReader(InputStreamReader(combinedStream, detectedCharset)).use { reader ->
                    var line: String?
                    var nextLine: String? = reader.readLine()
                    
                    while (nextLine != null) {
                        line = nextLine
                        nextLine = reader.readLine()
                        
                        emit(line)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }.flowOn(Dispatchers.IO)
    
    private var detectedData = ByteArray(0)
    
    private fun openInputStream(uri: Uri): InputStream? {
        return when (uri.scheme) {
            "content" -> {
                context.contentResolver.openInputStream(uri)
            }
            "file" -> {
                uri.path?.let { File(it).inputStream() }
            }
            else -> {
                uri.path?.let { File(it).inputStream() }
            }
        }
    }
    
    /**
     * 检测文件编码
     * 支持 UTF-8、GBK、GB2312、Big5 等常见中文编码
     */
    private fun detectFileEncoding(inputStream: InputStream): Charset {
        val detector = UniversalDetector(null)
        val bufferSize = 4096
        val buffer = ByteArray(bufferSize)
        
        try {
            var bytesRead = inputStream.read(buffer)
            var totalBytesRead = 0
            
            while (bytesRead > 0 && !detector.isDone()) {
                detector.handleData(buffer, 0, bytesRead)
                if (!detector.isDone()) {
                    bytesRead = inputStream.read(buffer)
                }
            }
            
            detector.dataEnd()
            
            // 获取检测到的编码
            val charsetName = detector.detectedCharset ?: "UTF-8"
            
            if (bytesRead > 0) {
                detectedData = buffer.copyOf(bytesRead)
            }
            
            return Charset.forName(charsetName)
        } catch (e: Exception) {
            // 检测失败时返回 UTF-8
            return Charsets.UTF_8
        } finally {
            detector.reset()
        }
    }
}
