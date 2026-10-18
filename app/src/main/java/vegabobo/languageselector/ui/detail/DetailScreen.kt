@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package vegabobo.languageselector.ui.detail

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vegabobo.languageselector.R
import vegabobo.languageselector.core.model.AppSummary
import vegabobo.languageselector.core.model.LocaleOption
import vegabobo.languageselector.core.model.ServiceStatus
import vegabobo.languageselector.ui.adaptive.WindowWidth
import vegabobo.languageselector.ui.components.AppBadge
import vegabobo.languageselector.ui.components.AppIcon
import vegabobo.languageselector.ui.components.ConfirmDialog
import vegabobo.languageselector.ui.components.EmptyState
import vegabobo.languageselector.ui.components.LanguageOptionRow
import vegabobo.languageselector.ui.components.RegionChips
import vegabobo.languageselector.ui.components.SearchField
import vegabobo.languageselector.ui.components.SectionHeader

private val WideContentMaxWidth = 760.dp

/**
 * Per-app language detail screen.
 *
 * The host (root) owns the snackbar host: this screen only *shows* snackbars on
 * [snackbarHostState], which the host must render with a `SnackbarHost`, and consumes
 * [DetailUiState.message] after presentation. It renders its own [TopAppBar] (do not wrap it
 * in another top bar). In wide layouts the same screen is used as the detail pane
 * (pass `showBackButton = false`).
 */
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    windowWidth: WindowWidth,
    showBackButton: Boolean,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message.resolve(context),
            actionLabel = if (message.undoable) context.getString(R.string.action_undo) else null,
            withDismissAction = false,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.onUndoApply()
        }
        viewModel.consumeMessage()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.app_language),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                if (showBackButton) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                }
            },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter,
        ) {
            val contentModifier = contentWidth(windowWidth)
            when {
                state.isLoading -> LoadingContent()
                state.packageName == null -> EmptyState(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.app_language),
                    message = stringResource(R.string.unavailable),
                    modifier = contentModifier,
                )

                state.app == null -> EmptyState(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.app_not_found),
                    message = stringResource(R.string.unavailable),
                    modifier = contentModifier,
                )

                else -> DetailBody(
                    state = state,
                    viewModel = viewModel,
                    contentModifier = contentModifier,
                )
            }
        }
    }

    if (state.pendingConfirm != null) {
        ConfirmDialog(
            title = stringResource(R.string.warning),
            message = stringResource(R.string.warning_system_apps),
            confirmLabel = stringResource(R.string.action_proceed),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = viewModel::onConfirmSystemApply,
            onDismiss = viewModel::onDismissSystemConfirm,
        )
    }
}

