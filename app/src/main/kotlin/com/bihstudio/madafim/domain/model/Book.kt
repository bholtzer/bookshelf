package com.bihstudio.madafim.domain.model

import java.time.Instant

data class Book(
    val id: String,
    val ownerId: String,
    val title: String,
    val description: String = "",
    val coverPageId: String? = null,       // ID of the page used as cover thumbnail
    val coverStyle: String? = null,        // null = automatic style from title/description
    val customCoverUri: String? = null,
    val customCoverRemoteUrl: String? = null,
    val customCoverPrompt: String? = null,
    val sharedEditorIds: List<String> = emptyList(),
    val pageCount: Int = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val isSynced: Boolean = false,
) {
    fun canEditPages(userId: String): Boolean =
        ownerId == userId || userId in sharedEditorIds
}
