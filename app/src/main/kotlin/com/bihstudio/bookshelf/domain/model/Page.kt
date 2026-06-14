package com.bihstudio.bookshelf.domain.model

import java.time.Instant

/**
 * A single page inside a Book. The actual file lives either:
 *   - locally at [localUri]  (before sync or when offline)
 *   - remotely at [remoteUrl] (after successful upload to Firebase Storage)
 *
 * [pageType] drives the renderer: images go to Coil, PDFs go through PdfRenderer.
 */
data class Page(
    val id: String,
    val bookId: String,
    val ownerId: String,
    val position: Int,                     // 0-based order within the book
    val pageType: PageType,
    val localUri: String? = null,          // content:// or file:// URI on device
    val remoteUrl: String? = null,         // Firebase Storage download URL
    val originalFileName: String = "",
    val removalSuggestedByIds: List<String> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val isSynced: Boolean = false,
    val syncError: String? = null,
)

enum class PageType {
    IMAGE,   // jpeg, png, webp, …
    PDF,     // single PDF file treated as one page (first page rendered as thumbnail)
}
