package vegabobo.languageselector.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import vegabobo.languageselector.IUserService
import vegabobo.languageselector.core.contract.ServiceController
import vegabobo.languageselector.core.contract.SettingsStore
import vegabobo.languageselector.core.dispatchers.AppDispatchers
import vegabobo.languageselector.core.model.OperationMode
import vegabobo.languageselector.core.model.ServiceFailure
import vegabobo.languageselector.core.model.ServiceStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the Shizuku / libsu user service lifecycle and serialises all binder calls.
 *
 * Design notes:
 * - [status] is only mutated from the main dispatcher (callbacks are posted there), so the UI
 *   can observe it safely.
 * - [withService] serialises binder calls through a [Mutex], wraps them in [withTimeout] and
 *   runs them on [AppDispatchers.io], so a slow or dead remote process can never block the
 *   main thread or a coroutine forever.
 * - Binding is reference counted: the first [acquire] starts the connection, the last
 *   [release] tears it down. No polling loops are used; state changes are driven by Shizuku
 *   listeners, [ServiceConnection] callbacks and a single connect timeout watchdog.
 */
@Singleton
class ServiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val dispatchers: AppDispatchers,
) : ServiceController {

    private val _status = MutableStateFlow<ServiceStatus>(ServiceStatus.Checking)
    override val status: StateFlow<ServiceStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
    private val binderMutex = Mutex()

    @Volatile
    private var referenceCount: Int = 0

    @Volatile
    private var service: IUserService? = null

    @Volatile
    private var serviceBinder: IBinder? = null

    @Volatile
    private var connection: ServiceConnection? = null

    @Volatile
    private var boundVia: OperationMode = OperationMode.NONE

    @Volatile
    private var bindingGeneration: Long = 0L

    @Volatile
    private var connecting: Boolean = false

    private var connectTimeoutJob: Job? = null

    private val deathRecipient = object : IBinder.DeathRecipient {
        override fun binderDied() {
            handleBinderLost(bindingGeneration, "User service binder died")
        }
    }

    private val binderReceivedListener = object : Shizuku.OnBinderReceivedListener {
        override fun onBinderReceived() {
            Log.d(TAG, "Shizuku binder received")
            launchSafe {
                if (referenceCount > 0 && service == null) {
                    connect()
                }
            }
        }
    }

    private val binderDeadListener = object : Shizuku.OnBinderDeadListener {
        override fun onBinderDead() {
            Log.w(TAG, "Shizuku binder died")
            launchSafe {
                if (boundVia == OperationMode.SHIZUKU) {
                    handleBinderLost(bindingGeneration, "Shizuku binder died")
                }
            }
        }
    }

    private val permissionResultListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            if (requestCode != REQUEST_PERMISSION_CODE) return
            launchSafe {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    if (referenceCount > 0 && service == null) {
                        connect()
                    } else if (service == null) {
                        updateStatus(ServiceStatus.Checking)
                    }
                } else {
                    updateStatus(ServiceStatus.PermissionRequired)
                }
            }
        }
    }

    init {
        registerShizukuListeners()
        observeRootSetting()
    }

    override fun refresh() {
        launchSafe {
            if (service != null && _status.value is ServiceStatus.Ready) return@launchSafe
            if (referenceCount > 0) {
                connect()
                return@launchSafe
            }
            val problem = if (prefersRoot() && isRootGranted()) {
                null
            } else {
                probeShizuku()
            }
            updateStatus(problem ?: ServiceStatus.Checking)
        }
    }

    override fun requestPermission() {
        launchSafe {
            if (isShizukuUsable()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    if (referenceCount > 0 && service == null) {
                        connect()
                    } else {
                        updateStatus(ServiceStatus.Checking)
                    }
                    return@launchSafe
                }
                Log.d(TAG, "Requesting Shizuku permission")
                Shizuku.requestPermission(REQUEST_PERMISSION_CODE)
                return@launchSafe
            }
            updateStatus(probeShizuku() ?: ServiceStatus.Checking)
        }
    }

    @Synchronized
    override fun acquire() {
        referenceCount++
        if (referenceCount == 1) {
            launchSafe { connect() }
        }
    }

    @Synchronized
    override fun release() {
        if (referenceCount <= 0) return
        referenceCount--
        if (referenceCount == 0) {
            launchSafe {
                // An acquire() may have happened again before this cleanup ran; in that case
                // keep the existing binding alive for the new owner.
                if (referenceCount > 0) return@launchSafe
                releaseBinding(unbind = true)
                updateStatus(ServiceStatus.Checking)
            }
        }
    }

    /**
     * Runs [block] against the bound user service.
     *
     * Serialised with a [Mutex], guarded by [withTimeout] and executed on
     * [AppDispatchers.io]. Throws [ServiceNotReadyException] when the service is not ready and
     * [ServiceException] when the binder call itself fails.
     */
    suspend fun <T> withService(
        timeoutMillis: Long = DEFAULT_CALL_TIMEOUT_MS,
        block: suspend (IUserService) -> T,
    ): T = binderMutex.withLock {
        val userService = service
        if (userService == null || _status.value !is ServiceStatus.Ready) {
            throw ServiceNotReadyException(
                notReadyFailure(),
                "Privileged service is not ready (status=${_status.value})",
            )
        }
        try {
            withTimeout(timeoutMillis) {
                withContext(dispatchers.io) { block(userService) }
            }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            throw ServiceException(
                ServiceFailure.TIMEOUT,
                "Binder call timed out after ${timeoutMillis}ms",
                timeout,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            if (throwable is ServiceException) throw throwable
            val failure = mapServiceFailure(throwable)
            if (failure == ServiceFailure.BINDER_DIED) {
                handleBinderLost(bindingGeneration, "User service binder died during a call")
            }
            throw ServiceException(failure, throwable.message ?: "Binder call failed", throwable)
        }
    }

    // ------------------------------------------------------------------------------------
    // Connection lifecycle
    // ------------------------------------------------------------------------------------

    private suspend fun connect() {
        if (connecting) return
        if (service != null && _status.value is ServiceStatus.Ready) return
        // A binding is already established or still being established; a second bind would
        // leak the first connection. releaseBinding() clears it before a reconnect.
        if (connection != null) return
        connecting = true
        try {
            updateStatus(ServiceStatus.Connecting)
            val backend = resolveBackend() ?: return
            if (referenceCount <= 0) {
                updateStatus(ServiceStatus.Checking)
                return
            }
            startConnectTimeout(backend)
            when (backend) {
                OperationMode.ROOT -> bindRoot()
                OperationMode.SHIZUKU -> bindShizuku()
                OperationMode.NONE -> Unit
            }
        } finally {
            connecting = false
        }
    }

    /** Returns the backend to use or updates [status] and returns `null` on failure. */
    private suspend fun resolveBackend(): OperationMode? {
        if (prefersRoot() && isRootGranted()) {
            Log.d(TAG, "Connecting through root")
            return OperationMode.ROOT
        }
        val problem = probeShizuku()
        if (problem != null) {
            Log.d(TAG, "Cannot connect through Shizuku: $problem")
            updateStatus(problem)
            return null
        }
        Log.d(TAG, "Connecting through Shizuku")
        return OperationMode.SHIZUKU
    }

    private fun bindShizuku() {
        val conn = createConnection(OperationMode.SHIZUKU)
        connection = conn
        boundVia = OperationMode.SHIZUKU
        val result = runCatching { Shizuku.bindUserService(ShizukuUserServiceArgs.args, conn) }
        result.onFailure { throwable ->
            Log.e(TAG, "Cannot bind the Shizuku user service", throwable)
            releaseBinding(unbind = false)
            updateStatus(ServiceStatus.Failed(mapServiceFailure(throwable), throwable.message))
        }
    }

    private fun bindRoot() {
        val conn = createConnection(OperationMode.ROOT)
        connection = conn
        boundVia = OperationMode.ROOT
        val result = runCatching {
            RootService.bind(Intent(context, RootUserService::class.java), conn)
        }
        result.onFailure { throwable ->
            Log.e(TAG, "Cannot bind the root user service", throwable)
            releaseBinding(unbind = false)
            updateStatus(ServiceStatus.Failed(mapServiceFailure(throwable), throwable.message))
        }
    }

    private fun createConnection(mode: OperationMode): ServiceConnection {
        val generation = ++bindingGeneration
        return object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                launchSafe { handleServiceConnected(generation, mode, binder) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                handleBinderLost(generation, "User service disconnected")
            }

            override fun onBindingDied(name: ComponentName?) {
                handleBinderLost(generation, "User service binding died")
            }

            override fun onNullBinding(name: ComponentName?) {
                launchSafe {
                    if (generation != bindingGeneration) return@launchSafe
                    releaseBinding(unbind = false)
                    updateStatus(
                        ServiceStatus.Failed(
                            ServiceFailure.SERVICE_UNAVAILABLE,
                            "The user service returned no binder",
                        ),
                    )
                }
            }
        }
    }

    private suspend fun handleServiceConnected(
        generation: Long,
        mode: OperationMode,
        binder: IBinder?,
    ) {
        if (generation != bindingGeneration || referenceCount <= 0) {
            // Stale callback from a connection that was already torn down.
            Log.d(TAG, "Ignoring stale onServiceConnected callback")
            return
        }
        if (binder == null) {
            releaseBinding(unbind = false)
            updateStatus(
                ServiceStatus.Failed(ServiceFailure.SERVICE_UNAVAILABLE, "The user service returned no binder"),
            )
            return
        }
        val userService = try {
            IUserService.Stub.asInterface(binder)
        } catch (throwable: Throwable) {
            Log.e(TAG, "Cannot create the user service interface", throwable)
            null
        }
        if (userService == null) {
            releaseBinding(unbind = false)
            updateStatus(
                ServiceStatus.Failed(ServiceFailure.SERVICE_UNAVAILABLE, "The user service binder is invalid"),
            )
            return
        }

        runCatching { binder.linkToDeath(deathRecipient, 0) }
        serviceBinder = binder
        service = userService
        try {
            // Cheap round trip that also proves the remote side really answers.
            withTimeout(SERVICE_VERIFY_TIMEOUT_MS) {
                withContext(dispatchers.io) { userService.uid }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            if (generation != bindingGeneration) return
            Log.e(TAG, "Cannot verify the user service", throwable)
            releaseBinding(unbind = true)
            updateStatus(ServiceStatus.Failed(mapServiceFailure(throwable), throwable.message))
            return
        }

        // The owner may have released (or rebound) while the verification round trip was in
        // flight; never publish Ready for a binding that is already gone.
        if (generation != bindingGeneration || referenceCount <= 0) return

        connectTimeoutJob?.cancel()
        connectTimeoutJob = null
        Log.d(TAG, "User service ready via $mode")
        updateStatus(ServiceStatus.Ready(mode))
    }

    private fun handleBinderLost(generation: Long, reason: String) {
        launchSafe {
            if (generation != bindingGeneration) return@launchSafe
            Log.w(TAG, reason)
            val hadBinding = connection != null || service != null
            releaseBinding(unbind = true)
            if (hadBinding && referenceCount > 0) {
                updateStatus(ServiceStatus.Failed(ServiceFailure.BINDER_DIED, reason))
            }
        }
    }

    private fun startConnectTimeout(mode: OperationMode) {
        connectTimeoutJob?.cancel()
        connectTimeoutJob = launchSafe {
            delay(CONNECT_TIMEOUT_MS)
            if (service == null) {
                Log.w(TAG, "Timed out connecting via $mode")
                releaseBinding(unbind = true)
                updateStatus(
                    ServiceStatus.Failed(
                        if (mode == OperationMode.ROOT) {
                            ServiceFailure.ROOT_DENIED
                        } else {
                            ServiceFailure.SERVICE_UNAVAILABLE
                        },
                        "Timed out waiting for the privileged user service",
                    ),
                )
            }
        }
    }

    /** Removes the current binding. Must be called from the main dispatcher. */
    private fun releaseBinding(unbind: Boolean) {
        bindingGeneration++
        connectTimeoutJob?.cancel()
        connectTimeoutJob = null

        val currentConnection = connection
        val currentMode = boundVia
        connection = null
        boundVia = OperationMode.NONE

        val binder = serviceBinder
        serviceBinder = null
        service = null
        if (binder != null) {
            runCatching { binder.unlinkToDeath(deathRecipient, 0) }
        }

        if (!unbind || currentConnection == null) return
        when (currentMode) {
            OperationMode.SHIZUKU -> runCatching {
                Shizuku.unbindUserService(ShizukuUserServiceArgs.args, currentConnection, true)
            }

            OperationMode.ROOT -> runCatching { RootService.unbind(currentConnection) }
            OperationMode.NONE -> Unit
        }
    }

    // ------------------------------------------------------------------------------------
    // Availability probing
    // ------------------------------------------------------------------------------------

    private fun registerShizukuListeners() {
        runCatching { Shizuku.addBinderReceivedListener(binderReceivedListener) }
            .onFailure { Log.w(TAG, "Cannot register the Shizuku binder received listener", it) }
        runCatching { Shizuku.addBinderDeadListener(binderDeadListener) }
            .onFailure { Log.w(TAG, "Cannot register the Shizuku binder dead listener", it) }
        runCatching { Shizuku.addRequestPermissionResultListener(permissionResultListener) }
            .onFailure { Log.w(TAG, "Cannot register the Shizuku permission listener", it) }
    }

    private fun observeRootSetting() {
        launchSafe {
            settingsStore.useRoot.drop(1).collect {
                if (referenceCount > 0) {
                    Log.d(TAG, "Root setting changed, reconnecting")
                    releaseBinding(unbind = true)
                    connect()
                }
            }
        }
    }

    private fun prefersRoot(): Boolean = settingsStore.useRoot.value

    private fun isShizukuUsable(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    private suspend fun isRootGranted(): Boolean = withContext(dispatchers.io) {
        runCatching { Shell.isAppGrantedRoot() == true }.getOrDefault(false)
    }

    /**
     * Returns the blocking [ServiceStatus] when Shizuku cannot be used, or `null` when it is
     * installed, running and permitted.
     */
    private suspend fun probeShizuku(): ServiceStatus? = withContext(dispatchers.io) {
        if (!isShizukuInstalled()) return@withContext ServiceStatus.ShizukuNotInstalled
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return@withContext ServiceStatus.ShizukuNotRunning
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) return@withContext ServiceStatus.PermissionRequired
        null
    }

    private fun isShizukuInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, PackageManager.PackageInfoFlags.of(0L))
    }.isSuccess

    private fun notReadyFailure(): ServiceFailure = when (val current = _status.value) {
        is ServiceStatus.Ready -> ServiceFailure.SERVICE_UNAVAILABLE
        is ServiceStatus.Failed -> current.failure
        ServiceStatus.PermissionRequired -> ServiceFailure.PERMISSION_DENIED
        ServiceStatus.Checking,
        ServiceStatus.Connecting,
        ServiceStatus.ShizukuNotInstalled,
        ServiceStatus.ShizukuNotRunning -> ServiceFailure.SERVICE_UNAVAILABLE
    }

    // ------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------

    private fun launchSafe(block: suspend () -> Unit): Job = scope.launch {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Log.e(TAG, "Service manager failure", throwable)
            updateStatus(ServiceStatus.Failed(mapServiceFailure(throwable), throwable.message))
        }
    }

    private fun updateStatus(newStatus: ServiceStatus) {
        _status.value = newStatus
    }

    companion object {
        private const val TAG = "ServiceManager"

        /** Request code used while asking Shizuku for the privileged permission. */
        const val REQUEST_PERMISSION_CODE = 1001

        /** Default timeout for a single binder call made through [withService]. */
        const val DEFAULT_CALL_TIMEOUT_MS = 8_000L

        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val SERVICE_VERIFY_TIMEOUT_MS = 5_000L
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
