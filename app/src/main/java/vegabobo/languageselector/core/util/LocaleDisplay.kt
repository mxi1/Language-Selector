package vegabobo.languageselector.core.util

import java.util.Locale

/**
 * Human readable name for a BCP-47 language tag, e.g. "English (United States)".
 * Falls back to the tag itself when the tag is unknown.
 */
fun localeDisplayName(languageTag: String, displayLocale: Locale = Locale.getDefault()): String {
    if (languageTag.isBlank()) return ""
    val locale = Locale.forLanguageTag(languageTag)
    val name = locale.getDisplayName(displayLocale)
    if (name.isBlank()) return languageTag
    return name.replaceFirstChar { first ->
        if (first.isLowerCase()) first.titlecase(displayLocale) else first.toString()
    }
}

/** Language name for a BCP-47 tag, e.g. "English" or "中文". */
fun localeLanguageName(languageTag: String, displayLocale: Locale = Locale.getDefault()): String {
    if (languageTag.isBlank()) return ""
    val locale = Locale.forLanguageTag(languageTag)
    val name = locale.getDisplayLanguage(displayLocale)
    if (name.isBlank()) return languageTag
    return name.replaceFirstChar { first ->
        if (first.isLowerCase()) first.titlecase(displayLocale) else first.toString()
    }
}

fun languageTagToLocale(languageTag: String): Locale = Locale.forLanguageTag(languageTag)
