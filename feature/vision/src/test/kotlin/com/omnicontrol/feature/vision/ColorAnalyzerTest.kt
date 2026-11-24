package com.omnicontrol.feature.vision

import com.omnicontrol.feature.vision.color.ColorAnalyzer
import com.omnicontrol.feature.vision.color.NamedColor
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorAnalyzerTest {

    @Test
    fun testParseColorNames() {
        assertEquals(NamedColor.RED, ColorAnalyzer.parseColorName("the red button"))
        assertEquals(NamedColor.BLUE, ColorAnalyzer.parseColorName("open the blue thumbnail"))
        assertEquals(NamedColor.GREEN, ColorAnalyzer.parseColorName("green icon"))
        assertEquals(NamedColor.YELLOW, ColorAnalyzer.parseColorName("yellow star"))
        assertEquals(NamedColor.PURPLE, ColorAnalyzer.parseColorName("purple badge"))
        assertEquals(NamedColor.ORANGE, ColorAnalyzer.parseColorName("orange alert"))
        assertEquals(NamedColor.BLACK, ColorAnalyzer.parseColorName("black header"))
        assertEquals(NamedColor.WHITE, ColorAnalyzer.parseColorName("white card"))
    }
}
