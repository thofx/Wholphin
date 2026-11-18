package com.github.damontecres.wholphin.ui.playback.theater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuDataTest {
    @Test
    fun parse_typesColorsOrderAndEscapes() {
        val xml =
            """<?xml version="1.0" encoding="utf-8"?><i><chatid>0</chatid>""" +
                """<d p="12.5,1,25,16777215,0,0,[renren]1,0,1">later</d>""" +
                """<d p="1.25,5,25,255,0,0,x,0,1">top &amp; &lt;b&gt;</d>""" +
                """<d p="3.0,4,25,65280,0,0,x,0,1">bottom</d>""" +
                """<d p="4.0,2,25,16777215,0,0,x,0,1">non-standard mode scrolls</d>""" +
                """<d p="bad,1,25,0">skipped: bad time</d>""" +
                """<d p="6.0,7,25,0">[0,0,"1-1",4.5,"advanced"]</d>""" +
                """<d p="6.5,8,25,0">code()</d>""" +
                """<d p="12.5,1,25,16777215,0,0,[renren]2,0,1">later</d>""" +
                """<d p="20.0,6,25,0">left to right</d>""" +
                """<d p="5.0,1,25,0">   </d></i>"""

        val result = DanmakuLoader.parse(xml)

        // The duplicate of "later" (same time, mode, color, text) is dropped
        assertEquals(listOf(1250L, 3000L, 4000L, 12500L, 20000L), result.map { it.timeMs })
        // Ids come from the file (position here, as these entries have no comment id) and are unique
        assertEquals(result.size, result.map { it.id }.toSet().size)
        assertEquals(DanmakuType.SCROLL_REVERSE, result[4].type)
        assertEquals(DanmakuType.TOP, result[0].type)
        assertEquals("top & <b>", result[0].text)
        assertEquals(255, result[0].color)
        assertEquals(DanmakuType.BOTTOM, result[1].type)
        assertEquals(DanmakuType.SCROLL, result[2].type)
    }

    @Test
    fun density_keepsRoughlyTheRatioAndIsStable() {
        val all = (0 until 10_000).map { Danmaku(it, it.toLong(), DanmakuType.SCROLL, 0, "x") }
        for (percent in listOf(25, 50, 75)) {
            val kept = all.filter { keepAtDensity(it, percent) }
            val ratio = kept.size * 100.0 / all.size
            assertTrue("$percent% kept $ratio%", kotlin.math.abs(ratio - percent) < 3)
            // Same input, same answer
            assertEquals(kept, all.filter { keepAtDensity(it, percent) })
        }
        assertEquals(all.size, all.count { keepAtDensity(it, 100) })
    }

    @Test
    fun density_doesNotClumpConsecutiveComments() {
        // Kept comments must not come in runs (a periodic hash kept ~7 in a row, then dropped ~18,
        // leaving the screen empty for seconds). For an even spread, P(next kept | kept) ≈ the ratio.
        val ids = 0 until 10_000
        val kept = ids.map { keepAtDensity(Danmaku(it, it.toLong(), DanmakuType.SCROLL, 0, "x"), 25) }
        val keptPairs = (0 until kept.size - 1).count { kept[it] && kept[it + 1] }
        val conditional = keptPairs.toDouble() / kept.count { it }
        assertTrue("P(next kept | kept) = $conditional", conditional < .35)
    }

    @Test
    fun parse_usesCommentIdAndToleratesExtraAttributes() {
        val xml =
            """<d p="1.5,1,25,0,0,0,u,12345,1">with id</d>""" +
                """<d  p="2.5,1,25,0" class="x">extra attribute</d>"""
        val result = DanmakuLoader.parse(xml)
        assertEquals(listOf("with id", "extra attribute"), result.map { it.text })
        // The comment id from the file, so density filtering survives a server-side refresh
        assertEquals(12345L.hashCode(), result[0].id)
    }

    @Test
    fun parse_spreadsWholeSecondTimes() {
        val xml = (0 until 20).joinToString("") { """<d p="7.00000,1,25,0">c$it</d>""" }
        val times = DanmakuLoader.parse(xml).map { it.timeMs }
        assertTrue(times.all { it in 7000L until 8000L })
        assertTrue("spread over the second: $times", times.toSet().size > 10)
        assertEquals(times, times.sorted())
    }
}
