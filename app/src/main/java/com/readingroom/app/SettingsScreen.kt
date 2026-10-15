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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

private const val ADB_COMMAND = "adb shell dpm set-device-owner com.readingroom.app/.AdminReceiver"

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
    val settings = state.settings
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restoreBackup(uri)
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section("Focus lock") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Lock the phone to Reading Room", fontWeight = FontWeight.Medium)
                        Text(
                            if (settings.lockEnabled) "On. Turning it off needs your PIN." else "Off. Other apps are reachable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.lockEnabled,
                        onCheckedChange = { if (it) onTurnLockOn() else onTurnLockOff() },
                    )
                }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Exit PIN", fontWeight = FontWeight.Medium)
                        Text(
                            if (settings.hasPin) "A 4-digit PIN is set." else "No PIN yet. You choose one when you turn the lock on.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onChangePin) { Text(if (settings.hasPin) "Change" else "Set") }
                }
            }

            Section("Backup to Google Drive") {
                Text(
                    "Saves your notes, highlights, bookmarks, reading place and reading time. It does not include the PDFs. " +
                        "In the file picker, choose Google Drive. To restore on a new phone, install the app, tap Restore and " +
                        "pick the same file, then import your PDFs and the notes come back.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (settings.lastBackupAt == 0L) "No backup yet."
                    else "Last backup: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(settings.lastBackupAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { saveBackup.launch(viewModel.backupFileName()) }) { Text("Back up now") }
                    OutlinedButton(onClick = { openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }) { Text("Restore") }
                }
            }

            Section("Reading") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Daily goal", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    TextButton(onClick = { viewModel.setGoal(settings.goalMinutes - 5) }, enabled = settings.goalMinutes > 5) { Text("−") }
                    Text("${settings.goalMinutes} min", fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { viewModel.setGoal(settings.goalMinutes + 5) }, enabled = settings.goalMinutes < 240) { Text("+") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Keep screen on while reading", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Switch(checked = settings.keepAwake, onCheckedChange = { viewModel.setKeepAwake(it) })
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
    }
}

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
