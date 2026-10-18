package vegabobo.languageselector.data

import kotlinx.coroutines.withContext
import vegabobo.languageselector.core.contract.LocaleCatalog
import vegabobo.languageselector.core.contract.LocaleRemote
import vegabobo.languageselector.core.dispatchers.AppDispatchers
import vegabobo.languageselector.core.model.LocaleGroup
import vegabobo.languageselector.core.model.LocaleOption
import vegabobo.languageselector.core.util.localeDisplayName
import vegabobo.languageselector.core.util.localeLanguageName
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the [LocaleOption] describing [languageTag] in [displayLocale]. */
internal fun localeOption(
    languageTag: String,
    displayLocale: Locale = Locale.getDefault(),
): LocaleOption = LocaleOption(
    languageTag = languageTag,
    displayName = localeDisplayName(languageTag, displayLocale),
    languageName = localeLanguageName(languageTag, displayLocale),
)

/**
 * Builds one option per distinct language tag, in input order.
 *
 * No language is filtered out: the old implementation hard-coded English and
 * Chinese only, the catalog now exposes every locale reported by the platform.
 */
internal fun buildLocaleOptions(
    locales: List<Locale>,
    displayLocale: Locale = Locale.getDefault(),
): List<LocaleOption> {
    val optionsByTag = LinkedHashMap<String, LocaleOption>(locales.size)
    for (locale in locales) {
        val languageTag = locale.toLanguageTag()
        if (languageTag.isBlank() || optionsByTag.containsKey(languageTag)) continue
        optionsByTag[languageTag] = localeOption(languageTag, displayLocale)
    }
    return optionsByTag.values.toList()
}

/**
 * Groups [locales] by their localized language name.
 *
 * Fixes the old bug where the first locale of every language created the group
 * key but was never added to the group itself. Options and groups are sorted
 * with a [Collator] so the order matches the user's locale.
 */
internal fun buildLocaleGroups(
    locales: List<Locale>,
    displayLocale: Locale = Locale.getDefault(),
): List<LocaleGroup> {
    val collator = Collator.getInstance(displayLocale)
    val optionsByLanguage = LinkedHashMap<String, MutableList<LocaleOption>>()
    for (option in buildLocaleOptions(locales, displayLocale)) {
        optionsByLanguage.getOrPut(option.languageName) { mutableListOf() }.add(option)
    }
    return optionsByLanguage
        .map { (languageName, options) ->
            LocaleGroup(
                languageName = languageName,
                options = options.sortedWith(
                    Comparator { left, right -> collator.compare(left.displayName, right.displayName) }
                ),
            )
        }
        .sortedWith(Comparator { left, right -> collator.compare(left.languageName, right.languageName) })
}

/** Case-insensitive, trim-tolerant search over display name, tag and language name. */
internal fun filterLocaleOptions(query: String, groups: List<LocaleGroup>): List<LocaleOption> {
    val needle = query.trim()
    if (needle.isEmpty()) return groups.flatMap { it.options }
    return groups
        .asSequence()
        .flatMap { it.options.asSequence() }
        .filter { option ->
            option.displayName.contains(needle, ignoreCase = true) ||
                option.languageTag.contains(needle, ignoreCase = true) ||
                option.languageName.contains(needle, ignoreCase = true)
        }
        .toList()
}

/**
 * All platform locales grouped by language, plus the suggested (system) locales.
 *
 * The grouped catalog is expensive to build, so it is cached per display locale
 * and rebuilt only when the default locale changes.
 */
@Singleton
class DefaultLocaleCatalog @Inject constructor(
    private val remote: LocaleRemote,
    private val dispatchers: AppDispatchers,
) : LocaleCatalog {

    private val cacheLock = Any()
    private var cachedDisplayLocaleTag: String? = null
    private var cachedGroups: List<LocaleGroup> = emptyList()

    override suspend fun groups(): List<LocaleGroup> = withContext(dispatchers.io) {
        loadGroups(Locale.getDefault())
    }

    override suspend fun suggested(): List<LocaleOption> = withContext(dispatchers.io) {
        val systemTags = remote.getSystemLocales()
            .mapNotNull { tag -> tag.trim().takeIf { it.isNotEmpty() } }
            .distinct()
        if (systemTags.isEmpty()) {
            listOf(localeOption(Locale.getDefault().toLanguageTag()))
        } else {
            systemTags.map { tag -> localeOption(tag) }
        }
    }

    /** Searches [groups] for [query]; an empty query returns every option. */
    suspend fun search(query: String, groups: List<LocaleGroup>): List<LocaleOption> =
        withContext(dispatchers.io) { filterLocaleOptions(query, groups) }

    private fun loadGroups(displayLocale: Locale): List<LocaleGroup> = synchronized(cacheLock) {
        val displayLocaleTag = displayLocale.toLanguageTag()
        if (cachedDisplayLocaleTag == displayLocaleTag && cachedGroups.isNotEmpty()) {
            return@synchronized cachedGroups
        }
        val built = buildLocaleGroups(Locale.getAvailableLocales().toList(), displayLocale)
        cachedDisplayLocaleTag = displayLocaleTag
        cachedGroups = built
        built
    }
}
