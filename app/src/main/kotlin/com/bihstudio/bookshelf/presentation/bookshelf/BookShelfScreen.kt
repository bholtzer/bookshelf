package com.bihstudio.bookshelf.presentation.bookshelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.bihstudio.bookshelf.domain.model.extractBookInviteCode
import com.bihstudio.bookshelf.domain.usecase.auth.GetCurrentUserUseCase
import com.bihstudio.bookshelf.domain.usecase.auth.RestoreUserLibraryUseCase
import com.bihstudio.bookshelf.domain.usecase.book.AcceptBookEditorInviteUseCase
import com.bihstudio.bookshelf.domain.usecase.book.CreateBookUseCase
import com.bihstudio.bookshelf.domain.usecase.book.ObserveBooksUseCase
import com.bihstudio.bookshelf.domain.usecase.page.ObserveFirstPagesForBooksUseCase
import com.bihstudio.bookshelf.domain.usecase.page.ObservePagesByIdsUseCase
import com.bihstudio.bookshelf.domain.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class BookShelfUiState(
    val books: List<Book> = emptyList(),
    val coverPages: Map<String, Page> = emptyMap(),
    val firstPages: Map<String, Page> = emptyMap(),
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
    private val observeFirstPagesForBooks: ObserveFirstPagesForBooksUseCase,
    private val createBook: CreateBookUseCase,
    private val acceptBookEditorInvite: AcceptBookEditorInviteUseCase,
    private val restoreUserLibrary: RestoreUserLibraryUseCase,
    private val subscriptions: SubscriptionRepository,
    private val analytics: AnalyticsLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(BookShelfUiState())
    val uiState: StateFlow<BookShelfUiState> = _state.asStateFlow()
    val subscriptionState = subscriptions.state

    init {
        val user = getCurrentUser()
        if (user == null) {
            _state.update { it.copy(isLoading = false) }
        } else {
            analytics.setUserId(user.uid)
            viewModelScope.launch {
                restoreUserLibrary(user.uid)
            }
            viewModelScope.launch {
                observeBooks(user.uid)
                    .flatMapLatest { books -> books.withCoverPages(user.editorShareCode) }
                    .collect { nextState -> _state.value = nextState }
            }
        }
    }

    fun createBook(title: String, description: String) {
        val user = getCurrentUser() ?: return
        viewModelScope.launch {
            createBook(user.uid, title, description)
        }
    }

    fun joinSharedBook(inviteText: String, onJoined: (String) -> Unit) {
        val user = getCurrentUser() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isJoiningInvite = true, joinInviteError = null) }
            when (val result = acceptBookEditorInvite(inviteText, user.uid)) {
                is AppResult.Error -> {
                    _state.update { it.copy(isJoiningInvite = false, joinInviteError = result.message) }
                }
                is AppResult.Success -> {
                    _state.update { it.copy(isJoiningInvite = false, joinInviteError = null) }
                    onJoined(result.data.id)
                }
            }
        }
    }

    private fun List<Book>.withCoverPages(editorShareCode: String): Flow<BookShelfUiState> {
        val coverIds = mapNotNull { it.coverPageId }.distinct()
        val bookIds = map { it.id }.distinct()
        val coverPagesFlow = if (coverIds.isEmpty()) flowOf(emptyMap()) else observePagesByIds(coverIds).map { pages -> pages.associateBy { it.id } }
        val firstPagesFlow = if (bookIds.isEmpty()) flowOf(emptyMap()) else observeFirstPagesForBooks(bookIds)
        return combine(coverPagesFlow, firstPagesFlow) { coverPages, firstPages ->
            BookShelfUiState(books = this, coverPages = coverPages, firstPages = firstPages, editorShareCode = editorShareCode, isLoading = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookShelfScreen(
    onOpenBook: (String) -> Unit,
    onEditBook: (String) -> Unit,
    onUpgrade: () -> Unit,
    pendingInviteText: String? = null,
    onInviteConsumed: () -> Unit = {},
    viewModel: BookShelfViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val subscription by viewModel.subscriptionState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var initialJoinText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val inviteCodeFromSearch = remember(searchQuery) { searchQuery.extractBookInviteCode() }
    val visibleBooks = remember(state.books, searchQuery) {
        if (searchQuery.isBlank() || inviteCodeFromSearch != null) state.books else state.books.filter { book ->
            book.title.contains(searchQuery, ignoreCase = true) || book.description.contains(searchQuery, ignoreCase = true)
        }
    }

    LaunchedEffect(pendingInviteText) {
        if (!pendingInviteText.isNullOrBlank()) {
            initialJoinText = pendingInviteText
            showJoinDialog = true
            onInviteConsumed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (subscription.isPro || state.books.size < FREE_BOOK_LIMIT) showCreateDialog = true
                    else onUpgrade()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) { Icon(Icons.Default.Add, contentDescription = "Add") }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            HiTechShelfBackdrop()
            
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                state.books.isEmpty() -> EmptyShelf(state.editorShareCode, Modifier.fillMaxSize().padding(32.dp), { showCreateDialog = true }, { showJoinDialog = true })
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        MusicAppHeader(state.books.size, subscription.isPro, { showJoinDialog = true }, onUpgrade)
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        LibrarySearchField(searchQuery, { searchQuery = it }, inviteCodeFromSearch != null)
                    }
                    
                    if (inviteCodeFromSearch != null) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            InviteSearchCard(inviteCodeFromSearch, state.isJoiningInvite, state.joinInviteError, { viewModel.joinSharedBook(searchQuery) { searchQuery = ""; onEditBook(it) } }, { searchQuery = "" })
                        }
                    }
                    
                    itemsIndexed(if (inviteCodeFromSearch == null) visibleBooks else state.books, key = { _, book -> book.id }) { index, book ->
                        var isVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { delay(index * 40L); isVisible = true }
                        AnimatedVisibility(visible = isVisible, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 }) {
                            AlbumBookCard(book, state.coverPages[book.coverPageId], state.firstPages[book.id], { onOpenBook(book.id) }, { onEditBook(book.id) })
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) BookDialog({ showCreateDialog = false }, { t, d -> viewModel.createBook(t, d); showCreateDialog = false })
    if (showJoinDialog) JoinSharedBookDialog(state.isJoiningInvite, state.joinInviteError, initialJoinText, { showJoinDialog = false }, { viewModel.joinSharedBook(it) { b -> showJoinDialog = false; onEditBook(b) } })
}

@Composable
private fun HiTechShelfBackdrop() {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0xFF050B18)))
        val infiniteTransition = rememberInfiniteTransition(label = "bg")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.05f, targetValue = 0.15f,
            animationSpec = infiniteRepeatable(tween(5000, easing = LinearOutSlowInEasing), RepeatMode.Reverse), label = "alpha"
        )
        Box(Modifier.size(600.dp).align(Alignment.TopEnd).offset(x = 200.dp, y = (-200).dp).background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = alpha), Color.Transparent))))
        Box(Modifier.fillMaxSize().drawWithCache {
            onDrawWithContent {
                drawContent()
                val step = 40.dp.toPx()
                for (x in 0..(size.width / step).toInt()) drawLine(Color.White.copy(alpha = 0.02f), Offset(x * step, 0f), Offset(x * step, size.height), 0.5.dp.toPx())
                for (y in 0..(size.height / step).toInt()) drawLine(Color.White.copy(alpha = 0.02f), Offset(0f, y * step), Offset(size.width, y * step), 0.5.dp.toPx())
            }
        })
    }
}

