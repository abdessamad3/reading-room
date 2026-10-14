package com.readingroom.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val PIN_LENGTH = 4

/** What the full-screen PIN pad is being used for. */
enum class PinMode { VERIFY, CREATE }

/**
 * Full-screen PIN entry. In CREATE mode the PIN is typed twice. [onDone] gets the confirmed PIN
 * (CREATE) or the typed PIN (VERIFY); it returns an error text to show and clear the entry, or null.
 */
@Composable
fun PinScreen(
    title: String,
    subtitle: String,
    mode: PinMode,
    onCancel: () -> Unit,
    onDone: (String) -> String?,
) {
    var entry by rememberSaveable { mutableStateOf("") }
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val heading = when {
        mode == PinMode.CREATE && first != null -> "Type it once more"
        else -> title
    }

    fun press(digit: String) {
        if (entry.length >= PIN_LENGTH) return
        entry += digit
        error = null
        if (entry.length < PIN_LENGTH) return
        val typed = entry
        entry = ""
        when {
            mode == PinMode.VERIFY -> error = onDone(typed)
            first == null -> first = typed
            first == typed -> error = onDone(typed)
            else -> {
                first = null
                error = "The two PINs did not match. Start again."
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(heading, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            error ?: subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.semantics { contentDescription = "${entry.length} of $PIN_LENGTH digits entered" }) {
            repeat(PIN_LENGTH) { i ->
                val on = i < entry.length
                Box(
                    Modifier
                        .size(14.dp)
                        .border(2.dp, if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { Key(it) { press(it) } } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Key("⌫", label = "Delete") { entry = entry.dropLast(1) }
                Key("0") { press("0") }
                Spacer(Modifier.size(64.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun Key(text: String, label: String = text, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}
