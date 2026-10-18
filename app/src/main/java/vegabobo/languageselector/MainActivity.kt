package vegabobo.languageselector

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import vegabobo.languageselector.core.contract.ServiceController
import vegabobo.languageselector.ui.LanguageSelectorApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var serviceController: ServiceController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fully transparent system bars: the app draws its own background edge-to-edge.
        // Passing transparent scrims explicitly is important on gesture-navigation devices
        // (e.g. HyperOS) where the default light scrim shows up as a grey/white band behind
        // the gesture pill on the home screen.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // Some OEMs re-enable contrast enforcement for gesture navigation; disable it so the
        // navigation bar never draws a translucent background over the app content.
        window.isNavigationBarContrastEnforced = false
        setContent {
            LanguageSelectorApp()
        }
        serviceController.refresh()
    }

    override fun onStart() {
        super.onStart()
        serviceController.acquire()
    }

    override fun onStop() {
        serviceController.release()
        super.onStop()
    }
}
