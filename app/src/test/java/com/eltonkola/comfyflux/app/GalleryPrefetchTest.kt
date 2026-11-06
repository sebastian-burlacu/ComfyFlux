package com.eltonkola.comfyflux.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryPrefetchTest {

    @Test
    fun warmsTwoOnEachSideWithoutTouchingTheShownOne() {
        assertEquals(listOf(1, 2, 4, 5), prefetchWindow(3, 8))
    }

    @Test
    fun clampsAtBothEndsOfTheList() {
        assertEquals(listOf(1, 2), prefetchWindow(0, 8))
        assertEquals(listOf(6, 7), prefetchWindow(8, 9))
    }

    @Test
    fun shortListsHaveNothingOrLittleToWarm() {
        assertEquals(emptyList<Int>(), prefetchWindow(0, 1))
        assertEquals(listOf(1), prefetchWindow(0, 2))
        assertEquals(emptyList<Int>(), prefetchWindow(0, 0))
    }
}
