package vegabobo.languageselector.service

import android.content.Intent
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService

/**
 * libsu based fallback for devices where the user runs the app as root instead of Shizuku.
 * The service itself is the same [UserService] implementation.
 */
class RootUserService : RootService() {
    override fun onBind(intent: Intent): IBinder = UserService()
}
