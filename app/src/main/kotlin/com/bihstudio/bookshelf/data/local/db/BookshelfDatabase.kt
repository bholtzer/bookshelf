package com.bihstudio.bookshelf.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bihstudio.bookshelf.data.local.model.BookEntity
import com.bihstudio.bookshelf.data.local.model.PageEntity

@Database(
    entities = [BookEntity::class, PageEntity::class],
    version = 5,
    exportSchema = true,          // keep migration history in /schemas
)
abstract class BookshelfDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun pageDao(): PageDao

    companion object {
        const val DATABASE_NAME = "com.bihstudio.bookshelf.db"
    }
}
