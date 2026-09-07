package com.bihstudio.madafim.domain.usecase.page

import com.bihstudio.madafim.domain.model.AppResult
import com.bihstudio.madafim.domain.model.Page
import com.bihstudio.madafim.domain.model.PageType
import com.bihstudio.madafim.domain.repository.BookRepository
import com.bihstudio.madafim.domain.repository.PageRepository
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

class ObservePagesByIdsUseCase @Inject constructor(
    private val pageRepository: PageRepository,
) {
    operator fun invoke(pageIds: List<String>): Flow<List<Page>> =
        pageRepository.observePagesByIds(pageIds)
}

class ObserveFirstPagesForBooksUseCase @Inject constructor(
    private val pageRepository: PageRepository,
) {
    operator fun invoke(bookIds: List<String>): Flow<Map<String, Page>> =
        pageRepository.observeFirstPagesForBooks(bookIds)
}

class SyncBookPagesUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val pageRepository: PageRepository,
) {
    suspend operator fun invoke(bookId: String): AppResult<Int> {
        val book = bookRepository.getBook(bookId)
            ?: return AppResult.Error("Book not found")
        return pageRepository.syncFromRemote(book.ownerId, book.id)
    }
}

class AddPageFromUriUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository,
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
        val book = bookRepository.getBook(bookId)
            ?: return AppResult.Error("Book not found")
        if (!book.canEditPages(ownerId)) {
            return AppResult.Error("This book was not shared with permission to edit pages")
        }
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
    private val bookRepository: BookRepository,
) {
    suspend operator fun invoke(pageId: String, currentUserId: String): AppResult<Unit> {
        val page = pageRepository.getPage(pageId)
            ?: return AppResult.Error("Page not found")
        val book = bookRepository.getBook(page.bookId)
            ?: return AppResult.Error("Book not found")
        if (book.ownerId != currentUserId) {
            return AppResult.Error("Only the book owner can remove pages")
        }
        return pageRepository.deletePage(pageId)
    }
}

class RecommendPageRemovalUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository,
) {
    suspend operator fun invoke(pageId: String, currentUserId: String): AppResult<Page> {
        val page = pageRepository.getPage(pageId)
            ?: return AppResult.Error("Page not found")
        val book = bookRepository.getBook(page.bookId)
            ?: return AppResult.Error("Book not found")
        if (!book.canEditPages(currentUserId)) {
            return AppResult.Error("This book was not shared with permission to edit pages")
        }
        if (book.ownerId == currentUserId) {
            return AppResult.Error("Owners can remove pages directly")
        }
        return pageRepository.recommendPageRemoval(pageId, currentUserId)
    }
}
