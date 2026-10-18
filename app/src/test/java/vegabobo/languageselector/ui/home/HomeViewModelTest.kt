package vegabobo.languageselector.ui.home

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vegabobo.languageselector.R
import vegabobo.languageselector.core.contract.AppCatalog
import vegabobo.languageselector.core.contract.HistoryStore
import vegabobo.languageselector.core.contract.LocaleStates
import vegabobo.languageselector.core.contract.ServiceController
import vegabobo.languageselector.core.dispatchers.AppDispatchers
import vegabobo.languageselector.core.model.AppLocaleState
import vegabobo.languageselector.core.model.AppSummary
import vegabobo.languageselector.core.model.OperationMode
import vegabobo.languageselector.core.model.ServiceStatus

/**
 * Pure JVM tests for [HomeViewModel]: fake contracts + `kotlinx-coroutines-test`, no Android
 * runtime involved. The real `.filterAndSort` / `indexByLetter` from the data layer are exercised
 * through the view model, so the screen-level logic is covered end to end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val alpha = AppSummary(packageName = "com.alpha", label = "Alpha", isSystem = false)
    private val zeta = AppSummary(packageName = "com.zeta", label = "Zeta", isSystem = false)
    private val system = AppSummary(packageName = "com.system", label = "System App", isSystem = true)

    /**
     * Runs a test with a virtual-time dispatcher installed as both `Dispatchers.Main` (viewModelScope)
     * and [AppDispatchers], sharing the `runTest` scheduler so `advanceUntilIdle()` drives everything.
     */
    private fun homeTest(block: suspend TestScope.(AppDispatchers) -> Unit) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            block.invoke(
                this,
                AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        dispatchers: AppDispatchers,
        apps: List<AppSummary> = listOf(zeta, alpha, system),
        localeStates: Map<String, AppLocaleState> = mapOf(
            alpha.packageName to AppLocaleState(listOf("fr-FR")),
        ),
        recents: List<String> = listOf(alpha.packageName),
        failRefresh: Boolean = false,
        serviceStatus: ServiceStatus = ServiceStatus.Ready(OperationMode.SHIZUKU),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): HomeViewModel = HomeViewModel(
        appCatalog = FakeAppCatalog(apps, failRefresh = failRefresh),
        localeStates = FakeLocaleStates(localeStates),
        historyStore = FakeHistoryStore(recents),
        serviceController = FakeServiceController(serviceStatus),
        dispatchers = dispatchers,
        savedStateHandle = savedStateHandle,
    )

    @Test
    fun initialLoadFiltersSortsAndGroupsApps() = homeTest { dispatchers ->
        val viewModel = createViewModel(dispatchers)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isInitialLoading)
        // "com.system" is filtered out (showSystem = false by default), the modified app comes first.
        assertEquals(listOf(alpha.packageName, zeta.packageName), state.apps.map { it.packageName })
        assertEquals(listOf(alpha.packageName), state.recentApps.map { it.packageName })
        assertEquals(setOf(alpha.packageName), state.modifiedPackages)
        assertEquals(listOf('A', 'Z'), state.letterSections.map { it.first.uppercaseChar() })
        assertEquals(
            listOf(alpha.packageName, zeta.packageName),
            state.letterSections.flatMap { it.second }.map { it.packageName },
        )
        assertEquals(ServiceStatus.Ready(OperationMode.SHIZUKU), state.serviceStatus)
    }

    @Test
    fun queryIsDebouncedBeforeFiltering() = homeTest { dispatchers ->
        val viewModel = createViewModel(dispatchers)
        advanceUntilIdle()

        viewModel.onQueryChange("zet")
        runCurrent()

        // The raw query updates immediately, the filtered list only after the debounce window.
        assertEquals("zet", viewModel.uiState.value.query)
        assertEquals(2, viewModel.uiState.value.apps.size)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("zet", state.query)
        assertEquals(listOf(zeta.packageName), state.apps.map { it.packageName })
        assertEquals(listOf('Z'), state.letterSections.map { it.first.uppercaseChar() })
    }

    @Test
    fun togglingFiltersRecomputesTheList() = homeTest { dispatchers ->
        val viewModel = createViewModel(dispatchers)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.apps.size)

        viewModel.toggleSystem()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.filter.showSystem)
        assertEquals(
            listOf(alpha.packageName, system.packageName, zeta.packageName),
            viewModel.uiState.value.apps.map { it.packageName },
        )

        viewModel.toggleModified()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.filter.onlyModified)
        assertEquals(
            listOf(alpha.packageName),
            viewModel.uiState.value.apps.map { it.packageName },
        )
    }

    @Test
    fun selectedPackageIsKeptInSavedStateHandle() = homeTest { dispatchers ->
        val savedStateHandle = SavedStateHandle()
        val viewModel = createViewModel(dispatchers, savedStateHandle = savedStateHandle)
        advanceUntilIdle()

        viewModel.selectApp(zeta.packageName)
        advanceUntilIdle()
        assertEquals(zeta.packageName, viewModel.uiState.value.selectedPackage)

        // A recreated view model with the same handle restores the selection.
        val recreated = createViewModel(dispatchers, savedStateHandle = savedStateHandle)
        advanceUntilIdle()
        assertEquals(zeta.packageName, recreated.uiState.value.selectedPackage)
    }

    @Test
    fun refreshFailureSurfacesMessageWithoutCrashing() = homeTest { dispatchers ->
        val viewModel = createViewModel(dispatchers, failRefresh = true)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(R.string.error_service_unavailable, state.message?.resId)

        viewModel.consumeMessage()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun serviceBannerIsDismissible() = homeTest { dispatchers ->
        val viewModel = createViewModel(
            dispatchers = dispatchers,
            serviceStatus = ServiceStatus.PermissionRequired,
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showServiceBanner)

        viewModel.dismissServiceBanner()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showServiceBanner)
    }

    private class FakeAppCatalog(
        initialApps: List<AppSummary>,
        private val failRefresh: Boolean = false,
    ) : AppCatalog {
        private val state = MutableStateFlow(initialApps)
        override val apps: StateFlow<List<AppSummary>> = state

        override suspend fun refresh() {
            if (failRefresh) throw IllegalStateException("catalog refresh failed")
        }

        override fun find(packageName: String): AppSummary? =
            state.value.firstOrNull { it.packageName == packageName }
    }

    private class FakeLocaleStates(
        initial: Map<String, AppLocaleState>,
    ) : LocaleStates {
        private val state = MutableStateFlow(initial)
        override val states: StateFlow<Map<String, AppLocaleState>> = state

        override suspend fun refresh() = Unit

        override suspend fun setLocale(packageName: String, languageTags: List<String>) {
            state.value = state.value + (packageName to AppLocaleState(languageTags))
        }

        override suspend fun resetLocale(packageName: String) {
            state.value = state.value + (packageName to AppLocaleState())
        }

        override suspend fun forceStop(packageName: String) = Unit

        override fun stateOf(packageName: String): AppLocaleState =
            state.value[packageName] ?: AppLocaleState()
    }

    private class FakeHistoryStore(initial: List<String>) : HistoryStore {
        private val recent = MutableStateFlow(initial)
        override val recentPackages: StateFlow<List<String>> = recent

        override suspend fun markSelected(packageName: String) {
            recent.value = (listOf(packageName) + recent.value.filterNot { it == packageName }).take(20)
        }

        override suspend fun clear() {
            recent.value = emptyList()
        }
    }

    private class FakeServiceController(initial: ServiceStatus) : ServiceController {
        private val state = MutableStateFlow(initial)
        override val status: StateFlow<ServiceStatus> = state

        override fun refresh() = Unit
        override fun requestPermission() = Unit
        override fun acquire() = Unit
        override fun release() = Unit
    }
}
