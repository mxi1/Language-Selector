package vegabobo.languageselector.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import vegabobo.languageselector.core.util.findActivity
import kotlin.math.max

/** Material 3 style width classes, computed from the actual window width (multi-window aware). */
enum class WindowWidth { COMPACT, MEDIUM, EXPANDED }

/** Physical form factor derived from folding features. */
enum class FoldPosture { FLAT, TABLETOP, BOOK }

/**
 * Everything the adaptive scaffolds need to know about the current window.
 * [hingeGap] is the size of a separating hinge/fold in dp (0 when flat).
 */
data class WindowForm(
    val width: WindowWidth,
    val posture: FoldPosture,
    val hingeGap: Dp,
) {
    val isWide: Boolean get() = width != WindowWidth.COMPACT
    val twoPane: Boolean get() = posture != FoldPosture.TABLETOP && isWide
}

/** Material 3 breakpoints, extracted for unit testing. */
fun windowWidthFor(widthDp: Int): WindowWidth = when {
    widthDp < 600 -> WindowWidth.COMPACT
    widthDp < 840 -> WindowWidth.MEDIUM
    else -> WindowWidth.EXPANDED
}

@Composable
fun rememberWindowForm(): WindowForm {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp

    val layoutInfo by produceState<WindowLayoutInfo?>(initialValue = null, activity) {
        val host = activity ?: return@produceState
        runCatching {
            WindowInfoTracker.getOrCreate(host).windowLayoutInfo(host).collect { value = it }
        }
    }

    val feature = layoutInfo?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()
    val separating = feature?.isSeparating == true
    val posture = when {
        !separating -> FoldPosture.FLAT
        feature?.orientation == FoldingFeature.Orientation.HORIZONTAL -> FoldPosture.TABLETOP
        else -> FoldPosture.BOOK
    }
    val hingeGap: Dp = if (feature == null) {
        0.dp
    } else {
        with(density) {
            val size = when (feature.orientation) {
                FoldingFeature.Orientation.HORIZONTAL -> feature.bounds.height()
                else -> feature.bounds.width()
            }
            max(size, 0).toDp()
        }
    }

    return WindowForm(
        width = windowWidthFor(widthDp),
        posture = posture,
        hingeGap = hingeGap,
    )
}
