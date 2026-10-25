package vegabobo.languageselector.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Two-pane scaffold for medium/expanded/two-pane foldable windows.
 * Compact windows use the navigation based single-pane flow instead.
 *
 * Tabletop posture stacks the panes vertically and reserves the hinge gap;
 * book posture splits horizontally with the hinge gap in between.
 */
@Composable
fun AdaptiveScaffold(
    windowForm: WindowForm,
    listPane: @Composable (Modifier) -> Unit,
    detailPane: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (windowForm.posture) {
        FoldPosture.TABLETOP -> Column(modifier = modifier.fillMaxSize()) {
            Box(Modifier.weight(0.42f).fillMaxWidth()) {
                listPane(Modifier.fillMaxSize())
            }
            HingeSpacer(windowForm.hingeGap, horizontal = true)
            Box(Modifier.weight(0.58f).fillMaxWidth()) {
                detailPane(Modifier.fillMaxSize())
            }
        }

        FoldPosture.BOOK -> Row(modifier = modifier.fillMaxSize()) {
            Box(Modifier.weight(0.42f).fillMaxHeight()) {
                listPane(Modifier.fillMaxSize())
            }
            HingeSpacer(windowForm.hingeGap, horizontal = false)
            Box(Modifier.weight(0.58f).fillMaxHeight()) {
                detailPane(Modifier.fillMaxSize())
            }
        }

        FoldPosture.FLAT -> when (listPaneStyle(windowForm)) {
            // Foldable inner screen (and tablet portrait): icon rail keeps the detail readable.
            ListPaneStyle.ICON_RAIL -> Row(modifier = modifier.fillMaxSize()) {
                Box(Modifier.width(IconRailWidth).fillMaxHeight()) {
                    listPane(Modifier.fillMaxSize())
                }
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    detailPane(Modifier.fillMaxSize())
                }
            }

            ListPaneStyle.FULL -> if (windowForm.width == WindowWidth.EXPANDED) {
                // Landscape / large window: 30/70 keeps the detail pane dominant.
                Row(modifier = modifier.fillMaxSize()) {
                    Box(Modifier.weight(0.30f).fillMaxHeight()) {
                        listPane(Modifier.fillMaxSize())
                    }
                    VerticalDivider()
                    Box(Modifier.weight(0.70f).fillMaxHeight()) {
                        detailPane(Modifier.fillMaxSize())
                    }
                }
            } else {
                // Medium landscape windows: a compact fixed list still leaves room for detail.
                Row(modifier = modifier.fillMaxSize()) {
                    Box(Modifier.width(280.dp).fillMaxHeight()) {
                        listPane(Modifier.fillMaxSize())
                    }
                    VerticalDivider()
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        detailPane(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/** Width of the icon-only app list rail used on medium portrait windows. */
val IconRailWidth: Dp = 88.dp

@Composable
private fun HingeSpacer(gap: androidx.compose.ui.unit.Dp, horizontal: Boolean) {
    if (gap <= 0.dp) return
    if (horizontal) {
        Spacer(Modifier.fillMaxWidth().height(gap))
    } else {
        Spacer(Modifier.fillMaxHeight().width(gap))
    }
}
