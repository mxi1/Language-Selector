package vegabobo.languageselector.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
import vegabobo.languageselector.core.model.LocaleOption
import vegabobo.languageselector.core.util.localeDisplayName
import javax.inject.Inject

/** SavedStateHandle key holding the package shown by the detail screen. */
internal const val KEY_PACKAGE_NAME = "packageName"

/**
 * External intents launched from the detail screen. Extracted behind an interface so the
 * view model can be unit tested without the Android framework.
 */
interface AppNavigator {
    /** @return true when the app could be launched. */
    fun openApp(packageName: String): Boolean

    /** @return true when the system app-settings screen could be opened. */
    fun openAppSettings(packageName: String): Boolean
}

/** [AppNavigator] backed by the application context. */
class AndroidAppNavigator(private val context: Context) : AppNavigator {

    override fun openApp(packageName: String): Boolean = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    override fun openAppSettings(packageName: String): Boolean = runCatching {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

/**
 * Drives the per-app language screen: loads the app and its locale state, applies/reset
 * languages with undo, pins languages and opens external intents.
 *
 * All contract calls that may touch IPC run on [AppDispatchers.io] and are wrapped in
 * try/catch; failures surface as a [UiMessage] instead of escaping to the UI.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val appCatalog: AppCatalog,
    private val localeStates: LocaleStates,
    private val localeCatalog: LocaleCatalog,
    private val pinnedLocales: PinnedLocales,
    private val settingsStore: SettingsStore,
    private val serviceController: ServiceController,
    @ApplicationContext context: Context,
    private val dispatchers: AppDispatchers,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DetailUiState(
            isLoading = false,
            pinned = pinnedLocales.pinned.value,
            serviceStatus = serviceController.status.value,
        ),
    )
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    /** Overridable in unit tests so launch failures can be simulated. */
    internal var navigator: AppNavigator = AndroidAppNavigator(context)

    private var loadJob: Job? = null
    private var actionJob: Job? = null
    private var undoSnapshot: UndoSnapshot? = null

    private data class UndoSnapshot(val packageName: String, val previousTags: List<String>)

    init {
        observeSources()
        val restored = savedStateHandle.get<String>(KEY_PACKAGE_NAME)
        if (restored.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false) }
        } else {
            selectPackage(restored)
        }
    }

    private fun observeSources() {
        viewModelScope.launch(dispatchers.io) {
            pinnedLocales.pinned.collect { options ->
                _uiState.update { it.copy(pinned = options) }
            }
        }
        viewModelScope.launch(dispatchers.io) {
            localeStates.states.collect { states ->
                val packageName = _uiState.value.packageName ?: return@collect
                val tags = states[packageName]?.languageTags ?: return@collect
                _uiState.update { current ->
                    if (current.packageName != packageName || current.currentTags == tags) {
                        current
                    } else {
                        current.copy(
                            currentTags = tags,
                            currentDisplay = tags.primaryDisplay(),
                            unsupportedHintVisible = isUnsupported(tags, current.suggested),
                        )
                    }
                }
            }
        }
        viewModelScope.launch(dispatchers.io) {
            serviceController.status.collect { status ->
                _uiState.update { it.copy(serviceStatus = status) }
            }
        }
    }

    /**
     * Selects the package rendered by the detail screen. Compact navigation passes the
     * route argument; wide layouts call this whenever a different app is picked in the
     * list pane. Passing null clears the pane.
     */
    fun selectPackage(packageName: String?) {
        val resolved = packageName?.takeIf { it.isNotBlank() }
        savedStateHandle[KEY_PACKAGE_NAME] = resolved
        loadJob?.cancel()
        undoSnapshot = null
        if (resolved == null) {
            _uiState.update {
                it.copy(
                    packageName = null,
                    app = null,
                    currentTags = emptyList(),
                    currentDisplay = null,
                    isLoading = false,
                    languageQuery = "",
                    suggested = emptyList(),
                    groups = emptyList(),
                    expandedLanguage = null,
                    unsupportedHintVisible = false,
                    pendingConfirm = null,
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                packageName = resolved,
                app = null,
                currentTags = emptyList(),
                currentDisplay = null,
                isLoading = true,
                languageQuery = "",
                expandedLanguage = null,
                unsupportedHintVisible = false,
                pendingConfirm = null,
            )
        }
        load(resolved)
    }

    fun onLanguageQueryChange(query: String) {
        _uiState.update { it.copy(languageQuery = query) }
    }

    fun onToggleLanguageGroup(languageName: String) {
        _uiState.update {
            it.copy(
                expandedLanguage = if (it.expandedLanguage == languageName) null else languageName,
            )
        }
    }

    fun onSelectOption(option: LocaleOption) {
        val state = _uiState.value
        val packageName = state.packageName ?: return
        if (!state.isServiceReady) {
            _uiState.update { it.copy(message = UiMessage(R.string.error_service_unavailable)) }
            return
        }
        if (state.app?.isSystem == true && !settingsStore.isSystemAppWarned(packageName)) {
            _uiState.update { it.copy(pendingConfirm = option) }
            return
        }
        applyOption(packageName, option)
    }

    fun onConfirmSystemApply() {
        val state = _uiState.value
        val option = state.pendingConfirm ?: return
        val packageName = state.packageName ?: return
        runCatching { settingsStore.markSystemAppWarned(packageName) }
        _uiState.update { it.copy(pendingConfirm = null) }
        applyOption(packageName, option)
    }

    fun onDismissSystemConfirm() {
        _uiState.update { it.copy(pendingConfirm = null) }
    }

    fun onResetToSystem() {
        val state = _uiState.value
        val packageName = state.packageName ?: return
        if (!state.isServiceReady) {
            _uiState.update { it.copy(message = UiMessage(R.string.error_service_unavailable)) }
            return
        }
        if (state.currentTags.isEmpty()) return
        actionJob?.cancel()
        actionJob = viewModelScope.launch(dispatchers.io) {
            val previousTags = _uiState.value.currentTags
            try {
                localeStates.resetLocale(packageName)
                undoSnapshot = UndoSnapshot(packageName, previousTags)
                _uiState.update { current ->
                    if (current.packageName != packageName) {
                        current
                    } else {
                        current.copy(
                            currentTags = emptyList(),
                            currentDisplay = null,
                            unsupportedHintVisible = false,
                            message = UiMessage(
                                resId = R.string.snackbar_language_reset,
                                undoable = previousTags.isNotEmpty(),
                            ),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(packageName, e)
            }
        }
    }

    fun onTogglePin(option: LocaleOption) {
        val isPinned = _uiState.value.pinned.any { it.languageTag == option.languageTag }
        try {
            if (isPinned) {
                pinnedLocales.unpin(option.languageTag)
                _uiState.update {
                    it.copy(message = UiMessage(R.string.snackbar_unpinned, listOf(option.displayName)))
                }
            } else {
                pinnedLocales.pin(option)
                _uiState.update {
                    it.copy(message = UiMessage(R.string.snackbar_pinned, listOf(option.displayName)))
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(message = failureMessage(e)) }
        }
    }

    fun onForceStop() {
        val state = _uiState.value
        val packageName = state.packageName ?: return
        if (!state.isServiceReady) {
            _uiState.update { it.copy(message = UiMessage(R.string.error_service_unavailable)) }
            return
        }
        viewModelScope.launch(dispatchers.io) {
            try {
                localeStates.forceStop(packageName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(packageName, e)
            }
        }
    }

    fun onOpenApp() {
        val packageName = _uiState.value.packageName ?: return
        val launched = runCatching { navigator.openApp(packageName) }.getOrDefault(false)
        if (!launched) {
            _uiState.update {
                it.copy(
                    message = UiMessage(
                        resId = R.string.snackbar_error,
                        argResId = R.string.app_not_found,
                    ),
                )
            }
        }
    }

    fun onOpenAppSettings() {
        val packageName = _uiState.value.packageName ?: return
        val launched = runCatching { navigator.openAppSettings(packageName) }.getOrDefault(false)
        if (!launched) {
            _uiState.update {
                it.copy(
                    message = UiMessage(
                        resId = R.string.snackbar_error,
                        argResId = R.string.app_not_found,
                    ),
                )
            }
        }
    }

    /** Restores the language tags that were active before the last apply/reset. */
    fun onUndoApply() {
        val snapshot = undoSnapshot ?: return
        undoSnapshot = null
        if (_uiState.value.packageName != snapshot.packageName) return
        actionJob?.cancel()
        actionJob = viewModelScope.launch(dispatchers.io) {
            try {
                if (snapshot.previousTags.isEmpty()) {
                    localeStates.resetLocale(snapshot.packageName)
                } else {
                    localeStates.setLocale(snapshot.packageName, snapshot.previousTags)
                }
                val display = snapshot.previousTags.primaryDisplay()
                _uiState.update { current ->
                    if (current.packageName != snapshot.packageName) {
                        current
                    } else {
                        current.copy(
                            currentTags = snapshot.previousTags,
                            currentDisplay = display,
                            unsupportedHintVisible = isUnsupported(
                                snapshot.previousTags,
                                current.suggested,
                            ),
                            message = if (snapshot.previousTags.isEmpty()) {
                                UiMessage(R.string.snackbar_language_reset)
                            } else {
                                UiMessage(
                                    resId = R.string.snackbar_language_set,
                                    args = listOf(display.orEmpty()),
                                )
                            },
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(snapshot.packageName, e)
            }
        }
    }

    /** Clears the one-shot snackbar message once the screen has presented it. */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun load(packageName: String) {
        loadJob = viewModelScope.launch(dispatchers.io) {
            try {
                val app = findApp(packageName)
                if (app == null) {
                    if (_uiState.value.packageName == packageName) {
                        _uiState.update {
                            it.copy(
                                app = null,
                                isLoading = false,
                                message = UiMessage(R.string.app_not_found),
                            )
                        }
                    }
                    return@launch
                }
                val localeState = try {
                    localeStates.stateOf(packageName)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLocaleState()
                }
                val groups = try {
                    localeCatalog.groups()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                val suggested = try {
                    localeCatalog.suggested()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                val tags = localeState.languageTags
                _uiState.update { current ->
                    if (current.packageName != packageName) {
                        current
                    } else {
                        current.copy(
                            app = app,
                            currentTags = tags,
                            currentDisplay = tags.primaryDisplay(),
                            groups = groups,
                            suggested = suggested,
                            isLoading = false,
                            unsupportedHintVisible = isUnsupported(tags, suggested),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_uiState.value.packageName == packageName) {
                    _uiState.update { it.copy(isLoading = false, message = failureMessage(e)) }
                }
            }
        }
    }

    private suspend fun findApp(packageName: String): AppSummary? {
        try {
            appCatalog.find(packageName)?.let { return it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Fall through to a catalog refresh below.
        }
        // The catalog may not have been refreshed yet after a cold start.
        try {
            appCatalog.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The refresh itself failed; surface "not found" through the second lookup.
        }
        return try {
            appCatalog.find(packageName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun applyOption(packageName: String, option: LocaleOption) {
        actionJob?.cancel()
        actionJob = viewModelScope.launch(dispatchers.io) {
            val previousTags = _uiState.value.currentTags
            try {
                localeStates.setLocale(packageName, listOf(option.languageTag))
                undoSnapshot = UndoSnapshot(packageName, previousTags)
                _uiState.update { current ->
                    if (current.packageName != packageName) {
                        current
                    } else {
                        current.copy(
                            currentTags = listOf(option.languageTag),
                            currentDisplay = option.displayName,
                            unsupportedHintVisible = isUnsupported(
                                listOf(option.languageTag),
                                current.suggested,
                            ),
                            message = UiMessage(
                                resId = R.string.snackbar_language_set,
                                args = listOf(option.displayName),
                                undoable = true,
                            ),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(packageName, e)
            }
        }
    }

    private fun reportFailure(packageName: String, error: Throwable) {
        if (_uiState.value.packageName != packageName) return
        _uiState.update { it.copy(message = failureMessage(error)) }
    }

    private fun failureMessage(error: Throwable): UiMessage =
        UiMessage(resId = R.string.snackbar_error, args = listOf(briefError(error)))

    /** Short, single-line reason for the snackbar; never leaks a full stack trace. */
    private fun briefError(error: Throwable): String {
        val raw = error.message?.takeIf { it.isNotBlank() }
            ?: error.javaClass.simpleName.takeIf { it.isNotBlank() }
            ?: "error"
        return raw.lineSequence().firstOrNull().orEmpty().trim().take(MAX_ERROR_LENGTH)
    }

    private fun isUnsupported(tags: List<String>, suggested: List<LocaleOption>): Boolean {
        val tag = tags.firstOrNull() ?: return false
        if (suggested.isEmpty()) return false
        return suggested.none { it.languageTag == tag }
    }

    private fun List<String>.primaryDisplay(): String? =
        firstOrNull()?.let { localeDisplayName(it) }

    private companion object {
        const val MAX_ERROR_LENGTH = 120
    }
}
