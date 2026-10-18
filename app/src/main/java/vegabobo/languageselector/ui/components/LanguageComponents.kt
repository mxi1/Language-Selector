package vegabobo.languageselector.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vegabobo.languageselector.R
import vegabobo.languageselector.core.model.LocaleOption

/** A language (or language+region) option with selection state and pin toggle. */
@Composable
fun LanguageOptionRow(
    option: LocaleOption,
    selected: Boolean,
    pinned: Boolean,
    onSelect: () -> Unit,
    onTogglePin: (() -> Unit)?,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = option.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onTogglePin != null) {
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = if (pinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (pinned) {
                        stringResource(R.string.cd_unpin, option.displayName)
                    } else {
                        stringResource(R.string.cd_pin, option.displayName)
                    },
                    tint = if (pinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** Region variants of one language, laid out as wrapped chips for narrow and wide panes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegionChips(
    options: List<LocaleOption>,
    selectedTag: String?,
    onSelect: (LocaleOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option.languageTag == selectedTag,
                onClick = { onSelect(option) },
                label = { Text(option.displayName) },
            )
        }
    }
}
