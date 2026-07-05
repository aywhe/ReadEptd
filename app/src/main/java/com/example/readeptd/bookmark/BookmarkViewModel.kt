package com.example.readeptd.bookmark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.room.Room
import com.example.readeptd.dao.AppDatabase

class BookmarkViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appDatabase by lazy {
        Room.databaseBuilder(
            application,
            AppDatabase::class.java, "readeptd-database"
        ).build()
    }
    val bookmarkRepository by lazy{BookmarkRepository(appDatabase.bookmarkDao())}
}