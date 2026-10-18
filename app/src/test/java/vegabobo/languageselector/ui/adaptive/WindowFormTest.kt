package vegabobo.languageselector.ui.adaptive

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
}
