package com.eltonkola.comfyflux.app

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest
import coil.memory.MemoryCache
import coil.disk.DiskCache

/**
 * One image loader for the whole app: the History list, the batch preview
 * grid and the viewer then share a memory cache and a disk cache, so opening
 * the same output twice is instant instead of another /view round trip.
 */
fun galleryImageLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder(context).maxSizePercent(0.20).build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                // Outputs are 1-2 MB PNGs; ~400 of them stay cached.
                .maxSizeBytes(512L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()

/**
 * A thumbnail request: decode at the display size, not at full resolution.
 * A 1024x1024 PNG costs ~4 MB of bitmap; a grid of twelve of them is how an
 * image-heavy list gets killed on a mid-range phone.
 */
fun thumbnailRequest(context: Context, url: String, pixelSize: Int = 384): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(pixelSize)
        .build()

/**
 * Indices worth warming around [index]: the two neighbours on each side,
 * skipping out-of-range slots and the current one. Kept separate from the
 * Coil call so the window is testable (a wrong window silently prefetches
 * nothing, or thrashes the network on a short list).
 */
fun prefetchWindow(index: Int, count: Int, radius: Int = 2): List<Int> {
    if (count <= 0) return emptyList()
    val from = (index - radius).coerceAtLeast(0)
    val to = (index + radius).coerceAtMost(count - 1)
    return (from..to).toList().filter { it != index }
}

/** Warm the disk+memory cache for the neighbours of the shown image. */
fun prefetchNeighbours(loader: ImageLoader, context: Context, urls: List<String>, index: Int) {
    prefetchWindow(index, urls.size).forEach { i ->
        loader.enqueue(
            ImageRequest.Builder(context)
                .data(urls[i])
                // Viewer-sized, so the swipe reuses this decode instead of
                // downloading again.
                .size(1536)
                .build()
        )
    }
}
