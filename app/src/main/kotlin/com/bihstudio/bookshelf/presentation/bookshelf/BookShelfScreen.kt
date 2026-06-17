package com.bihstudio.bookshelf.presentation.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.bihstudio.bookshelf.domain.analytics.AnalyticsEvent
import com.bihstudio.bookshelf.domain.analytics.AnalyticsLogger
import com.bihstudio.bookshelf.domain.analytics.AnalyticsParam
import com.bihstudio.bookshelf.domain.model.AppResult
import com.bihstudio.bookshelf.domain.model.Book
import com.bihstudio.bookshelf.domain.model.Page
import com.bihstudio.bookshelf.domain.model.PageType
import com.bihstudio.bookshelf.domain.usecase.auth.GetCurrentUserUseCase
import com.bihstudio.bookshelf.domain.usecase.auth.RestoreUserLibraryUseCase
import com.bihstudio.bookshelf.domain.usecase.book.AcceptBookEditorInviteUseCase
import com.bihstudio.bookshelf.domain.usecase.book.CreateBookUseCase
import com.bihstudio.bookshelf.domain.usecase.book.ObserveBooksUseCase
import com.bihstudio.bookshelf.domain.usecase.page.ObservePagesByIdsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookShelfUiState(
    val books: List<Book> = emptyList(),
    val coverPages: Map<String, Page> = emptyMap(),
    val editorShareCode: String? = null,
    val isLoading: Boolean = true,
    val isJoiningInvite: Boolean = false,
    val joinInviteError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookShelfViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val observeBooks: ObserveBooksUseCase,
    private val observePagesByIds: ObservePagesByIdsUseCase,
    private val createBook: CreateBookUseCase,
    private val acceptBookEditorInvite: AcceptBookEditorInviteUseCase,
    private val restoreUserLibrary: RestoreUserLibraryUseCase,
    private val analytics: AnalyticsLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(BookShelfUiState())
    val uiState: StateFlow<BookShelfUiState> = _state.asStateFlow()
    private var lastLoggedBookCount: Int? = null

    init {
        val user = getCurrentUser()
        if (user == null) {
            analytics.trackScreen("bookshelf_signed_out")
            _state.update { it.copy(isLoading = false) }
        } else {
            analytics.setUserId(user.uid)
            analytics.trackScreen("bookshelf")
            viewModelScope.launch {
                restoreUserLibrary(user.uid)
            }
            viewModelScope.launch {
                observeBooks(user.uid)
                    .flatMapLatest { books -> books.withCoverPages(user.editorShareCode) }
                    .collect { nextState ->
                        if (lastLoggedBookCount != nextState.books.size) {
                            lastLoggedBookCount = nextState.books.size
                            analytics.track(
                                AnalyticsEvent.SHELF_LOADED,
                                mapOf(
                                    AnalyticsParam.BOOK_COUNT to nextState.books.size,
                                ),
                            )
                        }
                        _state.value = nextState
                    }
            }
        }
    }

    fun createBook(title: String, description: String) {
        val user = getCurrentUser() ?: return
        viewModelScope.launch {
            analytics.track(
                AnalyticsEvent.BOOK_CREATE_STARTED,
                mapOf(AnalyticsParam.HAS_DESCRIPTION to description.isNotBlank()),
            )
            when (val result = createBook(user.uid, title, description)) {
                is AppResult.Error -> analytics.track(
                    AnalyticsEvent.BOOK_CREATE_RESULT,
                    mapOf(AnalyticsParam.RESULT to "failure"),
                )
                is AppResult.Success -> analytics.track(
                    AnalyticsEvent.BOOK_CREATE_RESULT,
                    mapOf(
                        AnalyticsParam.RESULT to "success",
                        AnalyticsParam.BOOK_ID to result.data.id,
                        AnalyticsParam.HAS_DESCRIPTION to description.isNotBlank(),
                    ),
                )
            }
        }
    }

    fun joinSharedBook(inviteText: String, onJoined: (String) -> Unit) {
        val user = getCurrentUser() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isJoiningInvite = true, joinInviteError = null) }
            when (val result = acceptBookEditorInvite(inviteText, user.uid)) {
                is AppResult.Error -> {
                    analytics.track(
                        AnalyticsEvent.EDITOR_SHARE_RESULT,
                        mapOf(AnalyticsParam.RESULT to "invite_accept_failure"),
                    )
                    _state.update {
                        it.copy(isJoiningInvite = false, joinInviteError = result.message)
                    }
                }
                is AppResult.Success -> {
                    analytics.track(
                        AnalyticsEvent.EDITOR_SHARE_RESULT,
                        mapOf(
                            AnalyticsParam.RESULT to "invite_accepted",
                            AnalyticsParam.BOOK_ID to result.data.id,
                        ),
                    )
                    _state.update { it.copy(isJoiningInvite = false, joinInviteError = null) }
                    onJoined(result.data.id)
                }
            }
        }
    }

    private fun List<Book>.withCoverPages(editorShareCode: String): Flow<BookShelfUiState> {
        val coverIds = mapNotNull { it.coverPageId }.distinct()
        if (coverIds.isEmpty()) {
            return flowOf(
                BookShelfUiState(
                    books = this,
                    editorShareCode = editorShareCode,
                    isLoading = false,
                )
            )
        }
        return observePagesByIds(coverIds).map { pages ->
            BookShelfUiState(
                books = this,
                coverPages = pages.associateBy { it.id },
                editorShareCode = editorShareCode,
                isLoading = false,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookShelfScreen(
    onOpenBook: (String) -> Unit,
    onEditBook: (String) -> Unit,
    pendingInviteText: String? = null,
    onInviteConsumed: () -> Unit = {},
    viewModel: BookShelfViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var initialJoinText by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(pendingInviteText) {
        if (!pendingInviteText.isNullOrBlank()) {
            initialJoinText = pendingInviteText
            showJoinDialog = true
            onInviteConsumed()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "New book")
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            LibraryBackdrop()
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.books.isEmpty() -> EmptyShelf(
                    editorShareCode = state.editorShareCode,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    onCreateBook = { showCreateDialog = true },
                    onJoinBook = { showJoinDialog = true },
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 126.dp),
                    contentPadding = PaddingValues(start = 16.dp, top = 22.dp, end = 16.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        ShelfHeader(
                            bookCount = state.books.size,
                            editorShareCode = state.editorShareCode,
                            onJoinBook = { showJoinDialog = true },
                        )
                    }
                    items(state.books, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            coverPage = book.coverPageId?.let { state.coverPages[it] },
                            onOpen = { onOpenBook(book.id) },
                            onEdit = { onEditBook(book.id) },
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        BookDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, description ->
                viewModel.createBook(title, description)
                showCreateDialog = false
            },
        )
    }

    if (showJoinDialog) {
        JoinSharedBookDialog(
            isJoining = state.isJoiningInvite,
            error = state.joinInviteError,
            initialText = initialJoinText,
            onDismiss = { showJoinDialog = false },
            onConfirm = { inviteText ->
                viewModel.joinSharedBook(inviteText) {
                    showJoinDialog = false
                    onEditBook(it)
                }
            },
        )
    }
}

@Composable
private fun LibraryBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFF8EF),
                        Color(0xFFF2DEC9),
                        Color(0xFFE4C4A5),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.Bottom,
        ) {
            repeat(5) {
                Spacer(Modifier.height(104.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF9B6A43),
                                    Color(0xFF6F3F28),
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.18f)),
                )
            }
        }
    }
}

