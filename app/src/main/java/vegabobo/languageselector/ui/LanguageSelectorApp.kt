package vegabobo.languageselector.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vegabobo.languageselector.ui.navigation.PlaceholderHome
import vegabobo.languageselector.ui.theme.LanguageSelectorTheme

/**
 * Root composable. The adaptive scaffold and real screens are plugged in during
 * integration; for now it proves theming, DI and Hilt wiring end to end.
 */
@Composable
fun LanguageSelectorApp(
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by appViewModel.dynamicColor.collectAsStateWithLifecycle()
    val serviceStatus by appViewModel.serviceStatus.collectAsStateWithLifecycle()

    LanguageSelectorTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
        Surface(modifier = Modifier.fillMaxSize()) {
            PlaceholderHome(
                status = serviceStatus,
                onRefresh = appViewModel::refreshService,
                onRequestPermission = appViewModel::requestPermission,
            )
        }
    }
}
