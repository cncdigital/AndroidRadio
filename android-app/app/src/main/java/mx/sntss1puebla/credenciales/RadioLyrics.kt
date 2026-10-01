package mx.sntss1puebla.credenciales

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TimedWord(val startMs: Long, val endMs: Long, val from: Int, val to: Int)
data class TimedLyric(val atMs: Long, val text: String, val words: List<TimedWord> = emptyList())

internal object RadioLyrics {
    private val timestamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordMark = Regex("""<(/?)(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private fun timeOf(match: MatchResult, word: Boolean = false): Long {
        val shift = if (word) 1 else 0
        val fraction = match.groupValues[3+shift]
        return (match.groupValues[1+shift].toLong()*60+match.groupValues[2+shift].toLong())*1000 +
            (fraction.toLongOrNull()?.times(when(fraction.length){1->100;2->10;else->1}) ?: 0)
    }

    fun fetch(id: Int): String {
        require(id > 0)
        val connection = (URL("${RadioCatalog.ORIGIN}/api/radio/lyrics/$id").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 7_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return ""
            val body = connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                val result = StringBuilder()
                while (result.length <= 300_000) {
                    val size = reader.read(buffer, 0, minOf(buffer.size, 300_001 - result.length))
                    if (size < 0) break
                    result.append(buffer, 0, size)
                }
                if (result.length > 300_000) return ""
                result.toString()
            }
            JSONObject(body).optString("lyrics").take(48_000)
        } finally {
            connection.disconnect()
        }
    }

    fun parse(text: String): List<TimedLyric> {
        val offset = Regex("""(?im)^\[offset:([+-]?\d{1,6})]$""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val result = mutableListOf<TimedLyric>()
        for (raw in text.lineSequence()) {
            val lines = timestamp.findAll(raw).filter { it.groupValues[2].toInt()<60 }.toList()
            if (lines.isEmpty()) continue
            val body = timestamp.replace(raw, "").trim()
            val marks = wordMark.findAll(body).toList()
            val plain = wordMark.replace(body, "")
            val clean = plain.trim()
            val trim = plain.length-plain.trimStart().length
            val words = mutableListOf<TimedWord>()
            var removed = 0
            marks.forEachIndexed { i, mark ->
                removed += mark.value.length
                val next = marks.getOrNull(i+1) ?: return@forEachIndexed
                if (mark.groupValues[1].isNotEmpty() || next.groupValues[1]!="/" || mark.groupValues[3].toInt()>=60 || next.groupValues[3].toInt()>=60) return@forEachIndexed
                val segment = body.substring(mark.range.last+1,next.range.first)
                if (segment.isBlank()) return@forEachIndexed
                val from = mark.range.last+1-removed-trim+segment.length-segment.trimStart().length
                val to = from+segment.trim().length
                val start = timeOf(mark,true)+offset; val end = timeOf(next,true)+offset
                if (start>=0 && end>start && from>=0 && to<=clean.length && (words.isEmpty() || start>=words.last().endMs)) words.add(TimedWord(start,end,from,to))
            }
            for (line in lines) {
                val start = (timeOf(line)+offset).coerceAtLeast(0)
                result.add(TimedLyric(start,clean,words.filter { it.startMs>=start-10 }))
                if (result.size>=400) return result.sortedBy { it.atMs }
            }
        }
        return result.sortedBy { it.atMs }
    }
}
