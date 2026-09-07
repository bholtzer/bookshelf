package com.bihstudio.madafim.presentation.viewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.bihstudio.madafim.domain.analytics.AnalyticsEvent
import com.bihstudio.madafim.domain.analytics.AnalyticsLogger
import com.bihstudio.madafim.domain.analytics.AnalyticsParam
import com.bihstudio.madafim.domain.model.Page
import com.bihstudio.madafim.domain.model.PageType
import com.bihstudio.madafim.domain.usecase.book.GetBookUseCase
import com.bihstudio.madafim.domain.usecase.page.ObservePagesUseCase
import com.bihstudio.madafim.domain.usecase.page.SyncBookPagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class BookViewerUiState(
    val title: String = "Viewer",
    val description: String = "",
    val style: String? = null,
    val pages: List<Page> = emptyList(),
    val isLoading: Boolean = true,
)

private data class ReaderPage(
    val page: Page,
    val pdfPageIndex: Int? = null,
    val pdfPageCount: Int = 1,
)

@HiltViewModel
class BookViewerViewModel @Inject constructor(
    private val getBook: GetBookUseCase,
    private val observePages: ObservePagesUseCase,
    private val syncBookPages: SyncBookPagesUseCase,
    private val analytics: AnalyticsLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(BookViewerUiState())
    val uiState: StateFlow<BookViewerUiState> = _state.asStateFlow()

    fun load(bookId: String) {
        viewModelScope.launch {
            analytics.trackScreen("book_viewer")
            analytics.track(AnalyticsEvent.BOOK_OPENED, mapOf(AnalyticsParam.BOOK_ID to bookId, AnalyticsParam.SOURCE to "viewer_load"))
            val book = getBook(bookId)
            if (book != null) {
                _state.update { it.copy(title = book.title, description = book.description, style = book.coverStyle) }
            }
            launch {
                while (true) {
                    runCatching { syncBookPages(bookId) }
                    delay(30_000)
                }
            }
            observePages(bookId).collect { pages ->
                analytics.track(AnalyticsEvent.READER_PAGES_LOADED, mapOf(AnalyticsParam.BOOK_ID to bookId, AnalyticsParam.PAGE_COUNT to pages.size))
                _state.update { it.copy(pages = pages, isLoading = false) }
            }
        }
    }

    fun trackPrintStarted(bookId: String, pageCount: Int) {
        analytics.track(AnalyticsEvent.PRINT_STARTED, mapOf(AnalyticsParam.BOOK_ID to bookId, AnalyticsParam.PAGE_COUNT to pageCount))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookViewerScreen(
    bookId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: BookViewerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var isFullPage by rememberSaveable { mutableStateOf(false) }
    
    val readerPages by produceState<List<ReaderPage>>(initialValue = emptyList(), state.pages) {
        value = buildReaderPages(state.pages)
    }
    
    val topic = remember(state.title, state.description, state.style) {
        CoverTopic.resolve(state.style, state.title, state.description)
    }

    LaunchedEffect(bookId) {
        viewModel.load(bookId)
    }

    Scaffold(
        containerColor = Color(0xFF050B18),
        topBar = {
          if (!isFullPage) {
            TopAppBar(
                title = {
                    Column {
                        Text(state.title, maxLines = 1, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        Text("SYSTEM // ${topic.label.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), letterSpacing = 2.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                },
                actions = {
                    val currentImagePage = readerPages.getOrNull(currentPageIndex)?.page?.takeIf { it.pageType == PageType.IMAGE }
                    IconButton(enabled = currentImagePage != null, onClick = {
                        currentImagePage?.let { page ->
                            scope.launch {
                                runCatching { shareImagePage(context, page) }
                                    .onFailure { Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show() }
                            }
                        }
                    }) { Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(enabled = state.pages.isNotEmpty(), onClick = {
                        viewModel.trackPrintStarted(bookId, state.pages.size)
                        printBook(context, state.title, state.pages)
                    }) { Icon(Icons.Default.Print, contentDescription = "Print", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Settings, contentDescription = "Config", tint = MaterialTheme.colorScheme.primary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                ),
            )
          }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            HiTechBackdrop()
            
            when {
                state.isLoading || (state.pages.isNotEmpty() && readerPages.isEmpty()) -> Box(Modifier.fillMaxSize(), Alignment.Center) { 
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) 
                }
                state.pages.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { 
                    Text("ARCHIVE EMPTY", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Light, letterSpacing = 4.sp) 
                }
                else -> {
                    val pagerState = rememberPagerState(pageCount = { readerPages.size })
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }.collectLatest { currentPageIndex = it }
                    }
                    
                    Column(Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            contentPadding = if (isFullPage) PaddingValues(0.dp) else PaddingValues(horizontal = 30.dp, vertical = 24.dp),
                            pageSpacing = if (isFullPage) 0.dp else 20.dp,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) { index ->
                            val pageOffset = (pagerState.currentPage - index + pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
                            PageSurface(
                                readerPage = readerPages[index],
                                isFullPage = isFullPage,
                                onToggleFullPage = { isFullPage = !isFullPage },
                                modifier = Modifier.graphicsLayer {
                                    val absOffset = kotlin.math.abs(pageOffset)
                                    rotationY = pageOffset * 30f
                                    transformOrigin = TransformOrigin(if (pageOffset < 0f) 0f else 1f, 0.5f)
                                    cameraDistance = 15f * density
                                    alpha = 1f - absOffset * 0.3f
                                    scaleX = 1f - absOffset * 0.1f
                                    scaleY = 1f - absOffset * 0.1f
                                },
                            )
                        }
                        
                        if (!isFullPage) Column(
                            Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, bottom = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            VisualizerRow(MaterialTheme.colorScheme.primary)
                            
                            Spacer(Modifier.height(16.dp))
                            
                            val progress = if (readerPages.isNotEmpty()) (pagerState.currentPage + 1).toFloat() / readerPages.size.toFloat() else 0f
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(progress)
                                        .fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                            )
                                        )
                                )
                            }
                            
                            Spacer(Modifier.height(20.dp))
                            
                            Surface(
                                color = Color.White.copy(alpha = 0.05f),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        "PAGE ${(pagerState.currentPage + 1).toString().padStart(2, '0')}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                    Box(Modifier.size(1.dp, 16.dp).background(Color.White.copy(alpha = 0.2f)))
                                    Text(
                                        "TOTAL ${readerPages.size.toString().padStart(2, '0')}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.5f),
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualizerRow(color: Color) {
    Row(
        modifier = Modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
        repeat(12) { i ->
            val duration = remember { (400..800).random() }
            val heightAnim by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )
            Box(
                Modifier
                    .width(2.dp)
                    .fillMaxHeight(heightAnim)
                    .background(color.copy(alpha = 0.6f), CircleShape)
            )
        }
    }
}

@Composable
private fun HiTechBackdrop() {
    val infiniteTransition = rememberInfiniteTransition(label = "hitech")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha"
    )

    Box(Modifier.fillMaxSize().background(Color(0xFF050B18))) {
        Box(
            modifier = Modifier
                .size(450.dp)
                .align(Alignment.TopEnd)
                .offset(x = 150.dp, y = (-150).dp)
                .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = alphaAnim), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .size(350.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-100).dp, y = 100.dp)
                .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.secondary.copy(alpha = alphaAnim), Color.Transparent)))
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        val gridStep = 40.dp.toPx()
                        for (x in 0..(size.width / gridStep).toInt()) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.04f),
                                start = Offset(x * gridStep, 0f),
                                end = Offset(x * gridStep, size.height),
                                strokeWidth = 0.5.dp.toPx()
                            )
                        }
                        for (y in 0..(size.height / gridStep).toInt()) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.04f),
                                start = Offset(0f, y * gridStep),
                                end = Offset(size.width, y * gridStep),
                                strokeWidth = 0.5.dp.toPx()
                            )
                        }
                    }
                }
        )
    }
}

