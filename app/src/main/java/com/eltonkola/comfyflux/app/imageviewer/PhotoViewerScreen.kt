package com.eltonkola.comfyflux.app.imageviewer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.Coil
import coil.compose.AsyncImage
import com.eltonkola.comfyflux.app.prefetchNeighbours
import com.eltonkola.comfyflux.data.model.MediaItem
import com.eltonkola.comfyflux.data.model.MediaKind
import com.eltonkola.comfyflux.data.model.serverMimeFor
import com.eltonkola.comfyflux.data.model.stillUrls
import com.eltonkola.comfyflux.R
import com.eltonkola.comfyflux.app.MainViewModel
import com.eltonkola.comfyflux.ui.theme.Ikona
import com.eltonkola.comfyflux.ui.theme.ikona.Back
import com.eltonkola.comfyflux.ui.theme.ikona.Download
import com.eltonkola.comfyflux.ui.theme.ikona.OpenFolder
import com.eltonkola.comfyflux.ui.theme.ikona.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import com.eltonkola.comfyflux.components.HelpIconButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(
    viewModel: MainViewModel,
    navController: NavController
) {

    val uiState by viewModel.imageUiState.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentImageUrl by remember { mutableStateOf("") }

    var showDialog by remember { mutableStateOf(false) }

    // Hoisted: the top bar acts on the page currently on screen.
    val pagerState =
        rememberPagerState(initialPage = uiState.selected, pageCount = { uiState.media.size })

    if (showDialog) {
        PhotoZoomDialog(photoUrl = currentImageUrl) {
            showDialog = false
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Gallery")
                },
                actions = {
                    val current = uiState.media.getOrNull(pagerState.currentPage)
                    if (current != null && !current.isStill) {
                        // Codecs the phone cannot play (ProRes .mov, alac ...)
                        // still have to be reachable: hand the /view URL to
                        // another app.
                        HelpIconButton(
                            onClick = {
                                openExternally(context, current.url)
                            },
                            imageVector = Ikona.OpenFolder,
                            help = "Open this clip in another app"
                        )
                    }
                    HelpIconButton(
                        onClick = {
                            coroutineScope.launch {
                                saveMediaToDownloads(
                                    context,
                                    uiState.media.getOrNull(pagerState.currentPage)
                                        ?: uiState.media.getOrNull(uiState.selected),
                                    share = true
                                )
                            }
                        },
                        imageVector = Ikona.Share,
                        help = "Share this output"
                    )
                    HelpIconButton(
                        onClick = {
                            coroutineScope.launch {
                                saveMediaToDownloads(
                                    context,
                                    uiState.media.getOrNull(pagerState.currentPage)
                                        ?: uiState.media.getOrNull(uiState.selected),
                                    share = false
                                )
                            }
                        },
                        imageVector = Ikona.Download,
                        help = "Save this output to the phone"
                    )

                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Ikona.Back,
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
        },
    ) { paddingValues ->

        HorizontalPager(
            state = pagerState,
            contentPadding = paddingValues,
            modifier = Modifier.fillMaxSize()
        ) { page ->

            val item = uiState.media[page]

            LaunchedEffect(key1 = page) {
                currentImageUrl = item.url
                if (item.isStill) {
                    // Warm the still neighbours while this page is up, so a
                    // swipe shows an already-downloaded picture. Clips stream
                    // instead, so warming them would only burn bandwidth.
                    val stills = uiState.media.stillUrls()
                    if (stills.isNotEmpty() && stills.size > 1) {
                        prefetchNeighbours(
                            Coil.imageLoader(context),
                            context,
                            stills,
                            stills.indexOf(item.url)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (item.isStill) Color.Unspecified else Color.Black)
            ) {
                when (item.kind) {
                    MediaKind.Video -> VideoPlayback(url = item.url)
                    MediaKind.Audio -> AudioPlayback(url = item.url)
                    MediaKind.Image -> AsyncImage(
                        model = item.url,
                        contentDescription = "Image ${page + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showDialog = true }
                    )
                }
            }

        }
    }
}

private fun share(context: Context, uri: Uri, mime: String) {
    val intent = Intent(Intent.ACTION_SEND)
    intent.type = mime
    intent.putExtra(Intent.EXTRA_STREAM, uri)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share output"))
}

/** Hand a clip to another player: covers codecs this phone cannot decode. */
private fun openExternally(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (e: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "No app on the phone can open that file", Toast.LENGTH_SHORT)
            .show()
    }
}

/**
 * Copy one saved output into the phone's media store: stills go to Pictures,
 * clips and audio to their own collections so the gallery picks them up.
 */
suspend fun saveMediaToDownloads(
    context: Context,
    item: MediaItem?,
    share: Boolean = false
) {
    if (item == null) {
        Toast.makeText(context, "Nothing is open to save", Toast.LENGTH_SHORT).show()
        return
    }
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = serverMimeFor(item.name)
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    when (item.kind) {
                        MediaKind.Video -> "Movies/${context.getString(R.string.app_name)}"
                        MediaKind.Audio -> "Music/${context.getString(R.string.app_name)}"
                        MediaKind.Image -> "Pictures/${context.getString(R.string.app_name)}"
                    }
                )
            }
        }
        val collection = when (item.kind) {
            MediaKind.Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            MediaKind.Audio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            MediaKind.Image -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        try {
            val url = URL(item.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()

            val input: InputStream = connection.inputStream
            val uri = resolver.insert(collection, contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    input.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    outputStream.flush()

                    withContext(Dispatchers.Main) {
                        if (share) {
                            share(context, uri, mime)
                        }
                        Toast.makeText(context, "Saved on the phone", Toast.LENGTH_SHORT)
                            .show()
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to open output stream", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            } ?: run {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to insert file", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save output", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
