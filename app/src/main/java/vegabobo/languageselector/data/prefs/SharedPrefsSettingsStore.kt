package vegabobo.languageselector.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vegabobo.languageselector.BuildConfig
import vegabobo.languageselector.core.contract.SettingsStore
import vegabobo.languageselector.core.model.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPrefsSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    override val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))
    override val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _useRoot = MutableStateFlow(prefs.getBoolean(KEY_USE_ROOT, false))
    override val useRoot: StateFlow<Boolean> = _useRoot.asStateFlow()

    override fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    override fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    override fun setUseRoot(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_ROOT, enabled).apply()
        _useRoot.value = enabled
    }

    override fun isSystemAppWarned(packageName: String): Boolean =
        prefs.getStringSet(KEY_WARNED_SYSTEM_APPS, emptySet())?.contains(packageName) == true

    override fun markSystemAppWarned(packageName: String) {
        val current = prefs.getStringSet(KEY_WARNED_SYSTEM_APPS, emptySet()).orEmpty().toMutableSet()
        if (current.add(packageName)) {
            prefs.edit().putStringSet(KEY_WARNED_SYSTEM_APPS, current).apply()
        }
    }

    private fun readThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_USE_ROOT = "use_root"
        const val KEY_WARNED_SYSTEM_APPS = "warned_system_apps"
    }
}