@Composable
private fun MusicAppHeader(count: Int, isPro: Boolean, onJoin: () -> Unit, onUpgrade: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("DATA ARCHIVE", style = MaterialTheme.typography.labelSmall, letterSpacing = 3.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            Text("Your Collection", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = (-1).sp)
            Text("$count MODULES SYNCED", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.4f), letterSpacing = 1.sp)
        }
        Surface(onClick = if (isPro) onJoin else onUpgrade, color = if (isPro) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f), shape = CircleShape, border = BorderStroke(1.dp, if (isPro) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f))) {
            Icon(if (isPro) Icons.Default.Verified else Icons.Default.WorkspacePremium, contentDescription = if (isPro) "Pro account" else "Upgrade to Pro", tint = if (isPro) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.padding(12.dp))
        }
        Spacer(Modifier.width(8.dp))
        Surface(onClick = onJoin, color = Color.White.copy(alpha = 0.05f), shape = CircleShape, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))) {
            Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun LibrarySearchField(query: String, onQueryChange: (String) -> Unit, isInvite: Boolean) {
    Surface(color = Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))) {
        OutlinedTextField(
            value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search local storage...", color = Color.White.copy(alpha = 0.3f)) },
            leadingIcon = { Icon(if (isInvite) Icons.Default.CloudDownload else Icons.Default.Search, contentDescription = null, tint = if (query.isNotBlank()) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
    }
}

@Composable
private fun AlbumBookCard(book: Book, cover: Page?, first: Page?, onOpen: () -> Unit, onEdit: () -> Unit) {
    var isOpening by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isOpening) 1.1f else 1f, tween(400), label = "scale")
    val alpha by animateFloatAsState(if (isOpening) 0f else 1f, tween(400, delayMillis = 200), label = "alpha")
    
    LaunchedEffect(isOpening) { if (isOpening) { delay(600); onOpen(); isOpening = false } }

    Column(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { isOpening = true },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).shadow(16.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0F172A))) {
            BookCoverContent(book, cover, first)
            // Play overlay icon for music feel
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(40.dp).border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape).padding(8.dp))
            }
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(book.title.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${book.pageCount} CHUNKS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), letterSpacing = 1.sp)
                IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.MoreVert, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

