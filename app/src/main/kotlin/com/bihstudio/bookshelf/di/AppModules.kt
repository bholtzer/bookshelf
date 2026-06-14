package com.bihstudio.bookshelf.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN sharedEditorIds TEXT NOT NULL DEFAULT '||'")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pages ADD COLUMN removalSuggestedByIds TEXT NOT NULL DEFAULT '||'")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN coverStyle TEXT")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN customCoverUri TEXT")
            db.execSQL("ALTER TABLE books ADD COLUMN customCoverRemoteUrl TEXT")
            db.execSQL("ALTER TABLE books ADD COLUMN customCoverPrompt TEXT")
        }
    }

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): BookshelfDatabase =
        Room.databaseBuilder(ctx, BookshelfDatabase::class.java, BookshelfDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