@Composable
private fun DetailBody(
    state: DetailUiState,
    viewModel: DetailViewModel,
    contentModifier: Modifier,
) {
    val app = state.app ?: return
    val ready = state.isServiceReady
    val sectionAlpha = if (ready) 1f else 0.5f
    val suggestedOptions = state.suggestedOptions
    val pinnedOptions = state.pinnedOptions
    val groups = state.filteredGroups

    // Draw edge-to-edge behind the transparent navigation bar; pad only the scrollable content.
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = contentModifier,
        contentPadding = PaddingValues(bottom = navigationBarBottom + 16.dp),
    ) {
        item(key = "header") {
            AppHeader(app = app, state = state)
        }
        item(key = "actions") {
            QuickActions(
                state = state,
                onOpen = viewModel::onOpenApp,
                onForceStop = viewModel::onForceStop,
                onOpenSettings = viewModel::onOpenAppSettings,
            )
        }
        if (state.unsupportedHintVisible) {
            item(key = "unsupported_hint") { UnsupportedHint() }
        }
        item(key = "search") {
            SearchField(
                query = state.languageQuery,
                placeholder = stringResource(R.string.search_languages),
                onQueryChange = viewModel::onLanguageQueryChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (!ready) {
            item(key = "service_banner") { ServiceBanner(state.serviceStatus) }
        }

        item(key = "system_default_header") {
            SectionHeader(stringResource(R.string.section_system_default))
        }
        item(key = "system_default") {
            LanguageOptionRow(
                option = systemDefaultOption(),
                selected = state.currentTags.isEmpty(),
                pinned = false,
                onSelect = viewModel::onResetToSystem,
                onTogglePin = null,
                modifier = Modifier.alpha(sectionAlpha),
            )
        }

        if (suggestedOptions.isNotEmpty()) {
            item(key = "suggested_header") {
                SectionHeader(stringResource(R.string.section_suggested))
            }
            items(suggestedOptions, key = { "suggested_${it.languageTag}" }) { option ->
                val pinned = pinnedOptions.any { it.languageTag == option.languageTag }
                LanguageOptionRow(
                    option = option,
                    selected = state.currentTags.firstOrNull() == option.languageTag,
                    pinned = pinned,
                    onSelect = { viewModel.onSelectOption(option) },
                    onTogglePin = { viewModel.onTogglePin(option) },
                    modifier = Modifier
                        .alpha(sectionAlpha)
                        .combinedClickable(
                            onClick = { viewModel.onSelectOption(option) },
                            onLongClick = { viewModel.onTogglePin(option) },
                        ),
                    supportingText = option.languageName.takeIf { it != option.displayName },
                )
            }
        }

        if (pinnedOptions.isNotEmpty()) {
            item(key = "pinned_header") {
                SectionHeader(stringResource(R.string.section_pinned))
            }
            items(pinnedOptions, key = { "pinned_${it.languageTag}" }) { option ->
                LanguageOptionRow(
                    option = option,
                    selected = state.currentTags.firstOrNull() == option.languageTag,
                    pinned = true,
                    onSelect = { viewModel.onSelectOption(option) },
                    onTogglePin = { viewModel.onTogglePin(option) },
                    modifier = Modifier
                        .alpha(sectionAlpha)
                        .combinedClickable(
                            onClick = { viewModel.onSelectOption(option) },
                            onLongClick = { viewModel.onTogglePin(option) },
                        ),
                    supportingText = option.languageName.takeIf { it != option.displayName },
                )
            }
        }

        item(key = "all_languages_header") {
            SectionHeader(stringResource(R.string.section_all_languages))
        }
        if (groups.isEmpty()) {
            item(key = "languages_empty") {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.no_languages_title),
                    message = stringResource(R.string.no_languages_message),
                )
            }
        } else {
            groups.forEach { group ->
                val expanded = state.expandedLanguage == group.languageName
                item(key = "language_${group.languageName}") {
                    LanguageGroupHeader(
                        languageName = group.languageName,
                        expanded = expanded,
                        modifier = Modifier.alpha(sectionAlpha),
                        onClick = { viewModel.onToggleLanguageGroup(group.languageName) },
                    )
                }
                item(key = "regions_${group.languageName}") {
                    AnimatedVisibility(visible = expanded) {
                        RegionChips(
                            options = group.options,
                            selectedTag = state.currentTags.firstOrNull(),
                            onSelect = viewModel::onSelectOption,
                            modifier = Modifier.alpha(sectionAlpha),
                        )
                    }
                }
            }
        }

        item(key = "bottom_spacer") { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun AppHeader(app: AppSummary, state: DetailUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            AppIcon(
                packageName = app.packageName,
                version = app.iconVersion,
                contentDescription = stringResource(R.string.cd_app_icon, app.label),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (app.isSystem) {
                    Spacer(Modifier.width(8.dp))
                    AppBadge(text = stringResource(R.string.label_system))
                }
            }
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.currentDisplay?.let { stringResource(R.string.current_language, it) }
                    ?: stringResource(R.string.system_default),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuickActions(
    state: DetailUiState,
    onOpen: () -> Unit,
    onForceStop: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickActionButton(
            icon = Icons.AutoMirrored.Outlined.OpenInNew,
            label = stringResource(R.string.action_open),
            onClick = onOpen,
            modifier = Modifier.weight(1f),
        )
        QuickActionButton(
            icon = Icons.Outlined.PowerSettingsNew,
            label = stringResource(R.string.action_force_stop),
            onClick = onForceStop,
            enabled = state.isServiceReady,
            modifier = Modifier.weight(1f),
        )
        QuickActionButton(
            icon = Icons.Outlined.Settings,
            label = stringResource(R.string.action_app_settings),
            onClick = onOpenSettings,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UnsupportedHint() {
    Text(
        text = stringResource(R.string.warning_unsupported),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
private fun ServiceBanner(status: ServiceStatus) {
    val title = when (status) {
        ServiceStatus.Checking, ServiceStatus.Connecting -> stringResource(R.string.service_connecting)
        else -> stringResource(R.string.error_service_unavailable)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(R.string.service_browse_only),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LanguageGroupHeader(
    languageName: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = languageName,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) {
                Icons.Outlined.KeyboardArrowUp
            } else {
                Icons.Outlined.KeyboardArrowDown
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun systemDefaultOption(): LocaleOption {
    val label = stringResource(R.string.system_default)
    return remember(label) {
        LocaleOption(languageTag = "", displayName = label, languageName = label)
    }
}

private fun contentWidth(windowWidth: WindowWidth): Modifier =
    if (windowWidth == WindowWidth.COMPACT) {
        Modifier.fillMaxWidth()
    } else {
        // Constrain first, then fill, so the content is centred at WideContentMaxWidth on big windows.
        Modifier
            .widthIn(max = WideContentMaxWidth)
            .fillMaxWidth()
    }

private fun UiMessage.resolve(context: Context): String = when {
    argResId != null -> context.getString(resId, context.getString(argResId))
    args.isEmpty() -> context.getString(resId)
    else -> context.getString(resId, *args.toTypedArray())
}
