package com.bookshelf.di

import android.content.Context
import androidx.room.Room
import com.bookshelf.data.local.db.BookDao
import com.bookshelf.data.local.db.BookshelfDatabase
import com.bookshelf.data.local.db.PageDao
import com.bookshelf.data.repository.AuthRepositoryImpl
import com.bookshelf.data.repository.BookRepositoryImpl
import com.bookshelf.data.repository.PageRepositoryImpl
import com.bookshelf.domain.repository.AuthRepository
import com.bookshelf.domain.repository.BookRepository
import com.bookshelf.domain.repository.PageRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import com.google.firebase.storage.FirebaseStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// ── Database module ───────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BookshelfDatabase =
        Room.databaseBuilder(context, BookshelfDatabase::class.java, BookshelfDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideBookDao(db: BookshelfDatabase): BookDao = db.bookDao()

    @Provides
    fun providePageDao(db: BookshelfDatabase): PageDao = db.pageDao()
}

// ── Firebase module ───────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore =
        FirebaseFirestore.getInstance().also { db ->
            // Enable offline persistence (memory cache for SDK v25+)
            db.firestoreSettings = firestoreSettings {
                setLocalCacheSettings(memoryCacheSettings {})
            }
        }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()
}

// ── Repository bindings ───────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindPageRepository(impl: PageRepositoryImpl): PageRepository
}
