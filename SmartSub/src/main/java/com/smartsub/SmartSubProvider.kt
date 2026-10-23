package com.smartsub

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.subtitles.SubtitleProvider
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import com.lagradost.nicehttp.Requests

class SmartSubProvider : SubtitleProvider {
    override val name = "SmartSub EN (Forced & Sync)"
    override val idPrefix = "smartsub_en"

    private val subdlApiBase = "https://api.subdl.com/api/v1/subtitles"
    private val apiKey = "WaVCcvF3uUpwt23O2OCcUvig4rNGAcJa"

    override suspend fun searchSubtitle(query: SubtitleSearchQuery): List<SubtitleResource> {
        val results = mutableListOf<SubtitleResource>()
        val title = query.name
        val season = query.season
        val episode = query.episode

        val url = if (season != null && episode != null) {
            "$subdlApiBase?api_key=$apiKey&film_name=$title&type=tv&season_number=$season&episode_number=$episode&languages=EN"
        } else {
            "$subdlApiBase?api_key=$apiKey&film_name=$title&type=movie&languages=EN"
        }

        val app = Requests()
        val response = app.get(url)
        if (!response.isSuccessful) return emptyList()

        val json = response.parsedSafe<SubdlResponse>() ?: return emptyList()
        val streamTag = query.title?.lowercase() ?: ""

        json.subtitles.forEach { sub ->
            val releaseName = sub.release_info.lowercase()
            val isForced = releaseName.contains("forced") || sub.comment?.lowercase()?.contains("forced") == true
            val isHearingImpaired = sub.hi == true

            var score = 0
            if (streamTag.isNotEmpty() && releaseName.contains(streamTag)) score += 10
            if (releaseName.contains("web-dl") || releaseName.contains("webrip")) score += 3
            if (releaseName.contains("amzn") || releaseName.contains("netflix") || releaseName.contains("hbo")) score += 2

            val prefix = when {
                isForced -> "🔥 [FORCED - Foreign Only] "
                isHearingImpaired -> "[SDH] "
                else -> "[FULL] "
            }

            results.add(
                SubtitleResource(
                    id = sub.url,
                    lang = "en",
                    name = "$prefix${sub.release_info} (Puan: $score)",
                    url = "https://dl.subdl.com${sub.url}"
                )
            )
        }

        return results.sortedByDescending { it.name.startsWith("🔥") }
    }
}

data class SubdlResponse(
    val status: Boolean,
    val subtitles: List<SubdlItem> = emptyList()
)

data class SubdlItem(
    val release_info: String,
    val url: String,
    val hi: Boolean?,
    val comment: String?
)
