package vegabobo.languageselector.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicePureTest {

    @Test
    fun `chunking an empty list returns no batches`() {
        assertTrue(chunkPackages(emptyList()).isEmpty())
    }

    @Test
    fun `packages are split into batches of fifty`() {
        val packages = (0 until 120).map { index -> "package.$index" }
        val batches = chunkPackages(packages)

        assertEquals(3, batches.size)
        assertEquals(50, batches[0].size)
        assertEquals(50, batches[1].size)
        assertEquals(20, batches[2].size)
        assertEquals(packages, batches.flatten())
    }

    @Test
    fun `custom batch size is honoured`() {
        val batches = chunkPackages(listOf("a", "b", "c"), batchSize = 2)
        assertEquals(listOf(listOf("a", "b"), listOf("c")), batches)
    }

    @Test
    fun `non positive batch size falls back to the default`() {
        val packages = (0 until 60).map { index -> "package.$index" }
        val batches = chunkPackages(packages, batchSize = 0)
        assertEquals(2, batches.size)
        assertEquals(50, batches[0].size)
    }

    @Test
    fun `parse tags handles null blank and empty values`() {
        assertEquals(emptyList<String>(), parseTags(null))
        assertEquals(emptyList<String>(), parseTags(""))
        assertEquals(emptyList<String>(), parseTags("  ;  ; "))
    }

    @Test
    fun `parse tags splits trims and removes duplicates`() {
        assertEquals(listOf("en-US"), parseTags("en-US"))
        assertEquals(listOf("en-US", "ja-JP"), parseTags("en-US;ja-JP"))
        assertEquals(listOf("en-US", "ja-JP"), parseTags(" en-US ;; en-US ; ja-JP "))
    }

    @Test
    fun `formatted tags round trip through the parser`() {
        val tags = listOf("en-US", "zh-Hans-CN")
        assertEquals(tags, parseTags(formatTags(tags)))
    }

    @Test
    fun `format tags drops blanks and duplicates`() {
        assertEquals("en-US;ja-JP", formatTags(listOf(" en-US ", "", "ja-JP", "en-US")))
        assertEquals("", formatTags(emptyList()))
    }

    @Test
    fun `normalize language tags keeps priority order`() {
        assertEquals(
            listOf("ja-JP", "en-US"),
            normalizeLanguageTags(listOf(" ja-JP ", "en-US", "ja-JP", "  ")),
        )
    }
}
