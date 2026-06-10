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

data class SharedUri(val uri: String, val mimeType: String)

data class ShareUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
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
        }
    }

    fun onUrisReceived(uris: List<String>, mimeType: String) {
        _pendingUris.value = uris.map { SharedUri(it, mimeType) }
        _state.update { it.copy(error = null) }
    }

    fun addToExistingBook(book: Book) {
        val user = getCurrentUser() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            _pendingUris.value.forEach { shared ->
                addPageFromUri(book.id, user.uid, shared.uri, shared.mimeType)
            }
            _state.update { it.copy(isSaving = false) }
            clearPending()
        }
    }

    fun createBookAndAdd(title: String, description: String) {
        val user = getCurrentUser() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            when (val result = createBook(user.uid, title, description)) {
                is AppResult.Error   -> _state.update { it.copy(isSaving = false, error = result.message) }
                is AppResult.Success -> {
                    _pendingUris.value.forEach { shared ->
                        addPageFromUri(result.data.id, user.uid, shared.uri, shared.mimeType)
                    }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(
                if (uris.size == 1) "Add 1 page to…" else "Add ${uris.size} pages to…",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
            }

            ListItem(
                headlineContent = { Text("New book…") },
                leadingContent  = { Icon(Icons.Default.Add, null) },
                modifier = Modifier.clickable { showDialog = true },
            )
            HorizontalDivider()

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(100.dp), Alignment.Center) { CircularProgressIndicator() }
            } else if (state.books.isEmpty()) {
                Text("No books yet. Create your first one above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(state.books, key = { it.id }) { book ->
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
