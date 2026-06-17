package com.bihstudio.bookshelf.presentation.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bihstudio.bookshelf.domain.analytics.AnalyticsEvent
import com.bihstudio.bookshelf.domain.analytics.AnalyticsLogger
import com.bihstudio.bookshelf.domain.analytics.AnalyticsParam
import com.bihstudio.bookshelf.domain.model.AppResult
import com.bihstudio.bookshelf.domain.model.Book
import com.bihstudio.bookshelf.domain.usecase.auth.GetCurrentUserUseCase
import com.bihstudio.bookshelf.domain.usecase.book.CreateBookUseCase
import com.bihstudio.bookshelf.domain.usecase.book.ObserveBooksUseCase
import com.bihstudio.bookshelf.domain.usecase.page.AddPageFromUriUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class SharedUri(
    val uri: String,
    val mimeType: String,
    val displayName: String = "",
)

data class ShareUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSignedIn: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ShareReceiverViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val observeBooks: ObserveBooksUseCase,
    private val createBook: CreateBookUseCase,
    private val addPageFromUri: AddPageFromUriUseCase,
    private val analytics: AnalyticsLogger,
) : ViewModel() {

    private val _pendingUris = MutableStateFlow<List<SharedUri>>(emptyList())
    val pendingUris: StateFlow<List<SharedUri>> = _pendingUris.asStateFlow()

    private val _state = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _state.asStateFlow()

    init {
        val user = getCurrentUser()
        if (user != null) {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                observeBooks(user.uid).collect { books ->
                    _state.update { it.copy(books = books, isLoading = false) }
                }
            }
        } else {
            _state.update { it.copy(isSignedIn = false, isLoading = false) }
        }
    }

    fun onUrisReceived(uris: List<SharedUri>) {
        _pendingUris.value = uris
        analytics.trackScreen("share_receiver")
        analytics.track(
            AnalyticsEvent.SHARE_RECEIVED,
            mapOf(
                AnalyticsParam.FILE_COUNT to uris.size,
                AnalyticsParam.MIME_TYPE to uris.firstOrNull()?.mimeType.orEmpty(),
            ),
        )
        _state.update { it.copy(error = null) }
    }

    fun addToExistingBook(book: Book) {
        val user = getCurrentUser() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            var failures = 0
            _pendingUris.value.forEach { shared ->
                if (addPageFromUri(book.id, user.uid, shared.uri, shared.mimeType) is AppResult.Error) {
                    failures++
                }
            }
            analytics.track(
                AnalyticsEvent.SHARE_ADD_TO_BOOK_RESULT,
                mapOf(
                    AnalyticsParam.BOOK_ID to book.id,
                    AnalyticsParam.FILE_COUNT to _pendingUris.value.size,
                    AnalyticsParam.RESULT to if (failures == 0) "success" else "partial_failure",
                ),
            )
            _state.update { it.copy(isSaving = false) }
            clearPending()
        }
    }

    fun createBookAndAdd(title: String, description: String) {
        val user = getCurrentUser() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            when (val result = createBook(user.uid, title, description)) {
                is AppResult.Error -> {
                    analytics.track(
                        AnalyticsEvent.SHARE_CREATE_BOOK_RESULT,
                        mapOf(AnalyticsParam.RESULT to "failure"),
                    )
                    _state.update { it.copy(isSaving = false, error = result.message) }
                }
                is AppResult.Success -> {
                    var failures = 0
                    _pendingUris.value.forEach { shared ->
                        if (addPageFromUri(result.data.id, user.uid, shared.uri, shared.mimeType) is AppResult.Error) {
                            failures++
                        }
                    }
                    analytics.track(
                        AnalyticsEvent.SHARE_CREATE_BOOK_RESULT,
                        mapOf(
                            AnalyticsParam.BOOK_ID to result.data.id,
                            AnalyticsParam.FILE_COUNT to _pendingUris.value.size,
                            AnalyticsParam.RESULT to if (failures == 0) "success" else "partial_failure",
                        ),
                    )
                    _state.update { it.copy(isSaving = false) }
                    clearPending()
                }
            }
        }
    }

    fun clearPending() {
        _pendingUris.value = emptyList()
        _state.update { it.copy(error = null) }
    }

    fun dismissPending() {
        if (_pendingUris.value.isNotEmpty()) {
            analytics.track(
                AnalyticsEvent.SHARE_SHEET_DISMISSED,
                mapOf(AnalyticsParam.FILE_COUNT to _pendingUris.value.size),
            )
        }
        clearPending()
    }
}

