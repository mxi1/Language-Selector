package vegabobo.languageselector.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import vegabobo.languageselector.core.contract.AppCatalog
import vegabobo.languageselector.core.contract.LocaleRemote
import vegabobo.languageselector.core.contract.LocaleStates
import vegabobo.languageselector.core.dispatchers.AppDispatchers
import vegabobo.languageselector.core.model.AppLocaleState
import javax.inject.Inject
import javax.inject.Singleton

/** Maximum number of packages sent to [LocaleRemote.getApplicationLocalesTags] per IPC. */
internal const val LOCALE_QUERY_BATCH_SIZE = 50

/** Splits package names into IPC-sized batches, preserving order. */
internal fun chunkPackages(
    packages: List<String>,
    batchSize: Int = LOCALE_QUERY_BATCH_SIZE,
): List<List<String>> {
    require(batchSize > 0) { "batchSize must be positive, was $batchSize" }
    return packages.chunked(batchSize)
}

/**
 * Cache of the per-app locale state.
 *
 * [refresh] performs the expensive batched remote query; [setLocale] and
 * [resetLocale] update the map in place after a successful remote call instead
 * of re-querying every app. Failures are propagated to the caller so the UI can
 * map them to a snackbar.
 */
@Singleton
class DefaultLocaleStates @Inject constructor(
    private val remote: LocaleRemote,
    private val appCatalog: AppCatalog,
    private val dispatchers: AppDispatchers,
) : LocaleStates {

    private val _states = MutableStateFlow<Map<String, AppLocaleState>>(emptyMap())
    override val states: StateFlow<Map<String, AppLocaleState>> = _states.asStateFlow()

    override suspend fun refresh() {
        withContext(dispatchers.io) {
            var packages = installedPackages()
            if (packages.isEmpty()) {
                // The home screen refreshes the catalog and the states concurrently,
                // so the catalog may still be empty when this runs: load it once.
                appCatalog.refresh()
                packages = installedPackages()
            }
            if (packages.isEmpty()) {
                _states.value = emptyMap()
                return@withContext
            }
            val updated = LinkedHashMap<String, AppLocaleState>(packages.size)
            for (batch in chunkPackages(packages)) {
                val tagsByPackage = remote.getApplicationLocalesTags(batch)
                for (packageName in batch) {
                    updated[packageName] = AppLocaleState(tagsByPackage[packageName].orEmpty())
                }
            }
            _states.value = updated
        }
    }

    private fun installedPackages(): List<String> =
        appCatalog.apps.value.map { it.packageName }.distinct()

    override suspend fun setLocale(packageName: String, languageTags: List<String>) {
        withContext(dispatchers.io) {
            remote.setApplicationLocales(packageName, languageTags)
            _states.update { current -> current + (packageName to AppLocaleState(languageTags)) }
        }
    }

    override suspend fun resetLocale(packageName: String) {
        withContext(dispatchers.io) {
            remote.setApplicationLocales(packageName, emptyList())
            _states.update { current -> current + (packageName to AppLocaleState()) }
        }
    }

    override suspend fun forceStop(packageName: String) {
        withContext(dispatchers.io) {
            remote.forceStopPackage(packageName)
        }
    }

    override fun stateOf(packageName: String): AppLocaleState =
        _states.value[packageName] ?: AppLocaleState()
}
