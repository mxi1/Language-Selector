package vegabobo.languageselector.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import vegabobo.languageselector.R
import vegabobo.languageselector.core.model.ServiceStatus
import vegabobo.languageselector.ui.about.AboutScreen
import vegabobo.languageselector.ui.adaptive.AdaptiveScaffold
import vegabobo.languageselector.ui.adaptive.FoldPosture
import vegabobo.languageselector.ui.adaptive.ListPaneStyle
import vegabobo.languageselector.ui.adaptive.listPaneStyle
import vegabobo.languageselector.ui.adaptive.rememberWindowForm
import vegabobo.languageselector.ui.components.EmptyState
import vegabobo.languageselector.ui.detail.DetailScreen
import vegabobo.languageselector.ui.detail.DetailViewModel
import vegabobo.languageselector.ui.home.HomeScreen
import vegabobo.languageselector.ui.home.HomeViewModel
import vegabobo.languageselector.ui.navigation.ServiceStatusDialog
import vegabobo.languageselector.ui.onboarding.OnboardingScreen
import vegabobo.languageselector.ui.theme.LanguageSelectorTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info

private const val ROUTE_HOME = "home"
private const val ROUTE_DETAIL = "detail"
private const val ROUTE_ABOUT = "about"
private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
private const val SHIZUKU_WEBSITE = "https://shizuku.rikka.app/"

/**
 * Root of the rewritten app.
 *
 * - Compact windows use a [NavHost] with slide/fade transitions and predictive back.
 * - Medium/expanded windows, book posture and tabletop use two-pane scaffolds driven by
 *   [rememberWindowForm].
 * - Until the privileged service is ready the onboarding screen is shown instead of the list
 *   (users may still choose to browse apps read-only).
 */
@Composable
fun LanguageSelectorApp(
    appViewModel: AppViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
    detailViewModel: DetailViewModel = hiltViewModel(),
) {
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by appViewModel.dynamicColor.collectAsStateWithLifecycle()
    val serviceStatus by appViewModel.serviceStatus.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val windowForm = rememberWindowForm()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var browseOnly by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showServiceDialog by rememberSaveable { mutableStateOf(false) }

    val serviceReady = serviceStatus is ServiceStatus.Ready
    val showOnboarding = !serviceReady && !browseOnly
    val twoPane = windowForm.twoPane || windowForm.posture == FoldPosture.TABLETOP
    val iconRail = listPaneStyle(windowForm) == ListPaneStyle.ICON_RAIL

    LanguageSelectorTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
        if (twoPane && showAbout) {
            // In two-pane mode About lives in the detail pane; back returns to the app list.
            BackHandler { showAbout = false }
        }
        // Surface supplies the themed content color, which is required for text laid out
        // directly under the theme (Home/Onboarding) to stay readable in dark mode.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            when {
                showOnboarding -> OnboardingScreen(
                    status = serviceStatus,
                    hasRootOption = serviceStatus is ServiceStatus.ShizukuNotInstalled ||
                        serviceStatus is ServiceStatus.ShizukuNotRunning ||
                        serviceStatus is ServiceStatus.Failed,
                    onRetry = appViewModel::refreshService,
                    onRequestPermission = appViewModel::requestPermission,
                    onOpenShizuku = { openShizuku(context) },
                    onUseRoot = appViewModel::useRoot,
                    onBrowseOnly = { browseOnly = true },
                    modifier = Modifier.safeDrawingPadding(),
                )

                twoPane -> AdaptiveScaffold(
                    windowForm = windowForm,
                    listPane = { paneModifier ->
                        HomeScreen(
                            viewModel = homeViewModel,
                            windowWidth = windowForm.width,
                            onAppClick = { app ->
                                showAbout = false
                                detailViewModel.selectPackage(app.packageName)
                            },
                            onOpenAbout = { showAbout = true },
                            onOpenServiceStatus = { showServiceDialog = true },
                            snackbarHostState = snackbarHostState,
                            modifier = paneModifier,
                            iconRail = iconRail,
                        )
                    },
                    detailPane = { paneModifier ->
                        when {
                            showAbout -> AboutScreen(
                                onBack = { showAbout = false },
                                showBackButton = false,
                            )

                            homeState.selectedPackage != null -> DetailScreen(
                                viewModel = detailViewModel,
                                windowWidth = windowForm.width,
                                showBackButton = false,
                                onBack = {},
                                snackbarHostState = snackbarHostState,
                            )

                            else -> Box(modifier = paneModifier, contentAlignment = Alignment.Center) {
                                EmptyState(
                                    icon = Icons.Outlined.Info,
                                    title = stringResource(R.string.select_app_title),
                                    message = stringResource(R.string.select_app_hint),
                                )
                            }
                        }
                    },
                )

                else -> CompactNavigation(
                    homeViewModel = homeViewModel,
                    detailViewModel = detailViewModel,
                    windowWidth = windowForm.width,
                    snackbarHostState = snackbarHostState,
                    onOpenServiceStatus = { showServiceDialog = true },
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
            )
            }
        }

        if (showAbout && !twoPane) {
            AboutScreen(onBack = { showAbout = false }, showBackButton = true)
        }

        if (showServiceDialog) {
            ServiceStatusDialog(
                status = serviceStatus,
                onReconnect = appViewModel::refreshService,
                onRequestPermission = appViewModel::requestPermission,
                onDismiss = { showServiceDialog = false },
            )
        }
    }
}

@Composable
private fun CompactNavigation(
    homeViewModel: HomeViewModel,
    detailViewModel: DetailViewModel,
    windowWidth: vegabobo.languageselector.ui.adaptive.WindowWidth,
    snackbarHostState: SnackbarHostState,
    onOpenServiceStatus: () -> Unit,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME,
        enterTransition = {
            fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 }
        },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = {
            fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 10 }
        },
    ) {
        composable(ROUTE_HOME) {
            HomeScreen(
                viewModel = homeViewModel,
                windowWidth = windowWidth,
                onAppClick = { app -> navController.navigate("$ROUTE_DETAIL/${Uri.encode(app.packageName)}") },
                onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                onOpenServiceStatus = onOpenServiceStatus,
                snackbarHostState = snackbarHostState,
            )
        }

        composable(
            route = "$ROUTE_DETAIL/{packageName}",
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val packageName = entry.arguments?.getString("packageName")
            LaunchedEffect(packageName) {
                detailViewModel.selectPackage(packageName)
            }
            DetailScreen(
                viewModel = detailViewModel,
                windowWidth = windowWidth,
                showBackButton = true,
                onBack = { navController.navigateUp() },
                snackbarHostState = snackbarHostState,
            )
        }

        composable(ROUTE_ABOUT) {
            AboutScreen(onBack = { navController.navigateUp() }, showBackButton = true)
        }
    }
}

/** Opens the Shizuku app if installed, otherwise its website. */
private fun openShizuku(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launchIntent) }
        return
    }
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_WEBSITE))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
