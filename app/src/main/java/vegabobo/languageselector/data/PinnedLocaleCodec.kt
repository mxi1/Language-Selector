package vegabobo.languageselector.data

import vegabobo.languageselector.core.model.LocaleOption
import vegabobo.languageselector.core.util.localeLanguageName
import java.text.Collator
import java.util.Locale

/**
 * Pure (Android-free) codec for the pinned-locale SharedPreferences value.
 *
 * The pre-rewrite app stored a `StringSet` under `"pinned_locales"` where every
 * entry looks like `"displayName,tag"`. These helpers keep that format readable
 * and testable without touching the Android framework:
 * - malformed entries (no separator, blank name or tag) are ignored,
 * - duplicates are collapsed by tag,
 * - the separator is the *last* comma, so display names may contain commas.
 */

/** SharedPreferences key used by the pre-rewrite app and the QS tile. */
internal const val PINNED_LOCALES_KEY = "pinned_locales"

/** Parses the legacy `"displayName,tag"` string set into a sorted option list. */
internal fun parsePinnedLocales(
    raw: Set<String>?,
    displayLocale: Locale = Locale.getDefault(),
): List<LocaleOption> {
    if (raw.isNullOrEmpty()) return emptyList()
    val optionsByTag = LinkedHashMap<String, LocaleOption>(raw.size)
    for (encoded in raw) {
        val option = parsePinnedEntry(encoded, displayLocale) ?: continue
        optionsByTag.putIfAbsent(option.languageTag, option)
    }
    val collator = Collator.getInstance(displayLocale)
    return optionsByTag.values.sortedWith(
        Comparator { left, right -> collator.compare(left.displayName, right.displayName) }
    )
}

/** Parses a single `"displayName,tag"` entry, or null when it is malformed. */
internal fun parsePinnedEntry(
    encoded: String,
    displayLocale: Locale = Locale.getDefault(),
): LocaleOption? {
    val languageTag = decodePinnedTag(encoded) ?: return null
    val separator = encoded.lastIndexOf(',')
    val displayName = encoded.substring(0, separator).trim()
    if (displayName.isEmpty()) return null
    return LocaleOption(
        languageTag = languageTag,
        displayName = displayName,
        languageName = localeLanguageName(languageTag, displayLocale),
    )
}

/** Extracts the tag of a `"displayName,tag"` entry, or null when malformed. */
internal fun decodePinnedTag(encoded: String): String? {
    val separator = encoded.lastIndexOf(',')
    if (separator <= 0 || separator == encoded.lastIndex) return null
    return encoded.substring(separator + 1).trim().ifEmpty { null }
}

/** Serializes an option using the legacy `"displayName,tag"` format. */
internal fun serializePinnedLocale(option: LocaleOption): String {
    val displayName = option.displayName.trim().ifEmpty { option.languageTag }
    return "$displayName,${option.languageTag}"
}

/** Adds [option] unless a locale with the same tag is already pinned. */
internal fun addPinnedLocale(raw: Set<String>, option: LocaleOption): Set<String> {
    val alreadyPinned = raw.any { decodePinnedTag(it) == option.languageTag }
    return if (alreadyPinned) raw else raw + serializePinnedLocale(option)
}

/** Removes every pinned entry whose tag equals [languageTag]. */
internal fun removePinnedLocale(raw: Set<String>, languageTag: String): Set<String> =
    raw.filterNot { decodePinnedTag(it) == languageTag }.toSet()
