package com.eltonkola.comfyflux.app.drawer

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.eltonkola.comfyflux.app.AppScreens
import com.eltonkola.comfyflux.app.MainViewModel
import com.eltonkola.comfyflux.app.thumbnailRequest
import com.eltonkola.comfyflux.components.LoadingUi
import com.eltonkola.comfyflux.app.MediaTile
import com.eltonkola.comfyflux.data.model.HistoryItem
import com.eltonkola.comfyflux.data.model.MediaItem
import com.eltonkola.comfyflux.data.model.summary
import com.eltonkola.comfyflux.data.model.Triage
import com.eltonkola.comfyflux.data.model.TriageFilter
import com.eltonkola.comfyflux.data.model.filterHistory
import com.eltonkola.comfyflux.data.model.triageCounts
import com.eltonkola.comfyflux.data.model.runParams
import com.eltonkola.comfyflux.ui.theme.Ikona
import com.eltonkola.comfyflux.ui.theme.ikona.Copy
import com.eltonkola.comfyflux.ui.theme.ikona.Delete
import com.eltonkola.comfyflux.ui.theme.ikona.Error
import com.eltonkola.comfyflux.ui.theme.ikona.Image
import com.eltonkola.comfyflux.ui.theme.ikona.Refresh
import com.eltonkola.comfyflux.components.HelpIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTab(viewModel: MainViewModel, navController: NavController) {

    val uiState by viewModel.historyUiState.collectAsState()

    LaunchedEffect(key1 = Unit) {
        if (uiState.history.isEmpty()) {
            viewModel.loadHistory()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All images on server") },
                actions = {
                    if (uiState.silentLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    HelpIconButton(
                        onClick = { viewModel.loadHistory() },
                        imageVector = Ikona.Refresh,
                        help = "Reload the history from the server"
                    )
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .padding(it)
                .fillMaxSize()
        ) {
            if (uiState.loading) {
                LoadingUi(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            } else if (uiState.error) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Ikona.Error, contentDescription = "error")
                    Text(text = "Error loading history!")
                    TextButton(onClick = { viewModel.loadHistory() }) {
                        Text(text = "Retry")
                    }
                }
            } else {
                if (uiState.history.isEmpty()) {
                    Text(text = "No History. Start creating something!")
                } else {
                    val visible = filterHistory(uiState.history, uiState.triage, uiState.triageFilter)
                    var confirmDiscard by remember { mutableStateOf(false) }

                    TriageFilterBar(
                        counts = triageCounts(uiState.history, uiState.triage),
                        selected = uiState.triageFilter,
                        discarded = uiState.triage.values.count { it == Triage.Discarded },
                        onSelect = { viewModel.setTriageFilter(it) },
                        onDeleteDiscarded = { confirmDiscard = true }
                    )
                    if (confirmDiscard) {
                        AlertDialog(
                            onDismissRequest = { confirmDiscard = false },
                            title = { Text("Delete the discarded runs?") },
                            text = {
                                Text(
                                    "Everything marked discarded is deleted on the server " +
                                            "(its images too). Your kept and unmarked runs stay."
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        confirmDiscard = false
                                        viewModel.deleteDiscarded()
                                    }
                                ) { Text("Delete") }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmDiscard = false }) { Text("Keep them") }
                            }
                        )
                    }

                    if (visible.isEmpty()) {
                        Text(
                            text = when (uiState.triageFilter) {
                                TriageFilter.Kept -> "Nothing marked kept yet - tap the star on a run you want to keep."
                                TriageFilter.Discarded -> "Nothing marked discarded. Mark the bad ones, then delete them in one go."
                                TriageFilter.Unmarked -> "Every run here is marked."
                                TriageFilter.All -> "No History. Start creating something!"
                            },
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {

                        items(visible) { row ->
                            HistoryRowUi(row, openMedia = { media, index ->
                                viewModel.viewMedia(media, index)
                                navController.navigate(AppScreens.ImageViewer.screenName)

                            },
                                onDelete = {
                                    viewModel.deleteHistory(row.id)
                                },
                                mark = uiState.triage[row.id],
                                onMark = { newMark -> viewModel.setTriage(row.id, newMark) },
                                onRerun = {
                                    viewModel.rerunFromHistory(row)
                                },
                                onUseSettings = {
                                    viewModel.applyRunSettings(row)
                                }
                            )
                            Spacer(modifier = Modifier.size(1.dp))
                        }

                    }
                }
            }
        }
    }
}

/** Filter chips for the local keep/discard marks, plus the bulk delete. */
@Composable
fun TriageFilterBar(
    counts: Map<TriageFilter, Int>,
    selected: TriageFilter,
    discarded: Int,
    onSelect: (TriageFilter) -> Unit,
    onDeleteDiscarded: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TriageFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = {
                    Text("${filter.label} (${counts[filter] ?: 0})", fontSize = 11.sp)
                }
            )
        }
        if (discarded > 0) {
            TextButton(onClick = onDeleteDiscarded) {
                Text(
                    text = "Delete $discarded discarded",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@SuppressLint("SuspiciousIndentation")
@Composable
fun HistoryRowUi(
    item: HistoryItem,
    openMedia: (List<MediaItem>, Int) -> Unit,
    onDelete: () -> Unit,
    onRerun: () -> Unit,
    onUseSettings: () -> Unit,
    mark: Triage? = null,
    onMark: (Triage?) -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    var showParams by remember { mutableStateOf(false) }
    val params = remember(item) { item.prompt.runParams() }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Takes the leftover width so the keep/discard buttons never
            // push the summary into a second line on a narrow phone.
            Text(
                text = "${item.media.summary()} · success: ${item.success} - completed: ${item.completed}",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // The phone's own marks; nothing is sent to the server.
            TextButton(onClick = { onMark(if (mark == Triage.Kept) null else Triage.Kept) }) {
                Text(text = if (mark == Triage.Kept) "★ kept" else "☆ keep", fontSize = 11.sp)
            }
            TextButton(onClick = { onMark(if (mark == Triage.Discarded) null else Triage.Discarded) }) {
                Text(
                    text = if (mark == Triage.Discarded) "✕ discarded" else "✕ discard",
                    fontSize = 11.sp,
                    color = if (mark == Triage.Discarded) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { showParams = !showParams }) {
                Text(
                    text = if (showParams) "Hide settings" else "Settings",
                    fontSize = 11.sp
                )
            }
        }
        if (showParams) {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = params.summary().ifBlank { "no sampler controls in this workflow" },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                params.models.forEach {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onUseSettings,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text("Use these settings on Create", fontSize = 11.sp)
                }
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (item.media.isNotEmpty()) {
                items(item.media) { media ->
                    MediaTile(
                        item = media,
                        pixelSize = 256,
                        modifier = Modifier.size(120.dp),
                        onClick = { openMedia(item.media, item.media.indexOf(media)) }
                    )
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Ikona.Image,
                            contentDescription = "failed",
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
            }
            item {
                Row {
                    Text(
                        modifier = Modifier.width(240.dp),
                        text = item.prompt.getPrompt(),
                        fontSize = 10.sp
                    )
                    Column {


                        HelpIconButton(
                            onClick = { onRerun() },
                            imageVector = Ikona.Refresh,
                            help = "Generate again with this entry's exact settings"
                        )

                        HelpIconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(item.prompt.getPrompt()))
                            },
                            imageVector = Ikona.Copy,
                            help = "Copy the prompt to the clipboard"
                        )

                        HelpIconButton(
                            onClick = { onDelete() },
                            imageVector = Ikona.Delete,
                            help = "Delete this generation from the server history"
                        )
                    }
                }
            }
        }
    }
}