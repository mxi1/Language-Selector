package vegabobo.languageselector.ui.detail

import androidx.annotation.StringRes
import vegabobo.languageselector.core.model.AppSummary
import vegabobo.languageselector.core.model.LocaleGroup
import vegabobo.languageselector.core.model.LocaleOption
import vegabobo.languageselector.core.model.ServiceStatus

/**
 * One-shot text for the snackbar. The view model only carries resource ids and
 * pre-formatted arguments, the screen resolves them with the current configuration.
 *
 * [argResId] is used when a message interpolates another translated string instead of
 * free-form data (for example `snackbar_error` + `app_not_found`).
 * [undoable] asks the screen to expose an "Undo" action.
 */
data class UiMessage(
    @StringRes val resId: Int,
    val args: List<String> = emptyList(),
    @StringRes val argResId: Int? = null,
    val undoable: Boolean = false,
)

/** Everything the app language detail screen renders. */
data class DetailUiState(
    val packageName: String? = null,
    val app: AppSummary? = null,
    val currentTags: List<String> = emptyList(),
    val currentDisplay: String? = null,
    val isLoading: Boolean = true,
    val languageQuery: String = "",
    val suggested: List<LocaleOption> = emptyList(),
    val pinned: List<LocaleOption> = emptyList(),
    val groups: List<LocaleGroup> = emptyList(),
    val expandedLanguage: String? = null,
    val unsupportedHintVisible: Boolean = false,
    val pendingConfirm: LocaleOption? = null,
    val message: UiMessage? = null,
    val serviceStatus: ServiceStatus = ServiceStatus.Checking,
) {
    /** True when privileged locale operations can be issued. */
    val isServiceReady: Boolean get() = serviceStatus is ServiceStatus.Ready

    /** All language groups narrowed down to the current [languageQuery]. */
    val filteredGroups: List<LocaleGroup>
        get() {
            val query = languageQuery.trim()
            if (query.isEmpty()) return groups
            return groups.mapNotNull { group ->
                val groupMatches = group.languageName.contains(query, ignoreCase = true)
                val matchingOptions = group.options.filter { option ->
                    option.displayName.contains(query, ignoreCase = true) ||
                        option.languageName.contains(query, ignoreCase = true) ||
                        option.languageTag.contains(query, ignoreCase = true)
                }
                when {
                    groupMatches -> group
                    matchingOptions.isEmpty() -> null
                    else -> group.copy(options = matchingOptions)
                }
            }
        }

    /** Suggestions that are neither currently selected nor pinned, in catalog order. */
    val suggestedOptions: List<LocaleOption>
        get() {
            val excluded = (currentTags + pinned.map { it.languageTag }).toSet()
            return suggested
                .filterNot { it.languageTag in excluded }
                .distinctBy { it.languageTag }
        }

    /** Pinned languages without duplicates, so list keys stay stable. */
    val pinnedOptions: List<LocaleOption>
        get() = pinned.distinctBy { it.languageTag }

    companion object {
        /** State shown when the detail pane has no package selected yet (wide layouts). */
        val Empty = DetailUiState(isLoading = false)
    }
}
