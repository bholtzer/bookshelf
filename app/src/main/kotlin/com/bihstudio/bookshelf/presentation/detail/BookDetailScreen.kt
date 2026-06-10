package com.bihstudio.bookshelf.presentation.detail

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.bihstudio.bookshelf.domain.model.AppResult
import com.bihstudio.bookshelf.domain.model.Book
import com.bihstudio.bookshelf.domain.model.Page
import com.bihstudio.bookshelf.domain.model.PageType
import com.bihstudio.bookshelf.domain.usecase.auth.GetCurrentUserUseCase
import com.bihstudio.bookshelf.domain.usecase.book.DeleteBookUseCase
import com.bihstudio.bookshelf.domain.usecase.book.GetBookUseCase
import com.bihstudio.bookshelf.domain.usecase.book.RenameBookUseCase
import com.bihstudio.bookshelf.domain.usecase.book.SetBookCoverUseCase
import com.bihstudio.bookshelf.domain.usecase.page.AddPageFromUriUseCase
import com.bihstudio.bookshelf.domain.usecase.page.DeletePageUseCase
import com.bihstudio.bookshelf.domain.usecase.page.ObservePagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookDetailUiState(
    val book: Book? = null,
    val pages: List<Page> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val getBook: GetBookUseCase,
    private val renameBook: RenameBookUseCase,
    private val setBookCover: SetBookCoverUseCase,
    private val deleteBook: DeleteBookUseCase,
    private val observePages: ObservePagesUseCase,
    private val addPageFromUri: AddPageFromUriUseCase,
    private val deletePage: DeletePageUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _state.asStateFlow()

    fun load(bookId: String) {
        viewModelScope.launch {
            _state.update { it.copy(book = getBook(bookId)) }
            observePages(bookId).collect { pages ->
                _state.update { it.copy(pages = pages, isLoading = false) }
            }
        }
    }

    fun save(title: String, description: String) {
        val book = _state.value.book ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            when (val result = renameBook(book.id, title, description)) {
                is AppResult.Error -> _state.update {
                    it.copy(isSaving = false, error = result.message)
                }
                is AppResult.Success -> _state.update {
                    it.copy(isSaving = false, book = result.data)
                }
            }
        }
    }

    fun addFiles(files: List<PickedFile>) {
        val user = getCurrentUser() ?: return
        val bookId = _state.value.book?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            for (file in files) {
                val result = addPageFromUri(
                    bookId = bookId,
                    ownerId = user.uid,
                    sourceUri = file.uri,
                    mimeType = file.mimeType,
                    originalFileName = file.displayName,
                )
                if (result is AppResult.Error) {
                    _state.update { it.copy(error = result.message) }
                }
            }
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun deletePage(pageId: String) {
        viewModelScope.launch {
            deletePage.invoke(pageId)
        }
    }

    fun setCover(pageId: String?) {
        val bookId = _state.value.book?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            when (val result = setBookCover(bookId, pageId)) {
                is AppResult.Error -> _state.update {
                    it.copy(isSaving = false, error = result.message)
                }
                is AppResult.Success -> _state.update {
                    it.copy(isSaving = false, book = result.data)
                }
            }
        }
    }

    fun deleteCurrentBook(onDeleted: () -> Unit) {
        val bookId = _state.value.book?.id ?: return
        viewModelScope.launch {
            deleteBook(bookId)
            onDeleted()
        }
    }
}

