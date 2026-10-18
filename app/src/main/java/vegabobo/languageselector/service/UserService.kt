package vegabobo.languageselector.service

import android.app.ActivityManager
import android.app.IActivityManager
import android.app.IActivityTaskManager
import android.app.ILocaleManager
import android.os.LocaleList
import android.os.Process
import android.util.Log
import rikka.shizuku.SystemServiceHelper
import vegabobo.languageselector.IUserService
import kotlin.system.exitProcess

/**
 * Privileged part of the service layer.
 *
 * This class is instantiated by Shizuku (`Shizuku.bindUserService`) or by libsu
 * (`RootService`) inside a `app_process` that runs as shell or root. That process is not a
 * regular Android app process but has full access to the hidden framework services, which
 * is why the system server interfaces are resolved here and not in the app process.
 */
class UserService : IUserService.Stub() {

    override fun exit() = destroy()

    override fun destroy() {
        exitProcess(0)
    }

    override fun getUid(): Int = Process.myUid()

    private val localeManager: ILocaleManager by lazy {
        val binder = SystemServiceHelper.getSystemService(SERVICE_LOCALE)
            ?: error("Android locale service is not available")
        ILocaleManager.Stub.asInterface(binder)
            ?: error("Android locale service returned no binder")
    }

    private val activityManager: IActivityManager by lazy {
        val binder = SystemServiceHelper.getSystemService(SERVICE_ACTIVITY)
            ?: error("Android activity service is not available")
        IActivityManager.Stub.asInterface(binder)
            ?: error("Android activity service returned no binder")
    }

    private val activityTaskManager: IActivityTaskManager by lazy {
        val binder = SystemServiceHelper.getSystemService(SERVICE_ACTIVITY_TASK)
            ?: error("Android activity_task service is not available")
        IActivityTaskManager.Stub.asInterface(binder)
            ?: error("Android activity_task service returned no binder")
    }

    override fun setApplicationLocales(packageName: String?, locales: LocaleList?) {
        val manager = localeManager
        val userId = currentUserId()
        try {
            // Android U+ signature, the last argument marks the call as coming from the
            // per-app locale delegate so that the change is persisted by the system.
            manager.setApplicationLocales(packageName, userId, locales, true)
        } catch (error: NoSuchMethodError) {
            Log.w(TAG, "setApplicationLocales(fromDelegate) is missing, using legacy overload", error)
            manager.setApplicationLocales(packageName, userId, locales)
        }
    }

    override fun getApplicationLocales(packageName: String?): LocaleList? =
        localeManager.getApplicationLocales(packageName, currentUserId())

    override fun getSystemLocales(): LocaleList? = localeManager.systemLocales

    override fun getApplicationLocalesTags(packageNames: Array<String>?): Array<String>? {
        if (packageNames == null) return null
        val manager = localeManager
        val userId = currentUserId()
        return Array(packageNames.size) { index ->
            val packageName = packageNames[index]
            if (packageName.isNullOrEmpty()) {
                ""
            } else {
                val locales = manager.getApplicationLocales(packageName, userId)
                if (locales == null || locales.isEmpty) {
                    ""
                } else {
                    val tags = ArrayList<String>(locales.size())
                    for (position in 0 until locales.size()) {
                        tags.add(locales.get(position).toLanguageTag())
                    }
                    formatTags(tags)
                }
            }
        }
    }

    override fun forceStopPackage(packageName: String?) {
        activityManager.forceStopPackage(packageName, currentUserId())
    }

    override fun getFirstRunningTaskPackage(): String {
        val manager = activityTaskManager
        val firstTask = try {
            manager.getTasks(1, false, false, -1).firstOrNull()
        } catch (error: NoSuchMethodError) {
            Log.w(TAG, "getTasks(displayId) is missing, using legacy overload", error)
            try {
                manager.getTasks(1, false, false).firstOrNull()
            } catch (throwable: Throwable) {
                Log.w(TAG, "getTasks failed", throwable)
                null
            }
        } catch (throwable: Throwable) {
            Log.w(TAG, "getTasks failed", throwable)
            null
        }
        return firstTask?.topActivity?.packageName ?: ""
    }

    /**
     * Current Android user id. `ActivityManager.getCurrentUser()` is the canonical hidden
     * API (see the compile-only shim in `:hidden_api`), but keep a public-API fallback for the
     * unlikely case it is missing from the framework build we run on. Android encodes the user
     * id in the uid (`userId * PER_USER_RANGE + appId`), so the process user can be derived
     * without any further hidden API.
     */
    private fun currentUserId(): Int {
        return try {
            ActivityManager.getCurrentUser()
        } catch (error: Throwable) {
            Log.w(TAG, "ActivityManager.getCurrentUser() is unavailable, using process user", error)
            Process.myUid() / PER_USER_RANGE
        }
    }

    private companion object {
        const val TAG = "LocaleUserService"
        const val SERVICE_LOCALE = "locale"
        const val SERVICE_ACTIVITY = "activity"
        const val SERVICE_ACTIVITY_TASK = "activity_task"

        /** Android's uid layout: userId * 100000 + appId. */
        const val PER_USER_RANGE = 100_000
    }
}
