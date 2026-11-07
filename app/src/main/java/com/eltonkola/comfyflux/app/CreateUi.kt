package com.eltonkola.comfyflux.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.eltonkola.comfyflux.app.presets.PromptPresetsSection
import com.eltonkola.comfyflux.data.model.SIZE_STEPS
import com.eltonkola.comfyflux.data.model.GraphKnob
import com.eltonkola.comfyflux.data.model.GraphKnobKind
import com.eltonkola.comfyflux.data.model.nearestAspect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import com.eltonkola.comfyflux.data.model.ImageInput
import com.eltonkola.comfyflux.data.model.StrengthSlot
import com.eltonkola.comfyflux.data.model.WorkflowNeeds
import androidx.compose.ui.Alignment
import coil.compose.AsyncImage
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.eltonkola.comfyflux.R
import com.eltonkola.comfyflux.app.usecase.nearestSizeIndex
import com.eltonkola.comfyflux.components.HelpIconButton
import com.eltonkola.comfyflux.data.model.ImageGenerationUiState
import com.eltonkola.comfyflux.data.model.ProgressGenerationUIState
import com.eltonkola.comfyflux.ui.theme.Ikona
import com.eltonkola.comfyflux.ui.theme.ikona.ArrowDown
import com.eltonkola.comfyflux.ui.theme.ikona.Clean
import com.eltonkola.comfyflux.ui.theme.ikona.Error
import com.eltonkola.comfyflux.ui.theme.ikona.Image
import com.eltonkola.comfyflux.ui.theme.ikona.Magic
import com.eltonkola.comfyflux.ui.theme.ikona.Paste
import com.eltonkola.comfyflux.ui.theme.ikona.Promp
import com.eltonkola.comfyflux.ui.theme.ikona.Random
import com.eltonkola.comfyflux.ui.theme.ikona.Refresh
import com.eltonkola.comfyflux.ui.theme.ikona.Seed
import com.eltonkola.comfyflux.ui.theme.ikona.WorkflowIc

@Composable
fun CreateUi(
    uiState: ImageGenerationUiState,
    progressUiState: ProgressGenerationUIState,
    viewModel: MainViewModel,
    navController: NavController,
    openWorkflows: () -> Unit
) {

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        ServerConnectionUi(viewModel = viewModel, uiState = uiState)
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.stats == null) {
            Text(
                text = stringResource(R.string.server_disconnected),
                modifier = Modifier.padding(16.dp)
            )

        } else {


            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        openWorkflows()
                    }
                    .padding(8.dp)
            ) {

                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Ikona.WorkflowIc,
                    contentDescription = null
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Workflow: ${uiState.workflow.name}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(
                    modifier = Modifier
                        .size(24.dp),
                    imageVector = Ikona.ArrowDown,
                    contentDescription = null
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { progressUiState.totalProgress() },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center
            ) {

                if (progressUiState.generatedMedia.isNotEmpty()) {
                    ImageGrid(media = progressUiState.generatedMedia) {
                        viewModel.viewMedia(
                            progressUiState.generatedMedia,
                            progressUiState.generatedMedia.indexOf(it)
                        )
                        navController.navigate(AppScreens.ImageViewer.screenName)
                    }
                } else if (progressUiState.previewImage != null) {
                    // Live latent preview straight off the websocket.
                    AsyncImage(
                        model = progressUiState.previewImage,
                        contentDescription = "Live preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Ikona.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
                val timer by viewModel.timer.collectAsState()

                if (progressUiState.executing) {

                    Text(
                        text = timer,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 22.dp)
                            .fillMaxWidth()
                    )

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        if (progressUiState.maxProgress > 0) {
                            CircularProgressIndicator(
                                progress = {
                                    progressUiState.partialProgress()
                                },
                                modifier = Modifier.size(64.dp),
                                color = MaterialTheme.colorScheme.onSecondary,
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(64.dp),
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }
                }
            }

            var tabIndex by remember { mutableIntStateOf(0) }

            val tabs = listOf("Prompt", "Size / Batch", "Advanced")

            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = tabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(text = { Text(title) },
                            selected = tabIndex == index,
                            onClick = { tabIndex = index }
                        )
                    }
                }
                when (tabIndex) {
                    0 -> PromptTab(uiState, progressUiState, viewModel)
                    1 -> SizeTab(uiState, viewModel)
                    2 -> AdvancedTab(uiState, viewModel)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (progressUiState.error.isNotEmpty()) {
                var showFailure by remember { mutableStateOf(false) }
                val hasDetail = progressUiState.errorDetail.isNotEmpty() ||
                        uiState.unavailableFiles.isNotEmpty()
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        text = "Error: ${progressUiState.error}",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (hasDetail) {
                            TextButton(onClick = { showFailure = true }) {
                                Text(text = "Show details")
                            }
                        }
                        TextButton(
                            onClick = { viewModel.generateImages() },
                            enabled = !progressUiState.executing
                        ) {
                            Text(text = "Retry with same settings")
                        }
                    }
                }
                if (showFailure) {
                    FailureDetailDialog(
                        detail = progressUiState.errorDetail.ifBlank { progressUiState.error },
                        unavailableFiles = uiState.unavailableFiles,
                        onDismiss = { showFailure = false }
                    )
                }
            }

        }
    }
}

