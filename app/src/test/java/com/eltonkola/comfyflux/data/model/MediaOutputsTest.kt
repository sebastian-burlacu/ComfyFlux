package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Video/audio graphs (`SaveVideo`, `SaveAudio`, animated savers) finish with a
 * clip or a track instead of a PNG. Classification has to survive both server
 * shapes: the dedicated output key, and clips filed under `images` with a
 * format tag.
 */
class MediaOutputsTest {

    @Test
    fun outputKeysDecideFirst() {
        assertEquals(MediaKind.Video, mediaKindFor("video", "clip.mp4", null))
        assertEquals(MediaKind.Audio, mediaKindFor("audio", "voice.wav", null))
        assertEquals(MediaKind.Image, mediaKindFor("images", "shot.png", null))
    }

    @Test
    fun formatTagClassifiesClipsFiledUnderImages() {
        // ComfyUI has shipped videos inside the "images" list with a mime tag.
        assertEquals(MediaKind.Video, mediaKindFor("images", "ComfyUI_00001_.mp4", "video/mp4"))
        assertEquals(MediaKind.Audio, mediaKindFor("images", "ComfyUI_00002_.flac", "audio/flac"))
        assertEquals(MediaKind.Image, mediaKindFor("images", "ComfyUI_00003_.png", "image/png"))
    }

    @Test
    fun extensionIsTheFallbackWhenNothingElseSays() {
        assertEquals(MediaKind.Video, mediaKindFor("images", "anim.webm", null))
        assertEquals(MediaKind.Video, mediaKindFor("images", "anim.gif", null))
        assertEquals(MediaKind.Audio, mediaKindFor("images", "take.opus", null))
        assertEquals(MediaKind.Image, mediaKindFor("images", "still.jpg", null))
        // Unknown extension stays a still: the gallery can still show a tile.
        assertEquals(MediaKind.Image, mediaKindFor("images", "mystery.bin", null))
    }

    @Test
    fun stillsAreTheOnlyThingCoilCanDecode() {
        val media = listOf(
            MediaItem("u1", "a.png", MediaKind.Image),
            MediaItem("u2", "b.mp4", MediaKind.Video),
            MediaItem("u3", "c.wav", MediaKind.Audio),
            MediaItem("u4", "d.png", MediaKind.Image)
        )

        assertEquals(listOf("u1", "u4"), media.stillUrls())
    }

    @Test
    fun summaryNamesWhatArrived() {
        assertEquals("no outputs", emptyList<MediaItem>().summary())
        assertEquals(
            "2 images, 1 video",
            listOf(
                MediaItem("a", "a.png", MediaKind.Image),
                MediaItem("b", "b.png", MediaKind.Image),
                MediaItem("c", "c.mp4", MediaKind.Video)
            ).summary()
        )
        assertEquals("1 audio clip", listOf(MediaItem("d", "d.wav", MediaKind.Audio)).summary())
    }

    @Test
    fun mimeComesFromTheSavedExtension() {
        assertEquals("video/mp4", serverMimeFor("video/ComfyUI_00001_.mp4"))
        assertEquals("video/quicktime", serverMimeFor("clip.mov"))
        assertEquals("audio/flac", serverMimeFor("audio/ComfyUI_00002_.flac"))
        // Unknown falls back to the app's usual PNG.
        assertEquals("image/png", serverMimeFor("weird.xyz"))
    }
}
