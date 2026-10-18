package vegabobo.languageselector.qs

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vegabobo.languageselector.R
import vegabobo.languageselector.core.contract.LocaleRemote
import vegabobo.languageselector.core.contract.PinnedLocales
import vegabobo.languageselector.core.contract.ServiceController
import vegabobo.languageselector.core.model.ServiceStatus
import vegabobo.languageselector.core.util.localeDisplayName
import javax.inject.Inject

/**
 * Quick settings tile that cycles the foreground app between the system default locales and
 * the user's pinned languages.
 *
 * The tile references the service with [ServiceController.acquire]/[ServiceController.release]
 * while it is listening. All privileged work happens in a coroutine scope bound to the main
 * thread, and every binder/package-manager call is dispatched to an IO thread, so listening
 * never blocks the main thread.
 */
@AndroidEntryPoint
class LocaleTileService : TileService() {

    @Inject
    lateinit var serviceController: ServiceController

    @Inject
    lateinit var localeRemote: LocaleRemote

    @Inject
    lateinit var pinnedLocales: PinnedLocales

    private var scope: CoroutineScope? = null
    private var listening: Boolean = false

    private var targetPackage: String? = null
    private var targetLabel: String = ""
    private var currentTags: List<String> = emptyList()

    override fun onStartListening() {
        super.onStartListening()
        if (listening) return
        listening = true
        serviceController.acquire()
        showUnavailable()

        val listeningScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = listeningScope
        listeningScope.launch {
            try {
                serviceController.status.collectLatest { status ->
                    if (status is ServiceStatus.Ready) {
                        refreshTile()
                    } else {
                        clearTarget()
                        showUnavailable()
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                clearTarget()
                showUnavailable()
            }
        }
    }

    override fun onStopListening() {
        stopListeningInternal()
        super.onStopListening()
    }

    override fun onDestroy() {
        stopListeningInternal()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val listeningScope = scope ?: return
        val packageName = targetPackage ?: return
        listeningScope.launch {
            try {
                val pinned = pinnedLocales.pinned.value
                if (pinned.isEmpty()) return@launch
                val cycle = buildList {
                    add(emptyList<String>())
                    pinned.forEach { option -> add(listOf(option.languageTag)) }
                }
                val current = currentTags
                val index = cycle.indexOfFirst { tags -> tags == current }
                val next = cycle[if (index < 0) 1 else (index + 1) % cycle.size]
                localeRemote.setApplicationLocales(packageName, next)
                currentTags = next
                applyTile(next)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                // Keep whatever was rendered before; the status collector will refresh the
                // tile if the service itself went away.
                applyTile(currentTags)
            }
        }
    }

    private fun stopListeningInternal() {
        if (listening) {
            listening = false
            scope?.cancel()
            scope = null
            serviceController.release()
        }
        clearTarget()
    }

    private suspend fun refreshTile() {
        val packageName = try {
            localeRemote.firstRunningTaskPackage()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            clearTarget()
            showUnavailable()
            return
        }

        if (!listening) return
        if (packageName.isBlank() || packageName == this.packageName) {
            clearTarget()
            showUnavailable()
            return
        }

        val applicationInfo = withContext(Dispatchers.IO) {
            runCatching {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0L),
                )
            }.getOrNull()
        }
        if (!listening) return
        if (applicationInfo == null || (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
            clearTarget()
            showUnavailable()
            return
        }
        if (pinnedLocales.pinned.value.isEmpty()) {
            clearTarget()
            showUnavailable()
            return
        }

        val tags = try {
            localeRemote.getApplicationLocalesTags(listOf(packageName))[packageName].orEmpty()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            clearTarget()
            showUnavailable()
            return
        }

        val label = withContext(Dispatchers.IO) {
            runCatching { packageManager.getApplicationLabel(applicationInfo).toString() }
                .getOrDefault(packageName)
        }
        if (!listening) return

        targetPackage = packageName
        targetLabel = label
        currentTags = tags
        applyTile(tags)
    }

    private fun applyTile(tags: List<String>) {
        if (!listening) return
        val tile = qsTile ?: return
        tile.label = if (tags.isEmpty()) {
            getString(R.string.system_default)
        } else {
            localeDisplayName(tags.first())
        }
        tile.subtitle = targetLabel
        tile.state = if (tags.isEmpty()) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.updateTile()
    }

    private fun showUnavailable() {
        if (!listening) return
        val tile = qsTile ?: return
        tile.state = Tile.STATE_UNAVAILABLE
        tile.label = getString(R.string.app_name)
        tile.subtitle = getString(R.string.unavailable)
        tile.updateTile()
    }

    private fun clearTarget() {
        targetPackage = null
        targetLabel = ""
        currentTags = emptyList()
    }
}
