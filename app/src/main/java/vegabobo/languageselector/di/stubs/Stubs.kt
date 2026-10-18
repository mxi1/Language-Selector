package vegabobo.languageselector.di.stubs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vegabobo.languageselector.core.contract.AppCatalog
import vegabobo.languageselector.core.contract.HistoryStore
import vegabobo.languageselector.core.contract.LocaleCatalog
import vegabobo.languageselector.core.contract.LocaleRemote
import vegabobo.languageselector.core.contract.LocaleStates
import vegabobo.languageselector.core.contract.PinnedLocales
import vegabobo.languageselector.core.contract.ServiceController
import vegabobo.languageselector.core.model.AppLocaleState
import vegabobo.languageselector.core.model.AppSummary
import vegabobo.languageselector.core.model.LocaleGroup
import vegabobo.languageselector.core.model.LocaleOption
import vegabobo.languageselector.core.model.ServiceStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Temporary bindings used while the real service/data layers are being implemented.
 * They keep the app buildable and let the UI be developed in parallel.
 */
@Singleton
class StubServiceController @Inject constructor() : ServiceController {
    private val _status = MutableStateFlow<ServiceStatus>(ServiceStatus.Checking)
    override val status: StateFlow<ServiceStatus> = _status.asStateFlow()

    override fun refresh() = Unit
    override fun requestPermission() = Unit
    override fun acquire() = Unit
    override fun release() = Unit
}

@Singleton
class StubLocaleRemote @Inject constructor() : LocaleRemote {
    override suspend fun getUid(): Int = -1
    override suspend fun setApplicationLocales(packageName: String, languageTags: List<String>) = Unit
    override suspend fun getApplicationLocalesTags(packageNames: List<String>): Map<String, List<String>> =
        emptyMap()

    override suspend fun getSystemLocales(): List<String> = emptyList()
    override suspend fun forceStopPackage(packageName: String) = Unit
    override suspend fun firstRunningTaskPackage(): String = ""
}

@Singleton
class StubAppCatalog @Inject constructor() : AppCatalog {
    private val _apps = MutableStateFlow<List<AppSummary>>(emptyList())
    override val apps: StateFlow<List<AppSummary>> = _apps.asStateFlow()

    override suspend fun refresh() = Unit
    override fun find(packageName: String): AppSummary? = null
}

@Singleton
class StubLocaleStates @Inject constructor() : LocaleStates {
    private val _states = MutableStateFlow<Map<String, AppLocaleState>>(emptyMap())
    override val states: StateFlow<Map<String, AppLocaleState>> = _states.asStateFlow()

    override suspend fun refresh() = Unit
    override suspend fun setLocale(packageName: String, languageTags: List<String>) = Unit
    override suspend fun resetLocale(packageName: String) = Unit
    override suspend fun forceStop(packageName: String) = Unit
    override fun stateOf(packageName: String): AppLocaleState = AppLocaleState()
}

@Singleton
class StubLocaleCatalog @Inject constructor() : LocaleCatalog {
    override suspend fun groups(): List<LocaleGroup> = emptyList()
    override suspend fun suggested(): List<LocaleOption> = emptyList()
}

@Singleton
class StubPinnedLocales @Inject constructor() : PinnedLocales {
    private val _pinned = MutableStateFlow<List<LocaleOption>>(emptyList())
    override val pinned: StateFlow<List<LocaleOption>> = _pinned.asStateFlow()

    override fun pin(option: LocaleOption) = Unit
    override fun unpin(languageTag: String) = Unit
}

@Singleton
class StubHistoryStore @Inject constructor() : HistoryStore {
    private val _recent = MutableStateFlow<List<String>>(emptyList())
    override val recentPackages: StateFlow<List<String>> = _recent.asStateFlow()

    override suspend fun markSelected(packageName: String) = Unit
    override suspend fun clear() = Unit
}