@Composable
private fun PageSurface(readerPage: ReaderPage, isFullPage: Boolean, onToggleFullPage: () -> Unit, modifier: Modifier = Modifier) {
    val shape = if (isFullPage) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 3.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 3.dp)
    Box(
        modifier
            .fillMaxSize()
            .shadow(
                elevation = if (isFullPage) 0.dp else 32.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = 0.65f)
            )
            .clip(shape)
            .background(if (isFullPage) Color.Black else Color(0xFFFFF9E9))
            .then(if (isFullPage) Modifier else Modifier.border(1.dp, Color(0xFFB59463), shape))
            .clickable(onClickLabel = if (isFullPage) "Exit full page" else "View full page", onClick = onToggleFullPage)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(if (isFullPage) PaddingValues(0.dp) else PaddingValues(start = 22.dp, top = 18.dp, end = 20.dp, bottom = 22.dp)),
            contentAlignment = Alignment.Center
        ) {
            when (readerPage.page.pageType) {
                PageType.IMAGE -> ZoomablePageImage(page = readerPage.page)
                PageType.PDF -> PdfPageSurface(page = readerPage.page, pdfPageIndex = readerPage.pdfPageIndex ?: 0, pdfPageCount = readerPage.pdfPageCount)
            }
        }
        
        if (!isFullPage) {
            // Reference-inspired binding gutter and gently curved paper lighting.
            Box(Modifier.fillMaxHeight().width(20.dp).align(Alignment.CenterStart).background(Brush.horizontalGradient(listOf(Color(0xFF6F4B28).copy(alpha = 0.4f), Color(0xFFD7BE91).copy(alpha = 0.3f), Color.Transparent))))
            Box(Modifier.fillMaxHeight().width(9.dp).align(Alignment.CenterEnd).background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFB89865).copy(alpha = 0.42f)))))
            Box(Modifier.fillMaxWidth().height(8.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFB89865).copy(alpha = 0.5f)))))
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color(0xFF8A6237).copy(alpha = 0.12f)), radius = 900f)))
        }
    }
}

