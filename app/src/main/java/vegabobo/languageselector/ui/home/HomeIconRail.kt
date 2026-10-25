package vegabobo.languageselector.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import vegabobo.languageselector.R
import vegabobo.languageselector.core.model.AppSummary
import vegabobo.languageselector.ui.components.AppIcon
import vegabobo.languageselector.ui.components.AppRow
import vegabobo.languageselector.ui.components.EmptyState
import vegabobo.languageselector.ui.components.SearchField

/**
 * Icon-only app list used by medium portrait windows (foldable inner screen / tablet portrait).
 *
 * A full-width list next to the detail pane leaves the detail only ~250dp wide, which truncates
 * quick actions and language rows. The rail keeps the app list reachable (icons + letter
 * grouping) while the detail pane gets almost the whole window. Search is available as a
 * full-screen overlay so the rail itself can stay narrow.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeIconRail(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onToggleSystem: () -> Unit,
    onToggleModified: () -> Unit,
    onRefresh: () -> Unit,
    onAppClick: (AppSummary) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenServiceStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = { showSearch = true }) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.cd_search),
            )
        }
        IconButton(onClick = onToggleSystem) {
            Icon(
                imageVector = Icons.Outlined.Apps,
                contentDescription = stringResource(R.string.filter_system),
                tint = if (state.filter.showSystem) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = onToggleModified) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.filter_modified),
                tint = if (state.filter.onlyModified) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
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
                    text = { Text(stringResource(R.string.action_retry)) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                    },
                    onClick = {
                        menuExpanded = false
                        onRefresh()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.about)) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Info, contentDescription = null)
                    },
                    onClick = {
                        menuExpanded = false
                        onOpenAbout()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.service_status)) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                    },
                    onClick = {
                        menuExpanded = false
                        onOpenServiceStatus()
                    },
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isInitialLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }

                state.apps.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Apps,
                        contentDescription = stringResource(R.string.no_apps_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    state.letterSections.forEach { (letter, apps) ->
                        stickyHeader(key = "rail-letter-$letter") {
                            Text(
                                text = letter.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(top = 6.dp, bottom = 2.dp),
                            )
                        }
                        items(
                            items = apps,
                            key = { app -> "rail-${app.packageName}" },
                        ) { app ->
                            IconRailItem(
                                app = app,
                                selected = state.selectedPackage == app.packageName,
                                onClick = { onAppClick(app) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSearch) {
        SearchOverlay(
            state = state,
            onQueryChange = onQueryChange,
            onAppClick = onAppClick,
            onDismiss = {
                showSearch = false
                onQueryChange("")
            },
        )
    }
}

@Composable
private fun IconRailItem(
    app: AppSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                )
                .padding(4.dp),
        ) {
            AppIcon(
                packageName = app.packageName,
                version = app.iconVersion,
                contentDescription = stringResource(R.string.cd_app_icon, app.label),
            )
        }
    }
}

/** Full-screen search surface for the icon rail: the rail itself is too narrow for a text field. */
@Composable
private fun SearchOverlay(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onAppClick: (AppSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SearchField(
                        query = state.query,
                        placeholder = stringResource(R.string.search_apps),
                        onQueryChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                }
                if (state.apps.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Search,
                        title = stringResource(R.string.no_search_results_title),
                        message = stringResource(R.string.no_search_results_message),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = state.apps,
                            key = { app -> "search-${app.packageName}" },
                        ) { app ->
                            AppRow(
                                app = app,
                                localeState = state.localeStates[app.packageName],
                                isSelected = state.selectedPackage == app.packageName,
                                onClick = {
                                    onAppClick(app)
                                    onDismiss()
                                },
                                showChevron = true,
                            )
                        }
                    }
                }
            }
        }
    }
}
