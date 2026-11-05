package com.eltonkola.comfyflux.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The server serves userdata files only under their exact name; the listing
 * strips ".json" and the content fetch must put it back (the 404-empty-body
 * bug that surfaced as "Cannot read Json element because of unexpected end
 * of the input").
 */
class WorkflowContentPathTest {

    @Test
    fun listingNamesGetTheJsonSuffixBack() {
        assertEquals(
            "userdata/workflows%2Fkrea-2-turbo-v2.json",
            workflowContentPath("krea-2-turbo-v2")
        )
    }

    @Test
    fun alreadySuffixedNamesAreNotDoubled() {
        assertEquals(
            "userdata/workflows%2Fimage_qwen.json",
            workflowContentPath("image_qwen.json")
        )
    }

    @Test
    fun escapedSlashAndSpacesSurvive() {
        assertEquals(
            "userdata/workflows%2Fmy%20workflow.json",
            workflowContentPath("my workflow")
        )
    }

    @Test
    fun pathSegmentsAreNotInjectable() {
        assertEquals(
            "userdata/workflows%2Fevil.json",
            workflowContentPath("a/evil")
        )
    }
}
