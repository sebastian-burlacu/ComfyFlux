package com.eltonkola.comfyflux.app.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.eltonkola.comfyflux.app.AppScreens
import com.eltonkola.comfyflux.data.LogEntry
import com.eltonkola.comfyflux.data.LogLevel
import com.eltonkola.comfyflux.data.LogStore
import com.eltonkola.comfyflux.ui.theme.Ikona
import com.eltonkola.comfyflux.ui.theme.ikona.Back
import com.eltonkola.comfyflux.ui.theme.ikona.Clean
import com.eltonkola.comfyflux.ui.theme.ikona.Download
import org.koin.compose.koinInject
import com.eltonkola.comfyflux.components.HelpIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(navController: NavController) {

    val logStore: LogStore = koinInject()
    val entries by logStore.entries.collectAsState()
    var errorsOnly by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val exportLogs = {
        try {
            val dir = java.io.File(context.cacheDir, "logs").apply { mkdirs() }
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
            val file = java.io.File(dir, "comfyflux-logs-$stamp.txt")
            file.writeText(logStore.exportText())
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Export logs"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val shown = if (errorsOnly) entries.filter { it.level == LogLevel.ERROR } else entries

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Logs") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Ikona.Back,
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    HelpIconButton(
                        onClick = { exportLogs() },
                        imageVector = Ikona.Download,
                        help = "Save the log to a text file and share it"
                    )
                    HelpIconButton(
                        onClick = { logStore.clear() },
                        imageVector = Ikona.Clean,
                        help = "Empty the log buffer"
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            Text(
                text = "Server events are what your ComfyUI instance reported; client events are app-side failures (connections, timeouts, workflow conversion).",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.size(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = errorsOnly,
                    onClick = { errorsOnly = !errorsOnly },
                    label = { Text("Errors only") }
                )
                Text(
                    text = "${shown.size} of ${entries.size} events",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            if (shown.isEmpty()) {
                Text(
                    text = "No events yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(shown.asReversed()) { entry ->
                    LogRow(entry)
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogLevel.TRACE -> MaterialTheme.colorScheme.outlineVariant
        LogLevel.DEBUG -> MaterialTheme.colorScheme.outline
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.timeString(),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = entry.level.name,
                fontSize = 11.sp,
                color = color
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = entry.source,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.size(2.dp))
        Text(
            text = entry.message,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}
