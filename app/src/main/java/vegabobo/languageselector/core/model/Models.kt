package vegabobo.languageselector.core.model

/** How the privileged operations are executed. */
enum class OperationMode { NONE, SHIZUKU, ROOT }

/** Categories used to describe why the service is not ready. */
enum class ServiceFailure {
    PERMISSION_DENIED,
    SERVICE_UNAVAILABLE,
    TIMEOUT,
    REMOTE_ERROR,
    BINDER_DIED,
    ROOT_DENIED,
    UNKNOWN,
}

/** Current state of the connection to the privileged user service. */
sealed interface ServiceStatus {
    data object Checking : ServiceStatus
    data object ShizukuNotInstalled : ServiceStatus
    data object ShizukuNotRunning : ServiceStatus
    data object PermissionRequired : ServiceStatus
    data object Connecting : ServiceStatus
    data class Ready(val mode: OperationMode) : ServiceStatus
    data class Failed(val failure: ServiceFailure, val detail: String? = null) : ServiceStatus
}

/** Lightweight app entry. Heavy fields (icons) are loaded lazily by the UI. */
data class AppSummary(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val iconVersion: Long = 0L,
    val enabled: Boolean = true,
)

/** Locale state of a single app. Empty language tags means "system default". */
data class AppLocaleState(val languageTags: List<String> = emptyList()) {
    val isModified: Boolean get() = languageTags.isNotEmpty()
    val primaryTag: String? get() = languageTags.firstOrNull()
}

/** A single selectable locale (a language + region combination). */
data class LocaleOption(
    val languageTag: String,
    val displayName: String,
    val languageName: String,
)

/** All locales of one language. */
data class LocaleGroup(
    val languageName: String,
    val options: List<LocaleOption>,
)

/** Filter applied to the home app list. */
data class AppFilter(
    val showSystem: Boolean = false,
    val onlyModified: Boolean = false,
)

/** Text shown to the user, either resolved from resources later or raw. */
data class UiText(val kind: Kind, val value: String = "", val args: List<String> = emptyList()) {
    enum class Kind { RESOURCE, RAW }

    companion object {
        fun raw(value: String) = UiText(Kind.RAW, value)
    }
}

/** User configuration that survives restarts. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }
