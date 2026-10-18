package vegabobo.languageselector.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vegabobo.languageselector.core.model.AppFilter
import vegabobo.languageselector.core.model.AppSummary

/** Pure JVM tests for the app list filtering/sorting/letter-indexing helpers. */
class AppListFilterTest {

    private fun app(
        packageName: String,
        label: String,
        isSystem: Boolean = false,
    ) = AppSummary(packageName = packageName, label = label, isSystem = isSystem)

    private val browser = app("com.example.browser", "Browser")
    private val chrome = app("com.android.chrome", "Chrome")
    private val settings = app("com.android.settings", "Settings", isSystem = true)
    private val apps = listOf(browser, chrome, settings)

    @Test
    fun `blank query returns the filtered list`() {
        assertEquals(listOf(browser, chrome), apps.filterAndSort(AppFilter(), "", emptySet()))
        assertEquals(listOf(browser, chrome), apps.filterAndSort(AppFilter(), "   ", emptySet()))
    }

    @Test
    fun `query matches label and package name ignoring case and spaces`() {
        assertEquals(
            listOf(chrome),
            apps.filterAndSort(AppFilter(), "  CHROME  ", emptySet()),
        )
        assertEquals(
            listOf(browser),
            apps.filterAndSort(AppFilter(), "example.BROWSER", emptySet()),
        )
        assertTrue(apps.filterAndSort(AppFilter(), "missing", emptySet()).isEmpty())
    }

    @Test
    fun `only modified keeps apps from the modified set`() {
        val filtered = apps.filterAndSort(
            filter = AppFilter(onlyModified = true),
            query = "",
            modifiedPackages = setOf(chrome.packageName),
        )

        assertEquals(listOf(chrome), filtered)
    }

    @Test
    fun `system apps are hidden unless modified`() {
        assertEquals(
            listOf(browser, chrome),
            apps.filterAndSort(AppFilter(showSystem = false), "", emptySet()),
        )

        val modifiedSystem = setOf(settings.packageName)
        assertEquals(
            listOf(settings, browser, chrome),
            apps.filterAndSort(AppFilter(showSystem = false), "", modifiedSystem),
        )
        assertEquals(
            listOf(settings, browser, chrome),
            apps.filterAndSort(AppFilter(showSystem = true), "", modifiedSystem),
        )
    }

    @Test
    fun `modified apps are sorted first and labels decide the rest`() {
        val zeta = app("com.example.zeta", "Zeta")
        val alpha = app("com.example.alpha", "Alpha")
        val beta = app("com.example.beta", "Beta")

        val sorted = listOf(zeta, alpha, beta)
            .filterAndSort(AppFilter(), "", setOf(zeta.packageName))

        assertEquals(listOf("Zeta", "Alpha", "Beta"), sorted.map { it.label })
    }

    @Test
    fun `sorting is deterministic for equal labels`() {
        val first = app("com.example.a", "Same")
        val second = app("com.example.b", "Same")

        val sorted = listOf(second, first).filterAndSort(AppFilter(), "", emptySet())

        assertEquals(listOf("com.example.a", "com.example.b"), sorted.map { it.packageName })
    }

    @Test
    fun `indexByLetter groups by upper cased first letter keeping incoming order`() {
        val alpha = app("p.alpha", "alpha")
        val anvil = app("p.anvil", "Anvil")
        val zulu = app("p.zulu", "Zulu")
        val digit = app("p.digit", "123 App")
        val chinese = app("p.zh", "中文应用")

        val sections = indexByLetter(listOf(alpha, zulu, anvil, digit, chinese))

        assertEquals(listOf('A', 'Z', '中', '#'), sections.map { it.first })
        assertEquals(listOf(alpha, anvil), sections.first { it.first == 'A' }.second)
        assertEquals(listOf(digit), sections.first { it.first == '#' }.second)
    }

    @Test
    fun `indexByLetter keeps every app and handles empty input`() {
        assertTrue(indexByLetter(emptyList()).isEmpty())
        assertEquals(apps.size, indexByLetter(apps).sumOf { it.second.size })
    }
}
