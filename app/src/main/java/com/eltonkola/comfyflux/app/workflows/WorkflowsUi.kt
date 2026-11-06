package com.eltonkola.comfyflux.app.workflows

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eltonkola.comfyflux.components.ExpandableSection
import com.eltonkola.comfyflux.data.model.ImageGenerationUiState
import com.eltonkola.comfyflux.data.model.WorkflowFile
import com.eltonkola.comfyflux.data.model.workflows
import com.eltonkola.comfyflux.ui.theme.Ikona
import com.eltonkola.comfyflux.ui.theme.ikona.ArrowRight
import com.eltonkola.comfyflux.ui.theme.ikona.WorkflowIc

@Composable
fun WorkflowsUi(
    onSelect: (WorkflowFile) -> Unit,
    uiState: ImageGenerationUiState,
    onBrowseTemplates: () -> Unit
) {

    var samplesOpen by remember { mutableStateOf(true) }
    var serverOpen by remember { mutableStateOf(true) }
    var localOpen by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            // Sections used to be fixed panes: with all three expanded the
            // last one fell off the sheet and nothing could reach it.
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Workflows",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "Select a workflow to generate images with.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.size(8.dp))

        // Public Comfy-Org template library; imports save to the server.
        OutlinedButton(
            onClick = onBrowseTemplates,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Browse Comfy-Org templates", fontSize = 14.sp)
            Spacer(modifier = Modifier.size(6.dp))
            Icon(
                imageVector = Ikona.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.size(8.dp))

        ExpandableSection(
            expanded = samplesOpen,
            title = "Built-in Workflows",
            onExpand = { samplesOpen = !samplesOpen }
        ) {

            workflows.forEach {
                WorkflowRow(it, uiState.workflow == it, onSelect)
                Spacer(modifier = Modifier.size(4.dp))
            }
            Spacer(modifier = Modifier.size(4.dp))

        }

        ExpandableSection(
            expanded = serverOpen,
            title = "Server Workflows",
            onExpand = { serverOpen = !serverOpen }
        ) {
            if (uiState.serverWorkflows.isEmpty()) {
                Text(
                    text = "No workflows saved on the server yet. Save one in the ComfyUI web UI and it appears here.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                uiState.serverWorkflows.forEach {
                    WorkflowRow(it, uiState.workflow == it, onSelect)
                    Spacer(modifier = Modifier.size(4.dp))
                }
            }
            Spacer(modifier = Modifier.size(4.dp))
        }

        ExpandableSection(
            expanded = localOpen,
            title = "Local Workflows",
            onExpand = { localOpen = !localOpen }
        ) {
            Text(
                text = "JSON workflow files from a folder on this device.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.size(8.dp))
            LocalWorkflows(uiState, onClick = {
                onSelect(it)
            })

        }


    }
}


@Composable
fun WorkflowRow(workflow: WorkflowFile, selected: Boolean, onSelect: (WorkflowFile) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp)
            )
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                onSelect(workflow)
            }
    ) {
        Icon(
            modifier = Modifier
                .padding(8.dp)
                .size(40.dp),
            imageVector = Ikona.WorkflowIc,
            contentDescription = null
        )
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = workflow.name,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = workflow.description,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = Ikona.ArrowRight,
            contentDescription = null
        )
    }
}