package mx.sntss1puebla.credenciales

import org.junit.Assert.*
import org.junit.Test

class RadioLyricsTest {
    @Test fun measuredWordsKeepGapsUnicodeAndOffsets() {
        val line = RadioLyrics.parse("[offset:+250]\n[00:10.00]<00:10.00>Hola</00:10.30> 😀 <00:12.00>corazón</00:12.50>!").single()
        assertEquals(10250L,line.atMs)
        assertEquals("Hola 😀 corazón!",line.text)
        assertEquals(listOf(TimedWord(10250,10550,0,4),TimedWord(12250,12750,8,15)),line.words)
    }
    @Test fun untimedAndInvalidWordsGetNoInventedTimes() {
        assertTrue(RadioLyrics.parse("[00:10.00]Letra por línea").single().words.isEmpty())
        val line = RadioLyrics.parse("[00:10.00]<00:10.00>Hola</00:09.00> sin tiempos").single()
        assertEquals("Hola sin tiempos",line.text)
        assertTrue(line.words.isEmpty())
    }
    @Test fun repeatedLineMarksAndInstrumentalBreakArePreserved() {
        val lines=RadioLyrics.parse("[00:10.00][00:30.00]Coro\n[00:35.00]")
        assertEquals(listOf(10000L,30000L,35000L),lines.map { it.atMs })
        assertEquals("",lines.last().text)
    }
}
