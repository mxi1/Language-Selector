package vegabobo.languageselector.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import vegabobo.languageselector.core.contract.ServiceController
import vegabobo.languageselector.core.contract.SettingsStore
import vegabobo.languageselector.core.model.ServiceStatus
import vegabobo.languageselector.core.model.ThemeMode
import javax.inject.Inject

/**
 * Root view model: theme preferences and the privileged service status that the
 * onboarding screen and the main scaffolds need.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val serviceController: ServiceController,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsStore.themeMode
    val dynamicColor: StateFlow<Boolean> = settingsStore.dynamicColor
    val serviceStatus: StateFlow<ServiceStatus> = serviceController.status

    fun refreshService() = serviceController.refresh()

    fun requestPermission() = serviceController.requestPermission()

    /** Enables the optional Root fallback and re-checks the service state. */
    fun useRoot() {
        settingsStore.setUseRoot(true)
        serviceController.refresh()
    }
}
