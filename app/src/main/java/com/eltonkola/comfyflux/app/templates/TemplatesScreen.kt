package com.eltonkola.comfyflux.app.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.eltonkola.comfyflux.app.AppScreens
import com.eltonkola.comfyflux.app.MainViewModel
import com.eltonkola.comfyflux.data.network.TemplateEntry
import com.eltonkola.comfyflux.data.network.TemplateKind
import com.eltonkola.comfyflux.ui.theme.Ikona
import com.eltonkola.comfyflux.ui.theme.ikona.Back
import com.eltonkola.comfyflux.ui.theme.ikona.WorkflowIc

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TemplatesScreen(viewModel: MainViewModel, navController: NavController) {

    val state by viewModel.templateState.collectAsState()
    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<TemplateEntry?>(null) }

    LaunchedEffect(Unit) {
        if (state.entries.isEmpty()) viewModel.refreshTemplates()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comfy-Org templates") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate(AppScreens.Main.screenName) }) {
                        Icon(imageVector = Ikona.Back, contentDescription = "Back")
                    }
                }
            )
        }
    ) { contentPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = "Ready-made workflows from the Comfy-Org library. Import saves the workflow to your server; pick it afterwards under Server Workflows.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search ${if (state.entries.isEmpty()) "" else "${state.entries.size} templates"}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Category filters. "Image input" means the workflow asks for a
            // photo on Create (img2img/editing). Video/3D/audio and Comfy
            // Cloud API workflows start hidden: they cannot run locally.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TemplateKind.entries.forEach { kind ->
                    FilterChip(
                        selected = kind in state.kinds,
                        onClick = { viewModel.toggleTemplateKind(kind) },
                        label = { Text(kind.label, fontSize = 12.sp) }
                    )
                }
            }

            state.message?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.entries.isEmpty() && state.error == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No templates loaded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val filtered = remember(query, state.kinds) {
                    state.entries.filter { it.kind in state.kinds }.let { e ->
                        if (query.isBlank()) e
                        else e.filter { it.title.contains(query.trim(), ignoreCase = true) }
                    }
                }
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Nothing matches - try another search or enable a category chip.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 10.dp, bottom = 40.dp)
                ) {
                    items(filtered) { entry ->
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (state.importingName == null) pending = entry
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.3f)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (entry.thumbUrl != null) {
                                    AsyncImage(
                                        model = entry.thumbUrl,
                                        contentDescription = entry.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Ikona.WorkflowIc,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (state.importingName == entry.fileName) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = entry.title,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entry.kind != TemplateKind.IMAGE) {
                                // Plain image workflows need no label; only
                                // opted-in categories get tagged.
                                Text(
                                    text = entry.kind.label,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pending?.let { entry ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(entry.title) },
            text = {
                Column {
                    Text(
                        "This saves '${entry.fileName}' into your ComfyUI server's saved workflows. " +
                                "The server must have the custom nodes the template uses - the app warns " +
                                "about missing ones after the import."
                    )
                    if (entry.kind == TemplateKind.CLOUD_API) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Heads up: this is a Comfy Cloud workflow. It calls hosted API nodes your server does not run, so it will fail unless you replace those nodes.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    pending = null
                    viewModel.importTemplate(entry)
                }) {
                    Text("Import to server")
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Cancel") }
            }
        )
    }
}
