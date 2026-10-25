package vegabobo.languageselector.data

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import vegabobo.languageselector.core.contract.LocaleRemote
import vegabobo.languageselector.core.dispatchers.AppDispatchers
import java.util.Locale

/**
 * Pure JVM tests for the locale catalog logic (grouping, dedupe, ordering and
 * search) plus the suspend parts of [DefaultLocaleCatalog] with a fake remote.
 * No Android runtime is involved.
 */
class LocaleCatalogTest {

    private val displayLocale: Locale = Locale.US

    @Test
    fun `every language group keeps its first locale`() {
        val groups = buildLocaleGroups(listOf(Locale.US, Locale.UK, Locale.CHINA), displayLocale)

        val english = groups.single { it.languageName == "English" }
        assertEquals(listOf("en-GB", "en-US"), english.options.map { it.languageTag })

        val chinese = groups.single { it.languageName == "Chinese" }
        assertEquals(listOf("zh-CN"), chinese.options.map { it.languageTag })
    }

    @Test
    fun `duplicate language tags are collapsed`() {
        val groups = buildLocaleGroups(
            listOf(Locale.US, Locale.forLanguageTag("en-US"), Locale.US),
            displayLocale,
        )

        assertEquals(1, groups.size)
        assertEquals(1, groups.single().options.size)
        assertEquals("en-US", groups.single().options.single().languageTag)
    }

    @Test
    fun `all languages are kept without a hardcoded filter`() {
        val groups = buildLocaleGroups(
            listOf(Locale.US, Locale.FRANCE, Locale.JAPAN, Locale.CHINA),
            displayLocale,
        )

        assertEquals(
            setOf("Chinese", "English", "French", "Japanese"),
            groups.map { it.languageName }.toSet(),
        )
        assertTrue(groups.all { it.options.isNotEmpty() })
    }

    @Test
    fun `groups and options are sorted with the display locale`() {
        val groups = buildLocaleGroups(
            listOf(Locale.US, Locale.CHINA, Locale.UK, Locale.FRANCE, Locale.JAPAN),
            displayLocale,
        )

        assertEquals(
            listOf("Chinese", "English", "French", "Japanese"),
            groups.map { it.languageName },
        )
        assertEquals(
            listOf("en-GB", "en-US"),
            groups.single { it.languageName == "English" }.options.map { it.languageTag },
        )
    }

    @Test
    fun `locale options expose display and language names`() {
        val option = localeOption("en-US", displayLocale)

        assertEquals("en-US", option.languageTag)
        assertEquals("English (United States)", option.displayName)
        assertEquals("English", option.languageName)
    }

    @Test
    fun `search matches display name tag and language name`() {
        val groups = buildLocaleGroups(listOf(Locale.US, Locale.UK, Locale.CHINA), displayLocale)

        assertEquals(3, filterLocaleOptions("", groups).size)
        assertEquals(3, filterLocaleOptions("   ", groups).size)
        assertEquals(2, filterLocaleOptions("united", groups).size)
        assertEquals(listOf("en-GB"), filterLocaleOptions("  en-GB  ", groups).map { it.languageTag })
        assertEquals(listOf("en-US"), filterLocaleOptions("united states", groups).map { it.languageTag })
        assertEquals(listOf("zh-CN"), filterLocaleOptions("Chinese", groups).map { it.languageTag })
        assertTrue(filterLocaleOptions("zzzzz", groups).isEmpty())
    }

    @Test
    fun `suggested locales keep all regions of the system language and add en-US`() = runTest {
        val catalog = DefaultLocaleCatalog(
            remote = CatalogFakeLocaleRemote(listOf("zh-Hans-CN")),
            dispatchers = testDispatchers(),
        )

        val suggested = catalog.suggested().map { it.languageTag }

        assertEquals("en-US", suggested.first())
        assertTrue(suggested.contains("zh-Hans-CN"))
        // Every available Chinese region variant must be present, not just the system locale.
        assertTrue(suggested.count { it.startsWith("zh-") } >= 2)
        assertEquals(suggested.distinct(), suggested)
    }

    @Test
    fun `suggested locales fall back to en-US and the default locale`() = runTest {
        val catalog = DefaultLocaleCatalog(
            remote = CatalogFakeLocaleRemote(emptyList()),
            dispatchers = testDispatchers(),
        )

        val suggested = catalog.suggested().map { it.languageTag }

        assertEquals("en-US", suggested.first())
        assertTrue(suggested.contains(Locale.getDefault().toLanguageTag()))
    }

    @Test
    fun `buildSuggestedOptions expands the system language group`() {
        val groups = buildLocaleGroups(
            listOf(
                Locale.forLanguageTag("zh-Hans-CN"),
                Locale.forLanguageTag("zh-Hant-TW"),
                Locale.forLanguageTag("zh-Hant-HK"),
                Locale.forLanguageTag("en-US"),
                Locale.forLanguageTag("en-GB"),
            ),
            displayLocale,
        )

        val suggested = buildSuggestedOptions(
            systemTags = listOf("zh-Hans-CN"),
            groups = groups,
            displayLocale = displayLocale,
        ).map { it.languageTag }

        assertEquals("en-US", suggested.first())
        assertEquals(listOf("en-US", "zh-Hans-CN", "zh-Hant-HK", "zh-Hant-TW"), suggested)
    }

    @Test
    fun `buildSuggestedOptions handles an English system without duplicates`() {
        val groups = buildLocaleGroups(
            listOf(
                Locale.forLanguageTag("en-US"),
                Locale.forLanguageTag("en-GB"),
                Locale.forLanguageTag("zh-Hans-CN"),
            ),
            displayLocale,
        )

        val suggested = buildSuggestedOptions(
            systemTags = listOf("en-GB"),
            groups = groups,
            displayLocale = displayLocale,
        ).map { it.languageTag }

        assertEquals("en-US", suggested.first())
        assertEquals(suggested.distinct(), suggested)
        assertTrue(suggested.contains("en-GB"))
        assertTrue(!suggested.contains("zh-Hans-CN"))
    }

    @Test
    fun `buildSuggestedOptions falls back when the group is missing`() {
        val suggested = buildSuggestedOptions(
            systemTags = listOf("fr-FR"),
            groups = emptyList(),
            displayLocale = displayLocale,
        ).map { it.languageTag }

        assertEquals(listOf("en-US", "fr-FR"), suggested)
    }

    @Test
    fun `groups are cached per display locale`() = runTest {
        val catalog = DefaultLocaleCatalog(
            remote = CatalogFakeLocaleRemote(emptyList()),
            dispatchers = testDispatchers(),
        )

        val first = catalog.groups()
        val second = catalog.groups()

        assertTrue(first.isNotEmpty())
        assertSame(first, second)
    }

    private fun TestScope.testDispatchers(): AppDispatchers {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)
    }
}

private class CatalogFakeLocaleRemote(
    private val systemLocales: List<String>,
) : LocaleRemote {
    override suspend fun getUid(): Int = -1

    override suspend fun setApplicationLocales(packageName: String, languageTags: List<String>) = Unit

    override suspend fun getApplicationLocalesTags(packageNames: List<String>): Map<String, List<String>> =
        emptyMap()

    override suspend fun getSystemLocales(): List<String> = systemLocales

    override suspend fun forceStopPackage(packageName: String) = Unit

    override suspend fun firstRunningTaskPackage(): String = ""
}
