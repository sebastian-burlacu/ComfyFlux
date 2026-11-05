package com.eltonkola.comfyflux.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlTest {

    @Test
    fun addsMissingHttpScheme() {
        assertEquals("http://192.168.1.5:8188", normalizeServerUrl("192.168.1.5:8188"))
    }

    @Test
    fun keepsHttpsAndStripsWhitespaceAndTrailingSlash() {
        assertEquals("https://example.com:8188", normalizeServerUrl("  https://example.com:8188/ "))
    }

    @Test
    fun keepsExplicitHttpScheme() {
        assertEquals("http://192.168.1.5:8188", normalizeServerUrl("http://192.168.1.5:8188"))
    }

    @Test
    fun blankInputStaysEmpty() {
        assertEquals("", normalizeServerUrl("   "))
    }
}
