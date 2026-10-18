package vegabobo.languageselector.ui.navigation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import vegabobo.languageselector.R
import vegabobo.languageselector.core.model.ServiceFailure
import vegabobo.languageselector.core.model.ServiceStatus

/** Resolves the human readable label for a service status. */
@Composable
fun serviceStatusLabel(status: ServiceStatus): String = when (status) {
    is ServiceStatus.Checking -> stringResource(R.string.loading)
    is ServiceStatus.Connecting -> stringResource(R.string.service_connecting)
    is ServiceStatus.ShizukuNotInstalled -> stringResource(R.string.shizuku_not_installed_title)
    is ServiceStatus.ShizukuNotRunning -> stringResource(R.string.shizuku_not_running_title)
    is ServiceStatus.PermissionRequired -> stringResource(R.string.permissions_required)
    is ServiceStatus.Ready ->
        if (status.mode == vegabobo.languageselector.core.model.OperationMode.ROOT) {
            stringResource(R.string.service_ready_root)
        } else {
            stringResource(R.string.service_ready_shizuku)
        }

    is ServiceStatus.Failed -> stringResource(
        R.string.service_failed,
        failureLabel(status.failure),
    )
}

@Composable
private fun failureLabel(failure: ServiceFailure): String = when (failure) {
    ServiceFailure.PERMISSION_DENIED -> stringResource(R.string.permissions_required)
    ServiceFailure.SERVICE_UNAVAILABLE -> stringResource(R.string.error_service_unavailable)
    ServiceFailure.TIMEOUT -> stringResource(R.string.error_service_unavailable)
    ServiceFailure.REMOTE_ERROR -> stringResource(R.string.error_service_unavailable)
    ServiceFailure.BINDER_DIED -> stringResource(R.string.error_service_unavailable)
    ServiceFailure.ROOT_DENIED -> stringResource(R.string.service_root_denied)
    ServiceFailure.UNKNOWN -> stringResource(R.string.error_service_unavailable)
}

/** Dialog explaining the current service state, with retry/permission actions. */
@Composable
fun ServiceStatusDialog(
    status: ServiceStatus,
    onReconnect: () -> Unit,
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.service_status)) },
        text = { Text(serviceStatusLabel(status)) },
        confirmButton = {
            TextButton(onClick = onReconnect) {
                Text(stringResource(R.string.action_reconnect))
            }
        },
        dismissButton = {
            if (status is ServiceStatus.PermissionRequired) {
                TextButton(onClick = onRequestPermission) {
                    Text(stringResource(R.string.action_proceed))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            }
        },
    )
}
