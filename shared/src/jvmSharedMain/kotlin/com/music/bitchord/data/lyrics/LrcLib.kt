package com.music.bitchord.data.lyrics

import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import kotlin.math.abs

/**
 * Lyrics from LRCLIB — a free, key-less, community lyrics database.
 *
 * Two calls: an exact `get` keyed on artist + title + duration, and a fuzzy
 * `search` when that misses (YouTube Music titles carry "(From ...)" and
 * "| Official Video" noise that the exact endpoint won't match). Results are
 * line-synced `[mm:ss.xx]` LRC — LRCLIB has no word-level timing, so
 * highlighting is per line.
 */
object LrcLib {

    private const val BASE = "https://lrclib.net/api"
    private const val AGENT = "BitChord (https://github.com/bitchord)"

    private val json = Json { ignoreUnknownKeys = true }

    /** Synced lyrics for a track, or null when nothing usable is published. */
    suspend fun lyrics(title: String, artist: String, durationMs: Long): List<LyricLine>? =
        withContext(Dispatchers.IO) {
            val cleanTitle = title.clean()
            val cleanArtist = artist.clean()
            val seconds = (durationMs / 1000).toInt()

            val exact = runCatching { exactMatch(cleanTitle, cleanArtist, seconds) }.getOrNull()
            val synced = exact ?: runCatching { bestSearchHit(cleanTitle, cleanArtist, seconds) }
                .getOrNull()
            synced?.let(::parseLrc)?.takeIf { it.isNotEmpty() }
        }

    private fun exactMatch(title: String, artist: String, seconds: Int): String? {
        val url = "$BASE/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("duration", seconds.toString())
            .build()
        val body = get(url.toString()) ?: return null
        return (json.parseToJsonElement(body) as? JsonObject)
            ?.get("syncedLyrics")?.jsonPrimitive?.contentOrNull
    }

