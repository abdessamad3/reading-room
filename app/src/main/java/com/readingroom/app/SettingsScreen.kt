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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private const val ADB_COMMAND = "adb shell dpm set-device-owner com.readingroom.app/.AdminReceiver"

private val TABS = listOf(R.string.tab_lock, R.string.tab_reading, R.string.tab_backup, R.string.tab_about)

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
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            TABS.forEachIndexed { i, name ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(name)) })
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
    Section(stringResource(R.string.sec_focus_lock)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.lock_phone_title), fontWeight = FontWeight.Medium)
                Hint(stringResource(if (settings.lockEnabled) R.string.lock_on_hint else R.string.lock_off_hint))
            }
            Switch(checked = settings.lockEnabled, onCheckedChange = { if (it) onTurnLockOn() else onTurnLockOff() })
        }
    }

    Section(stringResource(R.string.sec_lock_strength)) {
        Card {
            when (lock.strength) {
                LockStrength.LOCKED_DEVICE -> {
                    Text(stringResource(R.string.strength_device), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.strength_device_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                LockStrength.SCREEN_PINNING -> {
                    Text(stringResource(R.string.strength_pinning), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.strength_pinning_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    // A command is always typed left to right, even in Arabic.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        SelectionContainer {
                            Text(ADB_COMMAND, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    Section(stringResource(R.string.sec_exit_pin)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (settings.hasPin) R.string.pin_is_set else R.string.pin_is_none), fontWeight = FontWeight.Medium)
                Hint(stringResource(if (settings.hasPin) R.string.pin_set_hint else R.string.pin_none_hint))
            }
            TextButton(onClick = onChangePin) { Text(stringResource(if (settings.hasPin) R.string.change else R.string.set_pin)) }
        }
    }

    if (settings.lockEnabled) {
        OutlinedButton(
            onClick = onTurnLockOff,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        ) { Text(stringResource(R.string.exit_focus), color = MaterialTheme.colorScheme.error) }
    }
}

// ---------------------------------------------------------------- Reading

@Composable
private fun ReadingTab(settings: Settings, viewModel: AppViewModel) {
    Section(stringResource(R.string.sec_daily_goal)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.goal_title), fontWeight = FontWeight.Medium)
                Hint(stringResource(R.string.goal_hint))
            }
            TextButton(onClick = { viewModel.setGoal(settings.goalMinutes - 5) }, enabled = settings.goalMinutes > 5) { Text("−") }
            Text(stringResource(R.string.minutes_value, settings.goalMinutes), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { viewModel.setGoal(settings.goalMinutes + 5) }, enabled = settings.goalMinutes < 240) { Text("+") }
        }
    }

    Section(stringResource(R.string.sec_page)) {
        Text(stringResource(R.string.page_color), fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderTheme.entries.forEach { t ->
                val label = stringResource(t.labelRes)
                if (t == settings.theme) {
                    Button(onClick = { viewModel.setTheme(t) }) { Text(label) }
                } else {
                    FilledTonalButton(onClick = { viewModel.setTheme(t) }) { Text(label) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.zoom), Modifier.weight(1f), fontWeight = FontWeight.Medium)
            TextButton(onClick = { viewModel.setZoom(settings.zoom - 0.25f) }, enabled = settings.zoom > 1f) { Text("−") }
            Text("${(settings.zoom * 100).roundToInt()}%", fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { viewModel.setZoom(settings.zoom + 0.25f) }, enabled = settings.zoom < 3f) { Text("+") }
        }
    }

    Section(stringResource(R.string.sec_screen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.match_brightness), fontWeight = FontWeight.Medium)
                Hint(stringResource(R.string.brightness_hint))
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
                Text(stringResource(R.string.keep_screen_title), fontWeight = FontWeight.Medium)
                Hint(stringResource(R.string.keep_screen_hint))
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

    Section(stringResource(R.string.sec_backup_drive)) {
        Card {
            Text(
                if (settings.lastBackupAt == 0L) stringResource(R.string.no_backup)
                else stringResource(
                    R.string.last_backup,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(settings.lastBackupAt)),
                ),
                fontWeight = FontWeight.SemiBold,
            )
            Hint(stringResource(R.string.picker_hint))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { saveBackup.launch(viewModel.backupFileName()) }) { Text(stringResource(R.string.back_up_now)) }
            OutlinedButton(
                onClick = { openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
            ) { Text(stringResource(R.string.restore)) }
        }
    }

    Section(stringResource(R.string.sec_what_saved)) {
        Bullet(stringResource(R.string.saved_1))
        Bullet(stringResource(R.string.saved_2))
        Bullet(stringResource(R.string.saved_3))
        Hint(stringResource(R.string.not_saved))
    }

    Section(stringResource(R.string.sec_new_phone)) {
        Bullet(stringResource(R.string.newphone_1))
        Bullet(stringResource(R.string.newphone_2))
        Bullet(stringResource(R.string.newphone_3))
    }

    if (state.pending.isNotEmpty()) {
        Section(stringResource(R.string.sec_waiting)) {
            Card {
                Text(LocalContext.current.resources.getQuantityString(R.plurals.waiting_books, state.pending.size, state.pending.size), fontWeight = FontWeight.SemiBold)
                state.pending.take(8).forEach { Text(it.title, style = MaterialTheme.typography.bodySmall) }
                if (state.pending.size > 8) Hint(stringResource(R.string.and_more, state.pending.size - 8))
                Hint(stringResource(R.string.waiting_hint))
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

    Section(stringResource(R.string.app_name)) {
        Card {
            Text(stringResource(R.string.version, version), fontWeight = FontWeight.SemiBold)
            Hint(stringResource(R.string.about_tagline))
        }
    }

    Section(stringResource(R.string.sec_library)) {
        Card {
            StatRow(stringResource(R.string.stat_books), state.books.size.toString())
            StatRow(stringResource(R.string.stat_notes), notes.toString())
            StatRow(stringResource(R.string.stat_bookmarks), bookmarks.toString())
            StatRow(
                stringResource(R.string.stat_time),
                if (minutes >= 60) stringResource(R.string.time_hm, minutes / 60, minutes % 60) else stringResource(R.string.minutes_value, minutes),
            )
        }
    }

    Section(stringResource(R.string.sec_privacy)) {
        Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium)
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