data class PickedFile(
    val uri: String,
    val mimeType: String,
    val displayName: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var title by remember(state.book?.id) { mutableStateOf(state.book?.title.orEmpty()) }
    var description by remember(state.book?.id) { mutableStateOf(state.book?.description.orEmpty()) }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val picked = uris.mapNotNull { context.toPickedFile(it) }
        viewModel.addFiles(picked)
    }

    androidx.compose.runtime.LaunchedEffect(bookId) {
        viewModel.load(bookId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Book") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete book")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.padding(padding).fillMaxSize(),
                Alignment.Center,
            ) { CircularProgressIndicator() }

            state.book == null -> Box(
                Modifier.padding(padding).fillMaxSize(),
                Alignment.Center,
            ) { Text("Book not found") }

            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Book name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        state.error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                enabled = title.isNotBlank() && !state.isSaving,
                                onClick = { viewModel.save(title, description) },
                            ) { Text("Save") }
                            Button(
                                enabled = !state.isSaving,
                                onClick = { filePicker.launch(arrayOf("image/*", "application/pdf")) },
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text("Add pages")
                            }
                        }
                    }
                }

                item {
                    state.book?.let { book ->
                        CoverPickerPreview(
                            book = book,
                            pages = state.pages,
                            onClearCover = { viewModel.setCover(null) },
                        )
                    }
                }

                item {
                    Text(
                        "Pages",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (state.pages.isEmpty()) {
                    item {
                        Text(
                            "No pages yet. Add images or PDFs to this book.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.pages, key = { it.id }) { page ->
                        PageRow(
                            page = page,
                            isCover = state.book?.coverPageId == page.id,
                            onSetCover = { viewModel.setCover(page.id) },
                            onDelete = { viewModel.deletePage(page.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete book?") },
            text = { Text("This removes the book and its local pages from this device.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCurrentBook(onBack) }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CoverPickerPreview(
    book: Book,
    pages: List<Page>,
    onClearCover: () -> Unit,
) {
    val coverPage = pages.firstOrNull { it.id == book.coverPageId }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverThumbnail(
                title = book.title,
                page = coverPage,
                modifier = Modifier.size(width = 84.dp, height = 116.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Book cover", style = MaterialTheme.typography.titleMedium)
                Text(
                    coverPage?.originalFileName?.ifBlank { "Selected page" }
                        ?: "Using generated cover",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.coverPageId != null) {
                    TextButton(onClick = onClearCover) { Text("Use generated cover") }
                }
            }
        }
    }
}

@Composable
private fun PageRow(
    page: Page,
    isCover: Boolean,
    onSetCover: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                page.originalFileName.ifBlank { "Page ${page.position + 1}" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                if (page.isSynced) "Backed up" else page.syncError ?: "Waiting for backup",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = {
            CoverThumbnail(
                title = page.originalFileName,
                page = page,
                modifier = Modifier.size(width = 52.dp, height = 70.dp),
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSetCover, enabled = !isCover) {
                    Icon(
                        if (isCover) Icons.Default.CheckCircle else Icons.Default.Star,
                        contentDescription = if (isCover) "Current cover" else "Use as cover",
                        tint = if (isCover) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete page")
                }
            }
        },
    )
}

@Composable
private fun CoverThumbnail(
    title: String,
    page: Page?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(8.dp))
            .background(defaultCoverBrush(title)),
        contentAlignment = Alignment.Center,
    ) {
        when (page?.pageType) {
            PageType.IMAGE -> AsyncImage(
                model = page.remoteUrl ?: page.localUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            PageType.PDF -> Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
            null -> Icon(
                Icons.Default.AutoStories,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

private fun defaultCoverBrush(seed: String): Brush {
    val palettes = listOf(
        listOf(Color(0xFF355C7D), Color(0xFFC06C84), Color(0xFFF8B195)),
        listOf(Color(0xFF1D3557), Color(0xFF2A9D8F), Color(0xFFE9C46A)),
        listOf(Color(0xFF4A4E69), Color(0xFF9A8C98), Color(0xFFC9ADA7)),
        listOf(Color(0xFF264653), Color(0xFFE76F51), Color(0xFFF4A261)),
        listOf(Color(0xFF3A506B), Color(0xFF5BC0BE), Color(0xFFEEF5DB)),
    )
    val colors = palettes[Math.floorMod(seed.hashCode(), palettes.size)]
    return Brush.linearGradient(colors)
}

private fun Context.toPickedFile(uri: Uri): PickedFile? {
    val mimeType = contentResolver.getType(uri) ?: return null
    val displayName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }.orEmpty()
    return PickedFile(uri = uri.toString(), mimeType = mimeType, displayName = displayName)
}
