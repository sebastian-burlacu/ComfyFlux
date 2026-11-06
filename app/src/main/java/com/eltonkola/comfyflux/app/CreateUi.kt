package com.eltonkola.comfyflux.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
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
import com.eltonkola.comfyflux.components.HelpIconButton
import com.eltonkola.comfyflux.data.model.ImageGenerationUiState
import com.eltonkola.comfyflux.data.model.ProgressGenerationUIState
import com.eltonkola.comfyflux.ui.theme.Ikona
import com.eltonkola.comfyflux.ui.theme.ikona.ArrowDown
import com.eltonkola.comfyflux.ui.theme.ikona.Clean
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

                if (progressUiState.generatedImages.isNotEmpty()) {
                    ImageGrid(images = progressUiState.generatedImages) {
                        viewModel.viewImage(
                            progressUiState.generatedImages,
                            progressUiState.generatedImages.indexOf(it)
                        )
                        navController.navigate(AppScreens.ImageViewer.screenName)
                    }
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
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        text = "Error: ${progressUiState.error}",
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = { viewModel.generateImages() },
                        enabled = !progressUiState.executing
                    ) {
                        Text(text = "Retry with same settings")
                    }
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
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.setInputImage(it.toString()) }
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

        // Only workflows with a real negative-input show this; others derive
        // the negative from the positive and keep the workflow's own text.
        // img2img: workflows with an image node take a photo from the
        // phone; it is uploaded to the server on Create and written into
        // the node automatically. Placed right under the prompt box and
        // boxed - at the bottom of the tab it was easy to miss.
        if (uiState.supportsImageInput) {
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
                            text = "This workflow loads an image",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                            Text(
                                text = if (uiState.inputImageName.isBlank()) "Choose image"
                                else if (uiState.inputImageName.length > 20) uiState.inputImageName.take(20) + "…"
                                else uiState.inputImageName,
                                fontSize = 13.sp
                            )
                        }
                        if (uiState.inputImageUri.isNotEmpty()) {
                            HelpIconButton(
                                onClick = { viewModel.setInputImage("") },
                                imageVector = Ikona.Clean,
                                size = 34.dp,
                                iconSize = 18.dp,
                                help = "Remove the input image"
                            )
                        }
                    }
                    Text(
                        text = if (uiState.inputImageUri.isNotEmpty())
                            "Uploaded when you tap Create and written into the workflow's main image input; the size below is matched to its aspect. Other reference images in the workflow keep the template's own files."
                        else "Pick one for img2img, or leave unset to use the file saved in the workflow.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
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
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}


@Composable
fun ImageSizeSelector(uiState: ImageGenerationUiState, viewModel: MainViewModel) {
    val imageSizes = listOf(256, 320, 384, 448, 512, 576, 640, 704, 768, 832, 896, 960, 1024)
    val sliderValues = List(imageSizes.size) { index -> index.toFloat() }

    var widthIndex by remember { mutableIntStateOf(imageSizes.indexOf(uiState.width)) }
    var heightIndex by remember { mutableIntStateOf(imageSizes.indexOf(uiState.height)) }

    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
        ) {
            Text(text = "Width: ${imageSizes[widthIndex]}Px", fontSize = 13.sp)
            Slider(
                value = widthIndex.toFloat(),
                valueRange = sliderValues.first()..sliderValues.last(),
                onValueChange = {
                    widthIndex = it.toInt()
                    viewModel.setImageWidth(imageSizes[widthIndex])
                },
                steps = sliderValues.size
            )

            Text(text = "Height: ${imageSizes[heightIndex]}Px", fontSize = 13.sp)
            Slider(
                value = heightIndex.toFloat(),
                valueRange = sliderValues.first()..sliderValues.last(),
                onValueChange = {
                    heightIndex = it.toInt()
                    viewModel.setImageHeight(imageSizes[heightIndex])
                },
                steps = sliderValues.size
            )
        }
        // Preview scales its AREA with total pixels (1024x1024 fills the
        // slot, 256x256 is a quarter of it) so the box shows both the
        // aspect ratio and how big the output actually is.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                val w = imageSizes[widthIndex]
                val h = imageSizes[heightIndex]
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
                text = "${imageSizes[widthIndex]}x${imageSizes[heightIndex]}",
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
fun SamplerSettingsSelector(uiState: ImageGenerationUiState, viewModel: MainViewModel) {
    // Local text so a half-typed "7." isn't rewritten to "7.0" under the cursor;
    // empty input means "0", i.e. keep the workflow's own value.
    var cfgText by remember { mutableStateOf(if (uiState.cfg > 0f) uiState.cfg.toString() else "") }
    var stepsText by remember { mutableStateOf(if (uiState.steps > 0) uiState.steps.toString() else "") }

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
                label = { Text("CFG (0 = keep)", fontSize = 13.sp) },
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
                label = { Text("Steps (0 = keep)", fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
}

