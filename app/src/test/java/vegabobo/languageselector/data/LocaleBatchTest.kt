package vegabobo.languageselector.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the IPC batching helper used by [DefaultLocaleStates]. */
class LocaleBatchTest {

    @Test
    fun `packages are split into batches of 50`() {
        val packages = (1..120).map { "com.example.app$it" }

        val batches = chunkPackages(packages)

        assertEquals(listOf(50, 50, 20), batches.map { it.size })
        assertEquals(packages, batches.flatten())
    }

    @Test
    fun `empty and short inputs keep their shape`() {
        assertTrue(chunkPackages(emptyList()).isEmpty())
        assertEquals(listOf(listOf("pkg.a")), chunkPackages(listOf("pkg.a")))
    }

    @Test
    fun `custom batch size is honoured`() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("c")),
            chunkPackages(listOf("a", "b", "c"), batchSize = 2),
        )
    }
}
