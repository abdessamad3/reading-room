package com.readingroom.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
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

/** What a finger does on the page. */
private enum class Tool { READ, HIGHLIGHT, NOTE, ERASE }

private const val MAX_ZOOM = 3f
private const val ZOOM_STEP = 0.25f
private val NOTE_MARKER = 26.dp

/** Finger-sized margin around a mark that still counts as touching it. */
private val HIT_MARGIN = 14.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    // Reader brightness, independent of the phone's setting.
    DisposableEffect(settings.brightness) {
        val window = (context as? Activity)?.window
        window?.let {
            val attrs = it.attributes
            attrs.screenBrightness = if (settings.brightness < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else settings.brightness
            it.attributes = attrs
        }
        onDispose {
            window?.let {
                val attrs = it.attributes
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.attributes = attrs
            }
        }
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
    var notesSheet by remember { mutableStateOf(false) }
    var sliding by remember { mutableStateOf<Float?>(null) }
    var tool by rememberSaveable { mutableStateOf(Tool.READ) }
    var penColor by rememberSaveable { mutableStateOf(MarkColor.YELLOW) }
    var editing by remember { mutableStateOf<Annotation?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var hintedHighlight by rememberSaveable { mutableStateOf(false) }
    val zoom = settings.zoom
    val currentPage = pagerState.currentPage
    val bookmarked = currentPage in book.bookmarks
    val byPage = remember(book.annotations) { book.annotations.groupBy { it.page } }

    fun turn(delta: Int) {
        val target = (pagerState.currentPage + delta).coerceIn(0, lastIndex)
        if (target == pagerState.currentPage) {
            viewModel.showMessage(if (delta > 0) "You reached the last page." else "This is the first page.")
        } else {
            scope.launch { pagerState.animateScrollToPage(target) }
        }
    }

    fun newAnnotation(page: Int, kind: AnnotationKind, l: Float, t: Float, r: Float, b: Float) = Annotation(
        id = UUID.randomUUID().toString(),
        page = page,
        kind = kind,
        color = penColor,
        left = l,
        top = t,
        right = r,
        bottom = b,
        createdAt = System.currentTimeMillis(),
    )

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
                Modifier.fillMaxSize().pointerInput(tool) {
                    detectTapGestures { offset ->
                        // While annotating, taps belong to the page, not to page turning.
                        if (tool != Tool.READ) return@detectTapGestures
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
                    userScrollEnabled = zoom <= 1.01f && tool == Tool.READ,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    PdfPageView(
                        doc = d,
                        index = index,
                        zoom = zoom,
                        filter = colors.filter,
                        annotations = byPage[index].orEmpty(),
                        tool = tool,
                        color = penColor,
                        onHighlight = { l, t, r, b ->
                            viewModel.addAnnotation(bookId, newAnnotation(index, AnnotationKind.HIGHLIGHT, l, t, r, b))
                            if (!hintedHighlight) {
                                hintedHighlight = true
                                viewModel.showMessage("Highlight added. Tap it to add a note or change its color.")
                            }
                        },
                        onNote = { x, y ->
                            editing = newAnnotation(index, AnnotationKind.NOTE, x, y, x, y)
                            editingIsNew = true
                        },
                        onOpen = { editing = it; editingIsNew = false },
                        onErase = {
                            viewModel.deleteAnnotation(bookId, it.id)
                            viewModel.showMessage(if (it.kind == AnnotationKind.NOTE) "Note deleted." else "Highlight deleted.")
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chrome || tool != Tool.READ,
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
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { notesSheet = true }) {
                    Icon(Icons.Default.EditNote, "Notes and highlights, ${book.annotations.size} so far", tint = colors.foreground)
                }
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
            visible = chrome || tool != Tool.READ,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier.fillMaxWidth().background(colors.background.copy(alpha = 0.95f)).padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                ToolRow(
                    tool = tool,
                    color = penColor,
                    foreground = colors.foreground,
                    onRead = { tool = Tool.READ },
                    onNote = { tool = Tool.NOTE },
                    onErase = { tool = Tool.ERASE },
                    onColor = { penColor = it; tool = Tool.HIGHLIGHT },
                )
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

    editing?.let { current ->
        AnnotationDialog(
            initial = current,
            isNew = editingIsNew,
            onSave = { saved ->
                val emptyNote = saved.kind == AnnotationKind.NOTE && saved.text.isBlank()
                when {
                    emptyNote && editingIsNew -> Unit
                    emptyNote -> viewModel.deleteAnnotation(bookId, saved.id)
                    editingIsNew -> viewModel.addAnnotation(bookId, saved)
                    else -> viewModel.updateAnnotation(bookId, saved)
                }
                editing = null
            },
            onDelete = {
                viewModel.deleteAnnotation(bookId, current.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (notesSheet) {
        NotesSheet(
            doc = doc,
            annotations = book.annotations,
            onGo = { a ->
                notesSheet = false
                scope.launch { pagerState.scrollToPage(a.page.coerceIn(0, lastIndex)) }
            },
            onDelete = {
                viewModel.deleteAnnotation(bookId, it.id)
                viewModel.showMessage(if (it.kind == AnnotationKind.NOTE) "Note deleted." else "Highlight deleted.")
            },
            onCopy = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Notes", NotesExport.format(book.title, book.annotations)))
                viewModel.showMessage("Notes copied. Paste them into any app.")
            },
            onDismiss = { notesSheet = false },
        )
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
                    Text("Match phone brightness", Modifier.weight(1f))
                    Switch(
                        checked = settings.brightness < 0f,
                        onCheckedChange = { viewModel.setBrightness(if (it) BRIGHTNESS_SYSTEM else 0.7f) },
                    )
                }
                if (settings.brightness >= 0f) {
                    Slider(
                        value = settings.brightness,
                        onValueChange = { viewModel.setBrightness(it) },
                        valueRange = 0.05f..1f,
                    )
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
private fun ToolRow(
    tool: Tool,
    color: MarkColor,
    foreground: Color,
    onRead: () -> Unit,
    onNote: () -> Unit,
    onErase: () -> Unit,
    onColor: (MarkColor) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconButton(onClick = onRead) {
            Icon(Icons.Default.PanTool, "Read and scroll", tint = if (tool == Tool.READ) accent else foreground)
        }
        MarkColor.entries.forEach { c ->
            val selected = tool == Tool.HIGHLIGHT && c == color
            Box(
                Modifier
                    .size(36.dp)
                    .clickable(role = Role.Button) { onColor(c) }
                    .semantics { contentDescription = "${c.label} highlighter" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(Color(c.argb), CircleShape)
                        .then(if (selected) Modifier.border(2.5.dp, foreground, CircleShape) else Modifier),
                )
            }
        }
        IconButton(onClick = onNote) {
            Icon(Icons.Default.StickyNote2, "Add a note", tint = if (tool == Tool.NOTE) accent else foreground)
        }
        IconButton(onClick = onErase) {
            Icon(Icons.Default.Delete, "Eraser", tint = if (tool == Tool.ERASE) MaterialTheme.colorScheme.error else foreground)
        }
        Text(
            when (tool) {
                Tool.ERASE -> "Tap a mark to delete it"
                Tool.READ -> "Tap a mark to edit it"
                Tool.HIGHLIGHT -> "Drag over text"
                Tool.NOTE -> "Tap where the note goes"
            },
            Modifier.weight(1f),
            color = foreground.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
        )
    }
}

@Composable
private fun PdfPageView(
    doc: PdfDoc,
    index: Int,
    zoom: Float,
    filter: ColorFilter?,
    annotations: List<Annotation>,
    tool: Tool,
    color: MarkColor,
    onHighlight: (Float, Float, Float, Float) -> Unit,
    onNote: (Float, Float) -> Unit,
    onOpen: (Annotation) -> Unit,
    onErase: (Annotation) -> Unit,
) {
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
                Box(Modifier.width(pageWidth).aspectRatio(bmp.width.toFloat() / bmp.height)) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${index + 1}",
                        contentScale = ContentScale.FillWidth,
                        colorFilter = filter,
                        modifier = Modifier.fillMaxSize(),
                    )
                    AnnotationLayer(annotations, tool, color, onHighlight, onNote, onOpen, onErase)
                }
            }
        }
    }
}

/** Draws the marks on a page and turns touches into new marks or edits. */
@Composable
private fun AnnotationLayer(
    annotations: List<Annotation>,
    tool: Tool,
    color: MarkColor,
    onHighlight: (Float, Float, Float, Float) -> Unit,
    onNote: (Float, Float) -> Unit,
    onOpen: (Annotation) -> Unit,
    onErase: (Annotation) -> Unit,
) {
    var drag by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }

    val input: Modifier = when (tool) {
        // Reading: only react to a tap that lands on a mark, and leave every other touch alone.
        Tool.READ -> Modifier.pointerInput(annotations) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                if ((up.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
                val hit = hitTest(annotations, up.position)
                if (hit != null) {
                    up.consume()
                    onOpen(hit)
                }
            }
        }

        Tool.ERASE -> Modifier.pointerInput(annotations) {
            detectTapGestures { pos -> hitTest(annotations, pos)?.let(onErase) }
        }

        Tool.NOTE -> Modifier.pointerInput(annotations, color) {
            detectTapGestures { pos ->
                val hit = hitTest(annotations, pos)
                if (hit != null) onOpen(hit)
                else onNote((pos.x / size.width).coerceIn(0f, 0.95f), (pos.y / size.height).coerceIn(0f, 0.97f))
            }
        }

        Tool.HIGHLIGHT -> Modifier
            .pointerInput(annotations) {
                detectTapGestures { pos -> hitTest(annotations, pos)?.let(onOpen) }
            }
            .pointerInput(color) {
                detectDragGestures(
                    onDragStart = { drag = it to it },
                    onDragEnd = {
                        val d = drag
                        drag = null
                        if (d != null) {
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val l = (min(d.first.x, d.second.x) / w).coerceIn(0f, 1f)
                            val r = (max(d.first.x, d.second.x) / w).coerceIn(0f, 1f)
                            val t = (min(d.first.y, d.second.y) / h).coerceIn(0f, 1f)
                            val b = (max(d.first.y, d.second.y) / h).coerceIn(0f, 1f)
                            if (r - l > 0.02f && b - t > 0.004f) onHighlight(l, t, r, b)
                        }
                    },
                    onDragCancel = { drag = null },
                ) { change, _ ->
                    change.consume()
                    drag = drag?.let { it.first to change.position }
                }
            }
    }

    Canvas(Modifier.fillMaxSize().then(input)) {
        val w = size.width
        val h = size.height
        annotations.forEach { a ->
            val base = Color(a.color.argb)
            when (a.kind) {
                AnnotationKind.HIGHLIGHT -> {
                    drawRect(
                        color = base,
                        topLeft = Offset(a.left * w, a.top * h),
                        size = Size((a.right - a.left) * w, (a.bottom - a.top) * h),
                        alpha = 0.38f,
                    )
                    if (a.text.isNotBlank()) {
                        drawCircle(Color(0xCC000000), radius = 3.dp.toPx(), center = Offset(a.right * w, a.top * h))
                    }
                }
                AnnotationKind.NOTE -> {
                    val side = NOTE_MARKER.toPx()
                    val topLeft = Offset(a.left * w, a.top * h)
                    drawRoundRect(base, topLeft, Size(side, side), CornerRadius(6.dp.toPx()))
                    drawRoundRect(Color(0x66000000), topLeft, Size(side, side), CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
                    for (i in 0..2) {
                        val y = topLeft.y + side * (0.30f + 0.20f * i)
                        drawLine(Color(0x99000000), Offset(topLeft.x + side * 0.24f, y), Offset(topLeft.x + side * 0.76f, y), 1.5.dp.toPx())
                    }
                }
            }
        }
        drag?.let { (from, to) ->
            drawRect(
                color = Color(color.argb),
                topLeft = Offset(min(from.x, to.x), min(from.y, to.y)),
                size = Size(kotlin.math.abs(to.x - from.x), kotlin.math.abs(to.y - from.y)),
                alpha = 0.38f,
            )
        }
    }
}

/** The topmost mark under [pos]. Highlights get a finger-sized margin so thin ones are easy to hit. */
private fun PointerInputScope.hitTest(annotations: List<Annotation>, pos: Offset): Annotation? {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val markerW = NOTE_MARKER.toPx() / w
    val markerH = NOTE_MARKER.toPx() / h
    val slopX = HIT_MARGIN.toPx() / w
    val slopY = HIT_MARGIN.toPx() / h
    return annotations.lastOrNull { it.contains(pos.x / w, pos.y / h, markerW, markerH, slopX, slopY) }
}

@Composable
private fun AnnotationDialog(
    initial: Annotation,
    isNew: Boolean,
    onSave: (Annotation) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(initial.id) { mutableStateOf(initial.text) }
    var color by remember(initial.id) { mutableStateOf(initial.color) }
    val isNote = initial.kind == AnnotationKind.NOTE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNote) "Note on page ${initial.page + 1}" else "Highlight on page ${initial.page + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(if (isNote) "Your note" else "Note (optional)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!isNew) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Text(
                            if (isNote) " Delete this note" else " Delete this highlight",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Color", style = MaterialTheme.typography.bodyMedium)
                    MarkColor.entries.forEach { c ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(Color(c.argb), CircleShape)
                                .then(if (c == color) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable(role = Role.RadioButton) { color = c }
                                .semantics { contentDescription = c.label },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(initial.copy(text = text.trim(), color = color)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesSheet(
    doc: PdfDoc?,
    annotations: List<Annotation>,
    onGo: (Annotation) -> Unit,
    onCopy: () -> Unit,
    onDelete: (Annotation) -> Unit,
    onDismiss: () -> Unit,
) {
    val sorted = remember(annotations) { annotations.sortedWith(compareBy({ it.page }, { it.top }, { it.left })) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notes and highlights (${annotations.size})", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onCopy, enabled = annotations.isNotEmpty()) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                    Text(" Copy all")
                }
            }
            if (sorted.isEmpty()) {
                Text(
                    "Nothing yet. Pick a highlighter color or the note tool in the bottom bar, then mark the page.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(sorted, key = { it.id }) { a ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onGo(a) }.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.padding(top = 4.dp).size(14.dp).background(Color(a.color.argb), CircleShape))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Page ${a.page + 1} · ${if (a.kind == AnnotationKind.NOTE) "note" else "highlight"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (a.kind == AnnotationKind.HIGHLIGHT && doc != null) HighlightPreview(doc, a)
                                if (a.text.isNotBlank()) Text(a.text)
                            }
                            IconButton(onClick = { onDelete(a) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    "Delete ${if (a.kind == AnnotationKind.NOTE) "note" else "highlight"} on page ${a.page + 1}",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A cut-out of the page showing the highlighted area. */
@Composable
private fun HighlightPreview(doc: PdfDoc, a: Annotation) {
    val image by produceState<ImageBitmap?>(null, doc, a.id, a.left, a.top, a.right, a.bottom) {
        value = try {
            val page = doc.render(a.page, 720)
            val pad = 0.01f
            val x = ((a.left - pad) * page.width).toInt().coerceIn(0, page.width - 1)
            val y = ((a.top - pad) * page.height).toInt().coerceIn(0, page.height - 1)
            val w = ((a.right + pad) * page.width).toInt().coerceIn(x + 1, page.width) - x
            val h = ((a.bottom + pad) * page.height).toInt().coerceIn(y + 1, page.height) - y
            Bitmap.createBitmap(page, x, y, w, h).asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
    image?.let { bmp ->
        Image(
            bitmap = bmp,
            contentDescription = "Highlighted part of page ${a.page + 1}",
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 90.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(a.color.argb).copy(alpha = 0.4f)),
        )
    }
}
