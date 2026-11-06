package com.eltonkola.comfyflux.app.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eltonkola.comfyflux.components.HelpIconButton
import com.eltonkola.comfyflux.ui.theme.Ikona
import com.eltonkola.comfyflux.ui.theme.ikona.Clean
import com.eltonkola.comfyflux.ui.theme.ikona.Delete
import com.eltonkola.comfyflux.ui.theme.ikona.Search
import org.koin.compose.koinInject

/**
 * Saved user prompts on the create screen: search them, star the keepers,
 * apply one over the current prompt, or file the prompt you are typing away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptPresetsSection(
    currentText: String,
    onApply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val useCase: PresetUseCase = koinInject()
    val presets by useCase.presets.collectAsState()

    var query by remember { mutableStateOf("") }
    var favoriteOnly by remember { mutableStateOf(false) }
    var activeTag by remember { mutableStateOf<String?>(null) }
    var saveName by remember { mutableStateOf("") }
    var saveTags by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<PromptPreset?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    val allTags = presets.flatMap { it.tags }.distinct().sorted()
    val matches = filterPresets(presets, query, favoriteOnly, activeTag)
    val suggestedName = remember(currentText) { suggestName(currentText) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = "Prompt presets",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = "Prompts you keep retyping, saved on this device. Apply one, then edit it freely; the preset itself stays untouched.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search presets", fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Ikona.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    HelpIconButton(
                        onClick = { query = "" },
                        imageVector = Ikona.Clean,
                        help = "Clear the search box",
                        size = 34.dp,
                        iconSize = 18.dp
                    )
                }
            }
        )

        if (presets.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = favoriteOnly,
                    onClick = { favoriteOnly = !favoriteOnly },
                    label = { Text("\u2605 Favorites", fontSize = 11.sp) }
                )
                allTags.forEach { tag ->
                    FilterChip(
                        selected = tag == activeTag,
                        onClick = { activeTag = if (tag == activeTag) null else tag },
                        label = { Text(tag, fontSize = 11.sp) }
                    )
                }
            }
        }

        if (presets.isEmpty()) {
            Text(
                text = "No presets yet. Type a prompt you like and save it below; it will show up here on any run.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else if (matches.isEmpty()) {
            Text(
                text = "Nothing matches this search. Clear the filters to see all ${presets.size}.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        matches.take(8).forEach { preset ->
            PresetCard(
                preset = preset,
                onApply = {
                    onApply(preset.text)
                    note = "Applied \u201c${preset.name}\u201d (${preset.text.length} chars) to the prompt box."
                },
                onToggleFavorite = { useCase.setFavorite(preset.id, !preset.favorite) },
                onDelete = { pendingDelete = preset }
            )
        }
        if (matches.size > 8) {
            Text(
                text = "${matches.size - 8} more match; narrow the search to see them.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        note?.let {
            Text(
                text = it,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Save current prompt",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = saveName,
                onValueChange = { saveName = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(
                        text = suggestedName.ifBlank { "Preset name" },
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
            OutlinedTextField(
                value = saveTags,
                onValueChange = { saveTags = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("tags, comma separated", fontSize = 13.sp) }
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            OutlinedButton(
                enabled = currentText.isNotBlank(),
                onClick = {
                    useCase.saveFromCurrent(saveName, currentText, saveTags)
                    val saved = useCase.presets.value.maxByOrNull { it.createdAt }
                    note = saved?.let { "Saved \u201c${it.name}\u201d as a preset." } ?: note
                    saveName = ""
                    saveTags = ""
                }
            ) {
                Text("Save", fontSize = 13.sp)
            }
            if (currentText.isBlank()) {
                Text(
                    text = "Type a prompt first.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    pendingDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete preset?") },
            text = {
                Text(
                    text = "\u201c${preset.name}\u201d will be gone from this device. Apply it once first if you still need the text.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        useCase.delete(preset.id)
                        pendingDelete = null
                        note = "Deleted \u201c${preset.name}\u201d."
                    }
                ) { Text("Delete", fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Keep", fontSize = 13.sp)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetCard(
    preset: PromptPreset,
    onApply: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = when {
            preset.favorite ->
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
            else ->
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.30f)
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = preset.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (preset.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    preset.tags.forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            Text(
                text = preset.text,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                TextButton(onClick = onApply) {
                    Text("Apply", fontSize = 13.sp)
                }
                TextButton(onClick = onToggleFavorite) {
                    Text(
                        text = if (preset.favorite) "\u2605 Favorite" else "\u2606 Favorite",
                        fontSize = 13.sp
                    )
                }
                HelpIconButton(
                    onClick = onDelete,
                    imageVector = Ikona.Delete,
                    help = "Delete this preset",
                    size = 34.dp,
                    iconSize = 18.dp
                )
            }
        }
    }
}
