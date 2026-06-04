package com.bookshelf.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bookshelf.data.local.model.BookEntity
import com.bookshelf.data.local.model.PageEntity

@Database(
    entities = [BookEntity::class, PageEntity::class],
    version = 1,
    exportSchema = true,          // keep migration history in /schemas
)
abstract class BookshelfDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun pageDao(): PageDao

    companion object {
        const val DATABASE_NAME = "bookshelf.db"
    }
}
