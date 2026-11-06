package com.eltonkola.comfyflux.app.workflows

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eltonkola.comfyflux.data.model.ImageGenerationUiState
import com.eltonkola.comfyflux.data.model.WorkflowFile
import com.eltonkola.comfyflux.ui.theme.Ikona
import com.eltonkola.comfyflux.ui.theme.ikona.Error
import com.eltonkola.comfyflux.ui.theme.ikona.Folder
import com.eltonkola.comfyflux.ui.theme.ikona.OpenFolder
import com.eltonkola.comfyflux.ui.theme.ikona.Refresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.eltonkola.comfyflux.components.HelpIconButton


class FolderViewModel(application: Application) : AndroidViewModel(application) {
    private val _files = MutableStateFlow<List<WorkflowFile>>(emptyList())
    val files = _files.asStateFlow()

    private val _selectedFolder = MutableStateFlow<Uri?>(null)
    val selectedFolder = _selectedFolder.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission = _hasPermission.asStateFlow()

    init {
        loadSavedFolder()
    }

    /** Re-read the saved folder; catches files dropped in after the pick. */
    fun refresh() {
        val uri = _selectedFolder.value ?: savedFolderUri() ?: return
        if (!checkPermission(uri)) {
            _hasPermission.value = false
            return
        }
        rememberFolderName(uri)
        viewModelScope.launch(Dispatchers.IO) {
            val documentsTree = DocumentFile.fromTreeUri(getApplication(), uri)
            _files.value = documentsTree?.listFiles()
                ?.filter { it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true }
                ?.map { it.normalize() }
                ?: emptyList()
        }
    }

    /** Human-readable folder name (raw tree URIs are long encoded gibberish). */
    private val _folderName = MutableStateFlow<String?>(null)
    val folderName = _folderName.asStateFlow()

    private fun rememberFolderName(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = DocumentFile.fromTreeUri(getApplication(), uri)
            _folderName.value = doc?.name ?: uri.lastPathSegment?.substringAfterLast(':')
        }
    }

    private fun savedFolderUri(): Uri? =
        getApplication<Application>().getSharedPreferences("FolderPrefs", Application.MODE_PRIVATE)
            .getString("SELECTED_FOLDER_URI", null)?.let { Uri.parse(it) }

    fun loadFiles(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {

            // Take persistent permission immediately
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                takeFlags
            )

            if (checkPermission(uri)) {
                val documentsTree = DocumentFile.fromTreeUri(getApplication(), uri)
                // Only actual JSON workflow files show up; folders and other
                // files in there would just fail to load anyway.
                val fileList = documentsTree?.listFiles()
                    ?.filter { it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true }
                    ?.toList() ?: emptyList()
                _files.value = fileList.map { it.normalize() }
                _selectedFolder.value = uri
                _hasPermission.value = true
                rememberFolderName(uri)
                saveSelectedFolderUri(uri)
            } else {
                _hasPermission.value = false
                _selectedFolder.value = null
                _files.value = emptyList()
            }
        }
    }

    private fun saveSelectedFolderUri(uri: Uri) {
        val sharedPreferences = getApplication<Application>().getSharedPreferences(
            "FolderPrefs",
            Application.MODE_PRIVATE
        )
        sharedPreferences.edit().putString("SELECTED_FOLDER_URI", uri.toString()).apply()
    }

    private fun loadSavedFolder() {
        val sharedPreferences = getApplication<Application>().getSharedPreferences(
            "FolderPrefs",
            Application.MODE_PRIVATE
        )
        val savedUriString = sharedPreferences.getString("SELECTED_FOLDER_URI", null)
        savedUriString?.let {
            val uri = Uri.parse(it)
            if (checkPermission(uri)) {
                loadFiles(uri)
            } else {
                _hasPermission.value = false
                _selectedFolder.value = null
                _files.value = emptyList()
            }
        }
    }


    private fun checkPermission(uri: Uri): Boolean {
        val list = getApplication<Application>().contentResolver.persistedUriPermissions
        val hasPermission = list.any { it.uri == uri && it.isReadPermission }
        return hasPermission
    }


}

fun DocumentFile.normalize(): WorkflowFile {
    return WorkflowFile(
        name = this.name ?: "",
        description = "Local workflow",
        workflowUri = this.uri.toString(),
        isAsset = false
    )
}


@Composable
fun LocalWorkflows(
    uiState: ImageGenerationUiState,
    onClick: (WorkflowFile) -> Unit,
    viewModel: FolderViewModel = viewModel()
) {
    val files by viewModel.files.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val folderName by viewModel.folderName.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()

    // The drawer recomposes every time it opens: rescan so files copied in
    // from Files/downloads appear without re-picking the folder.
    LaunchedEffect(Unit) {
        if (hasPermission) viewModel.refresh()
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadFiles(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        if (hasPermission) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = Ikona.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )

                Text(
                    modifier = Modifier.weight(1f),
                    text = folderName ?: "Selected folder",
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                HelpIconButton(
                    onClick = {
                        viewModel.refresh()
                    },
                    imageVector = Ikona.Refresh,
                    help = "Re-read the folder (picks up files copied in later)"
                )

                HelpIconButton(
                    onClick = {
                        folderPicker.launch(null)
                    },
                    imageVector = Ikona.OpenFolder,
                    help = "Choose a folder on the phone to load workflow files from"
                )

            }

            Spacer(modifier = Modifier.height(6.dp))

            files.forEach {
                WorkflowRow(it, uiState.workflow == it, onClick)
                Spacer(modifier = Modifier.size(4.dp))
            }

            if (files.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Icon(
                        imageVector = Ikona.Error,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "No .json workflow files in this folder yet. Copy ComfyUI workflow JSONs into it (by hand or via Drive/Dropbox) and tap the refresh icon.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Button(
                        onClick = {
                            folderPicker.launch(null)
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Select Another Folder")
                    }

                }
            }


        } else {
            Button(
                onClick = {
                    folderPicker.launch(null)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Select Folder")
            }
            Icon(
                imageVector = Ikona.OpenFolder,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Text("No folder selected or permission denied. Please select a folder.")

        }

    }
}



