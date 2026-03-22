package vegabobo.languageselector.ui.screen.main

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import vegabobo.languageselector.BuildConfig
import vegabobo.languageselector.RootReceivedListener
import vegabobo.languageselector.dao.AppInfoDb
import vegabobo.languageselector.service.UserServiceProvider
import javax.inject.Inject


@HiltViewModel
@OptIn(FlowPreview::class)
class MainScreenVm @Inject constructor(
    val app: Application,
    appInfoDb: AppInfoDb
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainScreenState())
    val uiState: StateFlow<MainScreenState> = _uiState.asStateFlow()
    var lastSelectedApp: AppInfo? = null
    val dao = appInfoDb.appInfoDao()

    private val _searchQueryFlow = MutableStateFlow("")

    private val appComparator =
        compareByDescending<AppInfo> { it.isModified() }.thenBy { it.name.lowercase() }

    init {
        fillListOfApps()
        viewModelScope.launch {
            _searchQueryFlow
                .debounce(300L)
                .distinctUntilChanged()
                .collect { recomputeSearchResults() }
        }
    }

    fun getIndexFromAppInfoItem(): Int {
        return _uiState.value.displayedApps.indexOfFirst { it.pkg == lastSelectedApp?.pkg }
    }

    fun loadOperationMode() {
        if (Shell.getShell().isAlive)
            Shell.getShell().close()
        Shell.getShell()
        if (Shell.isAppGrantedRoot() == true) {
            _uiState.update { it.copy(operationMode = OperationMode.ROOT) }
            RootReceivedListener.onRootReceived()
            return
        }

        val isAvail = Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        if (isAvail) {
            _uiState.update { it.copy(operationMode = OperationMode.SHIZUKU) }
            return
        }

        _uiState.update { it.copy(operationMode = OperationMode.NONE) }
    }

    fun parseAppInfo(a: ApplicationInfo): AppInfo {
        val isSystemApp = (a.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val service = UserServiceProvider.getService()
        val languagePreferences = service.getApplicationLocales(a.packageName)
        val labels = arrayListOf<AppLabels>()
        if (isSystemApp)
            labels.add(AppLabels.SYSTEM_APP)
        if (!languagePreferences.isEmpty)
            labels.add(AppLabels.MODIFIED)
        return AppInfo(
            icon = app.packageManager.getAppIcon(a),
            name = app.packageManager.getLabel(a),
            pkg = a.packageName,
            labels = labels
        )
    }

    fun fillListOfApps() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.operationMode == OperationMode.NONE)
                loadOperationMode()
            val sortedList = getInstalledPackages()
                .map { parseAppInfo(it) }
                .sortedWith(appComparator)
            val displayed = computeDisplayed(sortedList, _uiState.value.isShowSystemAppsHome)
            _uiState.update {
                it.copy(
                    listOfApps = sortedList,
                    displayedApps = displayed,
                    isLoading = false
                )
            }
        }
    }

    fun getInstalledPackages(): List<ApplicationInfo> {
        return app.packageManager.getInstalledApplications(
            PackageManager.ApplicationInfoFlags.of(0)
        ).mapNotNull {
            if (!it.enabled || BuildConfig.APPLICATION_ID == it.packageName)
                null
            else
                it
        }
    }

    private fun computeDisplayed(apps: List<AppInfo>, showSystem: Boolean): List<AppInfo> {
        return if (showSystem) apps else apps.filter { !it.isSystemApp() || it.isModified() }
    }

    fun toggleDropdown() {
        val newDropdownVisibility = !uiState.value.isDropdownVisible
        _uiState.update { it.copy(isDropdownVisible = newDropdownVisibility) }
    }

    fun toggleSystemAppsVisibility() {
        val newShowSystemApps = !uiState.value.isShowSystemAppsHome
        val apps = _uiState.value.listOfApps
        _uiState.update {
            it.copy(
                isShowSystemAppsHome = newShowSystemApps,
                displayedApps = computeDisplayed(apps, newShowSystemApps)
            )
        }
        toggleDropdown()
    }

    fun onClickProceedShizuku() {
        loadOperationMode()
    }

    fun onSearchTextFieldChange(newText: String) {
        _uiState.update { it.copy(searchTextFieldValue = newText) }
        _searchQueryFlow.value = newText
    }

    fun onSearchExpandedChange() {
        val isExpanded = !uiState.value.isExpanded
        _uiState.update { it.copy(isExpanded = isExpanded) }
        if (isExpanded)
            updateHistory()
        else {
            _uiState.update { it.copy(searchTextFieldValue = "", searchResults = emptyList()) }
            _searchQueryFlow.value = ""
        }
    }

    fun onSelectedLabelChange(label: AppLabels) {
        val current = _uiState.value.selectLabels
        val newLabels = if (current.contains(label)) current - label else current + label
        _uiState.update { it.copy(selectLabels = newLabels) }
        recomputeSearchResults()
    }

    private fun recomputeSearchResults() {
        val query = _searchQueryFlow.value
        if (query.isEmpty()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        val apps = _uiState.value.listOfApps
        val labels = _uiState.value.selectLabels
        viewModelScope.launch(Dispatchers.Default) {
            val lQuery = query.lowercase()
            val results = apps.filter { app ->
                if (labels.contains(AppLabels.MODIFIED) && !app.isModified()) return@filter false
                if (!labels.contains(AppLabels.SYSTEM_APP) && app.isSystemApp()) return@filter false
                app.pkg.lowercase().contains(lQuery) || app.name.lowercase().contains(lQuery)
            }
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    fun updateHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val pkgOrder = dao.getHistory().map { it.pkg }
            val appMap = _uiState.value.listOfApps.associateBy { it.pkg }
            val history = pkgOrder.mapNotNull { appMap[it] }
            _uiState.update { it.copy(history = history) }
        }
    }

    fun addAppToHistory(ai: AppInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            if (dao.findByPkg(ai.pkg) == null) {
                dao.insert(ai.toAppInfoEntity())
            }
            dao.setLastSelected(ai.pkg, System.currentTimeMillis())
            updateHistory()
        }
    }

    fun onClickClear() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.cleanLastSelectedAll()
            updateHistory()
        }
    }

    fun reloadLastSelectedItem() {
        if (lastSelectedApp == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val pkg = app.packageManager.getApplicationInfo(lastSelectedApp!!.pkg, 0)
            val updatedAi = parseAppInfo(pkg)
            val apps = _uiState.value.listOfApps
            val idx = apps.indexOfFirst { it.pkg == updatedAi.pkg }
            if (idx != -1 && updatedAi.labels != apps[idx].labels) {
                val newList = apps.toMutableList().also { it[idx] = updatedAi }
                    .sortedWith(appComparator)
                val displayed = computeDisplayed(newList, _uiState.value.isShowSystemAppsHome)
                _uiState.update {
                    it.copy(
                        listOfApps = newList,
                        displayedApps = displayed,
                        snackBarDisplay = if (updatedAi.isModified()) SnackBarDisplay.MOVED_TO_TOP
                        else SnackBarDisplay.MOVED_TO_BOTTOM
                    )
                }
            }
        }
    }

    fun resetSnackBarDisplay() = _uiState.update { it.copy(snackBarDisplay = SnackBarDisplay.NONE) }

    fun onClickApp(ai: AppInfo) {
        lastSelectedApp = ai
        addAppToHistory(ai)
    }
}
