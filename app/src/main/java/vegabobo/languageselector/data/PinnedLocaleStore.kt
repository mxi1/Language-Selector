package vegabobo.languageselector.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vegabobo.languageselector.BuildConfig
import vegabobo.languageselector.core.contract.PinnedLocales
import vegabobo.languageselector.core.model.LocaleOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pinned languages persisted in the same SharedPreferences file
 * ([BuildConfig.APPLICATION_ID]) and under the same key as the pre-rewrite app,
 * so upgrades keep the user's pinned list and the QS tile keeps working.
 *
 * The encoding/parsing helpers live in `PinnedLocaleCodec.kt` and are pure JVM
 * code covered by unit tests.
 */
@Singleton
class DefaultPinnedLocales @Inject constructor(
    @ApplicationContext context: Context,
) : PinnedLocales {

    private val prefs = context.getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE)

    private val _pinned = MutableStateFlow(
        parsePinnedLocales(prefs.getStringSet(PINNED_LOCALES_KEY, emptySet()))
    )
    override val pinned: StateFlow<List<LocaleOption>> = _pinned.asStateFlow()

    override fun pin(option: LocaleOption) {
        val current = rawPinned()
        val updated = addPinnedLocale(current, option)
        if (updated == current) return
        prefs.edit().putStringSet(PINNED_LOCALES_KEY, updated).apply()
        _pinned.value = parsePinnedLocales(updated)
    }

    override fun unpin(languageTag: String) {
        val current = rawPinned()
        val updated = removePinnedLocale(current, languageTag)
        if (updated == current) return
        prefs.edit().putStringSet(PINNED_LOCALES_KEY, updated).apply()
        _pinned.value = parsePinnedLocales(updated)
    }

    private fun rawPinned(): Set<String> =
        prefs.getStringSet(PINNED_LOCALES_KEY, emptySet()).orEmpty()
}
