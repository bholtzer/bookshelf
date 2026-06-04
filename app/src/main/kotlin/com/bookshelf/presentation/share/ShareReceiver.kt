package com.bookshelf.presentation.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookshelf.domain.model.AppResult
import com.bookshelf.domain.model.Book
import com.bookshelf.domain.usecase.auth.GetCurrentUserUseCase
import com.bookshelf.domain.usecase.book.CreateBookUseCase
import com.bookshelf.domain.usecase.book.ObserveBooksUseCase
import com.bookshelf.domain.usecase.page.AddPageFromUriUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SharedUri(
    val uri: String,
    val mimeType: String,
)

data class ShareUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class ShareReceiverViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val observeBooks: ObserveBooksUseCase,
    private val createBook: CreateBookUseCase,
    private val addPageFromUri: AddPageFromUriUseCase,
) : ViewModel() {

    private val _pendingUris = MutableStateFlow<List<SharedUri>>(emptyList())
    val pendingUris: StateFlow<List<SharedUri>> = _pendingUris.asStateFlow()

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    init {
        loadBooks()
    }

    private fun loadBooks() {
        val user = getCurrentUser() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            observeBooks(user.uid).collect { books ->
                _uiState.update { it.copy(books = books, isLoading = false) }
            }
        }
    }

    fun onUrisReceived(uris: List<String>, mimeType: String) {
        _pendingUris.value = uris.map { SharedUri(it, mimeType) }
        _uiState.update { it.copy(success = false, error = null) }
    }

    /** Add all pending URIs to an existing book. */
    fun addToExistingBook(book: Book) {
        val uris = _pendingUris.value
        val user = getCurrentUser() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            var hadError = false
            for (shared in uris) {
                val result = addPageFromUri(
                    bookId           = book.id,
                    ownerId          = user.uid,
                    sourceUri        = shared.uri,
                    mimeType         = shared.mimeType,
                )
                if (result is AppResult.Error) {
                    hadError = true
                    _uiState.update { it.copy(error = result.message) }
                }
            }
            _uiState.update { it.copy(isSaving = false, success = !hadError) }
            if (!hadError) clearPending()
        }
    }

    /** Create a new book then add all pending URIs to it. */
    fun createBookAndAdd(title: String, description: String) {
        val uris = _pendingUris.value
        val user = getCurrentUser() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val bookResult = createBook(user.uid, title, description)
            if (bookResult is AppResult.Error) {
                _uiState.update { it.copy(isSaving = false, error = bookResult.message) }
                return@launch
            }
            val newBook = (bookResult as AppResult.Success).data
            for (shared in uris) {
                addPageFromUri(
                    bookId    = newBook.id,
                    ownerId   = user.uid,
                    sourceUri = shared.uri,
                    mimeType  = shared.mimeType,
                )
            }
            _uiState.update { it.copy(isSaving = false, success = true) }
            clearPending()
        }
    }

    fun clearPending() {
        _pendingUris.value = emptyList()
        _uiState.update { it.copy(success = false, error = null) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

package com.bookshelf.presentation.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookshelf.domain.model.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareReceiverBottomSheet(
    uris: List<SharedUri>,
    onDismiss: () -> Unit,
    viewModel: ShareReceiverViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showNewBookDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = if (uris.size == 1) "Add 1 page to…" else "Add ${uris.size} pages to…",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // Error
            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Create new book option
            ListItem(
                headlineContent = { Text("New book…") },
                leadingContent  = { Icon(Icons.Default.Add, contentDescription = null) },
                modifier = Modifier.clickable { showNewBookDialog = true },
            )

            HorizontalDivider()

            // Existing books
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.books.isEmpty()) {
                Text(
                    text = "No books yet. Create your first one above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(uiState.books, key = { it.id }) { book ->
                        BookListItem(
                            book      = book,
                            isSaving  = uiState.isSaving,
                            onClick   = { viewModel.addToExistingBook(book) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showNewBookDialog) {
        NewBookDialog(
            isSaving  = uiState.isSaving,
            onConfirm = { title, desc ->
                viewModel.createBookAndAdd(title, desc)
                showNewBookDialog = false
            },
            onDismiss = { showNewBookDialog = false },
        )
    }
}

@Composable
private fun BookListItem(book: Book, isSaving: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text("${book.pageCount} pages", style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Icon(Icons.Default.Book, contentDescription = null)
        },
        modifier = Modifier.clickable(enabled = !isSaving, onClick = onClick),
    )
}

@Composable
private fun NewBookDialog(
    isSaving: Boolean,
    onConfirm: (title: String, description: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("New book") },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = title,
                    onValueChange = { title = it },
                    label         = { Text("Title *") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value         = description,
                    onValueChange = { description = it },
                    label         = { Text("Description (optional)") },
                    maxLines      = 3,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { onConfirm(title, description) },
                enabled  = title.isNotBlank() && !isSaving,
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Create & add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
