package com.raf.capsuleos.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcLyricsParserTest {

    @Test
    fun `given standard LRC timestamps and multi-timestamp lines, then parses sorted LyricLines and resolves active index`() {
        val raw = """
            [ti:Midnight City]
            [ar:M83]
            [00:05.50]Waiting in a car
            [00:12.00][01:02.25]Waiting for a ride in the dark
            [00:25.000]The night city grows
        """.trimIndent()

        val parsed = LrcLyricsParser.parse(raw)
        assertEquals(4, parsed.size)
        assertEquals(5_500L, parsed[0].timestampMs)
        assertEquals("Waiting in a car", parsed[0].text)
        assertEquals(12_000L, parsed[1].timestampMs)
        assertEquals(25_000L, parsed[2].timestampMs)
        assertEquals(62_250L, parsed[3].timestampMs)
        assertEquals("Waiting for a ride in the dark", parsed[3].text)

        assertEquals(-1, LrcLyricsParser.activeLineIndex(parsed, 2_000L))
        assertEquals(0, LrcLyricsParser.activeLineIndex(parsed, 5_500L))
        assertEquals(1, LrcLyricsParser.activeLineIndex(parsed, 18_000L))
        assertEquals(2, LrcLyricsParser.activeLineIndex(parsed, 40_000L))
        assertEquals(3, LrcLyricsParser.activeLineIndex(parsed, 70_000L))
    }

    @Test
    fun `given plain multi-line lyrics without timestamps, then preserves lines with -1 timestamp`() {
        val raw = "First line\nSecond line\nThird line"
        val parsed = LrcLyricsParser.parse(raw)
        assertEquals(3, parsed.size)
        assertTrue(parsed.all { it.timestampMs == -1L })
        assertEquals(-1, LrcLyricsParser.activeLineIndex(parsed, 10_000L))
    }
}
