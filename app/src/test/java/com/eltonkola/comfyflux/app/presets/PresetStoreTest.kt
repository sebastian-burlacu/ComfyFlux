package com.eltonkola.comfyflux.app.presets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetStoreTest {

    private fun preset(
        id: String,
        name: String = id,
        text: String = "a cat",
        tags: List<String> = emptyList(),
        favorite: Boolean = false,
        createdAt: Long = 0L
    ) = PromptPreset(id, name, text, tags, favorite, createdAt)

    @Test
    fun presetsSurviveAnEncodeDecodeRoundTrip() {
        val saved = listOf(
            preset("p1", "Portrait", "a cat in a hat", listOf("cat", "fun"), favorite = true, createdAt = 5L),
            preset("p2", "Plain", "a dog", createdAt = 4L)
        )
        val raw = encodePresets(saved)
        assertTrue("stored list should be pretty-printed", raw.contains("\n"))
        assertEquals(saved, presetsFromJson(raw))
    }

    @Test
    fun corruptJsonDecodesToEmptyWithoutThrowing() {
        assertEquals(emptyList<PromptPreset>(), presetsFromJson("{not json at all"))
        assertEquals(emptyList<PromptPreset>(), presetsFromJson(""))
        assertNull(decodePresetsOrNull("[{\"id\": }"))
        assertEquals(emptyList<PromptPreset>(), presetsFromJson("[]"))
    }

    @Test
    fun unknownFieldsFromNewerBuildsAreIgnored() {
        val list = presetsFromJson(
            """[{"id":"p1","name":"Old","text":"a cat","futureField":42}]"""
        )
        assertEquals(1, list.size)
        assertEquals("p1", list.first().id)
        assertEquals(emptyList<String>(), list.first().tags)
    }

    @Test
    fun tagsAreTrimmedLowercasedAndDeduplicated() {
        assertEquals(
            listOf("portrait", "golden hour", "8k"),
            parseTags(" Portrait , golden hour , 8K ,portrait,,  ")
        )
        assertEquals(emptyList<String>(), parseTags(" , , "))
    }

    @Test
    fun searchMatchesNameTextAndTagsCaseInsensitively() {
        val presets = listOf(
            preset("p1", name = "Sunset", text = "wide shot"),
            preset("p2", name = "Macro", text = "close up", tags = listOf("lens")),
            preset("p3", name = "Studio", text = "studio lighting", tags = listOf("Portrait"))
        )
        assertEquals(listOf("p1"), filterPresets(presets, "sun", false, null).map { it.id })
        assertEquals(listOf("p2"), filterPresets(presets, "close", false, null).map { it.id })
        assertEquals(listOf("p3"), filterPresets(presets, "portrait", false, null).map { it.id })
        assertEquals(listOf("p1", "p2", "p3"), filterPresets(presets, "", false, null).map { it.id })
    }

    @Test
    fun tagFilterMatchesWholeTagAndFavoriteOnlyKeepsFavorites() {
        val presets = listOf(
            preset("p1", tags = listOf("portrait"), favorite = true),
            preset("p2", tags = listOf("portraits"), favorite = false),
            preset("p3", tags = listOf("landscape", "portrait"), favorite = false)
        )
        // "portrait" as a tag filter must not catch the "portraits" tag.
        assertEquals(listOf("p1", "p3"), filterPresets(presets, "", false, "portrait").map { it.id })
        assertEquals(listOf("p1", "p2", "p3"), filterPresets(presets, "", false, null).map { it.id })
        assertEquals(listOf("p1"), filterPresets(presets, "", true, null).map { it.id })
    }

    @Test
    fun resultsSortFavoritesFirstThenNewestFirst() {
        val presets = listOf(
            preset("old-fav", favorite = true, createdAt = 10L),
            preset("new-plain", favorite = false, createdAt = 100L),
            preset("new-fav", favorite = true, createdAt = 20L),
            preset("old-plain", favorite = false, createdAt = 1L)
        )
        assertEquals(
            listOf("new-fav", "old-fav", "new-plain", "old-plain"),
            filterPresets(presets, "", false, null).map { it.id }
        )
    }

    @Test
    fun blankQueryAndFiltersReturnEverythingSorted() {
        val presets = listOf(preset("p2", createdAt = 2L), preset("p1", createdAt = 1L))
        assertEquals(listOf("p2", "p1"), filterPresets(presets, "   ", false, null).map { it.id })
    }

    @Test
    fun suggestedNameTakesTheFirstWordsOfThePrompt() {
        assertEquals("a cat in a hat", suggestName("  a cat in a hat wearing boots  "))
        assertEquals("one two three four five", suggestName("one two three four five six seven"))
        assertEquals("", suggestName("   "))
    }

    @Test
    fun idsAreUniqueEvenWithinOneMillisecond() {
        val first = newPresetId(1_700_000_000_000L, 0)
        val second = newPresetId(1_700_000_000_000L, 1)
        assertTrue(first.startsWith("p"))
        assertTrue(first != second)
        assertTrue(newPresetId(1_700_000_000_001L, 0) != first)
    }
}
