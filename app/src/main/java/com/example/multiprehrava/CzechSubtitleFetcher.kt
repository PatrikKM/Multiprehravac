package com.example.multiprehrava

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SubtitleLine(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

object CzechSubtitleFetcher {

    private const val TAG = "CzechSubtitleFetcher"
    private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

    suspend fun fetchCzechAiSubtitles(urlOrVideoId: String, targetLang: String = "cs"): List<SubtitleLine> = withContext(Dispatchers.IO) {
        val videoId = MediaHelper.getVideoId(urlOrVideoId) ?: urlOrVideoId
        if (videoId.isBlank()) return@withContext emptyList()

        val candidateUrls = if (targetLang == "en") {
            listOf(
                "https://www.youtube.com/api/timedtext?v=$videoId&lang=en&fmt=json3",
                "https://www.youtube.com/api/timedtext?v=$videoId&kind=asr&lang=en&fmt=json3",
                "https://www.youtube.com/api/timedtext?v=$videoId&kind=asr&lang=es&tlang=en&fmt=json3",
                "https://www.youtube.com/api/timedtext?v=$videoId&kind=asr&lang=fr&tlang=en&fmt=json3"
            )
        } else {
            listOf(
                "https://www.youtube.com/api/timedtext?v=$videoId&lang=cs&fmt=json3",
                "https://www.youtube.com/api/timedtext?v=$videoId&kind=asr&lang=en&tlang=cs&fmt=json3",
                "https://www.youtube.com/api/timedtext?v=$videoId&kind=asr&lang=fr&tlang=cs&fmt=json3",
                "https://www.youtube.com/api/timedtext?v=$videoId&kind=asr&lang=de&tlang=cs&fmt=json3",
                "https://www.youtube.com/api/timedtext?v=$videoId&kind=asr&lang=es&tlang=cs&fmt=json3"
            )
        }

        for (urlStr in candidateUrls) {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
                    connectTimeout = 4000
                    readTimeout = 4000
                }

                if (conn.responseCode == 200) {
                    val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                    if (jsonText.isNotBlank() && jsonText.contains("events")) {
                        val parsed = parseJson3Subtitles(jsonText)
                        if (parsed.isNotEmpty()) {
                            Log.d(TAG, "Successfully loaded ${parsed.size} subtitle lines from $urlStr")
                            return@withContext parsed
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error fetching subtitle candidate $urlStr", e)
            }
        }

        return@withContext emptyList()
    }

    private fun parseJson3Subtitles(jsonText: String): List<SubtitleLine> {
        val list = mutableListOf<SubtitleLine>()
        try {
            val root = JSONObject(jsonText)
            val events = root.optJSONArray("events") ?: return emptyList()

            for (i in 0 until events.length()) {
                val event = events.optJSONObject(i) ?: continue
                val startMs = event.optLong("tStartMs", -1)
                val durationMs = event.optLong("dDurationMs", 0)

                if (startMs < 0) continue

                val segs = event.optJSONArray("segs") ?: continue
                val sb = StringBuilder()

                for (j in 0 until segs.length()) {
                    val seg = segs.optJSONObject(j) ?: continue
                    val utf8Text = seg.optString("utf8", "")
                    sb.append(utf8Text)
                }

                val fullText = sb.toString().trim()
                    .replace("\n", " ")
                    .replace(Regex("\\s+"), " ")

                if (fullText.isNotBlank() && fullText != "\n") {
                    val endMs = startMs + durationMs
                    list.add(SubtitleLine(startMs = startMs, endMs = endMs, text = fullText))
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error parsing json3 subtitles", e)
        }
        return list
    }
}
