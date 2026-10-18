package vegabobo.languageselector

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
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
