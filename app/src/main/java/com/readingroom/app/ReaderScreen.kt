package com.readingroom.app

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private class Palette(val background: Color, val foreground: Color, val filter: ColorFilter?)

private fun palette(theme: ReaderTheme): Palette = when (theme) {
    ReaderTheme.PAPER -> Palette(Color(0xFFFCFCF9), Color(0xFF1E1F1C), null)
    ReaderTheme.SEPIA -> Palette(
        Color(0xFFF2E6CE), Color(0xFF3A2E20),
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.96f, 0f, 0f, 0f, 0f,
                    0f, 0.90f, 0f, 0f, 0f,
                    0f, 0f, 0.74f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        ),
    )
    ReaderTheme.NIGHT -> Palette(
        Color(0xFF0B0D0C), Color(0xFFC8D1CC),
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -0.86f, 0f, 0f, 0f, 226f,
                    0f, -0.86f, 0f, 0f, 226f,
                    0f, 0f, -0.86f, 0f, 226f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        ),
    )
}

private const val MAX_ZOOM = 3f
private const val ZOOM_STEP = 0.25f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: AppViewModel, bookId: String) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val book = state.books.firstOrNull { it.id == bookId }
    if (book == null) {
        LaunchedEffect(Unit) { viewModel.go(Screen.Library) }
        return
    }
    val settings = state.settings
    val colors = palette(settings.theme)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var doc by remember(book.uri) { mutableStateOf<PdfDoc?>(null) }
    var failed by remember(book.uri) { mutableStateOf(false) }
    DisposableEffect(book.uri) {
        val opened = PdfDoc.open(context, Uri.parse(book.uri))
        doc = opened
        failed = opened == null
        onDispose {
            opened?.close()
            doc = null
        }
    }

    // Keep the screen awake while reading.
    DisposableEffect(settings.keepAwake) {
        val window = (context as? Activity)?.window
        if (settings.keepAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Count reading time only while the screen is in front of the reader.
    var session by remember { mutableIntStateOf(0) }
    val saved = remember { intArrayOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(1000)
                session++
                if (session - saved[0] >= 10) {
                    viewModel.addReadingSeconds(session - saved[0])
                    saved[0] = session
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.addReadingSeconds(session - saved[0]) }
    }

    val lastIndex = (book.pages - 1).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = book.lastPage.coerceIn(0, lastIndex)) { book.pages }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { viewModel.setPage(bookId, it) }
    }

    var chrome by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf(false) }
    var sliding by remember { mutableStateOf<Float?>(null) }
    val zoom = settings.zoom
    val currentPage = pagerState.currentPage
    val bookmarked = currentPage in book.bookmarks

    fun turn(delta: Int) {
        val target = (pagerState.currentPage + delta).coerceIn(0, lastIndex)
        if (target == pagerState.currentPage) {
            viewModel.showMessage(if (delta > 0) "You reached the last page." else "This is the first page.")
        } else {
            scope.launch { pagerState.animateScrollToPage(target) }
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        val d = doc
        when {
            failed -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("This PDF could not be opened.", color = colors.foreground, fontWeight = FontWeight.SemiBold)
                Text(
                    "It may have been moved, deleted or protected with a password.",
                    color = colors.foreground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.go(Screen.Library) }) { Text("Back") }
                    Button(onClick = { viewModel.removeBook(bookId); viewModel.go(Screen.Library) }) { Text("Remove") }
                }
            }

            d == null -> Unit

            else -> Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val third = size.width * 0.3f
                        when {
                            offset.x < third -> turn(-1)
                            offset.x > size.width - third -> turn(1)
                            else -> chrome = !chrome
                        }
                    }
                },
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = zoom <= 1.01f,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    PdfPageView(d, index, zoom, colors.filter)
                }
            }
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier.fillMaxWidth().background(colors.background.copy(alpha = 0.95f)).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.go(Screen.Library) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to library", tint = colors.foreground)
                }
                Text(
                    book.title,
                    Modifier.weight(1f),
                    color = colors.foreground,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                IconButton(onClick = { viewModel.toggleBookmark(bookId, currentPage) }) {
                    Icon(
                        if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        if (bookmarked) "Remove bookmark" else "Bookmark this page",
                        tint = if (bookmarked) MaterialTheme.colorScheme.primary else colors.foreground,
                    )
                }
                IconButton(onClick = { sheet = true }) { Icon(Icons.Default.TextFields, "Reading settings", tint = colors.foreground) }
            }
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier.fillMaxWidth().background(colors.background.copy(alpha = 0.95f)).padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Slider(
                    value = sliding ?: currentPage.toFloat(),
                    onValueChange = { sliding = it },
                    onValueChangeFinished = {
                        val target = (sliding ?: currentPage.toFloat()).roundToInt()
                        sliding = null
                        scope.launch { pagerState.scrollToPage(target) }
                    },
                    valueRange = 0f..lastIndex.coerceAtLeast(1).toFloat(),
                    enabled = book.pages > 1,
                )
                Row {
                    val shown = (sliding?.roundToInt() ?: currentPage) + 1
                    Text("Page $shown of ${book.pages}", Modifier.weight(1f), color = colors.foreground.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                    Text(
                        "This session ${session / 60}:${"%02d".format(session % 60)}",
                        color = colors.foreground.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Page color", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderTheme.entries.forEach { t ->
                        val label = t.name.lowercase().replaceFirstChar { it.uppercase() }
                        if (t == settings.theme) {
                            Button(onClick = { viewModel.setTheme(t) }) { Text(label) }
                        } else {
                            FilledTonalButton(onClick = { viewModel.setTheme(t) }) { Text(label) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Zoom", Modifier.weight(1f))
                    TextButton(onClick = { viewModel.setZoom(zoom - ZOOM_STEP) }, enabled = zoom > 1f) { Text("−") }
                    Text("${(zoom * 100).roundToInt()}%", fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { viewModel.setZoom(zoom + ZOOM_STEP) }, enabled = zoom < MAX_ZOOM) { Text("+") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Keep screen on", Modifier.weight(1f))
                    Switch(checked = settings.keepAwake, onCheckedChange = { viewModel.setKeepAwake(it) })
                }
            }
        }
    }
}

@Composable
private fun PdfPageView(doc: PdfDoc, index: Int, zoom: Float, filter: ColorFilter?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val pageWidth = maxWidth * zoom
        val widthPx = with(density) { pageWidth.roundToPx() }.coerceAtLeast(1)
        val bitmap by produceState<Bitmap?>(null, doc, index, widthPx) {
            value = doc.render(index, widthPx)
        }
        var scrollModifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        if (zoom > 1.01f) scrollModifier = scrollModifier.horizontalScroll(rememberScrollState())
        Box(scrollModifier, contentAlignment = Alignment.TopCenter) {
            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.FillWidth,
                    colorFilter = filter,
                    modifier = Modifier.width(pageWidth).aspectRatio(bmp.width.toFloat() / bmp.height),
                )
            }
        }
    }
}
