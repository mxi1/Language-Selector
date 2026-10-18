package vegabobo.languageselector.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vegabobo.languageselector.R
import vegabobo.languageselector.core.model.AppFilter
import vegabobo.languageselector.core.model.AppSummary
import vegabobo.languageselector.core.model.OperationMode
import vegabobo.languageselector.core.model.ServiceStatus
import vegabobo.languageselector.ui.adaptive.WindowWidth
import vegabobo.languageselector.ui.components.AppRow
import vegabobo.languageselector.ui.components.EmptyState
import vegabobo.languageselector.ui.components.SearchField
import vegabobo.languageselector.ui.components.SectionHeader

/** Maximum content width on medium/expanded windows, centred like the Material 3 guidance. */
private val ContentMaxWidth = 720.dp

/**
 * Home screen: searchable, filterable list of installed apps.
 *
 * The host (root) owns the snackbar host and the onboarding switch: this composable renders
 * its own [TopAppBar] (do not wrap it in another top bar) and only *shows* snackbars on
 * [snackbarHostState], which the host must render with a `SnackbarHost`.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    windowWidth: WindowWidth,
    onAppClick: (AppSummary) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenServiceStatus: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message = state.message
    val messageText = message?.let { snackbarText(it) }

    LaunchedEffect(message) {
        if (message != null && messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    HomeContent(
        state = state,
        windowWidth = windowWidth,
        onQueryChange = viewModel::onQueryChange,
        onToggleSystem = viewModel::toggleSystem,
        onToggleModified = viewModel::toggleModified,
        onRefresh = viewModel::refresh,
        onClearRecent = viewModel::clearRecent,
        onDismissServiceBanner = viewModel::dismissServiceBanner,
        onAppClick = { app ->
            viewModel.selectApp(app.packageName)
            onAppClick(app)
        },
        onOpenAbout = onOpenAbout,
        onOpenServiceStatus = onOpenServiceStatus,
        modifier = modifier,
    )
}

/** Wraps the localized reason into the generic "Action failed: %1$s" snackbar text. */
@Composable
private fun snackbarText(message: UiMessage): String =
    stringResource(R.string.snackbar_error, message.resolve())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    windowWidth: WindowWidth,
    onQueryChange: (String) -> Unit,
    onToggleSystem: () -> Unit,
    onToggleModified: () -> Unit,
    onRefresh: () -> Unit,
    onClearRecent: () -> Unit,
    onDismissServiceBanner: () -> Unit,
    onAppClick: (AppSummary) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenServiceStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompact = windowWidth == WindowWidth.COMPACT

    Column(modifier = modifier.fillMaxSize().navigationBarsPadding()) {
        HomeTopBar(
            onOpenAbout = onOpenAbout,
            onOpenServiceStatus = onOpenServiceStatus,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth(),
            ) {
                ServiceStatusBanner(
                    status = state.serviceStatus,
                    dismissed = state.isServiceBannerDismissed,
                    onDismiss = onDismissServiceBanner,
                    onClick = onOpenServiceStatus,
                )
                SearchField(
                    query = state.query,
                    placeholder = stringResource(R.string.search_apps),
                    onQueryChange = onQueryChange,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                FilterRow(
                    filter = state.filter,
                    onToggleSystem = onToggleSystem,
                    onToggleModified = onToggleModified,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (state.isInitialLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        PullToRefreshBox(
                            isRefreshing = state.isLoading,
                            onRefresh = onRefresh,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (state.apps.isEmpty()) {
                                EmptyAppList(query = state.query)
                            } else {
                                AppList(
                                    state = state,
                                    showChevron = isCompact,
                                    onAppClick = onAppClick,
                                    onClearRecent = onClearRecent,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    onOpenAbout: () -> Unit,
    onOpenServiceStatus: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.cd_more),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.about)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenAbout()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.service_status)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenServiceStatus()
                        },
                    )
                }
            }
        },
    )
}

