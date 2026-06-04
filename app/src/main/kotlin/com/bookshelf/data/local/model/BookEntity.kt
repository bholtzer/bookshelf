package com.bookshelf.data.local.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bookshelf.domain.model.Book
import java.time.Instant

@Entity(
    tableName = "books",
    indices = [Index("ownerId"), Index("updatedAt")],
)
data class BookEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val title: String,
    val description: String,
    val coverPageId: String?,
    val pageCount: Int,
    val createdAt: Long,        // epoch millis — Room can't store Instant natively
    val updatedAt: Long,
    val isSynced: Boolean,
)

// ── Mappers ──────────────────────────────────────────────────────────────────

fun BookEntity.toDomain() = Book(
    id = id,
    ownerId = ownerId,
    title = title,
    description = description,
    coverPageId = coverPageId,
    pageCount = pageCount,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    isSynced = isSynced,
)

fun Book.toEntity() = BookEntity(
    id = id,
    ownerId = ownerId,
    title = title,
    description = description,
    coverPageId = coverPageId,
    pageCount = pageCount,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    isSynced = isSynced,
)