@Composable
private fun ZoomablePageImage(page: Page) {
    val imageModel = page.localUri?.let(::File) ?: page.remoteUrl

    if (imageModel == null) {
        MissingPageFilePlaceholder(page = page)
    } else {
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pageZoom(page.id),
        )
    }
}

private fun Modifier.pageZoom(key: Any): Modifier = composed {
    var scale by remember(key) { mutableFloatStateOf(1f) }
    var translation by remember(key) { mutableStateOf(Offset.Zero) }
    var viewport by remember(key) { mutableStateOf(IntSize.Zero) }

    onSizeChanged { viewport = it }
        .pointerInput(key) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val zoomChange = event.calculateZoom()
                    if (zoomChange != 1f || scale > 1f) {
                        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
                        val pan = event.calculatePan()
                        val maxX = viewport.width * (nextScale - 1f) / 2f
                        val maxY = viewport.height * (nextScale - 1f) / 2f
                        translation = Offset(
                            (translation.x + pan.x).coerceIn(-maxX, maxX),
                            (translation.y + pan.y).coerceIn(-maxY, maxY),
                        )
                        scale = nextScale
                        event.changes.forEach { change -> if (change.pressed) change.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationX = translation.x
            translationY = translation.y
        }
}

@Composable
private fun MissingPageFilePlaceholder(page: Page) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = "LINK OFFLINE",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 4.sp
        )
        Text(
            text = page.originalFileName,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f),
            maxLines = 1
        )
    }
}

@Composable
private fun PdfPageSurface(page: Page, pdfPageIndex: Int, pdfPageCount: Int) {
    val bitmap by produceState<Bitmap?>(initialValue = null, page.localUri, pdfPageIndex) {
        value = withContext(Dispatchers.IO) { renderPdfPage(page.localUri, pdfPageIndex, maxWidth = 1600) }
    }

    if (bitmap == null) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pageZoom("${page.id}:$pdfPageIndex")
            )
            Text(
                "DATA CHUNK ${pdfPageIndex + 1}/${pdfPageCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.padding(8.dp),
                letterSpacing = 2.sp
            )
        }
    }
}

