package com.raf.capsuleos.data.media

import com.raf.capsuleos.domain.model.LrcLyricsParser
import com.raf.capsuleos.domain.model.LyricLine
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Fetches synced (LRC) or plain lyrics from the public LRCLIB API for streaming players such as
 * YouTube Music and Spotify that do not expose lyrics inside Android [android.media.MediaMetadata].
 */
internal object OnlineLyricsFetcher {
    private const val USER_AGENT = "RAFCapsuleOS/1.2.0 (https://github.com/RidhwanAF/RAF-Capsule)"
    private const val TIMEOUT_MS = 3_500

    private val NOISE_PARENS_REGEX = Regex(
        """\s*[\[(](?:official\s*(?:music\s*)?video|official\s*audio|official\s*lyric\s*video|lyrics?|audio|hd|4k|mv|visualizer|explicit|clean|remastered(?:\s*\d{4})?)[\])]""",
        RegexOption.IGNORE_CASE,
    )
    private val TOPIC_SUFFIX_REGEX = Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE)

    /** Normalizes YouTube Music / streaming track metadata into a stable cache key. */
    fun cacheKey(title: String, artist: String): String =
        "${cleanArtist(artist).lowercase()}|${cleanTitle(title).lowercase()}"

    /** Queries LRCLIB `/api/get` then `/api/search`, strongly preferring timestamped LRC lyrics. */
    fun fetch(title: String, artist: String, durationMs: Long): List<LyricLine> {
        val cleanedTitle = cleanTitle(title)
        if (cleanedTitle.isBlank()) return emptyList()
        val cleanedArtist = cleanArtist(artist)
        val durationSec = (durationMs / 1000L).takeIf { it in 20L..1200L }

        val (exactSynced, exactPlain) = if (cleanedArtist.isNotBlank()) {
            queryExact(cleanedTitle, cleanedArtist, durationSec)
        } else {
            null to null
        }
        if (!exactSynced.isNullOrBlank()) {
            val parsed = LrcLyricsParser.parse(exactSynced)
            if (parsed.any { it.timestampMs >= 0L }) return parsed
        }

        val (searchSynced, searchPlain) = querySearch(cleanedTitle, cleanedArtist, durationSec)
        val bestRaw = searchSynced ?: exactPlain ?: searchPlain
        return LrcLyricsParser.parse(bestRaw)
    }

    private fun queryExact(title: String, artist: String, durationSec: Long?): Pair<String?, String?> {
        val params = buildString {
            append("track_name=").append(encode(title))
            append("&artist_name=").append(encode(artist))
            if (durationSec != null) {
                append("&duration=").append(durationSec)
            }
        }
        val body = httpGet("https://lrclib.net/api/get?$params") ?: return null to null
        return runCatching {
            extractLyricsPair(JSONObject(body))
        }.getOrDefault(null to null)
    }

    private fun querySearch(title: String, artist: String, durationSec: Long?): Pair<String?, String?> {
        val params = buildString {
            append("track_name=").append(encode(title))
            if (artist.isNotBlank()) {
                append("&artist_name=").append(encode(artist))
            }
        }
        val body = httpGet("https://lrclib.net/api/search?$params") ?: return null to null
        return runCatching {
            val arr = JSONArray(body)
            var bestSynced: String? = null
            var bestSyncedDelta = Long.MAX_VALUE
            var bestPlain: String? = null
            var bestPlainDelta = Long.MAX_VALUE

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optBoolean("instrumental", false)) continue
                val itemDur = obj.optDouble("duration", -1.0).toLong()
                val delta = if (durationSec != null && itemDur > 0L) {
                    abs(itemDur - durationSec)
                } else {
                    0L
                }
                if (durationSec != null && itemDur > 0L && delta > 20L) continue

                val (synced, plain) = extractLyricsPair(obj)
                if (!synced.isNullOrBlank() && delta < bestSyncedDelta) {
                    bestSynced = synced
                    bestSyncedDelta = delta
                }
                if (!plain.isNullOrBlank() && delta < bestPlainDelta) {
                    bestPlain = plain
                    bestPlainDelta = delta
                }
            }
            bestSynced to bestPlain
        }.getOrDefault(null to null)
    }

    private fun extractLyricsPair(obj: JSONObject): Pair<String?, String?> {
        if (obj.optBoolean("instrumental", false)) return null to null
        val synced = obj.optString("syncedLyrics", "").trim().takeIf { it.isNotBlank() && it != "null" }
        val plain = obj.optString("plainLyrics", "").trim().takeIf { it.isNotBlank() && it != "null" }
        return synced to plain
    }

    private fun httpGet(urlString: String): String? = runCatching {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun cleanTitle(raw: String): String =
        raw.replace(NOISE_PARENS_REGEX, "").trim()

    private fun cleanArtist(raw: String): String =
        raw.replace(TOPIC_SUFFIX_REGEX, "").trim()

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
