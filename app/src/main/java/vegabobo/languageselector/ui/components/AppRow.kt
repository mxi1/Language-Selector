package vegabobo.languageselector.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vegabobo.languageselector.R
import vegabobo.languageselector.core.model.AppLocaleState
import vegabobo.languageselector.core.model.AppSummary
import vegabobo.languageselector.core.util.localeDisplayName

/** One tappable app row. Icons are loaded lazily through [AppIcon]. */
@Composable
fun AppRow(
    app: AppSummary,
    localeState: AppLocaleState?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showChevron: Boolean = false,
    dense: Boolean = false,
) {
    val background = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    // At very large font scales the trailing language label would squeeze the app name into an
    // ellipsis; show the current language as a badge under the package name instead.
    val showTrailingLocale = !dense &&
        localeState?.isModified == true &&
        LocalDensity.current.fontScale <= 1.4f
    val currentTag = localeState?.primaryTag
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = if (dense) 8.dp else 12.dp, vertical = if (dense) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            packageName = app.packageName,
            version = app.iconVersion,
            contentDescription = stringResource(R.string.cd_app_icon, app.label),
        )
        Spacer(Modifier.width(if (dense) 10.dp else 14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!dense) {
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app.isSystem && !dense) {
                    AppBadge(text = stringResource(R.string.label_system))
                }
                if (localeState?.isModified == true) {
                    AppBadge(text = stringResource(R.string.label_modified))
                }
                if (!dense && !showTrailingLocale && currentTag != null) {
                    AppBadge(text = localeDisplayName(currentTag))
                }
            }
        }
        if (showTrailingLocale) {
            currentTag?.let { tag ->
                Spacer(Modifier.width(8.dp))
                Text(
                    text = localeDisplayName(tag),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
            }
        }
        if (showChevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
