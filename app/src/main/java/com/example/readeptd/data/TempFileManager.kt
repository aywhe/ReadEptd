package com.example.readeptd.data

import android.app.Application
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 临时文件管理器
 * 统一管理应用缓存目录中的临时文件（书籍阅读用）
 */
object TempFileManager {

    private const val TAG = "TempFileManager"
    private const val TEMP_FILE_PREFIX = "book"

    /**
     * 生成临时文件名（内部使用统一前缀）
     */
    private fun generateTempFileName(uri: String, fileName: String): String {
        val uriHash = uri.hashCode().toString().replace("-", "_")
        val fileExtension = fileName.substringAfterLast(".", "")
        val baseName = fileName.substringBeforeLast(".")
        return "${TEMP_FILE_PREFIX}_${uriHash}_${baseName}.${fileExtension}"
    }

    /**
     * 获取临时文件对象
     */
    private fun getTempFile(application: Application, uri: String, fileName: String): File {
        val tempFileName = generateTempFileName(uri, fileName)
        return File(application.cacheDir, tempFileName)
    }

    /**
     * 创建或获取临时文件（如果已存在则复用）
     * @return 临时文件对象，如果创建失败返回 null
     */
    suspend fun createOrGetTempFile(application: Application, uri: Uri, fileName: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val tempFile = getTempFile(application, uri.toString(), fileName)
                
                if (!tempFile.exists()) {
                    application.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: return@withContext null
                    
                    Log.d(TAG, "临时文件创建成功: ${tempFile.absolutePath}")
                } else {
                    Log.d(TAG, "临时文件已存在，复用: ${tempFile.absolutePath}")
                }
                
                tempFile
            } catch (e: Exception) {
                Log.e(TAG, "创建临时文件失败: $fileName", e)
                null
            }
        }
    }

    /**
     * 删除指定文件的临时文件
     * @return 是否删除成功
     */
    suspend fun deleteTempFile(application: Application, uri: String, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val tempFile = getTempFile(application, uri, fileName)

                if (tempFile.exists()) {
                    val deleted = tempFile.delete()
                    if (deleted) {
                        Log.d(TAG, "已删除临时文件: $fileName")
                    }
                    deleted
                } else {
                    Log.d(TAG, "临时文件不存在，无需删除: $fileName")
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "删除临时文件失败: $fileName", e)
                false
            }
        }
    }

    /**
     * 清理孤儿临时文件（不在有效列表中的文件）
     * @param validFileInfos 有效的文件信息列表
     * @return 清理的文件数量
     */
    suspend fun cleanupOrphanedFiles(
        application: Application,
        validFileInfos: List<FileInfo>
    ): Int {
        return withContext(Dispatchers.IO) {
            try {
                val cacheDir = application.cacheDir
                val tempFiles = cacheDir.listFiles { file ->
                    file.name.startsWith("${TEMP_FILE_PREFIX}_")
                }

                // 生成所有有效文件的临时文件名
                val validFileNames = validFileInfos.map { fileInfo ->
                    generateTempFileName(fileInfo.uri, fileInfo.fileName)
                }.toSet()

                var cleanedCount = 0
                tempFiles?.forEach { file ->
                    if (file.name !in validFileNames) {
                        file.delete()
                        Log.d(TAG, "清理孤儿临时文件: ${file.name}")
                        cleanedCount++
                    }
                }

                Log.d(TAG, "孤儿文件清理完成，共清理 $cleanedCount 个文件")
                cleanedCount
            } catch (e: Exception) {
                Log.e(TAG, "清理孤儿文件失败", e)
                0
            }
        }
    }

    /**
     * 检查临时文件是否存在
     */
    fun tempFileExists(application: Application, uri: String, fileName: String): Boolean {
        return getTempFile(application, uri, fileName).exists()
    }

    /**
     * 获取所有临时文件
     */
    fun getAllTempFiles(application: Application): List<File> {
        val cacheDir = application.cacheDir
        return cacheDir.listFiles { file ->
            file.name.startsWith("${TEMP_FILE_PREFIX}_")
        }?.toList() ?: emptyList()
    }

    /**
     * 获取临时文件总大小（字节）
     */
    fun getTotalTempFileSize(application: Application): Long {
        return getAllTempFiles(application).sumOf { it.length() }
    }
}