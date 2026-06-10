package com.bihstudio.bookshelf.di

import android.content.Context
import androidx.room.Room
import com.bihstudio.bookshelf.data.local.db.*
import com.bihstudio.bookshelf.data.repository.*
import com.bihstudio.bookshelf.domain.repository.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): BookshelfDatabase =
        Room.databaseBuilder(ctx, BookshelfDatabase::class.java, BookshelfDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun provideBookDao(db: BookshelfDatabase): BookDao = db.bookDao()
    @Provides fun providePageDao(db: BookshelfDatabase): PageDao = db.pageDao()
}

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides @Singleton fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    @Provides @Singleton fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
    @Provides @Singleton fun provideStorage(): FirebaseStorage = FirebaseStorage.getInstance()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
    @Binds @Singleton abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository
    @Binds @Singleton abstract fun bindPageRepository(impl: PageRepositoryImpl): PageRepository
}
