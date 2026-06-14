package com.bihstudio.bookshelf.presentation.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.bihstudio.bookshelf.domain.model.Page
import com.bihstudio.bookshelf.domain.model.PageType
import com.bihstudio.bookshelf.domain.usecase.book.GetBookUseCase
import com.bihstudio.bookshelf.domain.usecase.page.ObservePagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BookViewerUiState(
    val title: String = "Viewer",
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
) : ViewModel() {

    private val _state = MutableStateFlow(BookViewerUiState())
    val uiState: StateFlow<BookViewerUiState> = _state.asStateFlow()

    fun load(bookId: String) {
        viewModelScope.launch {
            val book = getBook(bookId)
            if (book != null) {
                _state.update { it.copy(title = book.title) }
            }
            observePages(bookId).collect { pages ->
                _state.update { it.copy(pages = pages, isLoading = false) }
            }
        }
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
    val readerPages by produceState(initialValue = emptyList<ReaderPage>(), state.pages) {
        value = buildReaderPages(state.pages)
    }

    LaunchedEffect(bookId) {
        viewModel.load(bookId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = state.pages.isNotEmpty(),
                        onClick = { printBook(context, state.title, state.pages) },
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print book")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit book")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.pages.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("This book has no pages yet.")
                }

                readerPages.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                else -> {
                    val pagerState = rememberPagerState(pageCount = { readerPages.size })
                    Column(Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) { index ->
                            PageSurface(readerPage = readerPages[index])
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "${pagerState.currentPage + 1} / ${readerPages.size}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageSurface(readerPage: ReaderPage) {
    val page = readerPage.page
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when (page.pageType) {
            PageType.IMAGE -> AsyncImage(
                model = page.localUri?.let(::File) ?: page.remoteUrl,
                contentDescription = page.originalFileName.ifBlank { "Book page" },
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )

            PageType.PDF -> PdfPageSurface(
                page = page,
                pdfPageIndex = readerPage.pdfPageIndex ?: 0,
                pdfPageCount = readerPage.pdfPageCount,
            )
        }
    }
}

@Composable
private fun PdfPageSurface(
    page: Page,
    pdfPageIndex: Int,
    pdfPageCount: Int,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, page.localUri, pdfPageIndex) {
        value = renderPdfPage(page.localUri, pdfPageIndex, maxWidth = 1800)
    }

    if (bitmap == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                page.originalFileName.ifBlank { "PDF page" },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (page.localUri == null) {
                    "This PDF is not available locally yet."
                } else {
                    "Loading PDF page..."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = page.originalFileName.ifBlank { "PDF page" },
                contentScale = ContentScale.Fit,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Text(
                "PDF page ${pdfPageIndex + 1} / $pdfPageCount",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private suspend fun buildReaderPages(pages: List<Page>): List<ReaderPage> =
    withContext(Dispatchers.IO) {
        pages.flatMap { page ->
            if (page.pageType != PageType.PDF) {
                listOf(ReaderPage(page = page))
            } else {
                val count = getPdfPageCount(page.localUri)
                if (count <= 0) {
                    listOf(ReaderPage(page = page, pdfPageIndex = 0, pdfPageCount = 1))
                } else {
                    (0 until count).map { index ->
                        ReaderPage(page = page, pdfPageIndex = index, pdfPageCount = count)
                    }
                }
            }
        }
    }

private suspend fun renderPdfPage(
    localUri: String?,
    pageIndex: Int,
    maxWidth: Int,
): Bitmap? = withContext(Dispatchers.IO) {
    val file = localUri?.let(::File)?.takeIf { it.exists() } ?: return@withContext null
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            if (pageIndex !in 0 until renderer.pageCount) return@withContext null
            renderer.openPage(pageIndex).use { pdfPage ->
                val scale = maxWidth.toFloat() / pdfPage.width.toFloat()
                val bitmap = Bitmap.createBitmap(
                    maxWidth,
                    (pdfPage.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
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
    printManager.print(
        title.ifBlank { "BookShelf book" },
        BookPrintDocumentAdapter(context, title, pages),
        PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build(),
    )
}

private class BookPrintDocumentAdapter(
    private val context: Context,
    private val title: String,
    private val pages: List<Page>,
) : PrintDocumentAdapter() {

    private var attributes: PrintAttributes? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?,
    ) {
        attributes = newAttributes
        callback?.onLayoutFinished(
            PrintDocumentInfo.Builder("${title.ifBlank { "bookshelf-book" }}.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build(),
            true,
        )
    }

    override fun onWrite(
        requestedPages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?,
    ) {
        if (destination == null) {
            callback?.onWriteFailed("No print destination")
            return
        }

        runCatching {
            val pdf = PrintedPdfDocument(
                context,
                attributes ?: PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .build(),
            )
            try {
                var printPageNumber = 1
                pages.forEach { page ->
                    if (cancellationSignal?.isCanceled == true) return@forEach
                    printPageNumber = pdf.appendBookPage(page, printPageNumber)
                }
                pdf.writeTo(ParcelFileDescriptor.AutoCloseOutputStream(destination))
            } finally {
                pdf.close()
            }
        }.fold(
            onSuccess = { callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) },
            onFailure = { callback?.onWriteFailed(it.message ?: "Print failed") },
        )
    }
}

private fun PrintedPdfDocument.appendBookPage(page: Page, startPageNumber: Int): Int {
    return when (page.pageType) {
        PageType.IMAGE -> {
            val bitmap = page.localUri?.let { BitmapFactory.decodeFile(it) }
            if (bitmap != null) {
                val pdfPage = startPage(startPageNumber)
                pdfPage.canvas.drawBitmapFit(bitmap)
                finishPage(pdfPage)
                bitmap.recycle()
                startPageNumber + 1
            } else {
                startPageNumber
            }
        }

        PageType.PDF -> {
            val file = page.localUri?.let(::File)?.takeIf { it.exists() } ?: return startPageNumber
            var nextPageNumber = startPageNumber
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    repeat(renderer.pageCount) { index ->
                        renderer.openPage(index).use { sourcePage ->
                            val pdfPage = startPage(nextPageNumber)
                            val targetWidth = pdfPage.canvas.width
                            val scale = targetWidth.toFloat() / sourcePage.width.toFloat()
                            val bitmap = Bitmap.createBitmap(
                                targetWidth,
                                (sourcePage.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            sourcePage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            pdfPage.canvas.drawBitmapFit(bitmap)
                            finishPage(pdfPage)
                            bitmap.recycle()
                            nextPageNumber++
                        }
                    }
                }
            }
            nextPageNumber
        }
    }
}

private fun Canvas.drawBitmapFit(bitmap: Bitmap) {
    val scale = min(
        width.toFloat() / bitmap.width.toFloat(),
        height.toFloat() / bitmap.height.toFloat(),
    )
    val targetWidth = bitmap.width * scale
    val targetHeight = bitmap.height * scale
    val left = (width - targetWidth) / 2f
    val top = (height - targetHeight) / 2f
    drawBitmap(bitmap, null, RectF(left, top, left + targetWidth, top + targetHeight), null)
}
