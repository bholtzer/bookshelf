package com.bihstudio.madafim.presentation.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.bihstudio.madafim.domain.analytics.AnalyticsEvent
import com.bihstudio.madafim.domain.analytics.AnalyticsLogger
import com.bihstudio.madafim.domain.analytics.AnalyticsParam
import com.bihstudio.madafim.domain.model.AppResult
import com.bihstudio.madafim.domain.model.Book
import com.bihstudio.madafim.domain.model.Page
import com.bihstudio.madafim.domain.model.PageType
import com.bihstudio.madafim.domain.model.extractBookInviteCode
import com.bihstudio.madafim.domain.model.toBookInviteCode
import com.bihstudio.madafim.domain.model.toBookInvitePlayStoreLink
import com.bihstudio.madafim.domain.usecase.auth.GetCurrentUserUseCase
import com.bihstudio.madafim.domain.usecase.book.CreateBookEditorInviteUseCase
import com.bihstudio.madafim.domain.usecase.book.DeleteBookUseCase
import com.bihstudio.madafim.domain.usecase.book.GetBookUseCase
import com.bihstudio.madafim.domain.usecase.book.RemoveBookLocallyUseCase
import com.bihstudio.madafim.domain.usecase.book.RenameBookUseCase
import com.bihstudio.madafim.domain.usecase.book.SetBookCoverStyleUseCase
import com.bihstudio.madafim.domain.usecase.book.SetBookCoverUseCase
import com.bihstudio.madafim.domain.usecase.book.SetBookCustomCoverFromUriUseCase
import com.bihstudio.madafim.domain.usecase.book.ShareBookWithEditorUseCase
import com.bihstudio.madafim.domain.usecase.page.AddPageFromUriUseCase
import com.bihstudio.madafim.domain.usecase.page.DeletePageUseCase
import com.bihstudio.madafim.domain.usecase.page.ObservePagesUseCase
import com.bihstudio.madafim.domain.usecase.page.RecommendPageRemovalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BookDetailUiState(
    val book: Book? = null,
    val pages: List<Page> = emptyList(),
    val currentUserId: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isCreatingInvite: Boolean = false,
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
    private val shareBookWithEditor: ShareBookWithEditorUseCase,
    private val createBookEditorInvite: CreateBookEditorInviteUseCase,
    private val deleteBook: DeleteBookUseCase,
    private val removeBookLocally: RemoveBookLocallyUseCase,
    private val observePages: ObservePagesUseCase,
    private val addPageFromUri: AddPageFromUriUseCase,
    private val deletePage: DeletePageUseCase,
    private val recommendPageRemovalUseCase: RecommendPageRemovalUseCase,
    private val analytics: AnalyticsLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _state.asStateFlow()

    fun load(bookId: String) {
        viewModelScope.launch {
            val userId = getCurrentUser()?.uid
            val book = getBook(bookId)
            analytics.trackScreen("book_detail")
            analytics.track(
                AnalyticsEvent.BOOK_EDIT_OPENED,
                mapOf(
                    AnalyticsParam.BOOK_ID to bookId,
                    AnalyticsParam.CAN_EDIT to (userId != null && book?.canEditPages(userId) == true),
                    AnalyticsParam.PAGE_COUNT to (book?.pageCount ?: 0),
                    AnalyticsParam.SOURCE to "detail_load",
                ),
            )
            _state.update { it.copy(book = book, currentUserId = userId) }
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
                is AppResult.Error -> {
                    analytics.track(AnalyticsEvent.BOOK_UPDATED, mapOf(AnalyticsParam.BOOK_ID to book.id, AnalyticsParam.RESULT to "failure"))
                    _state.update { it.copy(isSaving = false, error = result.message) }
                }
                is AppResult.Success -> {
                    analytics.track(AnalyticsEvent.BOOK_UPDATED, mapOf(AnalyticsParam.BOOK_ID to book.id, AnalyticsParam.RESULT to "success", AnalyticsParam.HAS_DESCRIPTION to description.isNotBlank()))
                    _state.update { it.copy(isSaving = false, book = result.data) }
                }
            }
        }
    }

    fun addFiles(files: List<PickedFile>) {
        val user = getCurrentUser() ?: return
        val bookId = _state.value.book?.id ?: return
        if (!_state.value.canEditPages) {
            _state.update { it.copy(error = "Permission Denied: Edit access required.") }
            return
        }
        viewModelScope.launch {
            analytics.track(AnalyticsEvent.PAGES_ADD_STARTED, mapOf(AnalyticsParam.BOOK_ID to bookId, AnalyticsParam.FILE_COUNT to files.size))
            _state.update { it.copy(isSaving = true, error = null) }
            var failures = 0
            for (file in files) {
                val result = addPageFromUri(bookId = bookId, ownerId = user.uid, sourceUri = file.uri, mimeType = file.mimeType, originalFileName = file.displayName)
                if (result is AppResult.Error) {
                    failures++
                    _state.update { it.copy(error = result.message) }
                }
            }
            analytics.track(AnalyticsEvent.PAGES_ADD_RESULT, mapOf(AnalyticsParam.BOOK_ID to bookId, AnalyticsParam.FILE_COUNT to files.size, AnalyticsParam.RESULT to if (failures == 0) "success" else "partial_failure"))
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun deletePage(pageId: String) {
        val user = getCurrentUser() ?: return
        viewModelScope.launch {
            when (val result = deletePage.invoke(pageId, user.uid)) {
                is AppResult.Error -> {
                    analytics.track(AnalyticsEvent.PAGE_DELETE_RESULT, mapOf(AnalyticsParam.RESULT to "failure"))
                    _state.update { it.copy(error = result.message) }
                }
                is AppResult.Success -> analytics.track(AnalyticsEvent.PAGE_DELETE_RESULT, mapOf(AnalyticsParam.RESULT to "success"))
            }
        }
    }

    fun recommendPageRemoval(pageId: String) {
        val user = getCurrentUser() ?: return
        viewModelScope.launch {
            when (val result = recommendPageRemovalUseCase(pageId, user.uid)) {
                is AppResult.Error -> {
                    analytics.track(AnalyticsEvent.PAGE_REMOVE_RECOMMEND_RESULT, mapOf(AnalyticsParam.RESULT to "failure"))
                    _state.update { it.copy(error = result.message) }
                }
                is AppResult.Success -> analytics.track(AnalyticsEvent.PAGE_REMOVE_RECOMMEND_RESULT, mapOf(AnalyticsParam.RESULT to "success"))
            }
        }
    }

    fun setCover(pageId: String?) {
        val bookId = _state.value.book?.id ?: return
        if (!_state.value.canEditPages) {
            _state.update { it.copy(error = "Permission Denied: Edit access required.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            when (val result = setBookCover(bookId, pageId)) {
                is AppResult.Error -> {
                    analytics.track(AnalyticsEvent.COVER_STYLE_RESULT, mapOf(AnalyticsParam.BOOK_ID to bookId, AnalyticsParam.RESULT to "failure", AnalyticsParam.STYLE to "page"))
                    _state.update { it.copy(isSaving = false, error = result.message) }
                }
                is AppResult.Success -> {
                    analytics.track(AnalyticsEvent.COVER_STYLE_RESULT, mapOf(AnalyticsParam.BOOK_ID to bookId, AnalyticsParam.RESULT to "success", AnalyticsParam.STYLE to if (pageId == null) "generated" else "page"))
                    _state.update { it.copy(isSaving = false, book = result.data) }
                }
            }
        }
    }

    fun setGeneratedCoverStyle(style: String?) {
        val bookId = _state.value.book?.id ?: return
        if (!_state.value.canEditPages) {
            _state.update { it.copy(error = "Permission Denied: Edit access required.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            when (val result = setBookCoverStyle(bookId, style)) {
                is AppResult.Error -> {
                    analytics.track(AnalyticsEvent.COVER_STYLE_RESULT, mapOf(AnalyticsParam.BOOK_ID to bookId, AnalyticsParam.RESULT to "failure", AnalyticsParam.STYLE to (style ?: "automatic")))
                    _state.update { it.copy(isSaving = false, error = result.message) }
                }
                is AppResult.Success -> {
                    analytics.track(AnalyticsEvent.COVER_STYLE_RESULT, mapOf(AnalyticsParam.BOOK_ID to bookId, AnalyticsParam.RESULT to "success", AnalyticsParam.STYLE to (style ?: "automatic")))
                    _state.update { it.copy(isSaving = false, book = result.data) }
                }
            }
        }
    }

    fun setCustomCoverFromUri(sourceUri: String) {
        val bookId = _state.value.book?.id ?: return
        if (!_state.value.canEditPages) {
            _state.update { it.copy(error = "Permission Denied: Edit access required.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            when (val result = setBookCustomCoverFromUri(bookId, sourceUri)) {
                is AppResult.Error -> {
                    analytics.track(AnalyticsEvent.COVER_PHOTO_RESULT, mapOf(AnalyticsParam.BOOK_ID to bookId, AnalyticsParam.RESULT to "failure"))
                    _state.update { it.copy(isSaving = false, error = result.message) }
                }
                is AppResult.Success -> {
                    analytics.track(AnalyticsEvent.COVER_PHOTO_RESULT, mapOf(AnalyticsParam.BOOK_ID to bookId, AnalyticsParam.RESULT to "success"))
                    _state.update { it.copy(isSaving = false, book = result.data) }
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
                is AppResult.Error -> {
                    analytics.track(AnalyticsEvent.EDITOR_SHARE_RESULT, mapOf(AnalyticsParam.BOOK_ID to bookId, AnalyticsParam.RESULT to "failure"))
                    _state.update { it.copy(isSaving = false, error = result.message) }
                }
                is AppResult.Success -> {
                    analytics.track(AnalyticsEvent.EDITOR_SHARE_RESULT, mapOf(AnalyticsParam.BOOK_ID to bookId, AnalyticsParam.RESULT to "success"))
                    _state.update { it.copy(isSaving = false, book = result.data) }
                }
            }
        }
    }

    fun createEditorInvite(onInviteReady: (String, String) -> Unit, onError: (String) -> Unit = {}) {
        val userId = getCurrentUser()?.uid
        val book = _state.value.book
        if (userId == null || book == null) {
            val message = "Authentication Required."
            _state.update { it.copy(error = message) }
            onError(message)
            return
        }
        if (_state.value.isCreatingInvite) return
        _state.update { it.copy(isCreatingInvite = true, error = null) }
        viewModelScope.launch {
            when (val result = createBookEditorInvite(book.id, userId)) {
                is AppResult.Error -> {
                    analytics.track(AnalyticsEvent.EDITOR_SHARE_RESULT, mapOf(AnalyticsParam.BOOK_ID to book.id, AnalyticsParam.RESULT to "invite_failure"))
                    _state.update { it.copy(isCreatingInvite = false, error = result.message) }
                    onError(result.message)
                }
                is AppResult.Success -> {
                    analytics.track(AnalyticsEvent.EDITOR_SHARE_RESULT, mapOf(AnalyticsParam.BOOK_ID to book.id, AnalyticsParam.RESULT to "invite_created"))
                    _state.update { it.copy(isCreatingInvite = false, error = null) }
                    onInviteReady(book.title, result.data)
                }
            }
        }
    }

    fun deleteCurrentBook(onDeleted: () -> Unit) {
        val bookId = _state.value.book?.id ?: return
        val userId = getCurrentUser()?.uid ?: return
        val book = _state.value.book ?: return
        viewModelScope.launch {
            val result = if (book.ownerId == userId) deleteBook(bookId) else removeBookLocally(bookId, userId)
            when (result) {
                is AppResult.Error -> {
                    analytics.track(AnalyticsEvent.BOOK_DELETED, mapOf(AnalyticsParam.BOOK_ID to bookId, AnalyticsParam.RESULT to "failure"))
                    _state.update { it.copy(error = result.message) }
                }
                is AppResult.Success -> {
                    analytics.track(AnalyticsEvent.BOOK_DELETED, mapOf(AnalyticsParam.BOOK_ID to bookId, AnalyticsParam.RESULT to "success"))
                    onDeleted()
                }
            }
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
    val clipboardManager = LocalClipboardManager.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var title by remember(state.book?.id) { mutableStateOf(state.book?.title.orEmpty()) }
    var description by remember(state.book?.id) { mutableStateOf(state.book?.description.orEmpty()) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val picked = uris.mapNotNull { context.toPickedFile(it) }
        viewModel.addFiles(picked)
    }
    val coverPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.setCustomCoverFromUri(uri.toString())
    }

    LaunchedEffect(bookId) {
        viewModel.load(bookId)
    }

    Scaffold(
        containerColor = Color(0xFF050B18),
        topBar = {
            TopAppBar(
                title = { Text("MODULE CONFIG", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                },
                actions = {
                    if (state.book != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Purge Module", tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White)
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            HiTechDetailBackdrop()
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                state.book == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("MODULE NOT FOUND", color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp) }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        DetailSectionCard(title = "IDENTIFICATION") {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                HiTechTextField(value = title, onValueChange = { title = it }, label = "Module Name")
                                HiTechTextField(value = description, onValueChange = { description = it }, label = "Module Metadata", minLines = 2)
                                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        enabled = title.isNotBlank() && !state.isSaving,
                                        onClick = { viewModel.save(title, description) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("UPDATE", fontWeight = FontWeight.Bold) }
                                    Button(
                                        enabled = !state.isSaving && state.canEditPages,
                                        onClick = { filePicker.launch(arrayOf("image/*", "application/pdf")) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("ADD CHUNKS", fontWeight = FontWeight.Bold)
                                    }
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
                                onShareInvite = {
                                    viewModel.createEditorInvite(
                                        onInviteReady = { t, link -> context.shareBookInvite(t, link) },
                                        onError = { context.showToast(it) }
                                    )
                                },
                                onCopyInviteCode = {
                                    viewModel.createEditorInvite(
                                        onInviteReady = { _, link ->
                                            val code = link.extractBookInviteCode().orEmpty()
                                            clipboardManager.setText(AnnotatedString(code))
                                            context.showToast("Protocol code copied to clipboard.")
                                        },
                                        onError = { context.showToast(it) }
                                    )
                                },
                                isCreatingInvite = state.isCreatingInvite,
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
                                onSetGeneratedStyle = { viewModel.setGeneratedCoverStyle(it) },
                                onChoosePhoto = { coverPhotoPicker.launch(arrayOf("image/*")) },
                            )
                        }
                    }

                    item {
                        Text("DATA SEGMENTS", style = MaterialTheme.typography.labelSmall, letterSpacing = 3.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                    }

                    if (state.pages.isEmpty()) {
                        item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("NO DATA SEGMENTS DETECTED", color = Color.White.copy(alpha = 0.3f), letterSpacing = 2.sp) } }
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
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        val isOwner = state.book?.ownerId == state.currentUserId
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Color(0xFF0D1424),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.7f),
            title = { Text(if (isOwner) "PURGE MODULE?" else "DISCONNECT MODULE?") },
            text = { Text(if (isOwner) "Permanent deletion of all data segments from local and cloud storage." else "Remove local access link to this module.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCurrentBook(onBack) }) { Text(if (isOwner) "PURGE" else "DISCONNECT", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("CANCEL", color = Color.White.copy(alpha = 0.5f)) }
            },
        )
    }

    if (showShareDialog) {
        ShareEditorDialog(
            isSaving = state.isSaving,
            existingEditorIds = state.book?.sharedEditorIds.orEmpty(),
            onDismiss = { showShareDialog = false },
            onConfirm = { viewModel.shareWithEditor(it); showShareDialog = false },
        )
    }
}

@Composable
private fun HiTechDetailBackdrop() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(400.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 100.dp)
                .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), Color.Transparent)))
        )
    }
}

@Composable
private fun DetailSectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun HiTechTextField(value: String, onValueChange: (String) -> Unit, label: String, minLines: Int = 1) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
            focusedContainerColor = Color.White.copy(alpha = 0.03f),
            unfocusedContainerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun SharingPermissionsCard(
    book: Book,
    currentUserId: String?,
    canEditPages: Boolean,
    onAddEditor: () -> Unit,
    onShareInvite: () -> Unit,
    onCopyInviteCode: () -> Unit,
    isCreatingInvite: Boolean,
) {
    val isOwner = currentUserId == book.ownerId
    val shareCode = remember(book.ownerId, book.id) { "${book.ownerId}:${book.id}".toBookInviteCode() }

    DetailSectionCard(title = "ACCESS PROTOCOLS") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Column {
                    Text(if (isOwner) "STATUS: ORIGINATOR" else if (canEditPages) "STATUS: AUTHORIZED" else "STATUS: READ-ONLY", style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${book.sharedEditorIds.size} ACTIVE PEERS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                }
            }

            if (isOwner) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("NODE ACCESS CODE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                            Text(shareCode, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onCopyInviteCode, enabled = !isCreatingInvite) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCreatingInvite,
                    onClick = onShareInvite,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isCreatingInvite) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    else {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("BROADCAST ACCESS LINK", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCreatingInvite,
                    onClick = onAddEditor,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.width(8.dp))
                    Text("MANUAL PEER AUTHORIZATION", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun CoverPickerPreview(
    book: Book,
    pages: List<Page>,
    canEditPages: Boolean,
    onClearCover: () -> Unit,
    onSetGeneratedStyle: (String?) -> Unit,
    onChoosePhoto: () -> Unit,
) {
    val coverPage = pages.firstOrNull { it.id == book.coverPageId }
    
    DetailSectionCard(title = "VISUAL SIGNATURE") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverThumbnail(
                    title = book.title,
                    description = automaticCoverSubject(book.description, pages),
                    style = book.coverStyle,
                    customCoverUri = book.customCoverUri,
                    customCoverRemoteUrl = book.customCoverRemoteUrl,
                    page = coverPage,
                    modifier = Modifier.size(width = 100.dp, height = 140.dp).clip(RoundedCornerShape(12.dp)),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canEditPages,
                        onClick = onChoosePhoto,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) { Text("UPLOAD IMAGE") }
                    if (book.coverPageId != null) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canEditPages,
                            onClick = onClearCover,
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("USE SYSTEM GEN", color = Color.White) }
                    }
                }
            }

            Text("ALGORITHM PRESET", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), letterSpacing = 1.sp)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CoverStyleChip(label = "AUTO", icon = Icons.Default.AutoAwesome, selected = book.coverPageId == null && book.coverStyle == null, enabled = canEditPages, onClick = { onSetGeneratedStyle(null) })
                CoverTopic.manualStyles.forEach { topic ->
                    CoverStyleChip(label = topic.label, icon = topic.icon, selected = book.coverPageId == null && book.coverStyle == topic.name, enabled = canEditPages, onClick = { onSetGeneratedStyle(topic.name) })
                }
            }
        }
    }
}

