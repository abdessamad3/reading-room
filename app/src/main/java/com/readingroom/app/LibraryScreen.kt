package com.readingroom.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private val coverColors = listOf(
    Color(0xFF3D5A6C), Color(0xFF2E6A56), Color(0xFF7A3B2E), Color(0xFF5B6B2F), Color(0xFF5A4A78), Color(0xFF8A5A1F),
)

@Composable
fun LibraryScreen(viewModel: AppViewModel, onLockOn: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importBooks(uris)
    }
    var removing by remember { mutableStateOf<Book?>(null) }

    val today = LocalDate.now()
    val minutes = ReadingStats.minutesOn(state.readingLog, today)
    val streak = ReadingStats.streak(state.readingLog, today)
    val current = state.books.maxByOrNull { it.lastOpenedAt }?.takeIf { it.lastOpenedAt > 0L }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reading Room", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)
                    if (settings.lockEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(14.dp))
                            Text(" Focus lock on", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                IconButton(onClick = { viewModel.go(Screen.Settings) }) { Icon(Icons.Default.Settings, "Settings") }
            }
        }

        if (!settings.lockEnabled) {
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Focus lock is off", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "Your other apps are reachable. Turn the lock on to keep only your PDFs in front of you.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onLockOn) { Text("Turn lock on") }
                            OutlinedButton(onClick = { viewModel.go(Screen.Apps) }) { Text("All apps") }
                        }
                    }
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text("Today's reading", Modifier.weight(1f))
                        Text("$minutes of ${settings.goalMinutes} min", fontWeight = FontWeight.SemiBold)
                    }
                    LinearProgressIndicator(
                        progress = { (minutes.toFloat() / settings.goalMinutes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        Text(
                            if (streak == 0) "No streak yet" else "$streak-day streak",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (minutes >= settings.goalMinutes) "Goal reached" else "${settings.goalMinutes - minutes} min to go",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (current != null) {
            item {
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { viewModel.openBook(current.id) },
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BookCover(current, Modifier.width(56.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("CONTINUE READING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(current.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("Page ${current.lastPage + 1} of ${current.pages}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LinearProgressIndicator(progress = { current.progress }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your PDFs", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${state.books.size} file${if (state.books.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.books.isEmpty()) {
            item {
                Text(
                    "Nothing here yet. Tap Import PDF and pick the files you want to read.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(state.books, key = { it.id }) { book ->
            BookRow(book, onOpen = { viewModel.openBook(book.id) }, onRemove = { removing = book })
        }

        item {
            OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Text(" Import PDF")
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    removing?.let { book ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove from library?") },
            text = { Text("\"${book.title}\" is removed from Reading Room. The file itself is not deleted.") },
            confirmButton = {
                TextButton(onClick = { viewModel.removeBook(book.id); removing = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BookRow(book: Book, onOpen: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(book, Modifier.width(44.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${book.pages} pages · ${(book.progress * 100).roundToInt()}% read",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More options for ${book.title}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Remove from library") }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(null, book.uri) {
        value = PdfDoc.thumbnail(context, Uri.parse(book.uri), 240)?.asImageBitmap()
    }
    val color = coverColors[abs(book.id.hashCode()) % coverColors.size]
    Box(
        modifier = modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(4.dp)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = thumb
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                book.title,
                modifier = Modifier.padding(4.dp),
                color = Color.White,
                fontFamily = FontFamily.Serif,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
