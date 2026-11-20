package com.raf.capsuleos.domain.model

import kotlin.math.abs

/**
 * Pure Kotlin parser and playback-position resolver for synced LRC (`[mm:ss.xx]`) and plain
 * multi-line lyrics strings extracted from media session metadata.
 */
object LrcLyricsParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val METADATA_TAG_REGEX = Regex("""^\[[a-zA-Z]+:.*]$""")

    /**
     * Parses [raw] into an ordered list of [LyricLine] entries.
     *
     * Supports standard LRC timestamps (`[01:23.45]`, `[01:23.450]`, `[01:23]`), multiple timestamps
     * per line (`[00:12.00][01:02.00] Chorus`), and falls back to plain multi-line lyrics
     * (`timestampMs = -1L`) when no timestamps are present.
     */
    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()
        val rawLines = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (rawLines.isEmpty()) return emptyList()

        val timedLines = mutableListOf<LyricLine>()
        var foundAnyTimestamp = false

        for (line in rawLines) {
            if (METADATA_TAG_REGEX.matches(line)) continue
            val matches = TIMESTAMP_REGEX.findAll(line).toList()
            if (matches.isEmpty()) continue
            foundAnyTimestamp = true
            val text = line.replace(TIMESTAMP_REGEX, "").trim()
            if (text.isEmpty()) continue
            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                val fracRaw = match.groupValues[3]
                val millis = when (fracRaw.length) {
                    0 -> 0L
                    1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
                    else -> fracRaw.take(3).toLongOrNull() ?: 0L
                }
                val tsMs = (minutes * 60_000L) + (seconds * 1_000L) + millis
                timedLines.add(LyricLine(timestampMs = tsMs, text = text))
            }
        }

        if (foundAnyTimestamp) {
            return splitLongTimedLines(timedLines.sortedBy { it.timestampMs })
        }

        val plainLines = rawLines
            .filterNot { METADATA_TAG_REGEX.matches(it) }
            .map { LyricLine(timestampMs = -1L, text = it) }
        return if (plainLines.size >= 2) plainLines else emptyList()
    }

    /**
     * Splits overly long single-timestamp lyric lines (> 56 chars) into balanced timed sub-lines
     * so tall multi-line captions never overflow the compact expanded media lyrics viewport.
     */
    private fun splitLongTimedLines(sorted: List<LyricLine>): List<LyricLine> {
        if (sorted.isEmpty()) return sorted
        val result = ArrayList<LyricLine>(sorted.size)
        sorted.forEachIndexed { idx, line ->
            val text = line.text
            if (text.length <= 56) {
                result.add(line)
                return@forEachIndexed
            }
            val nextTs = sorted.getOrNull(idx + 1)?.timestampMs
            val spanMs = if (nextTs != null && nextTs > line.timestampMs) {
                (nextTs - line.timestampMs).coerceAtMost(9_000L)
            } else {
                4_000L
            }
            val splitPos = findBalancedSplitIndex(text)
            if (splitPos <= 0 || splitPos >= text.length - 4 || spanMs < 1_800L) {
                result.add(line)
            } else {
                val firstPart = text.substring(0, splitPos).trim()
                val secondPart = text.substring(splitPos).trim()
                if (firstPart.isEmpty() || secondPart.isEmpty()) {
                    result.add(line)
                } else {
                    val ratio = firstPart.length.toFloat() / (firstPart.length + secondPart.length).toFloat()
                    val secondTs = line.timestampMs + (spanMs * ratio).toLong().coerceAtLeast(800L)
                    result.add(LyricLine(timestampMs = line.timestampMs, text = firstPart))
                    result.add(LyricLine(timestampMs = secondTs, text = secondPart))
                }
            }
        }
        return result
    }

    private fun findBalancedSplitIndex(text: String): Int {
        val mid = text.length / 2
        val minIdx = (text.length * 0.30f).toInt().coerceAtLeast(12)
        val maxIdx = (text.length * 0.72f).toInt().coerceAtMost(text.length - 8)
        if (minIdx >= maxIdx) return -1

        // Prefer natural clause punctuation near the midpoint
        var bestPunct = -1
        var bestPunctDist = Int.MAX_VALUE
        for (i in minIdx..maxIdx) {
            val ch = text[i]
            if ((ch == ',' || ch == ';' || ch == '/' || ch == '—') && i + 1 < text.length) {
                val dist = abs(i - mid)
                if (dist < bestPunctDist) {
                    bestPunctDist = dist
                    bestPunct = i + 1
                }
            }
        }
        if (bestPunct > 0) return bestPunct

        // Fall back to nearest whitespace around the midpoint
        var bestSpace = -1
        var bestSpaceDist = Int.MAX_VALUE
        for (i in minIdx..maxIdx) {
            if (text[i] == ' ') {
                val dist = abs(i - mid)
                if (dist < bestSpaceDist) {
                    bestSpaceDist = dist
                    bestSpace = i
                }
            }
        }
        return bestSpace
    }

    /**
     * Resolves the active lyric index for [positionMs], or `-1` if [lyrics] has no timestamps or
     * playback is before the first timestamped line.
     */
    fun activeLineIndex(lyrics: List<LyricLine>, positionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        if (lyrics.none { it.timestampMs >= 0L }) return -1
        var activeIdx = -1
        for (index in lyrics.indices) {
            val ts = lyrics[index].timestampMs
            if (ts in 0L..positionMs) {
                activeIdx = index
            } else if (ts > positionMs) {
                break
            }
        }
        return activeIdx
    }
}