/** Clickable warning banner shown whenever the privileged service is not ready. */
@Composable
private fun ServiceStatusBanner(
    status: ServiceStatus,
    dismissed: Boolean,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
) {
    if (dismissed || status is ServiceStatus.Ready) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.service_status),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = serviceBannerLabel(status),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.action_close),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Short status label for the banner. Kept local so the home feature does not depend on navigation. */
@Composable
private fun serviceBannerLabel(status: ServiceStatus): String = when (status) {
    is ServiceStatus.Checking -> stringResource(R.string.loading)
    is ServiceStatus.Connecting -> stringResource(R.string.service_connecting)
    is ServiceStatus.ShizukuNotInstalled -> stringResource(R.string.shizuku_not_installed_title)
    is ServiceStatus.ShizukuNotRunning -> stringResource(R.string.shizuku_not_running_title)
    is ServiceStatus.PermissionRequired -> stringResource(R.string.permissions_required)
    is ServiceStatus.Ready -> if (status.mode == OperationMode.ROOT) {
        stringResource(R.string.service_ready_root)
    } else {
        stringResource(R.string.service_ready_shizuku)
    }

    is ServiceStatus.Failed -> stringResource(
        R.string.service_failed,
        stringResource(R.string.error_service_unavailable),
    )
}

@Composable
private fun FilterRow(
    filter: AppFilter,
    onToggleSystem: () -> Unit,
    onToggleModified: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filter.showSystem,
            onClick = onToggleSystem,
            label = { Text(stringResource(R.string.filter_system)) },
        )
        FilterChip(
            selected = filter.onlyModified,
            onClick = onToggleModified,
            label = { Text(stringResource(R.string.filter_modified)) },
        )
    }
}

@Composable
private fun EmptyAppList(query: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (query.isNotBlank()) {
            EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.no_search_results_title),
                message = stringResource(R.string.no_search_results_message),
            )
        } else {
            EmptyState(
                icon = Icons.Outlined.Apps,
                title = stringResource(R.string.no_apps_title),
                message = stringResource(R.string.no_apps_message),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppList(
    state: HomeUiState,
    showChevron: Boolean,
    onAppClick: (AppSummary) -> Unit,
    onClearRecent: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (state.showRecent) {
            item(key = "recent-header") {
                SectionHeaderWithAction(
                    title = stringResource(R.string.section_recent),
                    action = stringResource(R.string.action_clear),
                    onAction = onClearRecent,
                )
            }
            items(
                items = state.recentApps,
                key = { app -> "recent-${app.packageName}" },
            ) { app ->
                HomeAppRow(
                    app = app,
                    state = state,
                    showChevron = showChevron,
                    onAppClick = onAppClick,
                )
            }
        }
        item(key = "all-apps-header") {
            SectionHeader(stringResource(R.string.section_all_apps))
        }
        state.letterSections.forEach { (letter, apps) ->
            stickyHeader(key = "letter-$letter") {
                SectionHeader(
                    title = letter.toString(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface),
                )
            }
            items(
                items = apps,
                key = { app -> "app-${app.packageName}" },
            ) { app ->
                HomeAppRow(
                    app = app,
                    state = state,
                    showChevron = showChevron,
                    onAppClick = onAppClick,
                )
            }
        }
    }
}

@Composable
private fun HomeAppRow(
    app: AppSummary,
    state: HomeUiState,
    showChevron: Boolean,
    onAppClick: (AppSummary) -> Unit,
) {
    AppRow(
        app = app,
        localeState = state.localeStates[app.packageName],
        isSelected = state.selectedPackage == app.packageName,
        onClick = { onAppClick(app) },
        showChevron = showChevron,
    )
}

@Composable
private fun SectionHeaderWithAction(
    title: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader(
            title = title,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onAction,
            modifier = Modifier.padding(end = 12.dp),
        ) {
            Text(action)
        }
    }
}
