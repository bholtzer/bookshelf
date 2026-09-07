package com.bihstudio.madafim.presentation.bookshelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import com.bihstudio.madafim.domain.analytics.AnalyticsLogger
import com.bihstudio.madafim.domain.model.AppResult
import com.bihstudio.madafim.domain.model.Book
import com.bihstudio.madafim.domain.model.Page
import com.bihstudio.madafim.domain.model.PageType
import com.bihstudio.madafim.domain.model.extractBookInviteCode
import com.bihstudio.madafim.domain.repository.SubscriptionRepository
import com.bihstudio.madafim.domain.usecase.auth.GetCurrentUserUseCase
import com.bihstudio.madafim.domain.usecase.auth.RestoreUserLibraryUseCase
import com.bihstudio.madafim.domain.usecase.book.AcceptBookEditorInviteUseCase
import com.bihstudio.madafim.domain.usecase.book.CreateBookUseCase
import com.bihstudio.madafim.domain.usecase.book.ObserveBooksUseCase
import com.bihstudio.madafim.domain.usecase.page.ObserveFirstPagesForBooksUseCase
import com.bihstudio.madafim.domain.usecase.page.ObservePagesByIdsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private enum class ShelfLayout { GRID, LIST }

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
    onAccount: () -> Unit,
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
    var shelfLayoutName by rememberSaveable { mutableStateOf(ShelfLayout.GRID.name) }
    val shelfLayout = ShelfLayout.valueOf(shelfLayoutName)
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
                else -> if (shelfLayout == ShelfLayout.GRID) LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        MusicAppHeader(state.books.size, shelfLayout, { shelfLayoutName = it.name }, { showJoinDialog = true }, onAccount)
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
                } else LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { MusicAppHeader(state.books.size, shelfLayout, { shelfLayoutName = it.name }, { showJoinDialog = true }, onAccount) }
                    item { LibrarySearchField(searchQuery, { searchQuery = it }, inviteCodeFromSearch != null) }
                    if (inviteCodeFromSearch != null) {
                        item { InviteSearchCard(inviteCodeFromSearch, state.isJoiningInvite, state.joinInviteError, { viewModel.joinSharedBook(searchQuery) { searchQuery = ""; onEditBook(it) } }, { searchQuery = "" }) }
                    }
                    itemsIndexed(if (inviteCodeFromSearch == null) visibleBooks else state.books, key = { _, book -> book.id }) { index, book ->
                        var isVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { delay(index * 40L); isVisible = true }
                        AnimatedVisibility(visible = isVisible, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 }) {
                            ListBookCard(book, state.coverPages[book.coverPageId], state.firstPages[book.id], { onOpenBook(book.id) }, { onEditBook(book.id) })
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
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF173F70), Color(0xFF07182E)))))
        val infiniteTransition = rememberInfiniteTransition(label = "bg")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.05f, targetValue = 0.15f,
            animationSpec = infiniteRepeatable(tween(5000, easing = LinearOutSlowInEasing), RepeatMode.Reverse), label = "alpha"
        )
        Box(Modifier.size(600.dp).align(Alignment.TopEnd).offset(x = 200.dp, y = (-200).dp).background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = alpha), Color.Transparent))))
        Box(Modifier.fillMaxSize().drawWithCache {
            onDrawWithContent {
                drawContent()
                val shelfGap = 360.dp.toPx()
                var y = 310.dp.toPx()
                while (y < size.height) {
                    drawRect(Color(0xFF6B351B).copy(alpha = 0.72f), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 18.dp.toPx()))
                    drawLine(Color(0xFFFFC36A).copy(alpha = 0.55f), Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                    drawRect(Color.Black.copy(alpha = 0.28f), Offset(0f, y + 18.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 10.dp.toPx()))
                    y += shelfGap
                }
            }
        })
    }
}

@Composable
private fun MusicAppHeader(count: Int, layout: ShelfLayout, onLayoutChange: (ShelfLayout) -> Unit, onJoin: () -> Unit, onAccount: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("MY LIBRARY", style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black)
            Text("MaDaFim", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("$count ${if (count == 1) "BOOK" else "BOOKS"}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f), letterSpacing = 1.sp)
        }
        val headerButtonModifier = Modifier.size(42.dp)
        Surface(onClick = onAccount, modifier = headerButtonModifier, color = Color.White.copy(alpha = 0.09f), shape = CircleShape, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.AccountCircle, contentDescription = "Account and privacy", tint = Color(0xFF78C943), modifier = Modifier.size(23.dp)) }
        }
        Spacer(Modifier.width(6.dp))
        Surface(onClick = onJoin, modifier = headerButtonModifier, color = Color.White.copy(alpha = 0.09f), shape = CircleShape, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Sync, contentDescription = "Sync a shared book", tint = Color(0xFF42A5F5), modifier = Modifier.size(21.dp)) }
        }
        Spacer(Modifier.width(6.dp))
        val targetLayout = if (layout == ShelfLayout.GRID) ShelfLayout.LIST else ShelfLayout.GRID
        Surface(
            onClick = { onLayoutChange(targetLayout) },
            modifier = headerButtonModifier,
            color = Color.White.copy(alpha = 0.09f),
            shape = CircleShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(if (targetLayout == ShelfLayout.GRID) Icons.Default.GridView else Icons.Default.ViewList, contentDescription = if (targetLayout == ShelfLayout.GRID) "Switch to grid view" else "Switch to list view", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
    }
}

