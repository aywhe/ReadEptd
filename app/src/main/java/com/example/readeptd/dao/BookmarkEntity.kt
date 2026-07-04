package com.example.readeptd.dao

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 书签实体类
 *
 * 用于在数据库中存储书签信息，包括：
 * - 书籍 ID
 * - 文件 URI
 * - MIME 类型
 * - 阅读位置
 * - 可选的笔记内容
 * - 创建时间和最后修改时间
 */
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val fileUri: String,
    val mimeType: String,
    val position: String,
    val note: String? = null,
    val createdTime: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)