@Composable
fun PromptTab(
    uiState: ImageGenerationUiState,
    progressUiState: ProgressGenerationUIState,
    viewModel: MainViewModel
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    // Which LoadImage slot the picker answers; "all" = one photo for every slot.
    var pendingSlot by remember { mutableStateOf<String?>(null) }
    // Slot being painted over; the dialog paints the photo that slot already holds.
    var maskSlot by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val slot = pendingSlot
        pendingSlot = null
        uri?.let {
            when (slot) {
                null -> Unit
                "all" -> viewModel.setAllInputImages(it.toString())
                else -> viewModel.setInputImage(slot, it.toString())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Side insets like the other sections; the bottom inset keeps
            // the recent-prompt chips clear of the floating History/Create bar.
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp)
            .verticalScroll(rememberScrollState())
    ) {
        TextField(
            value = uiState.prompt,
            onValueChange = { newText -> viewModel.updatePrompt(newText) },
            label = { Text("Enter your prompt") },
            placeholder = { Text("Enter your prompt") },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            minLines = 4,
            singleLine = false,
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    // While a job runs this queues another one behind it.
                    viewModel.generateImages()
                }
            ),
            leadingIcon = {
                Column(
                    verticalArrangement = Arrangement.Top,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(4.dp)
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = Ikona.Promp,
                        contentDescription = null
                    )
                }
            },
            trailingIcon = {
                Column(
                    verticalArrangement = Arrangement.Top,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(4.dp)
                ) {

                    val clipboard = LocalClipboardManager.current

                    // 4 x 48dp would overflow the 150dp field (the clear
                    // icon used to be cut off): compact buttons fit all four.
                    HelpIconButton(
                        onClick = {
                            viewModel.enrichPrompt()
                        },
                        imageVector = Ikona.Magic,
                        size = 34.dp,
                        iconSize = 18.dp,
                        help = "Rewrite the prompt into a richer one using AI (needs a Groq API key in Settings)"
                    )
                    HelpIconButton(
                        onClick = {
                            clipboard.getText()?.let {
                                viewModel.updatePrompt(uiState.prompt + it)
                            }
                        },
                        imageVector = Ikona.Paste,
                        size = 34.dp,
                        iconSize = 18.dp,
                        help = "Append the clipboard contents to the prompt"
                    )
                    HelpIconButton(
                        onClick = {
                            viewModel.randomPrompt()
                        },
                        imageVector = Ikona.Random,
                        size = 34.dp,
                        iconSize = 18.dp,
                        help = "Fill the prompt with a random entry from the offline prompt library"
                    )
                    HelpIconButton(
                        onClick = {
                            viewModel.updatePrompt("")
                        },
                        imageVector = Ikona.Clean,
                        size = 34.dp,
                        iconSize = 18.dp,
                        help = "Clear the prompt box"
                    )
                }
            }
        )

        if (uiState.enrichingPrompt) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(text = "Enriching prompt with AI...", fontSize = 12.sp)
            }
        }
        uiState.promptEnrichError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // Saved prompts of your own: tags, favorites, one tap to put a
        // proven prompt back in the box.
        PromptPresetsSection(
            currentText = uiState.prompt,
            onApply = { text -> viewModel.updatePrompt(text) }
        )

        // The server-gap report sits above everything else the workflow
        // needs: a run that cannot work should be obvious before the tap.
        WorkflowGapsCard(uiState)

        // Only workflows with a real negative-input show this; others derive
        // the negative from the positive and keep the workflow's own text.
        // img2img: workflows with an image node take a photo from the
        // phone; it is uploaded to the server on Create and written into
        // the node automatically. Placed right under the prompt box and
        // boxed - at the bottom of the tab it was easy to miss.
        ImageNeedsCard(
            uiState = uiState,
            viewModel = viewModel,
            pendingSlot = pendingSlot,
            onPick = { slot ->
                pendingSlot = slot
                imagePicker.launch("image/*")
            },
            onMask = { slot -> maskSlot = slot }
        )

        maskSlot?.let { slot ->
            uiState.imagePicks[slot]?.let { pick ->
                MaskEditorDialog(
                    photoUri = pick.uri,
                    slotId = slot,
                    initial = uiState.paintedMask?.takeIf { it.slotId == slot },
                    onDismiss = { maskSlot = null },
                    onApply = { mask ->
                        viewModel.setPaintedMask(mask)
                        maskSlot = null
                    }
                )
            }
        }

        if (uiState.supportsNegativePrompt) {
            TextField(
                value = uiState.negativePrompt,
                onValueChange = { newText -> viewModel.updateNegativePrompt(newText) },
                label = { Text("Negative prompt (optional)") },
                placeholder = { Text("What to avoid; empty keeps the workflow's own text") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(100.dp),
                minLines = 2,
                singleLine = false,
                trailingIcon = {
                    if (uiState.negativePrompt.isNotEmpty()) {
                        HelpIconButton(
                            onClick = { viewModel.updateNegativePrompt("") },
                            imageVector = Ikona.Clean,
                            size = 34.dp,
                            iconSize = 18.dp,
                            help = "Clear the negative prompt"
                        )
                    }
                }
            )
        }

        if (uiState.recentPrompts.isNotEmpty()) {
            Text(
                text = "Recent prompts",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(uiState.recentPrompts) { recent ->
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.updatePrompt(recent) },
                        label = {
                            Text(
                                text = if (recent.length > 40) recent.take(40) + "…" else recent,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun SizeTab(uiState: ImageGenerationUiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ImageSizeSelector(uiState = uiState, viewModel = viewModel)
        Spacer(modifier = Modifier.height(8.dp))
        AspectRatioSelector(uiState = uiState, viewModel = viewModel)
        Spacer(modifier = Modifier.height(8.dp))
        ImageBatchSelector(uiState = uiState, viewModel = viewModel)
    }
}


@Composable
fun AdvancedTab(uiState: ImageGenerationUiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SeedSelector(uiState = uiState, viewModel = viewModel)
        Spacer(modifier = Modifier.height(8.dp))
        SamplerSettingsSelector(uiState = uiState, viewModel = viewModel)
        Spacer(modifier = Modifier.height(8.dp))
        ModelSlotsSelector(uiState = uiState, viewModel = viewModel)
        Spacer(modifier = Modifier.height(8.dp))
        StrengthGroupsSection(uiState = uiState, viewModel = viewModel)
        Spacer(modifier = Modifier.height(8.dp))
        GraphOverridesSection(uiState = uiState, viewModel = viewModel)
        Spacer(modifier = Modifier.height(8.dp))
        WorkflowSwitchesSelector(uiState = uiState, viewModel = viewModel)
        // The History/Create bar floats over the tab content; keep the last
        // switch clear of it.
        Spacer(modifier = Modifier.height(96.dp))
    }
}


@Composable
fun WorkflowSwitchesSelector(uiState: ImageGenerationUiState, viewModel: MainViewModel) {
    if (uiState.workflowToggles.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = "Workflow switches",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = "On/off options defined by the workflow itself (prompt enhance, model thinking, ...). They override what is saved in the graph.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        uiState.workflowToggles.forEach { toggle ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(text = toggle.label, fontSize = 14.sp)
                    Text(
                        text = "Saved in the workflow: ${if (toggle.current) "on" else "off"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.toggleOverrides[toggle.key] ?: toggle.current,
                    onCheckedChange = { checked -> viewModel.setWorkflowToggle(toggle.key, checked) }
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSlotsSelector(uiState: ImageGenerationUiState, viewModel: MainViewModel) {
    if (uiState.modelSlots.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = "Models",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Choose the model files this run uses; the list comes from the server.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        uiState.modelSlots.forEach { slot ->
            val selected = uiState.modelOverrides[slot.key] ?: slot.current
            var expanded by remember(slot.key) { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = selected,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(slot.label, fontSize = 13.sp) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    slot.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, fontSize = 13.sp) },
                            onClick = {
                                expanded = false
                                viewModel.setModelChoice(slot.key, option)
                            }
                        )
                    }
                }
            }

            // LoRA strength next to the file it belongs to, so "too strong"
            // is fixable without opening the graph.
            slot.strengths.forEach { strength ->
                StrengthSliderRow(
                    key = slot.strengthKey(strength),
                    strength = strength,
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}


/**
 * One strength knob: slider + live value + a reset back to the workflow's
 * own number. Shared by the model-slot LoRA sliders and the apply-node
 * strength section; the override key space ("nodeId|input") is identical.
 */
@Composable
private fun StrengthSliderRow(
    key: String,
    strength: StrengthSlot,
    uiState: ImageGenerationUiState,
    viewModel: MainViewModel
) {
    val override = uiState.strengthOverrides[key]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = (override ?: strength.current).coerceIn(
                strength.range(override).start, strength.range(override).endInclusive
            ),
            onValueChange = { viewModel.setStrength(key, it) },
            valueRange = strength.range(override),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "%.2f".format(override ?: strength.current),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
    if (override != null) {
        TextButton(onClick = { viewModel.setStrength(key, null) }) {
            Text(
                text = "Use the workflow's ${strength.label} strength",
                fontSize = 11.sp
            )
        }
    }
}


/**
 * Strength knobs for nodes that carry no model file of their own (ControlNet
 * apply, adapters): the same sliders as the LoRA rows, so "the effect is too
 * strong" is fixable without opening the graph.
 */
@Composable
fun StrengthGroupsSection(uiState: ImageGenerationUiState, viewModel: MainViewModel) {
    if (uiState.strengthGroups.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        uiState.strengthGroups.forEach { group ->
            Text(
                text = "${group.label} strength (node ${group.nodeId})",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "How hard this node pushes into the run; remembered for this workflow",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            group.strengths.forEach { strength ->
                StrengthSliderRow(
                    key = "${group.nodeId}|${strength.input}",
                    strength = strength,
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}


@Composable
fun ImageSizeSelector(uiState: ImageGenerationUiState, viewModel: MainViewModel) {
    val imageSizes = SIZE_STEPS
    val sliderValues = List(imageSizes.size) { index -> index.toFloat() }

    // Sizes can come from anywhere (a picked photo's aspect ratio yields
    // multiples of 32 like 928), so the slider parks on the NEAREST step
    // instead of looking the value up in this fixed list - an exact
    // lookup returned -1 and crashed the tab.
    val widthIndex = nearestSizeIndex(imageSizes, uiState.width)
    val heightIndex = nearestSizeIndex(imageSizes, uiState.height)

    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
        ) {
            Text(text = "Width: ${uiState.width}Px", fontSize = 13.sp)
            Slider(
                value = widthIndex.toFloat(),
                valueRange = sliderValues.first()..sliderValues.last(),
                onValueChange = {
                    viewModel.setImageWidth(imageSizes[it.toInt()])
                },
                steps = sliderValues.size - 2
            )

            Text(text = "Height: ${uiState.height}Px", fontSize = 13.sp)
            Slider(
                value = heightIndex.toFloat(),
                valueRange = sliderValues.first()..sliderValues.last(),
                onValueChange = {
                    viewModel.setImageHeight(imageSizes[it.toInt()])
                },
                steps = sliderValues.size - 2
            )
        }
        // Preview scales its AREA with total pixels (1024x1024 fills the
        // slot, 256x256 is a quarter of it) so the box shows both the
        // aspect ratio and how big the output actually is.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                val w = uiState.width
                val h = uiState.height
                val maxArea = 1024f * 1024f
                val scale = kotlin.math.sqrt((w * h) / maxArea)
                val previewH = (96f * scale).coerceAtLeast(28f)
                val previewW = previewH * w / h
                Box(
                    modifier = Modifier
                        .size(width = previewW.dp, height = previewH.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {}
            }
            Text(
                text = "${uiState.width}x${uiState.height}",
                fontSize = 12.sp
            )
        }

    }
}


@Composable
fun ImageBatchSelector(uiState: ImageGenerationUiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = "Batch size",
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.batchSize.toString(),
                onValueChange = { text ->
                    if (text.all { char -> char.isDigit() }) {
                        // typing is clamped live so the field always shows a valid count
                        (text.toIntOrNull() ?: 1)
                            .coerceIn(1, 32)
                            .let(viewModel::setBatchSize)
                    }
                },
                label = { Text("Images per run", fontSize = 13.sp) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(140.dp)
            )
            Column {
                HelpIconButton(
                    imageVector = Ikona.ArrowDown,
                    help = "One more image per run",
                    onClick = { viewModel.setBatchSize((uiState.batchSize + 1).coerceIn(1, 32)) },
                    modifier = Modifier.rotate(180f),
                    size = 32.dp,
                    iconSize = 18.dp
                )
                HelpIconButton(
                    imageVector = Ikona.ArrowDown,
                    help = "One fewer image per run",
                    onClick = { viewModel.setBatchSize((uiState.batchSize - 1).coerceIn(1, 32)) },
                    size = 32.dp,
                    iconSize = 18.dp
                )
            }
        }
    }
}


@Composable
fun SeedSelector(uiState: ImageGenerationUiState, viewModel: MainViewModel) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = uiState.isRandom,
                onCheckedChange = {
                    viewModel.setSeed(uiState.seed, it)
                }
            )
            Text(text = "Use random seed", fontSize = 14.sp)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = { viewModel.incrementSeed() },
                enabled = !uiState.isRandom
            ) {
                Text(text = "Seed +1", fontSize = 14.sp)
            }
        }

        TextField(
            enabled = !uiState.isRandom,
            modifier = Modifier.fillMaxWidth(),
            value = uiState.seed.toString(),
            onValueChange = {
                if (it.all { char -> char.isDigit() }) {
                    try {
                        viewModel.setSeed(it.toLong(), uiState.isRandom)
                    } catch (e: Exception) {
                        //empty seed, dont change it
                    }
                }
            },
            label = { Text("Seed value", fontSize = 13.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailingIcon = {
                HelpIconButton(
                    imageVector = Ikona.Refresh,
                    help = "Roll a new random seed value",
                    onClick = {
                        if (!uiState.isRandom) {
                            viewModel.randomSeed()
                        }
                    }
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Ikona.Seed,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SamplerSettingsSelector(uiState: ImageGenerationUiState, viewModel: MainViewModel) {
    // Local text so a half-typed "7." isn't rewritten to "7.0" under the cursor.
    // Re-seeded when a different workflow (or its own defaults) arrive, so the
    // box shows the number that graph really has instead of an empty "0 = keep".
    val guidance = uiState.guidance
    var cfgText by remember(uiState.workflow.name, guidance?.cfgDefault) {
        mutableStateOf(uiState.cfg.takeIf { it > 0f }?.trimmedNumber() ?: "")
    }
    var stepsText by remember(uiState.workflow.name, guidance?.stepsDefault) {
        mutableStateOf(uiState.steps.takeIf { it > 0 }?.toString() ?: "")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = "Sampler settings",
            style = MaterialTheme.typography.titleMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                modifier = Modifier.weight(1f),
                value = cfgText,
                onValueChange = {
                    if (it.all { char -> char.isDigit() || char == '.' }) {
                        cfgText = it
                        try {
                            viewModel.setCfg(it.toFloat())
                        } catch (e: Exception) {
                            //empty or half-typed cfg, keep the workflow value
                            viewModel.setCfg(0f)
                        }
                    }
                },
                label = {
                    Text(
                        text = guidance?.cfgDefault?.let { "CFG (workflow: ${it.trimmedNumber()})" }
                            ?: "CFG (0 = workflow's own)",
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            TextField(
                modifier = Modifier.weight(1f),
                value = stepsText,
                onValueChange = {
                    if (it.all { char -> char.isDigit() }) {
                        stepsText = it
                        try {
                            viewModel.setSteps(it.toInt())
                        } catch (e: Exception) {
                            //empty steps, keep the workflow value
                            viewModel.setSteps(0)
                        }
                    }
                },
                label = {
                    Text(
                        text = guidance?.stepsDefault?.let { "Steps (workflow: $it)" }
                            ?: "Steps (0 = workflow's own)",
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        // CFG/Steps only do something where the graph has a widget for them.
        // Saying so beats a slider that silently changes nothing: FLUX.2 and
        // the Qwen turbo graphs fix their own guidance, and a ManualSigmas
        // graph has no step count at all.
        uiState.guidance?.let { targets ->
            listOfNotNull(
                if (targets.takesSteps || targets.fixedSigmaSchedule) null
                else "This workflow has no step-count widget; Create keeps its own value.",
                if (targets.takesSteps) null
                else if (targets.fixedSigmaSchedule)
                    "Its steps are baked into a sigma list - change that list in ComfyUI." else null,
                if (targets.takesCfg) null
                else "No CFG widget in this graph: guided models set their own guidance."
            ).forEach { note ->
                Text(
                    text = note,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Sampler / scheduler / denoise, straight from the server's own
        // option lists for the samplers this workflow uses.
        uiState.samplerSlots?.takeUnless { it.isEmpty }?.let { slots ->
            Spacer(modifier = Modifier.height(8.dp))
            listOf(
                "Sampler" to (slots.samplerOptions to (uiState.samplerChoice ?: slots.currentSampler)),
                "Scheduler" to (slots.schedulerOptions to (uiState.schedulerChoice ?: slots.currentScheduler))
            ).forEach { (label, pair) ->
                val (options, selected) = pair
                if (options.isEmpty()) return@forEach
                var expanded by remember(label) { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = selected,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = {
                            Text(
                                text = if (label == "Sampler") "Sampler" else "Scheduler",
                                fontSize = 13.sp
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, fontSize = 13.sp) },
                                onClick = {
                                    expanded = false
                                    if (label == "Sampler") {
                                        viewModel.setSamplerChoice(option)
                                    } else {
                                        viewModel.setSchedulerChoice(option)
                                    }
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (slots.hasDenoise) {
                val denoise = uiState.denoise
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = denoise ?: slots.currentDenoise.coerceIn(0.05f, 1f),
                        onValueChange = { viewModel.setDenoise(it) },
                        valueRange = 0.05f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (denoise == null) "keep (${slots.currentDenoise})"
                        else "%.2f".format(denoise),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                if (denoise != null) {
                    TextButton(onClick = { viewModel.setDenoise(null) }) {
                        Text("Use the workflow's denoise", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}


/** Aspect chips for workflows that size through a ResolutionSelector node. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AspectRatioSelector(uiState: ImageGenerationUiState, viewModel: MainViewModel) {
    val slot = uiState.sizeSlot ?: return
    if (slot.aspectOptions.isEmpty()) return
    val chosen = uiState.aspectChoice
        ?: nearestAspect(slot.aspectOptions, uiState.width, uiState.height)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text = "Aspect ratio", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "This workflow sizes itself: the sliders set the pixel count, " +
                    "the aspect decides the shape.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            slot.aspectOptions.forEach { option ->
                FilterChip(
                    selected = option == chosen,
                    onClick = { viewModel.setAspectChoice(option) },
                    label = { Text(option, fontSize = 11.sp) }
                )
            }
        }
        if (uiState.aspectChoice != null) {
            TextButton(onClick = { viewModel.setAspectChoice(null) }) {
                Text("Match the aspect to the sliders", fontSize = 11.sp)
            }
        }
    }
}


/** Full server failure + the files the template asked for that are missing. */
@Composable
fun FailureDetailDialog(
    detail: String,
    unavailableFiles: List<String>,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generation failed") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                Text(
                    text = detail,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (unavailableFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Files the template asked for that this server does not have:",
                        fontSize = 12.sp,
                        style = MaterialTheme.typography.titleSmall
                    )
                    unavailableFiles.forEach {
                        Text(text = it, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(
                        AnnotatedString(
                            buildString {
                                append(detail)
                                if (unavailableFiles.isNotEmpty()) {
                                    appendLine()
                                    appendLine()
                                    appendLine("Missing files:")
                                    unavailableFiles.forEach { appendLine(it) }
                                }
                            }
                        )
                    )
                }
            ) { Text(text = "Copy") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Close") }
        }
    )
}


/**
 * What the selected workflow asks for that this server cannot provide:
 * unknown custom nodes and model files missing from the server's lists.
 * Shown before Create so an impossible run is obvious here, not only after
 * it burns a queue slot to fail.
 */
@Composable
fun WorkflowGapsCard(uiState: ImageGenerationUiState) {
    val gaps = uiState.gaps
    // Substitution notes (the template asked for a file this server lacks and
    // the app swapped in another) belong here too: they explain a run before
    // it happens, not only after it fails.
    val swapped = uiState.unavailableFiles
    if (gaps.isEmpty && swapped.isEmpty()) return

    var showAll by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val fileNotes = swapped + gaps.missingFiles.map { it.note }
    val nodeTypesLine = gaps.missingNodeTypes
        .takeIf { it.isNotEmpty() }
        ?.let { "Node types this server does not know: ${it.joinToString(", ")}" }
    val allLines = fileNotes + listOfNotNull(nodeTypesLine)

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Ikona.Error,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "This workflow needs " +
                            "${swapped.size + gaps.missingFiles.size + gaps.missingNodeTypes.size} " +
                            (if (swapped.size + gaps.missingFiles.size + gaps.missingNodeTypes.size == 1)
                                "thing" else "things") +
                            " your server does not have",
                    fontSize = 14.sp
                )
            }
            // Up to four notes inline; the rest live behind Show all so a
            // big gap list never pushes ImageNeedsCard off the screen.
            fileNotes.take(4).forEach { note ->
                Text(
                    text = note,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            nodeTypesLine?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { showAll = true }) {
                    Text(text = "Show all (${allLines.size})", fontSize = 13.sp)
                }
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(allLines.joinToString("\n"))) }
                ) {
                    Text(text = "Copy list", fontSize = 13.sp)
                }
            }
        }
    }

    if (showAll) {
        AlertDialog(
            onDismissRequest = { showAll = false },
            title = { Text("What this workflow needs") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                ) {
                    allLines.forEach { line ->
                        Text(text = line, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(allLines.joinToString("\n"))) }
                ) { Text(text = "Copy") }
            },
            dismissButton = {
                TextButton(onClick = { showAll = false }) { Text(text = "Close") }
            }
        )
    }
}


/**
 * One card for everything a workflow consumes: how many image slots it has
 * (each with its own photo picker and the file the template ships), whether
 * the size sliders apply, and which node the prompt text lands in. Most
 * "the app broke my workflow" reports are really expectation gaps - this
 * makes what the workflow does to your photo visible before you tap Create.
 */
@Composable
fun ImageNeedsCard(
    uiState: ImageGenerationUiState,
    viewModel: MainViewModel,
    pendingSlot: String?,
    onPick: (String) -> Unit,
    onMask: (String) -> Unit
) {
    val needs = uiState.workflowNeeds
    if (!uiState.supportsImageInput && needs == null) return

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Ikona.Image,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = when {
                        uiState.imageInputs.isEmpty() -> "This workflow takes no image input"
                        uiState.imageInputs.size == 1 -> "This workflow loads one image"
                        else -> "This workflow loads ${uiState.imageInputs.size} images"
                    },
                    fontSize = 14.sp
                )
            }
            needs?.let {
                Text(
                    text = needsSummary(it),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            uiState.imageInputs.forEach { slot ->
                ImageSlotRow(
                    slot = slot,
                    uiState = uiState,
                    active = pendingSlot == slot.id,
                    maskAvailable = uiState.hasMaskInput,
                    masked = uiState.paintedMask?.slotId == slot.id,
                    onPick = { onPick(slot.id) },
                    onClear = { viewModel.setInputImage(slot.id, "") },
                    onMask = { onMask(slot.id) },
                    onClearMask = { viewModel.clearPaintedMask() }
                )
            }
            if (uiState.imageInputs.size > 1) {
                TextButton(onClick = { onPick("all") }) {
                    Text("Use one photo for all", fontSize = 13.sp)
                }
                Text(
                    text = "Only needed when the images are meant to be the same photo; " +
                            "edit templates usually want DIFFERENT files per slot.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Picked photos are uploaded when you tap Create, and the size " +
                        "sliders follow the aspect of the first photo. Slots you leave " +
                        "alone keep the file saved in the workflow.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun ImageSlotRow(
    slot: ImageInput,
    uiState: ImageGenerationUiState,
    active: Boolean,
    maskAvailable: Boolean,
    masked: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onMask: () -> Unit,
    onClearMask: () -> Unit
) {
    val pick = uiState.imagePicks[slot.id]
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        // Picked photo, or the template's own reference file straight off
        // the server input folder, so you can see what each slot expects.
        val model: Any? = when {
            pick != null -> pick.uri
            slot.file.isNotBlank() && uiState.stats != null ->
                "${uiState.server.trimEnd('/')}/view?filename=${android.net.Uri.encode(slot.file, "/")}&type=input"
            else -> null
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = slotLabel(slot),
                fontSize = 13.sp
            )
            Text(
                text = pick?.name?.takeIf { it.isNotBlank() }
                    ?: slot.file.ifBlank { "no file set" },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        OutlinedButton(onClick = onPick) {
            Text(text = if (pick == null) "Choose" else "Change", fontSize = 13.sp)
        }
        // Painting only appears where the graph consumes a MASK: elsewhere
        // it would be a button that changes nothing.
        if (maskAvailable && pick != null) {
            TextButton(onClick = onMask) {
                Text(text = if (masked) "Mask ✓" else "Mask", fontSize = 13.sp)
            }
            if (masked) {
                HelpIconButton(
                    onClick = onClearMask,
                    imageVector = Ikona.Clean,
                    size = 34.dp,
                    iconSize = 18.dp,
                    help = "Drop the painted mask"
                )
            }
        }
        if (pick != null) {
            HelpIconButton(
                onClick = onClear,
                imageVector = Ikona.Clean,
                size = 34.dp,
                iconSize = 18.dp,
                help = "Remove this image"
            )
        }
    }
}

/** "3.5" stays "3.5", "3.0" becomes "3": labels and boxes read like numbers. */
private fun Float.trimmedNumber(): String =
    if (this % 1f == 0f) this.toInt().toString() else this.toString()

private fun slotLabel(slot: ImageInput): String = when {
    slot.primary -> "Main image"
    else -> "Reference ${slot.file.substringAfterLast('/').substringBeforeLast('.')}"
}

private fun needsSummary(needs: WorkflowNeeds): String {
    val parts = mutableListOf<String>()
    if (needs.hasLatent) parts += "output size comes from the sliders"
    if (needs.usesImageLatent) parts += "output follows your image size"
    needs.encoder?.let { parts += "prompt goes to $it" }
    return parts.joinToString(" \u00b7 ")
}

/**
 * Per-node graph tweaks: every widget this workflow exposes that the panels
 * above do not own (FluxGuidance.guidance, ModelSamplingSD3.shift, custom-node
 * knobs). One control per input, keyed "nodeId|input"; the workflow's own
 * value comes back with one tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphOverridesSection(uiState: ImageGenerationUiState, viewModel: MainViewModel) {
    val knobs = uiState.graphKnobs
    if (knobs.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text = "Graph knobs", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Widgets this workflow exposes beyond the panels above; each belongs to one node.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        knobs.forEach { knob ->
            val override = uiState.graphKnobOverrides[knob.key]
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${knob.classType} \u00b7 ${knob.input} (node ${knob.nodeId})",
                fontSize = 13.sp
            )
            when (knob.kind) {
                GraphKnobKind.Boolean -> Row(verticalAlignment = Alignment.CenterVertically) {
                    val current = override?.toBooleanStrictOrNull()
                        ?: knob.current.toBooleanStrictOrNull() ?: false
                    Text(
                        text = if (current) "on" else "off",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = current,
                        onCheckedChange = { viewModel.setGraphKnob(knob.key, it.toString()) }
                    )
                }

                GraphKnobKind.Choice -> GraphChoiceRow(
                    knob = knob,
                    selected = override ?: knob.current,
                    onSelect = { viewModel.setGraphKnob(knob.key, it) }
                )

                else -> {
                    val current = override?.toFloatOrNull() ?: knob.current.toFloatOrNull() ?: 0f
                    val lo = knob.min ?: 0f
                    val hi = knob.max ?: (current * 2f + 10f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = current.coerceIn(lo, hi),
                            onValueChange = { value ->
                                viewModel.setGraphKnob(
                                    knob.key,
                                    if (knob.kind == GraphKnobKind.Int) {
                                        value.toInt().toString()
                                    } else {
                                        "%.2f".format(value)
                                    }
                                )
                            },
                            valueRange = lo..hi,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (knob.kind == GraphKnobKind.Int) {
                                current.toInt().toString()
                            } else {
                                "%.2f".format(current)
                            },
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
            if (override != null) {
                TextButton(onClick = { viewModel.setGraphKnob(knob.key, null) }) {
                    Text("Use the workflow's value", fontSize = 11.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraphChoiceRow(
    knob: GraphKnob,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(knob.input, fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            knob.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 13.sp) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}
