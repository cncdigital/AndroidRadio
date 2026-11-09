package mx.sntss1puebla.credenciales

import org.junit.Assert.*
import org.junit.Test

class RadioVisualStyleTest {
    @Test fun wrapsInBothDirectionsAndReturnsToOriginalCover() {
        assertEquals(RadioVisualStyle.BARS, RadioVisualStyle.COVER.shifted(1))
        assertEquals(RadioVisualStyle.STARS, RadioVisualStyle.COVER.shifted(-1))
        assertEquals(RadioVisualStyle.COVER, RadioVisualStyle.STARS.shifted(1))
        var style = RadioVisualStyle.COVER
        repeat(RadioVisualStyle.entries.size) { style = style.shifted(1) }
        assertEquals(RadioVisualStyle.COVER, style)
        repeat(RadioVisualStyle.entries.size) { style = style.shifted(-1) }
        assertEquals(RadioVisualStyle.COVER, style)
        assertEquals(style, style.shifted(0))
    }

    @Test fun storedIdsRoundTripAndUnknownIdsFallBackToCover() {
        assertEquals(6, RadioVisualStyle.entries.map { it.id }.toSet().size)
        for (style in RadioVisualStyle.entries) assertEquals(style, RadioVisualStyle.fromId(style.id))
        assertEquals(RadioVisualStyle.COVER, RadioVisualStyle.fromId(null))
        assertEquals(RadioVisualStyle.COVER, RadioVisualStyle.fromId("deleted-style"))
    }

    @Test fun onlyDeliberateHorizontalSwipesChangeBackground() {
        assertEquals(1, RadioBackgroundGesture.direction(-80f, 10f, 48f))
        assertEquals(-1, RadioBackgroundGesture.direction(80f, -10f, 48f))
        assertEquals(0, RadioBackgroundGesture.direction(2f, 1f, 48f))
        assertEquals(0, RadioBackgroundGesture.direction(47f, 0f, 48f))
        assertEquals(0, RadioBackgroundGesture.direction(50f, 80f, 48f))
        assertEquals(0, RadioBackgroundGesture.direction(80f, 80f, 48f))
        assertEquals(0, RadioBackgroundGesture.direction(80f, 54f, 48f))
        assertEquals(0, RadioBackgroundGesture.direction(Float.NaN, 0f, 48f))
        assertEquals(0, RadioBackgroundGesture.direction(Float.POSITIVE_INFINITY, 0f, 48f))
        assertEquals(0, RadioBackgroundGesture.direction(80f, 0f, 0f))
    }
}
