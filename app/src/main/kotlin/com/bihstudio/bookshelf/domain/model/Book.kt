package com.bihstudio.bookshelf.domain.model

import java.time.Instant

data class Book(
    val id: String,
    val ownerId: String,
    val title: String,
    val description: String = "",
    val coverPageId: String? = null,       // ID of the page used as cover thumbnail
    val pageCount: Int = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val isSynced: Boolean = false,
)
