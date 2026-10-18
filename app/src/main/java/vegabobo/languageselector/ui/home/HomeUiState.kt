package vegabobo.languageselector.ui.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import vegabobo.languageselector.core.model.AppFilter
import vegabobo.languageselector.core.model.AppLocaleState
import vegabobo.languageselector.core.model.AppSummary
import vegabobo.languageselector.core.model.ServiceStatus

/**
 * One user visible message produced by the home feature.
 *
 * The message is expressed as a string resource id so that the view model stays free of
 * Android resources; the composable resolves it through [resolve]. [args] are format
 * arguments applied in order when the resource declares them.
 */
data class UiMessage(
    @StringRes val resId: Int,
    val args: List<String> = emptyList(),
)

/** Resolves a [UiMessage] with the current configuration, e.g. `stringResource(R.string.x, arg)`. */
@Composable
fun UiMessage.resolve(): String = if (args.isEmpty()) {
    stringResource(resId)
} else {
    stringResource(resId, *args.toTypedArray())
}

/**
 * Everything the home screen renders. Built by [HomeViewModel] from the contract flows:
 * plain data only, so the composable never filters, sorts or groups anything itself.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val filter: AppFilter = AppFilter(),
    val recentApps: List<AppSummary> = emptyList(),
    val apps: List<AppSummary> = emptyList(),
    val letterSections: List<Pair<Char, List<AppSummary>>> = emptyList(),
    val modifiedPackages: Set<String> = emptySet(),
    val selectedPackage: String? = null,
    val serviceStatus: ServiceStatus = ServiceStatus.Checking,
    val message: UiMessage? = null,
    /** Full per-app locale states, needed by [vegabobo.languageselector.ui.components.AppRow] badges. */
    val localeStates: Map<String, AppLocaleState> = emptyMap(),
    /** True once the user dismissed the service warning banner for the current status. */
    val isServiceBannerDismissed: Boolean = false,
) {
    /** First load, before any app data arrived. */
    val isInitialLoading: Boolean
        get() = isLoading && apps.isEmpty() && recentApps.isEmpty()

    /** The recent section is only useful when browsing, not while searching. */
    val showRecent: Boolean
        get() = query.isBlank() && recentApps.isNotEmpty()

    /** The service warning banner is hidden while connected and after dismissal. */
    val showServiceBanner: Boolean
        get() = !isServiceBannerDismissed && serviceStatus !is ServiceStatus.Ready
}