@Composable
private fun LibrarySearchField(query: String, onQueryChange: (String) -> Unit, isInvite: Boolean) {
    Surface(color = Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))) {
        OutlinedTextField(
            value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search your books...", color = Color.White.copy(alpha = 0.45f)) },
            leadingIcon = { Icon(if (isInvite) Icons.Default.CloudDownload else Icons.Default.Search, contentDescription = null, tint = if (query.isNotBlank()) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
    }
}

@Composable
private fun AlbumBookCard(book: Book, cover: Page?, first: Page?, onOpen: () -> Unit, onEdit: () -> Unit) {
    var isOpening by remember { mutableStateOf(false) }
    val openingProgress by animateFloatAsState(if (isOpening) 1f else 0f, tween(850, easing = FastOutSlowInEasing), label = "book_open")
    
    LaunchedEffect(isOpening) { if (isOpening) { delay(900); onOpen(); isOpening = false } }

    Column(
        Modifier.fillMaxWidth()
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { isOpening = true },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BookCoverFrame(book, cover, first, Modifier.fillMaxWidth().aspectRatio(0.68f), openingProgress)
        Row(Modifier.padding(horizontal = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(book.title.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${book.pageCount} ${if (book.pageCount == 1) "PAGE" else "PAGES"}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFB51B), letterSpacing = 1.sp)
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "Configure ${book.title}", tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun ListBookCard(book: Book, cover: Page?, first: Page?, onOpen: () -> Unit, onEdit: () -> Unit) {
    var isOpening by remember { mutableStateOf(false) }
    val openingProgress by animateFloatAsState(if (isOpening) 1f else 0f, tween(850, easing = FastOutSlowInEasing), label = "list_book_open")
    LaunchedEffect(isOpening) { if (isOpening) { delay(900); onOpen(); isOpening = false } }
    Surface(onClick = { isOpening = true }, color = Color(0xFF16385D).copy(alpha = 0.92f), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF74B9F3).copy(alpha = 0.28f))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BookCoverFrame(book, cover, first, Modifier.width(126.dp).aspectRatio(0.68f), openingProgress)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${book.pageCount} ${if (book.pageCount == 1) "PAGE" else "PAGES"}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFB51B).copy(alpha = 0.85f), letterSpacing = 1.sp)
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.MoreVert, "Configure ${book.title}", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(26.dp)) }
        }
    }
}

@Composable
private fun BookCoverFrame(book: Book, cover: Page?, first: Page?, modifier: Modifier = Modifier, openingProgress: Float = 0f) {
    val bookShape = RoundedCornerShape(topStart = 2.dp, topEnd = 6.dp, bottomEnd = 6.dp, bottomStart = 2.dp)
    Box(
        modifier
            .graphicsLayer {
                scaleX = 0.97f + openingProgress * 0.03f
                scaleY = 0.97f + openingProgress * 0.03f
                rotationX = 3f * (1f - openingProgress)
                rotationZ = -1.5f * (1f - openingProgress)
                cameraDistance = 24f * density
            }
            .shadow(20.dp, bookShape, spotColor = Color.Black.copy(alpha = 0.7f))
    ) {
        // Cream paper block, inset from the hard cover so the fore-edge is visible.
        Box(
            Modifier.matchParentSize().padding(start = 7.dp, top = 4.dp, end = 1.dp, bottom = 2.dp)
                .clip(RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFE0CDA8), Color(0xFFFFFDF4), Color(0xFFF5E8CB))))
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        val gap = 3.dp.toPx()
                        var y = gap
                        while (y < size.height) {
                            drawLine(Color(0xFFD8CCB3).copy(alpha = 0.48f), Offset(size.width - 6.dp.toPx(), y), Offset(size.width, y), 0.6.dp.toPx())
                            y += gap
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxHeight().width(14.dp).align(Alignment.CenterStart).background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent))))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 12.dp, end = 8.dp)) {
                Icon(Icons.Default.AutoStories, null, tint = Color(0xFFB9935A), modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(8.dp))
                Text(book.title, color = Color(0xFF4A3827), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
            }
        }

        // The hard front cover swings from the left-hand spine when the book opens.
        Box(
            Modifier.matchParentSize().padding(end = 5.dp, bottom = 5.dp)
                .graphicsLayer {
                    rotationY = -176f * openingProgress
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    cameraDistance = 22f * density
                    shadowElevation = 10.dp.toPx() * (1f - openingProgress * 0.5f)
                }
                .clip(bookShape)
                .background(Color(0xFF0F172A))
                .border(1.dp, Color.White.copy(alpha = 0.18f), bookShape)
        ) {
            if (openingProgress < 0.5f) {
                BookCoverContent(book, cover, first)
                Box(Modifier.matchParentSize().padding(10.dp).border(1.dp, Color.White.copy(alpha = 0.38f), RoundedCornerShape(2.dp)))
                Box(Modifier.matchParentSize().padding(14.dp).border(1.dp, Color.Black.copy(alpha = 0.18f), RoundedCornerShape(2.dp)))
            } else {
                // Once the cover passes edge-on, show its paper-lined inside as the left page.
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFFFFFDF4), Color(0xFFF7EACD), Color(0xFFD5BD91))))) {
                    Column(Modifier.align(Alignment.Center).padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(book.title, color = Color(0xFF4A3827), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.width(28.dp).height(1.dp).background(Color(0xFFB9935A)))
                    }
                    Box(Modifier.fillMaxHeight().width(16.dp).align(Alignment.CenterStart).background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent))))
                }
            }
            Box(Modifier.fillMaxHeight().width(11.dp).align(Alignment.CenterStart).background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.58f), Color.White.copy(alpha = 0.13f), Color.Transparent))))
            Box(Modifier.fillMaxHeight().width(1.dp).align(Alignment.CenterStart).offset(x = 10.dp).background(Color.Black.copy(alpha = 0.35f)))
        }
        // A small ribbon gives the closed book the familiar readable-book silhouette.
        Box(Modifier.width(9.dp).height(18.dp).align(Alignment.BottomStart).offset(x = 25.dp, y = 9.dp).background(Color(0xFFD94841)))
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
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(topic.colors))) {
            Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 14.dp, top = 24.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(topic.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), letterSpacing = 2.sp)
                Spacer(Modifier.weight(0.65f))
                Icon(topic.icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(14.dp))
                Text(book.title.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(36.dp).height(1.dp).background(Color.White.copy(alpha = 0.55f)))
                Spacer(Modifier.height(8.dp))
                Text("MADAFIM", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f), letterSpacing = 1.5.sp)
            }
        }
    }
}

