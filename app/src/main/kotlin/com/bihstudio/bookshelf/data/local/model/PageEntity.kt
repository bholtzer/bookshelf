package com.bihstudio.bookshelf.data.local.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bihstudio.bookshelf.domain.model.Page
import com.bihstudio.bookshelf.domain.model.PageType
import java.time.Instant

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,   // deleting a book wipes its pages
        )
    ],
    indices = [Index("bookId"), Index("bookId", "position")],
)
data class PageEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val ownerId: String,
    val position: Int,
    val pageType: String,                  // PageType.name — stored as string
    val localUri: String?,                 // path inside app-internal storage
    val remoteUrl: String?,
    val originalFileName: String,
    val removalSuggestedByIds: String,
    val createdAt: Long,
    val isSynced: Boolean,
    val syncError: String?,
)

// ── Mappers ──────────────────────────────────────────────────────────────────

fun PageEntity.toDomain() = Page(
    id = id,
    bookId = bookId,
    ownerId = ownerId,
    position = position,
    pageType = PageType.valueOf(pageType),
    localUri = localUri,
    remoteUrl = remoteUrl,
    originalFileName = originalFileName,
    removalSuggestedByIds = removalSuggestedByIds.toRemovalSuggestionIdList(),
    createdAt = Instant.ofEpochMilli(createdAt),
    isSynced = isSynced,
    syncError = syncError,
)

fun Page.toEntity() = PageEntity(
    id = id,
    bookId = bookId,
    ownerId = ownerId,
    position = position,
    pageType = pageType.name,
    localUri = localUri,
    remoteUrl = remoteUrl,
    originalFileName = originalFileName,
    removalSuggestedByIds = removalSuggestedByIds.toRemovalSuggestionIdStorage(),
    createdAt = createdAt.toEpochMilli(),
    isSynced = isSynced,
    syncError = syncError,
)

fun List<String>.toRemovalSuggestionIdStorage(): String =
    distinct()
        .filter { it.isNotBlank() }
        .joinToString(separator = "", prefix = "|", postfix = "|")

private fun String.toRemovalSuggestionIdList(): List<String> =
    split("|").filter { it.isNotBlank() }
