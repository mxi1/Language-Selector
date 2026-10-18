package vegabobo.languageselector.ui.detail

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vegabobo.languageselector.R
import vegabobo.languageselector.core.contract.AppCatalog
import vegabobo.languageselector.core.contract.LocaleCatalog
import vegabobo.languageselector.core.contract.LocaleStates
import vegabobo.languageselector.core.contract.PinnedLocales
import vegabobo.languageselector.core.contract.ServiceController
import vegabobo.languageselector.core.contract.SettingsStore
import vegabobo.languageselector.core.dispatchers.AppDispatchers
import vegabobo.languageselector.core.model.AppLocaleState
import vegabobo.languageselector.core.model.AppSummary
import vegabobo.languageselector.core.model.LocaleGroup
import vegabobo.languageselector.core.model.LocaleOption
import vegabobo.languageselector.core.model.OperationMode
import vegabobo.languageselector.core.model.ServiceStatus
import vegabobo.languageselector.core.model.ThemeMode
import vegabobo.languageselector.core.util.localeDisplayName

private val APP = AppSummary(
    packageName = "com.example.app",
    label = "Example",
    isSystem = false,
)

private val SYSTEM_APP = APP.copy(isSystem = true)

private val SYSTEM_LOCALE = LocaleOption(
    languageTag = "en-US",
    displayName = "English (United States)",
    languageName = "English",
)

private val OTHER_LOCALE = LocaleOption(
    languageTag = "de-DE",
    displayName = "Deutsch (Deutschland)",
    languageName = "Deutsch",
)

private val SUGGESTED = listOf(SYSTEM_LOCALE)

private val GROUPS = listOf(
    LocaleGroup(languageName = "English", options = listOf(SYSTEM_LOCALE)),
    LocaleGroup(languageName = "Deutsch", options = listOf(OTHER_LOCALE)),
)

