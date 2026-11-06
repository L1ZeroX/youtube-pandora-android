package io.github.l1zerox.pandora

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardTextTest {
    @Test fun appendsCopiedLinesWithoutReplacingInput() {
        assertEquals("https://a.test\nhttps://b.test\nhttps://c.test",
            appendClipboardText("https://a.test"," https://b.test\nhttps://c.test\n"))
        assertEquals("https://a.test\nhttps://b.test",appendClipboardText("https://a.test\n","https://b.test"))
    }
    @Test fun emptyClipboardPreservesInput() {
        for(copied in listOf(null,""," \n")) assertEquals("existing",appendClipboardText("existing",copied))
        assertEquals("https://b.test",appendClipboardText(" \n","https://b.test"))
    }
}
