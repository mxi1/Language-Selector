package vegabobo.languageselector.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vegabobo.languageselector.core.contract.AppCatalog
import vegabobo.languageselector.core.dispatchers.AppDispatchers
import vegabobo.languageselector.core.model.AppSummary
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshot of the installed launchable/visible applications.
 *
 * The first [refresh] lazily registers a package broadcast receiver so the
 * snapshot stays up to date when apps are installed, removed or updated. The
 * package list itself is always loaded off the main thread.
 */
@Singleton
class DefaultAppCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
) : AppCatalog {

    private val _apps = MutableStateFlow<List<AppSummary>>(emptyList())
    override val apps: StateFlow<List<AppSummary>> = _apps.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val refreshMutex = Mutex()

    @Volatile
    private var index: Map<String, AppSummary> = emptyMap()

    /** Guards the one-time receiver registration; only touched under [refreshMutex]. */
    private var packageReceiverRegistered = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            scope.launch { refresh() }
        }
    }

    override suspend fun refresh() {
        refreshMutex.withLock {
            ensurePackageReceiverRegistered()
            val loaded = withContext(dispatchers.io) { loadInstalledApps() }
            index = loaded.associateBy { it.packageName }
            _apps.value = loaded
        }
    }

    override fun find(packageName: String): AppSummary? = index[packageName]

    private suspend fun ensurePackageReceiverRegistered() {
        if (packageReceiverRegistered) return
        withContext(dispatchers.main) {
            if (packageReceiverRegistered) return@withContext
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            // Package broadcasts are protected system broadcasts; a not-exported
            // receiver is enough and avoids exposing an exported receiver.
            runCatching {
                context.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                packageReceiverRegistered = true
            }
        }
    }

    private fun loadInstalledApps(): List<AppSummary> {
        val packageManager = context.packageManager
        val ownPackage = context.packageName
        // getInstalledPackages (instead of getInstalledApplications) provides the
        // last update time in the same binder call, which is used as the Coil icon
        // cache key without an extra per-app query.
        val packageInfos = packageManager.getInstalledPackages(
            PackageManager.PackageInfoFlags.of(0)
        )
        val collator = Collator.getInstance(Locale.getDefault())
        return packageInfos
            .asSequence()
            .mapNotNull { packageInfo ->
                val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                if (!appInfo.enabled || appInfo.packageName == ownPackage) return@mapNotNull null
                appInfo.toAppSummary(packageManager, packageInfo.lastUpdateTime)
            }
            .distinctBy { it.packageName }
            .sortedWith(Comparator { left, right -> collator.compare(left.label, right.label) })
            .toList()
    }

    private fun ApplicationInfo.toAppSummary(
        packageManager: PackageManager,
        iconVersion: Long,
    ): AppSummary =
        AppSummary(
            packageName = packageName,
            label = runCatching { packageManager.getApplicationLabel(this).toString() }
                .getOrDefault(packageName),
            isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            iconVersion = iconVersion,
            enabled = enabled,
        )
}