private suspend fun renderPdfPage(localUri: String?, pageIndex: Int, maxWidth: Int): Bitmap? {
    val file = localUri?.let(::File)?.takeIf { it.exists() } ?: return null
    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (pageIndex >= renderer.pageCount) return null
                renderer.openPage(pageIndex).use { pdfPage ->
                    val scale = maxWidth.toFloat() / pdfPage.width.toFloat()
                    val bitmap = Bitmap.createBitmap(maxWidth, (pdfPage.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }.getOrNull()
}

private suspend fun buildReaderPages(pages: List<Page>): List<ReaderPage> = withContext(Dispatchers.IO) {
    pages.flatMap { page ->
        if (page.pageType != PageType.PDF) listOf(ReaderPage(page))
        else {
            val count = getPdfPageCount(page.localUri)
            if (count <= 0) listOf(ReaderPage(page, 0, 1))
            else (0 until count).map { ReaderPage(page, it, count) }
        }
    }
}

private fun getPdfPageCount(localUri: String?): Int {
    val file = localUri?.let(::File)?.takeIf { it.exists() } ?: return 0
    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
        }
    }.getOrDefault(0)
}

private fun printBook(context: Context, title: String, pages: List<Page>) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    printManager.print(title.ifBlank { "MaDaFim" }, BookPrintDocumentAdapter(context, title, pages), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
}

private class BookPrintDocumentAdapter(val context: Context, val title: String, val pages: List<Page>) : PrintDocumentAdapter() {
    var attrs: PrintAttributes? = null
    override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, sig: CancellationSignal?, cb: LayoutResultCallback?, ex: Bundle?) {
        attrs = new
        cb?.onLayoutFinished(PrintDocumentInfo.Builder(title).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
    }
    override fun onWrite(pagesRange: Array<out PageRange>?, dest: ParcelFileDescriptor?, sig: CancellationSignal?, cb: WriteResultCallback?) {
        // Implementation for printing
    }
}

private suspend fun shareImagePage(context: Context, page: Page) {
    val shareFile = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "shared").also { it.mkdirs() }
        val dest = File(directory, "share-${page.id}.jpg")
        page.localUri?.let { File(it) }?.inputStream()?.use { it.copyTo(dest.outputStream()) }
        dest
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "image/jpeg"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share"))
}

private enum class CoverTopic(val label: String, val icon: ImageVector, val colors: List<Color>, val keywords: List<String>) {
    MUSIC("Audio", Icons.Default.MusicNote, listOf(Color(0xFF00F2FF), Color(0xFF7000FF)), listOf("music", "song")),
    LEARNING("Cognition", Icons.Default.School, listOf(Color(0xFF00F2FF), Color(0xFF00696D)), listOf("learn", "study")),
    COOKING("Alchemy", Icons.Default.Restaurant, listOf(Color(0xFFFF00E5), Color(0xFF9C4092)), listOf("cook", "recipe")),
    LIFE("Persona", Icons.Default.Person, listOf(Color(0xFF7000FF), Color(0xFFFF00E5)), listOf("diary", "life")),
    TRAVEL("Nexus", Icons.Default.Public, listOf(Color(0xFF00F2FF), Color(0xFF2BAAA7)), listOf("travel", "trip")),
    TECH("Protocol", Icons.Default.Code, listOf(Color(0xFF7000FF), Color(0xFF00F2FF)), listOf("code", "android")),
    ART("Creative", Icons.Default.Brush, listOf(Color(0xFFFF00E5), Color(0xFF7000FF)), listOf("art", "design")),
    LIBRARY("Archive", Icons.Default.Book, listOf(Color(0xFF00F2FF), Color(0xFF7000FF)), emptyList());

    companion object {
        fun resolve(style: String?, title: String, description: String): CoverTopic =
            style?.let { s -> entries.firstOrNull { it.name.equals(s, true) } } ?: from(title, description)
        fun from(title: String, description: String): CoverTopic {
            val text = "$title $description".lowercase()
            return entries.firstOrNull { t -> t.keywords.any { text.contains(it) } } ?: LIBRARY
        }
    }
}
