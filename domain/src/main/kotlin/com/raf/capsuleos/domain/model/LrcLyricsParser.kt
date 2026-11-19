package com.raf.capsuleos.domain.model

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
            return timedLines.sortedBy { it.timestampMs }
        }

        val plainLines = rawLines
            .filterNot { METADATA_TAG_REGEX.matches(it) }
            .map { LyricLine(timestampMs = -1L, text = it) }
        return if (plainLines.size >= 2) plainLines else emptyList()
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
