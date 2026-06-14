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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.bihstudio.bookshelf.domain.usecase.book.CreateBookCustomCoverImageUseCase
import com.bihstudio.bookshelf.domain.usecase.book.DeleteBookUseCase
import com.bihstudio.bookshelf.domain.usecase.book.GetBookUseCase
import com.bihstudio.bookshelf.domain.usecase.book.RenameBookUseCase
import com.bihstudio.bookshelf.domain.usecase.book.SetBookCustomCoverFromUriUseCase
import com.bihstudio.bookshelf.domain.usecase.book.SetBookCoverUseCase
import com.bihstudio.bookshelf.domain.usecase.book.SetBookCoverStyleUseCase
import com.bihstudio.bookshelf.domain.usecase.book.ShareBookWithEditorUseCase
import com.bihstudio.bookshelf.domain.usecase.page.AddPageFromUriUseCase
import com.bihstudio.bookshelf.domain.usecase.page.DeletePageUseCase
import com.bihstudio.bookshelf.domain.usecase.page.ObservePagesUseCase
import com.bihstudio.bookshelf.domain.usecase.page.RecommendPageRemovalUseCase
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
    val currentUserId: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val canEditPages: Boolean
        get() = currentUserId != null && book?.canEditPages(currentUserId) == true
}

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val getBook: GetBookUseCase,
    private val renameBook: RenameBookUseCase,
    private val setBookCover: SetBookCoverUseCase,
    private val setBookCoverStyle: SetBookCoverStyleUseCase,
    private val setBookCustomCoverFromUri: SetBookCustomCoverFromUriUseCase,
    private val createBookCustomCoverImage: CreateBookCustomCoverImageUseCase,
    private val shareBookWithEditor: ShareBookWithEditorUseCase,
    private val deleteBook: DeleteBookUseCase,
    private val observePages: ObservePagesUseCase,
    private val addPageFromUri: AddPageFromUriUseCase,
    private val deletePage: DeletePageUseCase,
    private val recommendPageRemovalUseCase: RecommendPageRemovalUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _state.asStateFlow()

    fun load(bookId: String) {
        viewModelScope.launch {
            val userId = getCurrentUser()?.uid
            _state.update { it.copy(book = getBook(bookId), currentUserId = userId) }
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
        if (!_state.value.canEditPages) {
            _state.update { it.copy(error = "This book was not shared with permission to edit pages") }
            return
        }
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
        val user = getCurrentUser() ?: return
        viewModelScope.launch {
            when (val result = deletePage.invoke(pageId, user.uid)) {
                is AppResult.Error -> _state.update { it.copy(error = result.message) }
                is AppResult.Success -> Unit
            }
        }
    }

    fun recommendPageRemoval(pageId: String) {
        val user = getCurrentUser() ?: return
        viewModelScope.launch {
            when (val result = recommendPageRemovalUseCase(pageId, user.uid)) {
                is AppResult.Error -> _state.update { it.copy(error = result.message) }
                is AppResult.Success -> Unit
            }
        }
    }

    fun setCover(pageId: String?) {
        val bookId = _state.value.book?.id ?: return
        if (!_state.value.canEditPages) {
            _state.update { it.copy(error = "This book was not shared with permission to edit pages") }
            return
        }
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

    fun setGeneratedCoverStyle(style: String?) {
        val bookId = _state.value.book?.id ?: return
        if (!_state.value.canEditPages) {
            _state.update { it.copy(error = "This book was not shared with permission to edit pages") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            when (val result = setBookCoverStyle(bookId, style)) {
                is AppResult.Error -> _state.update {
                    it.copy(isSaving = false, error = result.message)
                }
                is AppResult.Success -> _state.update {
                    it.copy(isSaving = false, book = result.data)
                }
            }
        }
    }

    fun setCustomCoverFromUri(sourceUri: String) {
        val bookId = _state.value.book?.id ?: return
        if (!_state.value.canEditPages) {
            _state.update { it.copy(error = "This book was not shared with permission to edit pages") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            when (val result = setBookCustomCoverFromUri(bookId, sourceUri)) {
                is AppResult.Error -> _state.update {
                    it.copy(isSaving = false, error = result.message)
                }
                is AppResult.Success -> _state.update {
                    it.copy(isSaving = false, book = result.data)
                }
            }
        }
    }

    fun createCustomCoverImage(prompt: String) {
        val bookId = _state.value.book?.id ?: return
        if (!_state.value.canEditPages) {
            _state.update { it.copy(error = "This book was not shared with permission to edit pages") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            when (val result = createBookCustomCoverImage(bookId, prompt)) {
                is AppResult.Error -> _state.update {
                    it.copy(isSaving = false, error = result.message)
                }
                is AppResult.Success -> _state.update {
                    it.copy(isSaving = false, book = result.data)
                }
            }
        }
    }

    fun shareWithEditor(editorUserId: String) {
        val userId = getCurrentUser()?.uid ?: return
        val bookId = _state.value.book?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            when (val result = shareBookWithEditor(bookId, userId, editorUserId)) {
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
        val userId = getCurrentUser()?.uid ?: return
        val book = _state.value.book ?: return
        if (book.ownerId != userId) {
            _state.update { it.copy(error = "Only the book owner can delete this book") }
            return
        }
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
    var showShareDialog by remember { mutableStateOf(false) }
    var showCreateCoverDialog by remember { mutableStateOf(false) }
    var title by remember(state.book?.id) { mutableStateOf(state.book?.title.orEmpty()) }
    var description by remember(state.book?.id) { mutableStateOf(state.book?.description.orEmpty()) }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val picked = uris.mapNotNull { context.toPickedFile(it) }
        viewModel.addFiles(picked)
    }
    val coverPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.setCustomCoverFromUri(uri.toString())
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
                    if (state.book?.ownerId == state.currentUserId) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete book")
                        }
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
                                enabled = !state.isSaving && state.canEditPages,
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
                        SharingPermissionsCard(
                            book = book,
                            currentUserId = state.currentUserId,
                            canEditPages = state.canEditPages,
                            onAddEditor = { showShareDialog = true },
                        )
                    }
                }

                item {
                    state.book?.let { book ->
                        CoverPickerPreview(
                            book = book,
                            pages = state.pages,
                            canEditPages = state.canEditPages,
                            onClearCover = { viewModel.setCover(null) },
                            onSetGeneratedStyle = { style -> viewModel.setGeneratedCoverStyle(style) },
                            onChoosePhoto = { coverPhotoPicker.launch(arrayOf("image/*")) },
                            onCreateImage = { showCreateCoverDialog = true },
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
                            canEditPages = state.canEditPages,
                            isOwner = state.book?.ownerId == state.currentUserId,
                            currentUserId = state.currentUserId,
                            onSetCover = { viewModel.setCover(page.id) },
                            onDelete = { viewModel.deletePage(page.id) },
                            onRecommendRemoval = { viewModel.recommendPageRemoval(page.id) },
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

    if (showShareDialog) {
        ShareEditorDialog(
            isSaving = state.isSaving,
            existingEditorIds = state.book?.sharedEditorIds.orEmpty(),
            onDismiss = { showShareDialog = false },
            onConfirm = { editorUserId ->
                viewModel.shareWithEditor(editorUserId)
                showShareDialog = false
            },
        )
    }

    if (showCreateCoverDialog) {
        CreateCoverImageDialog(
            isSaving = state.isSaving,
            onDismiss = { showCreateCoverDialog = false },
            onConfirm = { prompt ->
                viewModel.createCustomCoverImage(prompt)
                showCreateCoverDialog = false
            },
        )
    }
}

@Composable
private fun SharingPermissionsCard(
    book: Book,
    currentUserId: String?,
    canEditPages: Boolean,
    onAddEditor: () -> Unit,
) {
    val isOwner = currentUserId == book.ownerId

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text("Sharing & permissions", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (canEditPages) {
                            "You can add and remove pages in this book."
                        } else {
                            "View only. Only the owner or invited editors can change pages."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "Owner: ${book.ownerId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${book.sharedEditorIds.size} editor${if (book.sharedEditorIds.size == 1) "" else "s"} invited",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (book.sharedEditorIds.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    book.sharedEditorIds.forEach { editorId ->
                        Text(
                            "Editor: $editorId",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (isOwner) {
                Button(
                    enabled = true,
                    onClick = onAddEditor,
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Text("Add editor")
                }
            }
        }
    }
}

@Composable
private fun ShareEditorDialog(
    isSaving: Boolean,
    existingEditorIds: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var editorUserId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add editor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Editors can add pages, remove pages, and choose the cover for this book.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Enter the other user's account ID. They must have this app installed and be signed in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = editorUserId,
                    onValueChange = { editorUserId = it },
                    label = { Text("Other user's account ID") },
                    supportingText = { Text("This is the Firebase uid for their account.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (existingEditorIds.isNotEmpty()) {
                    Text(
                        "Already invited: ${existingEditorIds.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = editorUserId.isNotBlank() && !isSaving,
                onClick = { onConfirm(editorUserId.trim()) },
            ) { Text("Add editor") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun CoverPickerPreview(
    book: Book,
    pages: List<Page>,
    canEditPages: Boolean,
    onClearCover: () -> Unit,
    onSetGeneratedStyle: (String?) -> Unit,
    onChoosePhoto: () -> Unit,
    onCreateImage: () -> Unit,
) {
    val coverPage = pages.firstOrNull { it.id == book.coverPageId }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverThumbnail(
                    title = book.title,
                    description = book.description,
                    style = book.coverStyle,
                    customCoverUri = book.customCoverUri,
                    customCoverRemoteUrl = book.customCoverRemoteUrl,
                    page = coverPage,
                    modifier = Modifier.size(width = 84.dp, height = 116.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Book cover", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (book.customCoverUri != null || book.customCoverRemoteUrl != null) {
                            book.customCoverPrompt?.let { "Created image: $it" } ?: "Custom photo cover"
                        } else coverPage?.originalFileName?.ifBlank { "Selected page" }
                            ?: if (book.coverStyle == null) "Automatic generated cover" else "Custom generated cover",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (book.coverPageId != null) {
                        TextButton(
                            enabled = canEditPages,
                            onClick = onClearCover,
                        ) { Text("Use generated cover") }
                    }
                    if (!canEditPages) {
                        Text(
                            "View only. Ask the owner to share edit access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text("Make your own cover", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    enabled = canEditPages,
                    onClick = onChoosePhoto,
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Text("Photo")
                }
                Button(
                    enabled = canEditPages,
                    onClick = onCreateImage,
                ) {
                    Icon(Icons.Default.Brush, contentDescription = null)
                    Text("Create")
                }
            }

            Text("Generated cover style", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoverStyleChip(
                    label = "Automatic",
                    selected = book.coverPageId == null && book.coverStyle == null,
                    enabled = canEditPages,
                    onClick = { onSetGeneratedStyle(null) },
                )
                CoverTopic.manualStyles.forEach { topic ->
                    CoverStyleChip(
                        label = topic.label,
                        selected = book.coverPageId == null && book.coverStyle == topic.name,
                        enabled = canEditPages,
                        onClick = { onSetGeneratedStyle(topic.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverStyleChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(enabled = enabled, onClick = onClick) { Text(label) }
    } else {
        TextButton(enabled = enabled, onClick = onClick) { Text(label) }
    }
}

@Composable
private fun CreateCoverImageDialog(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var prompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create cover image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Describe the cover you want. The app will create a styled cover image from your prompt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Cover idea") },
                    placeholder = { Text("Example: cozy cooking notebook with warm colors") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = prompt.isNotBlank() && !isSaving,
                onClick = { onConfirm(prompt.trim()) },
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PageRow(
    page: Page,
    isCover: Boolean,
    canEditPages: Boolean,
    isOwner: Boolean,
    currentUserId: String?,
    onSetCover: () -> Unit,
    onDelete: () -> Unit,
    onRecommendRemoval: () -> Unit,
) {
    val hasRecommendedRemoval = currentUserId != null && currentUserId in page.removalSuggestedByIds
    ListItem(
        headlineContent = {
            Text(
                page.originalFileName.ifBlank { "Page ${page.position + 1}" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(
                    if (page.isSynced) "Backed up" else page.syncError ?: "Waiting for backup",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (page.removalSuggestedByIds.isNotEmpty()) {
                    Text(
                        "${page.removalSuggestedByIds.size} removal recommendation${if (page.removalSuggestedByIds.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
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
                IconButton(onClick = onSetCover, enabled = canEditPages && !isCover) {
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
                if (canEditPages) {
                    if (isOwner) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete page")
                        }
                    } else {
                        IconButton(
                            onClick = onRecommendRemoval,
                            enabled = !hasRecommendedRemoval,
                        ) {
                            Icon(
                                if (hasRecommendedRemoval) Icons.Default.CheckCircle else Icons.Default.Report,
                                contentDescription = if (hasRecommendedRemoval) {
                                    "Removal recommended"
                                } else {
                                    "Recommend removal"
                                },
                                tint = if (hasRecommendedRemoval) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun CoverThumbnail(
    title: String,
    description: String = "",
    style: String? = null,
    customCoverUri: String? = null,
    customCoverRemoteUrl: String? = null,
    page: Page?,
    modifier: Modifier = Modifier,
) {
    val topic = remember(title, description, style) { CoverTopic.resolve(style, title, description) }
    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (page == null) {
                    Brush.linearGradient(topic.colors)
                } else {
                    defaultCoverBrush(title)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (customCoverUri != null || customCoverRemoteUrl != null) {
            AsyncImage(
                model = customCoverUri?.let { java.io.File(it) } ?: customCoverRemoteUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else when (page?.pageType) {
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
                topic.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

private fun defaultCoverBrush(seed: String): Brush {
    val topic = CoverTopic.from(seed, "")
    return Brush.linearGradient(topic.colors)
}

private enum class CoverTopic(
    val label: String,
    val icon: ImageVector,
    val colors: List<Color>,
    val keywords: List<String>,
) {
    MUSIC(
        label = "Music",
        icon = Icons.Default.MusicNote,
        colors = listOf(Color(0xFF2D1B69), Color(0xFFB5179E), Color(0xFFF72585)),
        keywords = listOf("music", "song", "songs", "piano", "guitar", "vocal", "voice", "band", "album", "melody", "chord"),
    ),
    LEARNING(
        label = "Learning",
        icon = Icons.Default.School,
        colors = listOf(Color(0xFF12355B), Color(0xFF2A9D8F), Color(0xFFE9C46A)),
        keywords = listOf("learn", "learning", "study", "school", "lesson", "course", "class", "education", "notes", "exam", "math", "science"),
    ),
    COOKING(
        label = "Cooking",
        icon = Icons.Default.Restaurant,
        colors = listOf(Color(0xFF7A2E20), Color(0xFFE76F51), Color(0xFFF4A261)),
        keywords = listOf("cook", "cooking", "recipe", "recipes", "food", "kitchen", "bake", "baking", "meal", "dinner", "cake"),
    ),
    PERSON(
        label = "Person",
        icon = Icons.Default.Person,
        colors = listOf(Color(0xFF3D315B), Color(0xFF8F6593), Color(0xFFF7B2BD)),
        keywords = listOf("person", "people", "profile", "family", "friend", "baby", "life", "diary", "journal", "biography", "memories"),
    ),
    TRAVEL(
        label = "Travel",
        icon = Icons.Default.Public,
        colors = listOf(Color(0xFF005F73), Color(0xFF0A9396), Color(0xFF94D2BD)),
        keywords = listOf("travel", "trip", "vacation", "journey", "city", "country", "flight", "hotel", "map", "tour"),
    ),
    BUSINESS(
        label = "Business",
        icon = Icons.Default.BusinessCenter,
        colors = listOf(Color(0xFF1D3557), Color(0xFF457B9D), Color(0xFFA8DADC)),
        keywords = listOf("business", "work", "project", "meeting", "office", "client", "finance", "plan", "startup"),
    ),
    FITNESS(
        label = "Fitness",
        icon = Icons.Default.FitnessCenter,
        colors = listOf(Color(0xFF1B4332), Color(0xFF40916C), Color(0xFF95D5B2)),
        keywords = listOf("fitness", "sport", "sports", "gym", "training", "workout", "health", "run", "running", "yoga"),
    ),
    TECH(
        label = "Tech",
        icon = Icons.Default.Code,
        colors = listOf(Color(0xFF0B132B), Color(0xFF3A506B), Color(0xFF5BC0BE)),
        keywords = listOf("code", "coding", "programming", "android", "software", "tech", "computer", "app", "ai", "data"),
    ),
    ART(
        label = "Art",
        icon = Icons.Default.Brush,
        colors = listOf(Color(0xFF4A4E69), Color(0xFF9A8C98), Color(0xFFC9ADA7)),
        keywords = listOf("art", "draw", "drawing", "paint", "painting", "design", "creative", "sketch", "photo", "photos"),
    ),
    LIBRARY(
        label = "Book",
        icon = Icons.Default.AutoStories,
        colors = listOf(Color(0xFF6F3F28), Color(0xFF9B6A43), Color(0xFFD6A15F)),
        keywords = emptyList(),
    );

    companion object {
        val manualStyles: List<CoverTopic>
            get() = listOf(MUSIC, LEARNING, COOKING, PERSON, TRAVEL, BUSINESS, FITNESS, TECH, ART, LIBRARY)

        fun resolve(style: String?, title: String, description: String): CoverTopic =
            style?.let { fromStyleKey(it) } ?: from(title, description)

        private fun fromStyleKey(style: String): CoverTopic? =
            entries.firstOrNull { it.name.equals(style, ignoreCase = true) }

        fun from(title: String, description: String): CoverTopic {
            val text = "$title $description".lowercase()
            return entries.firstOrNull { topic ->
                topic.keywords.any { keyword -> text.contains(keyword) }
            } ?: LIBRARY
        }
    }
}

private fun Context.toPickedFile(uri: Uri): PickedFile? {
    val mimeType = contentResolver.getType(uri) ?: return null
    val displayName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }.orEmpty()
    return PickedFile(uri = uri.toString(), mimeType = mimeType, displayName = displayName)
}
