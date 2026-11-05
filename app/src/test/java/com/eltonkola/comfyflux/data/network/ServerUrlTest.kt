package com.eltonkola.comfyflux.data.network

import io.ktor.http.Url
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

    @Test
    fun ktorKeepsEncodedSlashInUserdataPath() {
        // /userdata/{file} is one aiohttp path segment; the app fetches saved
        // workflows as /userdata/workflows%2F<name>.json and ktor must send
        // the %2F untouched instead of decoding it into a path separator.
        val url = Url("http://192.168.1.5:8188/userdata/workflows%2Fmy_wf.json")
        assertEquals("/userdata/workflows%2Fmy_wf.json", url.encodedPath)
    }
}