@Composable
private fun CoverStyleChip(label: String, icon: ImageVector, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(8.dp),
        border = if (selected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
        }
    }
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
    Surface(
        color = Color.White.copy(alpha = 0.03f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CoverThumbnail(title = page.originalFileName, page = page, modifier = Modifier.size(50.dp, 65.dp).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1f)) {
                Text(page.originalFileName.ifBlank { "SEGMENT" }.uppercase(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (page.isSynced) "SYNCED" else "PENDING", style = MaterialTheme.typography.labelSmall, color = if (page.isSynced) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                if (page.removalSuggestedByIds.isNotEmpty()) {
                    Text("CONFLICT: ${page.removalSuggestedByIds.size} PURGE REQS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Row {
                IconButton(onClick = onSetCover, enabled = canEditPages && !isCover) {
                    Icon(if (isCover) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = if (isCover) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f))
                }
                if (canEditPages) {
                    IconButton(onClick = if (isOwner) onDelete else onRecommendRemoval, enabled = !hasRecommendedRemoval) {
                        Icon(if (isOwner) Icons.Default.Delete else Icons.Default.Report, contentDescription = null, tint = if (hasRecommendedRemoval) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
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
    val backgroundModifier = if (page == null) {
        Modifier.background(Brush.linearGradient(topic.colors))
    } else {
        Modifier.background(Color(0xFF0F172A))
    }
    
    Box(
        modifier = modifier.then(backgroundModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (customCoverUri != null || customCoverRemoteUrl != null) {
            AsyncImage(model = customCoverUri?.let { File(it) } ?: customCoverRemoteUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else when (page?.pageType) {
            PageType.IMAGE -> AsyncImage(model = page.remoteUrl ?: page.localUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            PageType.PDF -> Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
            null -> Icon(topic.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
        }
    }
}

private fun automaticCoverSubject(description: String, pages: List<Page>): String =
    buildString {
        append(description)
        pages.take(8).forEach { page -> append(' '); append(page.originalFileName); append(' '); append(page.pageType.name.lowercase()) }
    }

@Composable
private fun ShareEditorDialog(isSaving: Boolean, existingEditorIds: List<String>, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var shareTarget by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D1424),
        titleContentColor = Color.White,
        title = { Text("AUTHORIZE PEER", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Input peer ID or handshake link to grant access to this module archive.", color = Color.White.copy(alpha = 0.7f))
                HiTechTextField(value = shareTarget, onValueChange = { shareTarget = it }, label = "Peer ID / Link")
            }
        },
        confirmButton = {
            Button(enabled = shareTarget.isNotBlank() && !isSaving, onClick = { onConfirm(shareTarget.trim()) }, shape = RoundedCornerShape(8.dp)) { Text("AUTHORIZE") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Color.White.copy(alpha = 0.5f)) }
        },
    )
}

private enum class CoverTopic(val label: String, val icon: ImageVector, val colors: List<Color>, val keywords: List<String>) {
    MUSIC("Audio", Icons.Default.MusicNote, listOf(Color(0xFF00F2FF), Color(0xFF7000FF)), listOf("music", "song")),
    LEARNING("Cognition", Icons.Default.School, listOf(Color(0xFF00F2FF), Color(0xFF00696D)), listOf("learn", "study")),
    COOKING("Alchemy", Icons.Default.Restaurant, listOf(Color(0xFFFF00E5), Color(0xFF9C4092)), listOf("cook", "recipe")),
    LIFE("Persona", Icons.Default.Person, listOf(Color(0xFF7000FF), Color(0xFFFF00E5)), listOf("diary", "life")),
    TRAVEL("Nexus", Icons.Default.Public, listOf(Color(0xFF00F2FF), Color(0xFF2BAAA7)), listOf("travel", "trip")),
    TECH("Protocol", Icons.Default.Code, listOf(Color(0xFF7000FF), Color(0xFF00F2FF)), listOf("code", "android")),
    ART("Creative", Icons.Default.Brush, listOf(Color(0xFFFF00E5), Color(0xFF7000FF)), listOf("art", "design")),
    BUSINESS("Enterprise", Icons.Default.BusinessCenter, listOf(Color(0xFF1D3557), Color(0xFFA8DADC)), listOf("work", "business")),
    FITNESS("Physical", Icons.Default.FitnessCenter, listOf(Color(0xFF1B4332), Color(0xFF95D5B2)), listOf("fitness", "gym")),
    LIBRARY("Archive", Icons.Default.Book, listOf(Color(0xFF00F2FF), Color(0xFF7000FF)), emptyList());

    companion object {
        val manualStyles: List<CoverTopic> get() = entries.toList()
        fun resolve(style: String?, title: String, description: String): CoverTopic = style?.let { s -> entries.firstOrNull { it.name.equals(s, true) } } ?: from(title, description)
        fun from(title: String, description: String): CoverTopic {
            val text = "$title $description".lowercase()
            return entries.firstOrNull { t -> t.keywords.any { text.contains(it) } } ?: LIBRARY
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

private fun Context.shareBookInvite(title: String, inviteLink: String) {
    val code = inviteLink.extractBookInviteCode().orEmpty()
    val installLink = code.toBookInvitePlayStoreLink(packageName, inviteLink)
    val text = "ACCESS PROTOCOL INITIATED\nModule: ${title.ifBlank { "Unidentified" }}\nLink: $inviteLink\nCode: $code\nInstall: $installLink"
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    startActivity(Intent.createChooser(intent, "BROADCAST INVITE"))
}

private fun Context.showToast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
