package vegabobo.languageselector.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import vegabobo.languageselector.R
import vegabobo.languageselector.core.model.OperationMode
import vegabobo.languageselector.core.model.ServiceFailure
import vegabobo.languageselector.core.model.ServiceStatus

/** Maximum content width, so the onboarding stays readable on tablets and foldables. */
private val ContentMaxWidth = 560.dp

/**
 * First-run / disconnected screen for the privileged service (Shizuku or Root).
 *
 * Stateless: the host owns the service controller and passes the current [status] plus the
 * action callbacks. Rendered alone (no scaffold) so the host decides about insets/snackbar.
 */
@Composable
fun OnboardingScreen(
    status: ServiceStatus,
    hasRootOption: Boolean,
    onRetry: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onUseRoot: () -> Unit,
    onBrowseOnly: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = onboardingCopy(status)
    val busy = status is ServiceStatus.Checking || status is ServiceStatus.Connecting

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            } else {
                Icon(
                    imageVector = copy.icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = copy.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            copy.message?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (copy.showSteps) {
                Spacer(Modifier.height(24.dp))
                SetupStepsCard()
            }
            Spacer(Modifier.height(24.dp))
            OnboardingActions(
                status = status,
                copy = copy,
                hasRootOption = hasRootOption,
                onRetry = onRetry,
                onRequestPermission = onRequestPermission,
                onOpenShizuku = onOpenShizuku,
                onUseRoot = onUseRoot,
                onBrowseOnly = onBrowseOnly,
            )
        }
    }
}

/** Text/icon/visibility configuration derived from the current service status. */
private data class OnboardingCopy(
    val title: String,
    val message: String?,
    val icon: ImageVector,
    val showSteps: Boolean,
    val showOpenShizuku: Boolean,
    val showUseRoot: Boolean,
)

@Composable
private fun onboardingCopy(status: ServiceStatus): OnboardingCopy = when (status) {
    is ServiceStatus.Checking -> OnboardingCopy(
        title = stringResource(R.string.loading),
        message = stringResource(R.string.shizuku_required),
        icon = Icons.Outlined.Info,
        showSteps = false,
        showOpenShizuku = false,
        showUseRoot = false,
    )

    is ServiceStatus.Connecting -> OnboardingCopy(
        title = stringResource(R.string.service_connecting),
        message = stringResource(R.string.shizuku_required),
        icon = Icons.Outlined.Info,
        showSteps = false,
        showOpenShizuku = false,
        showUseRoot = false,
    )

    is ServiceStatus.ShizukuNotInstalled -> OnboardingCopy(
        title = stringResource(R.string.shizuku_not_installed_title),
        message = stringResource(R.string.shizuku_not_installed_message),
        icon = Icons.Outlined.Info,
        showSteps = true,
        showOpenShizuku = true,
        showUseRoot = true,
    )

    is ServiceStatus.ShizukuNotRunning -> OnboardingCopy(
        title = stringResource(R.string.shizuku_not_running_title),
        message = stringResource(R.string.shizuku_not_running_message),
        icon = Icons.Outlined.WarningAmber,
        showSteps = true,
        showOpenShizuku = true,
        showUseRoot = true,
    )

    is ServiceStatus.PermissionRequired -> OnboardingCopy(
        title = stringResource(R.string.shizuku_permission_title),
        message = stringResource(R.string.shizuku_permission_message),
        icon = Icons.Outlined.Lock,
        showSteps = true,
        showOpenShizuku = true,
        showUseRoot = true,
    )

    is ServiceStatus.Ready -> OnboardingCopy(
        title = if (status.mode == OperationMode.ROOT) {
            stringResource(R.string.service_ready_root)
        } else {
            stringResource(R.string.service_ready_shizuku)
        },
        message = null,
        icon = Icons.Outlined.CheckCircle,
        showSteps = false,
        showOpenShizuku = false,
        showUseRoot = false,
    )

    is ServiceStatus.Failed -> OnboardingCopy(
        title = stringResource(R.string.service_failed, failureLabel(status.failure)),
        message = stringResource(R.string.shizuku_required),
        icon = Icons.Outlined.WarningAmber,
        showSteps = false,
        showOpenShizuku = true,
        showUseRoot = true,
    )
}

@Composable
private fun failureLabel(failure: ServiceFailure): String = when (failure) {
    ServiceFailure.PERMISSION_DENIED -> stringResource(R.string.permissions_required)
    ServiceFailure.ROOT_DENIED -> stringResource(R.string.service_root_denied)
    else -> stringResource(R.string.error_service_unavailable)
}

@Composable
private fun SetupStepsCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.service_steps_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.service_step_install),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.service_step_grant),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.service_step_adb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnboardingActions(
    status: ServiceStatus,
    copy: OnboardingCopy,
    hasRootOption: Boolean,
    onRetry: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onUseRoot: () -> Unit,
    onBrowseOnly: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (status) {
            is ServiceStatus.PermissionRequired -> Button(onClick = onRequestPermission) {
                Text(stringResource(R.string.action_proceed))
            }

            is ServiceStatus.ShizukuNotRunning, is ServiceStatus.Failed -> Button(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }

            is ServiceStatus.ShizukuNotInstalled -> Button(onClick = onOpenShizuku) {
                Text(stringResource(R.string.service_open_shizuku))
            }

            else -> Unit
        }

        if (copy.showOpenShizuku && status !is ServiceStatus.ShizukuNotInstalled) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenShizuku) {
                Text(stringResource(R.string.service_open_shizuku))
            }
        }

        if (hasRootOption && copy.showUseRoot) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onUseRoot) {
                Text(stringResource(R.string.service_use_root))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.service_use_root_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        if (status !is ServiceStatus.Ready) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBrowseOnly) {
                Text(stringResource(R.string.service_browse_only))
            }
        }
    }
}