/**
 * Unit tests for [DetailViewModel] using hand written contract fakes. Constructing a real
 * [android.content.Context] is not needed: [ContextWrapper] is only used as an inert token,
 * because intents go through [AppNavigator] which is replaced in the tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val context: Context = ContextWrapper(null)

    private lateinit var appCatalog: FakeAppCatalog
    private lateinit var localeStates: FakeLocaleStates
    private lateinit var pinned: FakePinnedLocales
    private lateinit var settings: FakeSettingsStore
    private lateinit var service: FakeServiceController

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        appCatalog = FakeAppCatalog(listOf(APP))
        localeStates = FakeLocaleStates()
        pinned = FakePinnedLocales()
        settings = FakeSettingsStore()
        service = FakeServiceController()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(savedPackage: String? = APP.packageName): DetailViewModel {
        val handle = if (savedPackage == null) {
            SavedStateHandle()
        } else {
            SavedStateHandle(mapOf(KEY_PACKAGE_NAME to savedPackage))
        }
        return DetailViewModel(
            appCatalog = appCatalog,
            localeStates = localeStates,
            localeCatalog = FakeLocaleCatalog(),
            pinnedLocales = pinned,
            settingsStore = settings,
            serviceController = service,
            context = context,
            dispatchers = AppDispatchers(dispatcher, dispatcher, dispatcher),
            savedStateHandle = handle,
        )
    }

    private fun withSelectedState() {
        localeStates.putState(APP.packageName, AppLocaleState(listOf(SYSTEM_LOCALE.languageTag)))
    }

    @Test
    fun `selectPackage restores the app and its locale state`() = runTest(dispatcher) {
        withSelectedState()

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(APP.packageName, state.packageName)
        assertEquals(APP, state.app)
        assertFalse(state.isLoading)
        assertEquals(listOf(SYSTEM_LOCALE.languageTag), state.currentTags)
        assertEquals(localeDisplayName(SYSTEM_LOCALE.languageTag), state.currentDisplay)
        assertEquals(2, state.groups.size)
        assertEquals(SUGGESTED, state.suggested)
        assertFalse(state.unsupportedHintVisible)
    }

    @Test
    fun `selecting an option applies the locale and offers undo`() = runTest(dispatcher) {
        withSelectedState()
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSelectOption(OTHER_LOCALE)
        advanceUntilIdle()

        assertEquals(
            listOf(APP.packageName to listOf(OTHER_LOCALE.languageTag)),
            localeStates.setCalls,
        )
        val state = vm.uiState.value
        assertEquals(listOf(OTHER_LOCALE.languageTag), state.currentTags)
        assertEquals(OTHER_LOCALE.displayName, state.currentDisplay)
        assertEquals(R.string.snackbar_language_set, state.message?.resId)
        assertEquals(listOf(OTHER_LOCALE.displayName), state.message?.args)
        assertTrue(state.message?.undoable == true)
        assertTrue(state.unsupportedHintVisible)
    }

    @Test
    fun `failed apply reports a short error and keeps the selection`() = runTest(dispatcher) {
        withSelectedState()
        localeStates.failSet = true
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSelectOption(OTHER_LOCALE)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf(SYSTEM_LOCALE.languageTag), state.currentTags)
        assertEquals(R.string.snackbar_error, state.message?.resId)
        assertTrue(state.message?.args?.single().orEmpty().isNotBlank())
        assertFalse(state.message?.undoable == true)
    }

    @Test
    fun `undo restores the language that was active before the apply`() = runTest(dispatcher) {
        withSelectedState()
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSelectOption(OTHER_LOCALE)
        advanceUntilIdle()
        vm.onUndoApply()
        advanceUntilIdle()

        assertEquals(listOf(SYSTEM_LOCALE.languageTag), vm.uiState.value.currentTags)
        assertEquals(
            APP.packageName to listOf(SYSTEM_LOCALE.languageTag),
            localeStates.setCalls.last(),
        )
        assertEquals(R.string.snackbar_language_set, vm.uiState.value.message?.resId)
        assertEquals(
            localeDisplayName(SYSTEM_LOCALE.languageTag),
            vm.uiState.value.message?.args?.first(),
        )
    }

    @Test
    fun `reset clears the locale and undo restores the previous tags`() = runTest(dispatcher) {
        withSelectedState()
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onResetToSystem()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), vm.uiState.value.currentTags)
        assertNull(vm.uiState.value.currentDisplay)
        assertEquals(listOf(APP.packageName), localeStates.resetCalls)
        assertEquals(R.string.snackbar_language_reset, vm.uiState.value.message?.resId)
        assertTrue(vm.uiState.value.message?.undoable == true)

        vm.onUndoApply()
        advanceUntilIdle()

        assertEquals(listOf(SYSTEM_LOCALE.languageTag), vm.uiState.value.currentTags)
        assertEquals(
            APP.packageName to listOf(SYSTEM_LOCALE.languageTag),
            localeStates.setCalls.last(),
        )
    }

    @Test
    fun `system apps ask for confirmation before the first apply`() = runTest(dispatcher) {
        appCatalog = FakeAppCatalog(listOf(SYSTEM_APP))
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSelectOption(OTHER_LOCALE)
        advanceUntilIdle()

        assertEquals(OTHER_LOCALE, vm.uiState.value.pendingConfirm)
        assertTrue(localeStates.setCalls.isEmpty())
        assertFalse(settings.isSystemAppWarned(APP.packageName))

        vm.onConfirmSystemApply()
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingConfirm)
        assertTrue(settings.isSystemAppWarned(APP.packageName))
        assertEquals(
            listOf(APP.packageName to listOf(OTHER_LOCALE.languageTag)),
            localeStates.setCalls,
        )

        vm.onSelectOption(SYSTEM_LOCALE)
        advanceUntilIdle()

        assertEquals(2, localeStates.setCalls.size)
        assertNull(vm.uiState.value.pendingConfirm)
    }

    @Test
    fun `dismissing the system confirmation does not apply anything`() = runTest(dispatcher) {
        appCatalog = FakeAppCatalog(listOf(SYSTEM_APP))
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSelectOption(OTHER_LOCALE)
        advanceUntilIdle()
        vm.onDismissSystemConfirm()

        assertNull(vm.uiState.value.pendingConfirm)
        assertTrue(localeStates.setCalls.isEmpty())
        assertFalse(settings.isSystemAppWarned(APP.packageName))
    }

    @Test
    fun `pin and unpin update the pinned list and post messages`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onTogglePin(OTHER_LOCALE)
        advanceUntilIdle()

        assertEquals(listOf(OTHER_LOCALE), pinned.pinned.value)
        assertTrue(vm.uiState.value.pinned.any { it.languageTag == OTHER_LOCALE.languageTag })
        assertEquals(R.string.snackbar_pinned, vm.uiState.value.message?.resId)
        assertEquals(listOf(OTHER_LOCALE.displayName), vm.uiState.value.message?.args)

        vm.onTogglePin(OTHER_LOCALE)
        advanceUntilIdle()

        assertTrue(pinned.pinned.value.isEmpty())
        assertTrue(vm.uiState.value.pinned.isEmpty())
        assertEquals(R.string.snackbar_unpinned, vm.uiState.value.message?.resId)
    }

    @Test
    fun `missing app reports app_not_found`() = runTest(dispatcher) {
        val vm = createViewModel(savedPackage = "com.missing")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.app)
        assertFalse(state.isLoading)
        assertEquals(R.string.app_not_found, state.message?.resId)
    }

    @Test
    fun `apply is refused when the service is not ready`() = runTest(dispatcher) {
        service.statusFlow.value = ServiceStatus.PermissionRequired
        withSelectedState()
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSelectOption(OTHER_LOCALE)
        advanceUntilIdle()

        assertTrue(localeStates.setCalls.isEmpty())
        assertEquals(R.string.error_service_unavailable, vm.uiState.value.message?.resId)
    }

    @Test
    fun `open app failure posts an error message`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        val navigator = FakeAppNavigator(openResult = false)
        vm.navigator = navigator

        vm.onOpenApp()
        advanceUntilIdle()

        assertEquals(listOf(APP.packageName), navigator.opened)
        assertEquals(R.string.snackbar_error, vm.uiState.value.message?.resId)
        assertEquals(R.string.app_not_found, vm.uiState.value.message?.argResId)

        vm.consumeMessage()

        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `language query filters groups and toggles a single expanded language`() = runTest(dispatcher) {
        withSelectedState()
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onLanguageQueryChange("deu")
        advanceUntilIdle()

        assertEquals(listOf("Deutsch"), vm.uiState.value.filteredGroups.map { it.languageName })

        vm.onToggleLanguageGroup("English")
        assertEquals("English", vm.uiState.value.expandedLanguage)

        vm.onToggleLanguageGroup("English")
        assertNull(vm.uiState.value.expandedLanguage)
    }
}

private class FakeAppCatalog(initial: List<AppSummary>) : AppCatalog {
    private val _apps = MutableStateFlow(initial)
    override val apps: StateFlow<List<AppSummary>> = _apps

    override suspend fun refresh() = Unit

    override fun find(packageName: String): AppSummary? =
        _apps.value.firstOrNull { it.packageName == packageName }
}

private class FakeLocaleCatalog(
    private val groupList: List<LocaleGroup> = GROUPS,
    private val suggestedList: List<LocaleOption> = SUGGESTED,
) : LocaleCatalog {
    override suspend fun groups(): List<LocaleGroup> = groupList

    override suspend fun suggested(): List<LocaleOption> = suggestedList
}

private class FakeLocaleStates : LocaleStates {
    private val _states = MutableStateFlow<Map<String, AppLocaleState>>(emptyMap())
    override val states: StateFlow<Map<String, AppLocaleState>> = _states

    val setCalls = mutableListOf<Pair<String, List<String>>>()
    val resetCalls = mutableListOf<String>()
    val forceStopped = mutableListOf<String>()
    var failSet = false
    var failReset = false
    var failForceStop = false

    fun putState(packageName: String, state: AppLocaleState) {
        _states.update { it + (packageName to state) }
    }

    override suspend fun refresh() = Unit

    override suspend fun setLocale(packageName: String, languageTags: List<String>) {
        if (failSet) throw IllegalStateException("set failed")
        setCalls += packageName to languageTags
        putState(packageName, AppLocaleState(languageTags))
    }

    override suspend fun resetLocale(packageName: String) {
        if (failReset) throw IllegalStateException("reset failed")
        resetCalls += packageName
        _states.update { it - packageName }
    }

    override suspend fun forceStop(packageName: String) {
        if (failForceStop) throw IllegalStateException("force stop failed")
        forceStopped += packageName
    }

    override fun stateOf(packageName: String): AppLocaleState =
        _states.value[packageName] ?: AppLocaleState()
}

private class FakePinnedLocales(initial: List<LocaleOption> = emptyList()) : PinnedLocales {
    private val _pinned = MutableStateFlow(initial)
    override val pinned: StateFlow<List<LocaleOption>> = _pinned

    override fun pin(option: LocaleOption) {
        _pinned.update { current ->
            if (current.any { it.languageTag == option.languageTag }) current else current + option
        }
    }

    override fun unpin(languageTag: String) {
        _pinned.update { current -> current.filterNot { it.languageTag == languageTag } }
    }
}

private class FakeSettingsStore : SettingsStore {
    override val themeMode: StateFlow<ThemeMode> = MutableStateFlow(ThemeMode.SYSTEM)
    override val dynamicColor: StateFlow<Boolean> = MutableStateFlow(true)
    override val useRoot: StateFlow<Boolean> = MutableStateFlow(false)

    private val warned = mutableSetOf<String>()

    override fun setThemeMode(mode: ThemeMode) = Unit

    override fun setDynamicColor(enabled: Boolean) = Unit

    override fun setUseRoot(enabled: Boolean) = Unit

    override fun isSystemAppWarned(packageName: String): Boolean = packageName in warned

    override fun markSystemAppWarned(packageName: String) {
        warned += packageName
    }
}

private class FakeServiceController(
    initial: ServiceStatus = ServiceStatus.Ready(OperationMode.SHIZUKU),
) : ServiceController {
    val statusFlow = MutableStateFlow(initial)
    override val status: StateFlow<ServiceStatus> = statusFlow

    override fun refresh() = Unit

    override fun requestPermission() = Unit

    override fun acquire() = Unit

    override fun release() = Unit
}

private class FakeAppNavigator(
    private val openResult: Boolean = true,
    private val settingsResult: Boolean = true,
) : AppNavigator {
    val opened = mutableListOf<String>()
    val settingsOpened = mutableListOf<String>()

    override fun openApp(packageName: String): Boolean {
        opened += packageName
        return openResult
    }

    override fun openAppSettings(packageName: String): Boolean {
        settingsOpened += packageName
        return settingsResult
    }
}
