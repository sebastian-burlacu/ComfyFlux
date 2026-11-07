package com.eltonkola.comfyflux.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eltonkola.comfyflux.data.model.MediaItem
import com.eltonkola.comfyflux.data.model.MediaKind
import com.eltonkola.comfyflux.ui.theme.Ikona
import com.eltonkola.comfyflux.ui.theme.ikona.PlayAudio
import com.eltonkola.comfyflux.ui.theme.ikona.PlayMovie

/**
 * Thumbnails of a batch inside the create-screen preview. Whole images,
 * never cropped: 1 fills the preview, 2-4 use a 2x2 grid, 5+ a 3x3 grid
 * that scrolls for the overflow (10+). Tiles match the preview box's
 * aspect ratio so full grids fit without scrolling. Tap any tile to open
 * the zoom viewer.
 */
@Composable
fun ImageGrid(media: List<MediaItem>, onZoom: (MediaItem) -> Unit) {
    val columns = when {
        media.size <= 1 -> 1
        media.size <= 4 -> 2
        else -> 3
    }

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(2.dp),
    ) {
        items(media.size) { index ->
            MediaTile(
                item = media[index],
                pixelSize = 512,
                modifier = Modifier
                    .padding(2.dp)
                    .fillMaxWidth()
                    .aspectRatio(1.6f),
                onClick = { onZoom(media[index]) }
            )
        }
    }
}

/**
 * One saved output. Stills decode through the shared image loader; clips and
 * audio cannot be thumbnailed without downloading them, so they show a badge
 * and the file name instead of a fake preview.
 */
@Composable
fun MediaTile(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pixelSize: Int = 384
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.secondary)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (item.isStill) {
            AsyncImage(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(2.dp)),
                // Tiles are small; decoding ten full-resolution PNGs at
                // once is what makes the preview grid stutter.
                model = thumbnailRequest(LocalContext.current, item.url, pixelSize),
                contentDescription = null,
                contentScale = ContentScale.Fit,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(6.dp)
            ) {
                Icon(
                    imageVector = if (item.kind == MediaKind.Video) Ikona.PlayMovie else Ikona.PlayAudio,
                    contentDescription = item.kind.name,
                    modifier = Modifier.size(30.dp)
                )
                Text(
                    text = item.name.substringAfterLast('/'),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
