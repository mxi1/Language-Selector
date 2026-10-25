package vegabobo.languageselector.ui.adaptive

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowFormTest {

    @Test
    fun `compact below 600dp`() {
        assertEquals(WindowWidth.COMPACT, windowWidthFor(0))
        assertEquals(WindowWidth.COMPACT, windowWidthFor(599))
    }

    @Test
    fun `medium between 600 and 839dp`() {
        assertEquals(WindowWidth.MEDIUM, windowWidthFor(600))
        assertEquals(WindowWidth.MEDIUM, windowWidthFor(839))
    }

    @Test
    fun `expanded from 840dp`() {
        assertEquals(WindowWidth.EXPANDED, windowWidthFor(840))
        assertEquals(WindowWidth.EXPANDED, windowWidthFor(1280))
    }

    @Test
    fun `medium portrait uses the icon rail`() {
        val form = WindowForm(
            width = WindowWidth.MEDIUM,
            posture = FoldPosture.FLAT,
            hingeGap = 0.dp,
            isPortrait = true,
        )

        assertEquals(ListPaneStyle.ICON_RAIL, listPaneStyle(form))
    }

    @Test
    fun `medium landscape keeps the full list`() {
        val form = WindowForm(
            width = WindowWidth.MEDIUM,
            posture = FoldPosture.FLAT,
            hingeGap = 0.dp,
            isPortrait = false,
        )

        assertEquals(ListPaneStyle.FULL, listPaneStyle(form))
    }

    @Test
    fun `expanded portrait keeps the full list`() {
        val form = WindowForm(
            width = WindowWidth.EXPANDED,
            posture = FoldPosture.FLAT,
            hingeGap = 0.dp,
            isPortrait = true,
        )

        assertEquals(ListPaneStyle.FULL, listPaneStyle(form))
    }

    @Test
    fun `tabletop keeps the full list even in a medium portrait window`() {
        val form = WindowForm(
            width = WindowWidth.MEDIUM,
            posture = FoldPosture.TABLETOP,
            hingeGap = 24.dp,
            isPortrait = true,
        )

        assertEquals(ListPaneStyle.FULL, listPaneStyle(form))
    }
}