@Composable
private fun BookCoverContent(book: Book, cover: Page?, first: Page?) {
    val topic = remember(book.title, book.description) { CoverTopic.resolve(book.coverStyle, book.title, "${book.description}") }
    if (book.customCoverUri != null || book.customCoverRemoteUrl != null) {
        AsyncImage(model = book.customCoverUri?.let { File(it) } ?: book.customCoverRemoteUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else if (cover?.pageType == PageType.IMAGE) {
        AsyncImage(model = cover.remoteUrl ?: cover.localUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(topic.colors)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(topic.icon, null, tint = Color.White, modifier = Modifier.size(32.dp))
                Text(topic.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), letterSpacing = 2.sp)
            }
        }
    }
}

@Composable
private fun EmptyShelf(code: String?, modifier: Modifier, onCreate: () -> Unit, onJoin: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.LibraryMusic, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), modifier = Modifier.size(100.dp))
        Spacer(Modifier.height(24.dp))
        Text("ARCHIVE COLD", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
        Text("Initialize module storage to begin.", color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onCreate, shape = RoundedCornerShape(8.dp)) { Text("NEW MODULE") }
    }
}

@Composable
private fun InviteSearchCard(code: String, isJoining: Boolean, error: String?, onJoin: () -> Unit, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("REMOTE LINK DETECTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
            Text(code, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onJoin, enabled = !isJoining) { if (isJoining) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("SYNC") }
                TextButton(onClick = onClear) { Text("ABORT", color = Color.White.copy(alpha = 0.4f)) }
            }
        }
    }
}

@Composable
private fun JoinSharedBookDialog(isJoining: Boolean, error: String?, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFF0D1424), title = { Text("SYNC LINK", color = Color.White) }, text = {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Handshake Code") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
    }, confirmButton = { Button(onClick = { onConfirm(text) }) { Text("LINK") } })
}

@Composable
private fun BookDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var t by remember { mutableStateOf("") }
    var d by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFF0D1424), title = { Text("NEW MODULE", color = Color.White) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = t, onValueChange = { t = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            OutlinedTextField(value = d, onValueChange = { d = it }, label = { Text("Metadata") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
        }
    }, confirmButton = { Button(onClick = { onConfirm(t, d) }) { Text("COMPILE") } })
}

@Composable
private fun MiniShelf() { /* Placeholder for compatibility */ }

private const val FREE_BOOK_LIMIT = 3

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
        fun resolve(style: String?, title: String, description: String): CoverTopic = style?.let { s -> entries.firstOrNull { it.name.equals(s, true) } } ?: from(title, description)
        fun from(title: String, description: String): CoverTopic {
            val text = "$title $description".lowercase()
            return entries.firstOrNull { t -> t.keywords.any { text.contains(it) } } ?: LIBRARY
        }
        val manualStyles: List<CoverTopic> get() = entries.toList()
    }
}
