package vegabobo.languageselector.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vegabobo.languageselector.R
import vegabobo.languageselector.core.contract.AppCatalog
import vegabobo.languageselector.core.contract.HistoryStore
import vegabobo.languageselector.core.contract.LocaleStates
import vegabobo.languageselector.core.contract.ServiceController
import vegabobo.languageselector.core.dispatchers.AppDispatchers
import vegabobo.languageselector.core.model.AppFilter
import vegabobo.languageselector.core.model.AppLocaleState
import vegabobo.languageselector.core.model.AppSummary
import vegabobo.languageselector.data.filterAndSort
import vegabobo.languageselector.data.indexByLetter
import javax.inject.Inject

/**
 * Drives the home screen: app list, search, filters, recents and the service banner.
 *
 * All list computation (filter + sort + letter grouping) happens here on
 * [AppDispatchers.default], never inside a composable. The query is debounced so typing
 * does not recompute the list on every keystroke.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appCatalog: AppCatalog,
    private val localeStates: LocaleStates,
    private val historyStore: HistoryStore,
    private val serviceController: ServiceController,
    private val dispatchers: AppDispatchers,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(AppFilter())
    private val selectedPackage = MutableStateFlow(savedStateHandle.get<String>(KEY_SELECTED_PACKAGE))
    private val isLoading = MutableStateFlow(true)
    private val serviceBannerDismissed = MutableStateFlow(false)
    private val message = MutableStateFlow<UiMessage?>(null)
    private var refreshJob: Job? = null

    /** Raw query kept in the text field, debounced variant drives the expensive filtering. */
    @OptIn(FlowPreview::class)
    private val debouncedQuery: StateFlow<String> = query
        .debounce(QUERY_DEBOUNCE_MILLIS)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val localeStateMap: StateFlow<Map<String, AppLocaleState>> = localeStates.states
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val modifiedPackages: StateFlow<Set<String>> = localeStateMap
        .map { states -> states.filterValues { it.isModified }.keys.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val filteredApps: StateFlow<List<AppSummary>> = combine(
        appCatalog.apps,
        debouncedQuery,
        filter,
        modifiedPackages,
    ) { apps, currentQuery, currentFilter, modified ->
        withContext(dispatchers.default) {
            apps.filterAndSort(currentFilter, currentQuery, modified)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val letterSections: StateFlow<List<Pair<Char, List<AppSummary>>>> = filteredApps
        .map { apps -> indexByLetter(apps) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val recentApps: StateFlow<List<AppSummary>> = historyStore.recentPackages
        .map { packages -> packages.take(RECENT_LIMIT).mapNotNull(appCatalog::find) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** List-part of the state: everything derived from app/locale/history flows. */
    private data class ListPart(
        val apps: List<AppSummary>,
        val letterSections: List<Pair<Char, List<AppSummary>>>,
        val recentApps: List<AppSummary>,
        val localeStates: Map<String, AppLocaleState>,
        val modifiedPackages: Set<String>,
    )

    private val listPart = combine(
        filteredApps,
        letterSections,
        recentApps,
        localeStateMap,
        modifiedPackages,
    ) { apps, sections, recent, states, modified ->
        ListPart(apps, sections, recent, states, modified)
    }

    /** Input-part of the state: user actions and service status. */
    private data class InputPart(
        val query: String,
        val filter: AppFilter,
        val selectedPackage: String?,
        val serviceBannerDismissed: Boolean,
        val isLoading: Boolean,
    )

    private val inputPart = combine(
        query,
        filter,
        selectedPackage,
        serviceBannerDismissed,
        isLoading,
    ) { currentQuery, currentFilter, currentSelection, bannerDismissed, loading ->
        InputPart(currentQuery, currentFilter, currentSelection, bannerDismissed, loading)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        listPart,
        inputPart,
        message,
        serviceController.status,
    ) { list, input, currentMessage, status ->
        HomeUiState(
            isLoading = input.isLoading,
            query = input.query,
            filter = input.filter,
            recentApps = list.recentApps,
            apps = list.apps,
            letterSections = list.letterSections,
            modifiedPackages = list.modifiedPackages,
            selectedPackage = input.selectedPackage,
            serviceStatus = status,
            message = currentMessage,
            localeStates = list.localeStates,
            isServiceBannerDismissed = input.serviceBannerDismissed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(isLoading = true),
    )

    init {
        // Kick off the first load without blocking construction.
        refresh()
    }

    /** Re-reads installed apps and their locale states; failures surface as a snackbar message. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            isLoading.value = true
            serviceBannerDismissed.value = false
            val failure = try {
                coroutineScope {
                    launch { appCatalog.refresh() }
                    launch { localeStates.refresh() }
                }
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                throwable
            }
            if (failure != null) {
                message.value = UiMessage(R.string.error_service_unavailable)
            }
            isLoading.value = false
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun toggleSystem() {
        filter.update { it.copy(showSystem = !it.showSystem) }
    }

    fun toggleModified() {
        filter.update { it.copy(onlyModified = !it.onlyModified) }
    }

    /** Remembers the last opened app across process death and updates the recents. */
    fun selectApp(packageName: String) {
        selectedPackage.value = packageName
        savedStateHandle[KEY_SELECTED_PACKAGE] = packageName
        viewModelScope.launch {
            runCatching { historyStore.markSelected(packageName) }
        }
    }

    fun clearRecent() {
        viewModelScope.launch {
            runCatching { historyStore.clear() }
                .onFailure { message.value = UiMessage(R.string.error_service_unavailable) }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun dismissServiceBanner() {
        serviceBannerDismissed.value = true
    }

    private companion object {
        const val QUERY_DEBOUNCE_MILLIS = 200L
        const val RECENT_LIMIT = 5
        const val KEY_SELECTED_PACKAGE = "home_selected_package"
    }
}
