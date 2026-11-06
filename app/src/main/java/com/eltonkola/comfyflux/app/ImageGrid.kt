package com.eltonkola.comfyflux.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Thumbnails of a batch inside the create-screen preview. Whole images,
 * never cropped: 1 fills the preview, 2-4 use a 2x2 grid, 5+ a 3x3 grid
 * that scrolls for the overflow (10+). Tiles match the preview box's
 * aspect ratio so full grids fit without scrolling. Tap any tile to open
 * the zoom viewer.
 */
@Composable
fun ImageGrid(images: List<String>, onZoom: (String) -> Unit) {
    val columns = when {
        images.size <= 1 -> 1
        images.size <= 4 -> 2
        else -> 3
    }

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(2.dp),
    ) {
        items(images.size) { index ->
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondary)
                    .clickable { onZoom(images[index]) },
            ) {
                AsyncImage(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(2.dp)),
                    // Tiles are small; decoding ten full-resolution PNGs at
                    // once is what makes the preview grid stutter.
                    model = thumbnailRequest(LocalContext.current, images[index], 512),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}
