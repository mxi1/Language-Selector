package vegabobo.languageselector

import vegabobo.languageselector.ui.screen.appinfo.LocaleRegion
import vegabobo.languageselector.ui.screen.appinfo.SingleLocale
import vegabobo.languageselector.ui.screen.appinfo.capDisplayName
import java.util.Locale

class LocaleManager {

    val localeList = ArrayList<LocaleRegion>()

    init {
        val locales = Locale.getAvailableLocales()
        val localeListMap = mutableMapOf<String, LocaleRegion>()
        for (locale in locales) {
            val languageName = locale.capDisplayName()
            val languageTag = locale.toLanguageTag()

            if (languageTag != "zh-Hans" && languageTag != "zh-Hans-CN" && languageTag != "zh-Hans-HK"
                && languageTag != "zh-Hant" && languageTag != "zh-Hant-TW" &&
                languageTag != "en-GB" && languageTag != "en-US" && languageTag != "en-HK") {
                continue
            }

            val language = locale.getDisplayLanguage(locale).replaceFirstChar { it.uppercaseChar() }

            val existingLocale = localeListMap[language]
            if (existingLocale != null) {
                val singleLocale = SingleLocale(languageName, languageTag)
                existingLocale.locales.add(singleLocale)
                continue
            }

            localeListMap[language] =
                LocaleRegion(language, arrayListOf())
        }
        localeList.addAll(localeListMap.values)
        localeList.sortBy { it.language }
    }

}