@Composable
private fun ShelfHeader(
    bookCount: Int,
    editorShareCode: String?,
    onJoinBook: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF6F3F28),
                        Color(0xFF9B6A43),
                        Color(0xFF6F3F28),
                    ),
                ),
            ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.AutoStories,
                contentDescription = null,
                tint = Color(0xFFFFE8C7),
                modifier = Modifier.size(40.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "Your library",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFF3E2),
                )
                Text(
                    "$bookCount ${if (bookCount == 1) "book" else "books"} ready to open",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFE8C7),
                )
                editorShareCode?.let { code ->
                    Text(
                        "Your editor code: $code",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFFFF3E2),
                    )
                }
                TextButton(onClick = onJoinBook) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null)
                    Text("Join shared book")
                }
            }
        }
    }
}

@Composable
private fun EmptyShelf(
    editorShareCode: String?,
    modifier: Modifier = Modifier,
    onCreateBook: () -> Unit,
    onJoinBook: () -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 156.dp, height = 214.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(defaultCoverBrush("BookShelf")),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(58.dp),
                )
            }
            MiniShelf()
            Text(
                "Start your first book",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Add pages from images or PDFs, then choose the page that becomes the cover.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            editorShareCode?.let { code ->
                AssistChip(
                    onClick = {},
                    label = { Text("Editor code: $code") },
                )
            }
            AssistChip(
                onClick = onCreateBook,
                label = { Text("Create book") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            )
            AssistChip(
                onClick = onJoinBook,
                label = { Text("Join shared book") },
                leadingIcon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun JoinSharedBookDialog(
    isJoining: Boolean,
    error: String?,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var inviteText by remember(initialText) { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join shared book") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Paste the book share code or invite link from the owner. After joining, the book will appear on your shelf and you can add pages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = inviteText,
                    onValueChange = { inviteText = it },
                    label = { Text("Book share code or link") },
                    placeholder = { Text("BK-ABC123DE4567") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = inviteText.isNotBlank() && !isJoining,
                onClick = { onConfirm(inviteText.trim()) },
            ) {
                if (isJoining) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Join")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun BookCard(
    book: Book,
    coverPage: Page?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            BookCover(book = book, coverPage = coverPage)
            Text(
                book.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${book.pageCount} pages", style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit book")
                }
            }
        }
    }
}

@Composable
private fun BookCover(book: Book, coverPage: Page?) {
    val topic = remember(book.title, book.description) {
        CoverTopic.resolve(book.coverStyle, book.title, book.description)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.66f)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(topic.colors)),
    ) {
        if (book.customCoverUri != null || book.customCoverRemoteUrl != null) {
            AsyncImage(
                model = book.customCoverUri?.let(::File) ?: book.customCoverRemoteUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (coverPage?.pageType == PageType.IMAGE) {
            AsyncImage(
                model = coverPage.remoteUrl ?: coverPage.localUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (coverPage?.pageType == PageType.PDF) {
            PdfCoverLabel(modifier = Modifier.align(Alignment.Center))
        } else {
            DefaultCoverLabel(
                title = book.title,
                description = book.description,
                style = book.coverStyle,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(16.dp)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.28f),
                            Color.Black.copy(alpha = 0.10f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(10.dp)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.38f),
                            Color.White.copy(alpha = 0.12f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
                .fillMaxWidth(0.62f)
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.36f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 18.dp, end = 10.dp)
                .width(18.dp)
                .height(54.dp)
                .background(Color.White.copy(alpha = 0.22f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.16f)),
        )
    }
}

@Composable
private fun MiniShelf() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            val spines = listOf(
                Color(0xFF6F3F28) to 54.dp,
                Color(0xFF51664A) to 42.dp,
                Color(0xFF9B6A43) to 62.dp,
                Color(0xFF315C61) to 48.dp,
                Color(0xFFA44A3F) to 58.dp,
            )
            spines.forEach { (color, height) ->
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(height)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(color),
                )
            }
        }
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF7A5634)),
        )
    }
}

@Composable
private fun DefaultCoverLabel(
    title: String,
    description: String,
    style: String?,
    modifier: Modifier = Modifier,
) {
    val topic = remember(title, description, style) { CoverTopic.resolve(style, title, description) }
    Column(
        modifier = modifier.padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopicMark(topic = topic)
        Text(
            title.ifBlank { "Untitled" },
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            topic.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PdfCoverLabel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.PictureAsPdf,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(48.dp),
        )
        Text(
            "PDF",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun defaultCoverBrush(seed: String): Brush {
    val topic = CoverTopic.from(seed, "")
    return Brush.linearGradient(topic.colors)
}

@Composable
private fun TopicMark(topic: CoverTopic) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            topic.icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(34.dp),
        )
    }
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
        icon = Icons.Default.Book,
        colors = listOf(Color(0xFF6F3F28), Color(0xFF9B6A43), Color(0xFFD6A15F)),
        keywords = emptyList(),
    );

    companion object {
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

@Composable
private fun BookDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New book") },
        text = {
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onConfirm(title, description) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
