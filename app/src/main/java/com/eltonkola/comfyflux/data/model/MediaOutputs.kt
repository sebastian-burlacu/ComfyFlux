package com.eltonkola.comfyflux.data.model

/**
 * Server outputs are not only PNGs: `SaveVideo`/`SaveAudio` (and animated
 * `SaveAnimatedPNG`/`WEBP`) finish a graph with a clip or a track. Which kind a
 * saved file is comes from the output key first (`images` / `video` / `audio`),
 * then the declared `format` mime, then the extension - the server has shipped
 * clips under `images` with a format tag, so all three are consulted.
 */
enum class MediaKind { Image, Video, Audio }

data class MediaItem(
    val url: String,
    /** File name as the server saved it; shown on tiles the app cannot preview. */
    val name: String,
    val kind: MediaKind
) {
    val isStill: Boolean get() = kind == MediaKind.Image
}

private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mov", "mkv", "avi", "gif")
private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "ogg", "oga", "m4a", "aac", "opus")

fun mediaKindFor(outputKey: String, filename: String, format: String?): MediaKind {
    when (outputKey) {
        "video" -> return MediaKind.Video
        "audio" -> return MediaKind.Audio
    }
    format?.lowercase().let { mime ->
        if (mime != null) {
            if (mime.startsWith("video/")) return MediaKind.Video
            if (mime.startsWith("audio/")) return MediaKind.Audio
        }
    }
    return when (filename.substringAfterLast('.', "").lowercase()) {
        in VIDEO_EXTENSIONS -> MediaKind.Video
        in AUDIO_EXTENSIONS -> MediaKind.Audio
        else -> MediaKind.Image
    }
}

private val MIME_BY_EXTENSION = mapOf(
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "webp" to "image/webp",
    "gif" to "image/gif",
    "mp4" to "video/mp4",
    "webm" to "video/webm",
    "mov" to "video/quicktime",
    "mkv" to "video/x-matroska",
    "avi" to "video/x-msvideo",
    "mp3" to "audio/mpeg",
    "wav" to "audio/wav",
    "flac" to "audio/flac",
    "ogg" to "audio/ogg",
    "oga" to "audio/ogg",
    "opus" to "audio/opus",
    "m4a" to "audio/mp4",
    "aac" to "audio/aac"
)

/** Mime for saving into MediaStore/sharing: the server's own extension. */
fun serverMimeFor(filename: String): String =
    MIME_BY_EXTENSION[filename.substringAfterLast('.', "").lowercase()] ?: "image/png"

/** Still-image URLs only: what Coil and the prefetcher can decode. */
fun List<MediaItem>.stillUrls(): List<String> = filter { it.isStill }.map { it.url }

/** One-line count of what a run produced, e.g. "2 images, 1 video". */
fun List<MediaItem>.summary(): String {
    if (isEmpty()) return "no outputs"
    val images = count { it.kind == MediaKind.Image }
    val videos = count { it.kind == MediaKind.Video }
    val audios = count { it.kind == MediaKind.Audio }
    return listOfNotNull(
        images.takeIf { it > 0 }?.let { "$it image${if (it == 1) "" else "s"}" },
        videos.takeIf { it > 0 }?.let { "$it video${if (it == 1) "" else "s"}" },
        audios.takeIf { it > 0 }?.let { "$it audio ${if (it == 1) "clip" else "clips"}" }
    ).joinToString(", ")
}
