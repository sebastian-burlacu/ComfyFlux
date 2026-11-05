package com.eltonkola.comfyflux.app.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentPromptsTest {

    @Test
    fun newPromptGoesFirst() {
        assertEquals(
            listOf("a cat", "a dog"),
            pushRecent(listOf("a dog"), "a cat")
        )
    }

    @Test
    fun repeatedPromptMovesToFrontInsteadOfDuplicating() {
        assertEquals(
            listOf("a cat", "a dog", "a bird"),
            pushRecent(listOf("a dog", "a cat", "a bird"), "a cat")
        )
    }

    @Test
    fun dedupIsCaseSensitive() {
        assertEquals(
            listOf("A Cat", "a cat"),
            pushRecent(listOf("a cat"), "A Cat")
        )
    }

    @Test
    fun promptsAreTrimmedBeforeStoring() {
        assertEquals(
            listOf("a cat", "a dog"),
            pushRecent(listOf("a dog"), "  a cat  ")
        )
    }

    @Test
    fun listIsCappedAtTenNewestFirst() {
        val existing = (1..10).map { "prompt $it" }
        val result = pushRecent(existing, "brand new")
        assertEquals(10, result.size)
        assertEquals("brand new", result.first())
        assertEquals("prompt 9", result.last())
        assertEquals(false, result.contains("prompt 10"))
    }

    @Test
    fun blankAndWhitespacePromptsAreIgnored() {
        val existing = listOf("a cat")
        assertEquals(existing, pushRecent(existing, ""))
        assertEquals(existing, pushRecent(existing, "   \t\n "))
        assertEquals(emptyList<String>(), pushRecent(emptyList(), ""))
    }
}
