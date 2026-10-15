package com.readingroom.app

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LaunchableApp(val label: String, val packageName: String, val icon: ImageBitmap)

/** Every app on the phone. Only reachable while the focus lock is off. */
@Composable
fun AppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<LaunchableApp>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(query, 0)
                .filter { it.activityInfo.packageName != context.packageName }
                .map {
                    LaunchableApp(
                        label = it.loadLabel(pm).toString(),
                        packageName = it.activityInfo.packageName,
                        icon = it.loadIcon(pm).toBitmap(96, 96, Bitmap.Config.ARGB_8888).asImageBitmap(),
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.all_apps), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        val list = apps
        if (list == null) {
            Text(stringResource(R.string.loading_apps), Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                context.packageManager.getLaunchIntentForPackage(app.packageName)?.let(context::startActivity)
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        Text(app.label)
                    }
                }
            }
        }
    }
}
