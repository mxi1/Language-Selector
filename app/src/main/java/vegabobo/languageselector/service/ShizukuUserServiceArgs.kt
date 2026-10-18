package vegabobo.languageselector.service

import android.content.ComponentName
import rikka.shizuku.Shizuku
import vegabobo.languageselector.BuildConfig

/**
 * Arguments used to spawn the Shizuku user service.
 *
 * Non-daemon mode keeps the process tied to the app process lifetime; the version code makes
 * Shizuku recreate the service after an app update so the newest code always runs.
 */
internal object ShizukuUserServiceArgs {

    val args: Shizuku.UserServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("service")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)
}
