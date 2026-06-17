package com.bihstudio.bookshelf.domain.usecase.book

import com.bihstudio.bookshelf.domain.model.AppResult
import com.bihstudio.bookshelf.domain.model.Book
import com.bihstudio.bookshelf.domain.repository.AuthRepository
import com.bihstudio.bookshelf.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class ObserveBooksUseCase @Inject constructor(
    private val bookRepository: BookRepository,
) {
    operator fun invoke(ownerId: String): Flow<List<Book>> =
        bookRepository.observeBooks(ownerId)
}

class GetBookUseCase @Inject constructor(
    private val bookRepository: BookRepository,
) {
    suspend operator fun invoke(bookId: String): Book? =
        bookRepository.getBook(bookId)
}

class CreateBookUseCase @Inject constructor(
    private val bookRepository: BookRepository,
) {
    suspend operator fun invoke(
        ownerId: String,
        title: String,
        description: String = "",
    ): AppResult<Book> {
        if (title.isBlank()) return AppResult.Error("Book title cannot be empty")
        val book = Book(
            id = UUID.randomUUID().toString(),
            ownerId = ownerId,
            title = title.trim(),
            description = description.trim(),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
        return bookRepository.createBook(book)
    }
}

class RenameBookUseCase @Inject constructor(
    private val bookRepository: BookRepository,
) {
    suspend operator fun invoke(
        bookId: String,
        newTitle: String,
        newDescription: String,
    ): AppResult<Book> {
        if (newTitle.isBlank()) return AppResult.Error("Book title cannot be empty")
        val existing = bookRepository.getBook(bookId)
            ?: return AppResult.Error("Book not found")
        return bookRepository.updateBook(
            existing.copy(
                title = newTitle.trim(),
                description = newDescription.trim(),
                updatedAt = Instant.now(),
                isSynced = false,
            )
        )
    }
}

class SetBookCoverUseCase @Inject constructor(
    private val bookRepository: BookRepository,
) {
    suspend operator fun invoke(bookId: String, coverPageId: String?): AppResult<Book> {
        val existing = bookRepository.getBook(bookId)
            ?: return AppResult.Error("Book not found")
        return bookRepository.updateBook(
            existing.copy(
                coverPageId = coverPageId,
                customCoverUri = null,
                customCoverRemoteUrl = null,
                customCoverPrompt = null,
                updatedAt = Instant.now(),
                isSynced = false,
            )
        )
    }
}

class SetBookCoverStyleUseCase @Inject constructor(
    private val bookRepository: BookRepository,
) {
    suspend operator fun invoke(bookId: String, coverStyle: String?): AppResult<Book> {
        val existing = bookRepository.getBook(bookId)
            ?: return AppResult.Error("Book not found")
        return bookRepository.updateBook(
            existing.copy(
                coverStyle = coverStyle?.takeIf { it.isNotBlank() },
                coverPageId = null,
                customCoverUri = null,
                customCoverRemoteUrl = null,
                customCoverPrompt = null,
                updatedAt = Instant.now(),
                isSynced = false,
            )
        )
    }
}

class SetBookCustomCoverFromUriUseCase @Inject constructor(
    private val bookRepository: BookRepository,
) {
    suspend operator fun invoke(bookId: String, sourceUri: String): AppResult<Book> =
        bookRepository.setCustomCoverFromUri(bookId, sourceUri)
}

class CreateBookCustomCoverImageUseCase @Inject constructor(
    private val bookRepository: BookRepository,
) {
    suspend operator fun invoke(bookId: String, prompt: String): AppResult<Book> {
        if (prompt.isBlank()) return AppResult.Error("Cover prompt cannot be empty")
        return bookRepository.createCustomCoverImage(bookId, prompt)
    }
}

class ShareBookWithEditorUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        bookId: String,
        ownerId: String,
        shareTarget: String,
    ): AppResult<Book> {
        when (val resolved = authRepository.resolveShareTargetToUserId(shareTarget)) {
            is AppResult.Error -> return AppResult.Error(resolved.message, resolved.cause)
            is AppResult.Success -> {
                if (resolved.data == ownerId) {
                    return AppResult.Error("You already own this book")
                }
                return bookRepository.shareBookWithEditor(bookId, ownerId, resolved.data)
            }
        }
    }
}

class CreateBookEditorInviteUseCase @Inject constructor(
    private val bookRepository: BookRepository,
) {
    suspend operator fun invoke(bookId: String, ownerId: String): AppResult<String> =
        bookRepository.createBookEditorInvite(bookId, ownerId)
}

class AcceptBookEditorInviteUseCase @Inject constructor(
    private val bookRepository: BookRepository,
) {
    suspend operator fun invoke(inviteText: String, editorUserId: String): AppResult<Book> {
        if (inviteText.isBlank()) return AppResult.Error("Paste an invite link or code")
        return bookRepository.acceptBookEditorInvite(inviteText, editorUserId)
    }
}

class DeleteBookUseCase @Inject constructor(
    private val bookRepository: BookRepository,
) {
    suspend operator fun invoke(bookId: String): AppResult<Unit> =
        bookRepository.deleteBook(bookId)
}
