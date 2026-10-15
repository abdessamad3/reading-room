package com.readingroom.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private const val ADB_COMMAND = "adb shell dpm set-device-owner com.readingroom.app/.AdminReceiver"

private val TABS = listOf("Lock", "Reading", "Backup", "About")

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    lock: LockController,
    onBack: () -> Unit,
    onTurnLockOn: () -> Unit,
    onTurnLockOff: () -> Unit,
    onChangePin: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            TABS.forEachIndexed { i, name ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(name) })
            }
        }
        // Each tab scrolls on its own and starts at the top when you switch to it.
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            when (tab) {
                0 -> LockTab(state, lock, onTurnLockOn, onTurnLockOff, onChangePin)
                1 -> ReadingTab(state.settings, viewModel)
                2 -> BackupTab(state, viewModel)
                else -> AboutTab(state)
            }
        }
    }
}

// ---------------------------------------------------------------- Lock

@Composable
private fun LockTab(
    state: AppState,
    lock: LockController,
    onTurnLockOn: () -> Unit,
    onTurnLockOff: () -> Unit,
    onChangePin: () -> Unit,
) {
    val settings = state.settings
    Section("Focus lock") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Lock the phone to Reading Room", fontWeight = FontWeight.Medium)
                Hint(if (settings.lockEnabled) "On. Turning it off needs your PIN." else "Off. Other apps are reachable.")
            }
            Switch(checked = settings.lockEnabled, onCheckedChange = { if (it) onTurnLockOn() else onTurnLockOff() })
        }
    }

    Section("Lock strength") {
        Card {
            when (lock.strength) {
                LockStrength.LOCKED_DEVICE -> {
                    Text("Locked device", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Strongest lock. Home, Recents and notifications are blocked, and the lock comes back after a restart.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                LockStrength.SCREEN_PINNING -> {
                    Text("Screen pinning", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Android may ask you to confirm the first time. Holding Back and Recents together undoes it. " +
                            "For the strongest lock, make Reading Room the Device Owner once from a computer " +
                            "(remove Google accounts from the phone first, you can add them back afterwards):",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SelectionContainer {
                        Text(ADB_COMMAND, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    Section("Exit PIN") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (settings.hasPin) "A 4-digit PIN is set" else "No PIN yet", fontWeight = FontWeight.Medium)
                Hint(if (settings.hasPin) "You need it to turn the lock off." else "You choose one when you turn the lock on.")
            }
            TextButton(onClick = onChangePin) { Text(if (settings.hasPin) "Change" else "Set") }
        }
    }

    if (settings.lockEnabled) {
        OutlinedButton(
            onClick = onTurnLockOff,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        ) { Text("Exit focus mode", color = MaterialTheme.colorScheme.error) }
    }
}

// ---------------------------------------------------------------- Reading

@Composable
private fun ReadingTab(settings: Settings, viewModel: AppViewModel) {
    Section("Daily goal") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Minutes to read each day", fontWeight = FontWeight.Medium)
                Hint("Shown as a bar on your library.")
            }
            TextButton(onClick = { viewModel.setGoal(settings.goalMinutes - 5) }, enabled = settings.goalMinutes > 5) { Text("−") }
            Text("${settings.goalMinutes} min", fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { viewModel.setGoal(settings.goalMinutes + 5) }, enabled = settings.goalMinutes < 240) { Text("+") }
        }
    }

    Section("Page") {
        Text("Page color", fontWeight = FontWeight.Medium)
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
            Text("Zoom", Modifier.weight(1f), fontWeight = FontWeight.Medium)
            TextButton(onClick = { viewModel.setZoom(settings.zoom - 0.25f) }, enabled = settings.zoom > 1f) { Text("−") }
            Text("${(settings.zoom * 100).roundToInt()}%", fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { viewModel.setZoom(settings.zoom + 0.25f) }, enabled = settings.zoom < 3f) { Text("+") }
        }
    }

    Section("Screen") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Match phone brightness", fontWeight = FontWeight.Medium)
                Hint("Turn off to set the brightness for the reader only.")
            }
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
            Column(Modifier.weight(1f)) {
                Text("Keep screen on while reading", fontWeight = FontWeight.Medium)
                Hint("The screen will not go to sleep in the middle of a page.")
            }
            Switch(checked = settings.keepAwake, onCheckedChange = { viewModel.setKeepAwake(it) })
        }
    }
}

// ---------------------------------------------------------------- Backup

@Composable
private fun BackupTab(state: AppState, viewModel: AppViewModel) {
    val settings = state.settings
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restoreBackup(uri)
    }

    Section("Back up to Google Drive") {
        Card {
            Text(
                if (settings.lastBackupAt == 0L) "No backup yet"
                else "Last backup: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(settings.lastBackupAt))}",
                fontWeight = FontWeight.SemiBold,
            )
            Hint("In the file picker, choose Google Drive.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { saveBackup.launch(viewModel.backupFileName()) }) { Text("Back up now") }
            OutlinedButton(
                onClick = { openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
            ) { Text("Restore") }
        }
    }

    Section("What is saved") {
        Bullet("Notes and highlights, with the words you highlighted")
        Bullet("Bookmarks and the page you stopped at in each book")
        Bullet("Reading time, daily goal and reading settings")
        Hint("Not saved: the PDF files themselves, your PIN and the lock.")
    }

    Section("On a new phone") {
        Bullet("Install Reading Room and open Settings, then Backup")
        Bullet("Tap Restore and pick the same file from Google Drive")
        Bullet("Import your PDFs. Each book gets its notes back, matched by its name and page count")
    }

    if (state.pending.isNotEmpty()) {
        Section("Waiting for their PDF") {
            Card {
                Text("${state.pending.size} book${if (state.pending.size == 1) "" else "s"} with saved notes", fontWeight = FontWeight.SemiBold)
                state.pending.take(8).forEach { Text(it.title, style = MaterialTheme.typography.bodySmall) }
                if (state.pending.size > 8) Hint("and ${state.pending.size - 8} more")
                Hint("Import these PDFs from the library and the notes appear.")
            }
        }
    }
}

// ---------------------------------------------------------------- About

@Composable
private fun AboutTab(state: AppState) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val notes = state.books.sumOf { it.annotations.size }
    val bookmarks = state.books.sumOf { it.bookmarks.size }
    val minutes = state.readingLog.values.sum() / 60

    Section("Reading Room") {
        Card {
            Text("Version $version", fontWeight = FontWeight.SemiBold)
            Hint("A quiet place to read your PDFs, with a lock that keeps other apps away.")
        }
    }

    Section("Your library") {
        Card {
            StatRow("Books", state.books.size.toString())
            StatRow("Notes and highlights", notes.toString())
            StatRow("Bookmarks", bookmarks.toString())
            StatRow("Time read", if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min")
        }
    }

    Section("Privacy") {
        Text(
            "Everything stays on this phone. The app only reads the PDFs you choose to import, and sends nothing anywhere " +
                "unless you save a backup file to your Google Drive yourself.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ---------------------------------------------------------------- Pieces

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = MaterialTheme.colorScheme.primary)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
