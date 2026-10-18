package vegabobo.languageselector.core.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Finds the Activity hosting a Compose context (needed for window/fold info). */
fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
