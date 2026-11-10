package com.cemail.app

import com.cemail.app.data.mail.FetchedImage
import com.cemail.app.data.mail.InlineCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class InlineCacheTest {
    private fun tempDir(): File = Files.createTempDirectory("inline").toFile().also { it.deleteOnExit() }

    @Test
    fun picturesAreFetchedOnceAndReturnedAsDataUris() {
        val dir = File(tempDir(), "m1")
        var fetches = 0
        val fetch = {
            fetches++
            listOf(FetchedImage("logo@x", "image/png", byteArrayOf(1, 2, 3)), FetchedImage("pic@x", "image/jpeg", byteArrayOf(9)))
        }
        val first = InlineCache.load(dir, fetch)
        val second = InlineCache.load(dir, fetch)
        assertEquals(1, fetches)
        assertEquals(first, second)
        assertEquals("data:image/png;base64,AQID", first["logo@x"])
        assertEquals("data:image/jpeg;base64,CQ==", first["pic@x"])
    }

    @Test
    fun hugePicturesAreSkippedAndAMessageWithoutPicturesIsRememberedToo() {
        val dir = File(tempDir(), "m2")
        val result = InlineCache.load(dir) { listOf(FetchedImage("big@x", "image/png", ByteArray(5 * 1024 * 1024))) }
        assertTrue(result.isEmpty())
        var again = 0
        InlineCache.load(dir) { again++; emptyList() }
        assertEquals(0, again) // the empty index is kept: nothing is downloaded again
    }

    @Test
    fun contentIdsLoseTheirAngleBrackets() {
        assertEquals("abc@host", InlineCache.cleanCid(" <abc@host> "))
        assertNull(InlineCache.cleanCid("<>"))
        assertNull(InlineCache.cleanCid(null))
    }
}
