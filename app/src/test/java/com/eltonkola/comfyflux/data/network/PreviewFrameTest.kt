package com.eltonkola.comfyflux.data.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PreviewFrameTest {

    private fun frame(payload: ByteArray) = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)

    @Test
    fun plainPreviewFrameSplitsTypeAndImage() {
        val img = byteArrayOf(1, 2, 3, 4)
        val bytes = frame(ByteArray(0)).let { _ ->
            ByteBuffer.allocate(8 + img.size).order(ByteOrder.BIG_ENDIAN)
                .putInt(1).putInt(2).put(img).array()
        }
        assertArrayEquals(img, parsePreviewImageBytes(bytes))
    }

    @Test
    fun metadataFrameSplitsJsonAndImage() {
        val meta = """{"node_id":"7","prompt_id":"p"}""".toByteArray()
        val img = byteArrayOf(-1, -40, 9) // fake JPEG head bytes
        val bytes = ByteBuffer.allocate(8 + meta.size + img.size).order(ByteOrder.BIG_ENDIAN)
            .putInt(4).putInt(meta.size).put(meta).put(img).array()
        assertArrayEquals(img, parsePreviewImageBytes(bytes))
    }

    @Test
    fun unknownOrTruncatedFramesAreIgnored() {
        assertNull(parsePreviewImageBytes(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(3).putInt(0).array()))
        assertNull(parsePreviewImageBytes(byteArrayOf(0, 0)))
        // metadata length beyond the frame
        val bad = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(4).putInt(9999).array()
        assertNull(parsePreviewImageBytes(bad))
    }
}
