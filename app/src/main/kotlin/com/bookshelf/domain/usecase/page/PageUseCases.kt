package com.bookshelf.domain.usecase.page

import com.bookshelf.domain.model.AppResult
import com.bookshelf.domain.model.Page
import com.bookshelf.domain.model.PageType
import com.bookshelf.domain.repository.PageRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class ObservePagesUseCase @Inject constructor(
    private val pageRepository: PageRepository,
) {
    operator fun invoke(bookId: String): Flow<List<Page>> =
        pageRepository.observePages(bookId)
}

class AddPageFromUriUseCase @Inject constructor(
    private val pageRepository: PageRepository,
) {
    /**
     * Called when the user shares an image/PDF into the app, or picks one manually.
     *
     * @param sourceUri  the content:// or file:// URI of the incoming file
     * @param mimeType   used to determine [PageType]
     * @param position   where to insert; -1 = append at end
     */
    suspend operator fun invoke(
        bookId: String,
        ownerId: String,
        sourceUri: String,
        mimeType: String,
        originalFileName: String = "",
        position: Int = -1,
    ): AppResult<Page> {
        val pageType = when {
            mimeType.startsWith("image/") -> PageType.IMAGE
            mimeType == "application/pdf" -> PageType.PDF
            else -> return AppResult.Error("Unsupported file type: $mimeType")
        }
        val page = Page(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            ownerId = ownerId,
            position = position,               // repo normalises this on insert
            pageType = pageType,
            originalFileName = originalFileName,
            createdAt = Instant.now(),
            isSynced = false,
        )
        return pageRepository.addPage(page, sourceUri)
    }
}

class ReorderPagesUseCase @Inject constructor(
    private val pageRepository: PageRepository,
) {
    suspend operator fun invoke(
        bookId: String,
        orderedIds: List<String>,
    ): AppResult<Unit> = pageRepository.reorderPages(bookId, orderedIds)
}

class DeletePageUseCase @Inject constructor(
    private val pageRepository: PageRepository,
) {
    suspend operator fun invoke(pageId: String): AppResult<Unit> =
        pageRepository.deletePage(pageId)
}
