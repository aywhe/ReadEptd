package com.example.readeptd.dao

import androidx.activity.result.contract.ActivityResultContracts
import androidx.room.Entity
import androidx.room.PrimaryKey

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