// ── Bottom Sheet ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareReceiverBottomSheet(
    uris: List<SharedUri>,
    onDismiss: () -> Unit,
    viewModel: ShareReceiverViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    val recommendedBook = remember(state.books, uris) {
        state.books.recommendedFor(uris)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(
                if (uris.size == 1) "Add 1 file to BookShelf" else "Add ${uris.size} files to BookShelf",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                uris.toIncomingSummary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
            }

            ListItem(
                headlineContent = { Text("Create new book") },
                supportingContent = { Text("Save these files as the first pages") },
                leadingContent  = { Icon(Icons.Default.Add, null) },
                modifier = Modifier.clickable(enabled = state.isSignedIn) { showDialog = true },
            )
            HorizontalDivider()

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(100.dp), Alignment.Center) { CircularProgressIndicator() }
            } else if (!state.isSignedIn) {
                Text(
                    "Sign in to BookShelf first, then open this file again to add it to a book.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
                )
            } else if (state.books.isEmpty()) {
                Text("No books yet. Create your first one above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp))
            } else {
                recommendedBook?.let { book ->
                    Text(
                        "Recommended",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                    ListItem(
                        headlineContent = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("Best match for the incoming file") },
                        leadingContent = { Icon(Icons.Default.Book, null) },
                        modifier = Modifier.clickable(enabled = !state.isSaving) { viewModel.addToExistingBook(book) },
                    )
                    HorizontalDivider()
                }
                Text(
                    "Choose a book",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(state.books.filterNot { it.id == recommendedBook?.id }, key = { it.id }) { book ->
                        ListItem(
                            headlineContent   = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${book.pageCount} pages", style = MaterialTheme.typography.bodySmall) },
                            leadingContent    = { Icon(Icons.Default.Book, null) },
                            modifier = Modifier.clickable(enabled = !state.isSaving) { viewModel.addToExistingBook(book) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDialog) {
        NewBookDialog(
            isSaving  = state.isSaving,
            onConfirm = { t, d -> viewModel.createBookAndAdd(t, d); showDialog = false },
            onDismiss = { showDialog = false },
        )
    }
}

private fun List<Book>.recommendedFor(uris: List<SharedUri>): Book? {
    val fileText = uris.joinToString(" ") { it.displayName }.lowercase()
    if (fileText.isBlank()) return null
    return maxByOrNull { book ->
        book.title.lowercase()
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .count { token -> fileText.contains(token) }
    }?.takeIf { book ->
        book.title.lowercase()
            .split(Regex("\\s+"))
            .any { token -> token.length >= 3 && fileText.contains(token) }
    }
}

private fun List<SharedUri>.toIncomingSummary(): String {
    if (isEmpty()) return "Choose where to save the incoming file."
    if (size == 1) {
        val file = first()
        return file.displayName.ifBlank {
            if (file.mimeType == "application/pdf") "Incoming PDF file" else "Incoming image file"
        }
    }
    val imageCount = count { it.mimeType.startsWith("image/") }
    val pdfCount = count { it.mimeType == "application/pdf" }
    return listOfNotNull(
        imageCount.takeIf { it > 0 }?.let { "$it image${if (it == 1) "" else "s"}" },
        pdfCount.takeIf { it > 0 }?.let { "$it PDF${if (it == 1) "" else "s"}" },
    ).joinToString(", ").ifBlank { "$size files" }
}

@Composable
private fun NewBookDialog(isSaving: Boolean, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc  by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("New book") },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text("Title *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it },
                    label = { Text("Description (optional)") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, desc) }, enabled = title.isNotBlank() && !isSaving) {
                if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Create & add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