@Composable
private fun EmptyShelf(code: String?, modifier: Modifier, onCreate: () -> Unit, onJoin: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.AutoStories, null, tint = Color(0xFFFFB51B).copy(alpha = 0.75f), modifier = Modifier.size(100.dp))
        Spacer(Modifier.height(24.dp))
        Text("Your shelf is empty", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
        Text("Add your first book and start building your library.", color = Color.White.copy(alpha = 0.65f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onCreate, shape = RoundedCornerShape(8.dp)) { Text("ADD A BOOK") }
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
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFF0D1424), title = { Text("Sync a shared book", color = Color.White) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Paste the invitation link or code you received from the book owner. Then tap Join book to add it to your collection.", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Invitation link or code") }, placeholder = { Text("Paste here") }, supportingText = { if (error != null) Text(error, color = MaterialTheme.colorScheme.error) }, isError = error != null, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
        }
    }, confirmButton = { Button(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank() && !isJoining) { if (isJoining) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Join book") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
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
    MUSIC("Music", Icons.Default.MusicNote, listOf(Color(0xFF238BE4), Color(0xFF1764BD)), listOf("music", "song")),
    LEARNING("Learning", Icons.Default.School, listOf(Color(0xFF8BD346), Color(0xFF4EAA28)), listOf("learn", "study")),
    COOKING("Cooking", Icons.Default.Restaurant, listOf(Color(0xFFFFC229), Color(0xFFF09500)), listOf("cook", "recipe")),
    LIFE("Life", Icons.Default.Person, listOf(Color(0xFF9A72E8), Color(0xFF6842BE)), listOf("diary", "life")),
    TRAVEL("Travel", Icons.Default.Public, listOf(Color(0xFF7957D7), Color(0xFF4D31A0)), listOf("travel", "trip")),
    TECH("Technology", Icons.Default.Code, listOf(Color(0xFF2FA4EC), Color(0xFF1767B5)), listOf("code", "android")),
    ART("Creative", Icons.Default.Brush, listOf(Color(0xFFFF725E), Color(0xFFE23C36)), listOf("art", "design")),
    BUSINESS("Business", Icons.Default.BusinessCenter, listOf(Color(0xFF327FCC), Color(0xFF174E8B)), listOf("work", "business")),
    FITNESS("Fitness", Icons.Default.FitnessCenter, listOf(Color(0xFF7AC943), Color(0xFF3C9429)), listOf("fitness", "gym")),
    LIBRARY("Collection", Icons.Default.Book, listOf(Color(0xFFFF7057), Color(0xFFD93636)), emptyList());

    companion object {
        fun resolve(style: String?, title: String, description: String): CoverTopic = style?.let { s -> entries.firstOrNull { it.name.equals(s, true) } } ?: from(title, description)
        fun from(title: String, description: String): CoverTopic {
            val text = "$title $description".lowercase()
            return entries.firstOrNull { t -> t.keywords.any { text.contains(it) } } ?: LIBRARY
        }
        val manualStyles: List<CoverTopic> get() = entries.toList()
    }
}
