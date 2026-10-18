package vegabobo.languageselector.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vegabobo.languageselector.core.model.LocaleOption
import java.util.Locale

/** Pure JVM tests for the pinned-locale persistence helpers. */
class PinnedLocaleTest {

    private val displayLocale: Locale = Locale.US

    @Test
    fun `parse keeps well formed entries and ignores malformed ones`() {
        val parsed = parsePinnedLocales(
            setOf("English,en", "broken", ",en-GB", "NoTag,", "中文,zh-CN"),
            displayLocale,
        )

        assertEquals(setOf("en", "zh-CN"), parsed.map { it.languageTag }.toSet())
        assertEquals("English", parsed.single { it.languageTag == "en" }.displayName)
        assertEquals("中文", parsed.single { it.languageTag == "zh-CN" }.displayName)
    }

    @Test
    fun `parse deduplicates by language tag`() {
        val parsed = parsePinnedLocales(setOf("English,en", "Inglés,en"), displayLocale)

        assertEquals(1, parsed.size)
        assertEquals("en", parsed.single().languageTag)
    }

    @Test
    fun `parse supports display names containing commas`() {
        val parsed = parsePinnedLocales(setOf("Chinese, Simplified,zh-CN"), displayLocale)

        assertEquals("Chinese, Simplified", parsed.single().displayName)
        assertEquals("zh-CN", parsed.single().languageTag)
    }

    @Test
    fun `parsed entries are sorted by display name`() {
        val parsed = parsePinnedLocales(setOf("Zulu,zu", "Afrikaans,af"), displayLocale)

        assertEquals(listOf("af", "zu"), parsed.map { it.languageTag })
    }

    @Test
    fun `empty input yields an empty list`() {
        assertTrue(parsePinnedLocales(null, displayLocale).isEmpty())
        assertTrue(parsePinnedLocales(emptySet(), displayLocale).isEmpty())
    }

    @Test
    fun `decode extracts the tag and rejects malformed entries`() {
        assertEquals("en-US", decodePinnedTag("English (United States),en-US"))
        assertNull(decodePinnedTag("broken"))
        assertNull(decodePinnedTag(",en-US"))
        assertNull(decodePinnedTag("English,"))
    }

    @Test
    fun `serialize uses the legacy display name comma tag format`() {
        assertEquals(
            "English (United States),en-US",
            serializePinnedLocale(LocaleOption("en-US", "English (United States)", "English")),
        )
        assertEquals(
            "en-US,en-US",
            serializePinnedLocale(LocaleOption("en-US", "  ", "English")),
        )
    }

    @Test
    fun `pin adds a locale only once`() {
        val option = LocaleOption("en-US", "English (United States)", "English")

        val once = addPinnedLocale(emptySet(), option)
        assertEquals(setOf("English (United States),en-US"), once)

        val twice = addPinnedLocale(once, option)
        assertEquals(once, twice)
    }

    @Test
    fun `pin does not duplicate an already pinned tag with another display name`() {
        val existing = setOf("English,en-US")

        val updated = addPinnedLocale(
            existing,
            LocaleOption("en-US", "Inglés (Estados Unidos)", "English"),
        )

        assertEquals(existing, updated)
    }

    @Test
    fun `unpin removes every entry with the tag`() {
        val raw = setOf("English,en", "English (United States),en-US", "中文,zh-CN")

        assertEquals(setOf("English,en", "中文,zh-CN"), removePinnedLocale(raw, "en-US"))
        assertEquals(raw, removePinnedLocale(raw, "not-pinned"))
    }
}