    /**
     * Fuzzy fallback. Prefers whichever hit is closest in length to what we're
     * actually playing — same song, different edit, would drift otherwise.
     */
    private fun bestSearchHit(title: String, artist: String, seconds: Int): String? {
        val url = "$BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .build()
        val body = get(url.toString()) ?: return null
        val hits = json.parseToJsonElement(body) as? JsonArray ?: return null
        return hits.mapNotNull { it as? JsonObject }
            .filter { it["syncedLyrics"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true }
            .minByOrNull {
                val d = it["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                abs(d - seconds)
            }
            ?.get("syncedLyrics")?.jsonPrimitive?.contentOrNull
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", AGENT).build()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    /**
     * `[mm:ss.xx] words`, plus the Apple Music voice tags that LRC converters
     * (SpotiFLAC / Paxsenix) write:
     *
     *  - `v1:` / `v2:` prefixes name the voice singing the line. The prefix is
     *    stripped and the voices are laid out on opposite sides of the panel
     *    with [lineAlignments], the same walk the TTML parser uses.
     *  - `[bg: <mm:ss.xx>word…]` lines are the answering vocal. They carry no
     *    line stamp of their own, so they used to fall out of the parse; now
     *    they keep their word timings and hang under the lead they start
     *    beneath as [LyricLine.background], drawn on their own clock.
     *
     * A stamp with no words still marks an instrumental break; a trailing
     * stamp still closes the final line.
     */
    fun parseLrc(lrc: String): List<LyricLine> {
        val leads = ArrayList<Lead>()
        val backs = ArrayList<LyricLine>()

        for (raw in lrc.lineSequence()) {
            val line = raw.trim()

            // Answering vocal: "[bg: <00:33.831>Wi<00:34.255>thout<00:36.601>]"
            val bg = BG_LINE.matchEntire(line)
            if (bg != null) {
                val body = bg.groupValues[1]
                val start = WORD_STAMP.find(body)?.let { msOf(it) } ?: continue
                val text = body.replace(WORD_STAMP, "").trim()
                if (text.isEmpty()) continue
                backs += LyricLine(start, text, parseWordRuns(body))
                continue
            }

            val match = STAMP.find(line) ?: continue
            val (minutes, seconds, fraction) = match.destructured
            // Two digits mean centiseconds, three mean milliseconds.
            val fractionMs = when (fraction.length) {
                2 -> fraction.toLong() * 10
                3 -> fraction.toLong()
                else -> 0L
            }
            var body = line.substring(match.range.last + 1)
            // Detect the RTL marker that was written by [toEnhancedLrc].
            val (marked, alignment) = stripAlignmentMarker(body)
            body = marked
            // Strip the voice tag ("v1: ") and remember who sings the line.
            val voice = VOICE_PREFIX.find(body)
            val singer = voice?.let { "v" + it.groupValues[1] }
            if (voice != null) body = body.removeRange(voice.range)

            leads += Lead(
                timeMs = minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs,
                // Stripped rather than rebuilt from the runs below: the spacing
                // and punctuation between two words belong to the line, and
                // re-joining the words with single spaces would quietly rewrite
                // a line that never had them.
                text = body.replace(WORD_STAMP, "").trim(),
                words = parseWordRuns(body),
                alignment = alignment,
                singer = singer,
            )
        }
        leads.sortBy { it.timeMs }
        backs.sortBy { it.timeMs }

        // Duet layout: the same alternation the TTML parser uses, so a v1/v2
        // file reads as two people rather than one long verse.
        val sides = lineAlignments(leads.map { it.singer }, emptyMap())
        val all = leads.mapIndexed { index, lead ->
            LyricLine(
                timeMs = lead.timeMs,
                text = lead.text,
                words = lead.words,
                alignment = if (lead.singer != null) sides[index] else lead.alignment,
            )
        }.toMutableList()

        // Hang each answering vocal under the lead it starts beneath.
        for (back in backs) {
            val at = all.indexOfLast { it.timeMs <= back.timeMs && !it.isGap }
            if (at >= 0) {
                val lead = all[at]
                if (lead.background == null) all[at] = lead.copy(background = back)
            } else {
                all.add(back.copy(alignment = LyricAlignment.End))
            }
        }
        all.sortBy { it.timeMs }

        val kept = all.filterIndexed { index, line ->
            if (!line.isGap) return@filterIndexed true
            // A trailing stamp closes off the last line — that's the outro.
            val next = all.getOrNull(index + 1) ?: return@filterIndexed true
            next.timeMs - line.timeMs >= MIN_GAP_MS
        }

        // Nothing stands for the intro — LRC files start at the first sung
        // word — so give the run-up its own break when it's long enough.
        val first = kept.firstOrNull() ?: return kept
        return if (!first.isGap && first.timeMs >= MIN_GAP_MS) {
            listOf(LyricLine(0L, "")) + kept
        } else {
            kept
        }
    }

    private class Lead(
        val timeMs: Long,
        val text: String,
        val words: List<LyricWord>,
        val alignment: LyricAlignment,
        val singer: String?,
    )

    /**
     * Strip the alignment marker written by [toEnhancedLrc] and return the
     * clean body along with the recovered alignment.
     *
     * The marker is `<R>` — a word-stamp-looking token that never appears in
     * genuine A2 output (word stamps use `<mm:ss.xx>`, not bare `<R>`). It sits
     * at the very start of the body, before the first real stamp.
     */
    private fun stripAlignmentMarker(body: String): Pair<String, LyricAlignment> {
        if (body.startsWith(ALIGNMENT_MARKER)) {
            return body.substringAfter(ALIGNMENT_MARKER) to LyricAlignment.End
        }
        return body to LyricAlignment.Start
    }

    private const val ALIGNMENT_MARKER = "<R>"

    /** YouTube Music titles are noisy — "(From "Raees")", "| Official Video",
     * "(Lyrical)" — and LRCLIB matches on the plain song name. */
    private fun String.clean(): String = this
        .replace(NOISE, " ")
        .substringBefore(" | ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { this }

    /**
     * The `<mm:ss.xx>` runs of an "enhanced" A2 line, as words.
     *
     * Each run ends where the next one starts, which is why a line written by
     * [toEnhancedLrc] closes with a bare stamp: that last one names no word, it
     * just states where the previous one stopped. A run with no text is
     * therefore a terminator rather than a word, here and in the files other
     * A2 writers produce.
     *
     * Empty for a plain line, which is what keeps [LyricLine.isWordSynced]
     * honest — a line-synced source stays line-synced through this.
     */
    private fun parseWordRuns(body: String): List<LyricWord> {
        val marks = WORD_STAMP.findAll(body).toList()
        if (marks.isEmpty()) return emptyList()
        val runs = marks.mapIndexed { index, mark ->
            val until = marks.getOrNull(index + 1)?.range?.first ?: body.length
            msOf(mark) to body.substring(mark.range.last + 1, until)
        }
        // The next run's stamp is this one's end — including when that run is
        // the closing terminator, which is the only thing that gives the last
        // word of a line an end at all. A run with no space after it is one
        // syllable of a word that carries on, which is how [toEnhancedLrc]
        // writes a word whose syllables were timed apart.
        return wordsFromRuns(
            runs.mapIndexed { index, (startMs, text) ->
                TimedRun(startMs, runs.getOrNull(index + 1)?.first ?: startMs, text)
            },
        )
    }

    private fun msOf(mark: MatchResult): Long {
        val (minutes, seconds, fraction) = mark.destructured
        val fractionMs = when (fraction.length) {
            2 -> fraction.toLong() * 10
            3 -> fraction.toLong()
            else -> 0L
        }
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs
    }

    private val STAMP = Regex("""\[(\d{1,2}):(\d{2})[.:](\d{2,3})\]""")
    private val WORD_STAMP = Regex("""<(\d{1,3}):(\d{2})[.:](\d{2,3})>""")
    private val BG_LINE = Regex("""^\[bg\s*:\s*(.*?)\]\s*$""", RegexOption.IGNORE_CASE)
    private val VOICE_PREFIX = Regex("""^v(\d+)\s*:\s*""", RegexOption.IGNORE_CASE)
    private val NOISE = Regex(
        """\((?:from|feat\.?|official|lyrical|video|audio|remix)[^)]*\)|\[[^\]]*\]|""" +
            """\b(?:official (?:video|audio|music video)|lyrical|full song|4k video)\b",
        RegexOption.IGNORE_CASE,
    )
}
