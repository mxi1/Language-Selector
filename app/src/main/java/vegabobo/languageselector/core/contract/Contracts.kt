package vegabobo.languageselector.core.contract

import kotlinx.coroutines.flow.StateFlow
import vegabobo.languageselector.core.model.AppFilter
import vegabobo.languageselector.core.model.AppLocaleState
import vegabobo.languageselector.core.model.AppSummary
import vegabobo.languageselector.core.model.LocaleGroup
import vegabobo.languageselector.core.model.LocaleOption
import vegabobo.languageselector.core.model.ServiceStatus
import vegabobo.languageselector.core.model.ThemeMode

/**
 * Owns the Shizuku / Root binding lifecycle and exposes the current status.
 * Implemented by the service layer.
 */
interface ServiceController {
    val status: StateFlow<ServiceStatus>

    /** Re-check Shizuku availability, permission and root option. */
    fun refresh()

    /** Request the Shizuku permission from the Shizuku app. */
    fun requestPermission()

    /** Keep the user service bound while an owner (UI or QS tile) is active. */
    fun acquire()

    /** Release a previous [acquire]; unbinds the user service at zero. */
    fun release()
}

/**
 * Remote privileged locale operations. Every call suspends and may fail;
 * implementations map failures to exceptions handled by the repositories.
 */
interface LocaleRemote {
    suspend fun getUid(): Int
    suspend fun setApplicationLocales(packageName: String, languageTags: List<String>)
    suspend fun getApplicationLocalesTags(packageNames: List<String>): Map<String, List<String>>
    suspend fun getSystemLocales(): List<String>
    suspend fun forceStopPackage(packageName: String)
    suspend fun firstRunningTaskPackage(): String
}

/** Installed apps snapshot. */
interface AppCatalog {
    val apps: StateFlow<List<AppSummary>>
    suspend fun refresh()
    fun find(packageName: String): AppSummary?
}

/** Per-app locale state cache backed by [LocaleRemote]. */
interface LocaleStates {
    val states: StateFlow<Map<String, AppLocaleState>>
    suspend fun refresh()
    suspend fun setLocale(packageName: String, languageTags: List<String>)
    suspend fun resetLocale(packageName: String)
    suspend fun forceStop(packageName: String)
    fun stateOf(packageName: String): AppLocaleState
}

/** Locale catalog (all available locales grouped by language, plus system locales). */
interface LocaleCatalog {
    suspend fun groups(): List<LocaleGroup>
    suspend fun suggested(): List<LocaleOption>
}

/** Pinned languages, persisted across restarts and shared with the QS tile. */
interface PinnedLocales {
    val pinned: StateFlow<List<LocaleOption>>
    fun pin(option: LocaleOption)
    fun unpin(languageTag: String)
}

/** Recently selected apps. */
interface HistoryStore {
    val recentPackages: StateFlow<List<String>>
    suspend fun markSelected(packageName: String)
    suspend fun clear()
}

/** User settings persisted in SharedPreferences. */
interface SettingsStore {
    val themeMode: StateFlow<ThemeMode>
    val dynamicColor: StateFlow<Boolean>
    val useRoot: StateFlow<Boolean>
    fun setThemeMode(mode: ThemeMode)
    fun setDynamicColor(enabled: Boolean)
    fun setUseRoot(enabled: Boolean)
    fun isSystemAppWarned(packageName: String): Boolean
    fun markSystemAppWarned(packageName: String)
}

/** Helper used by the home screen to build sections out of filtered apps. */
data class AppListSnapshot(
    val apps: List<AppSummary>,
    val filter: AppFilter,
    val modifiedPackages: Set<String>,
)
