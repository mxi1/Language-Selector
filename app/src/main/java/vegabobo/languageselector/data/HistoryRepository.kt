package vegabobo.languageselector.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import vegabobo.languageselector.core.contract.HistoryStore
import vegabobo.languageselector.core.dispatchers.AppDispatchers
import vegabobo.languageselector.data.db.AppInfoDb
import vegabobo.languageselector.data.db.AppInfoEntity
import javax.inject.Inject
import javax.inject.Singleton

/** How many recently selected apps are kept in the history. */
internal const val MAX_HISTORY_ENTRIES = 20

/**
 * Recently selected apps backed by [AppInfoDb].
 *
 * [markSelected] upserts the row (`findByPkg` then insert/update), stamps it with
 * the current time and trims the history to [MAX_HISTORY_ENTRIES] rows by
 * clearing older timestamps (rows are kept for schema compatibility).
 */
@Singleton
class DefaultHistoryStore @Inject constructor(
    db: AppInfoDb,
    private val dispatchers: AppDispatchers,
) : HistoryStore {

    private val dao = db.appInfoDao()

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    override val recentPackages: StateFlow<List<String>> = dao.observeHistory()
        .map { entities -> entities.map { it.pkg } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun markSelected(packageName: String) {
        withContext(dispatchers.io) {
            val now = System.currentTimeMillis()
            val existing = dao.findByPkg(packageName)
            if (existing == null) {
                // The history contract only provides the package name; the label
                // is resolved by the app catalog when the list is rendered.
                dao.insert(AppInfoEntity(pkg = packageName, name = packageName, lastSelected = now))
            } else {
                dao.update(existing.copy(lastSelected = now))
            }
            dao.trimHistory(MAX_HISTORY_ENTRIES)
        }
    }

    override suspend fun clear() {
        withContext(dispatchers.io) {
            dao.clearLastSelected()
        }
    }
}
