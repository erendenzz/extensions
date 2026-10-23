package recloudstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink

class SmartSubProvider : MainAPI() {
    override var name = "SmartSub EN (Forced & Sync)"
    override var mainUrl = "https://subdl.com"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "en"
    override val hasMainPage = false

    private val subdlApiBase = "https://api.subdl.com/api/v1/subtitles"
    private val apiKey = "WaVCcvF3uUpwt23O2OCcUvig4rNGAcJa"

    override suspend fun search(query: String): List<SearchResponse> {
        val cleanQuery = query.trim()
        val seasonEpRegex = Regex("""(?i)^(.*?)(?:[ ._-]+[sS](\d{1,2})[ ._-]*[eE](\d{1,2}))?""")
        val match = seasonEpRegex.find(cleanQuery)
        val title = match?.groupValues?.getOrNull(1)?.ifBlank { cleanQuery } ?: cleanQuery

        val url = "$subdlApiBase?api_key=$apiKey&film_name=$title&languages=EN"
        val response = app.get(url)
        if (!response.isSuccessful) return emptyList()

        val json = response.parsedSafe<SubdlResponse>() ?: return emptyList()
        if (json.subtitles.isEmpty()) return emptyList()

        return listOf(
            newMovieSearchResponse(
                name = cleanQuery,
                url = cleanQuery,
                type = TvType.Movie
            )
        )
    }

    override suspend fun load(url: String): LoadResponse {
        val query = url.removePrefix(mainUrl).removePrefix("/").ifBlank { url }
        return newMovieLoadResponse(
            name = query,
            url = url,
            type = TvType.Movie,
            dataUrl = query
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val seasonEpRegex = Regex("""(?i)^(.*?)[ ._-]+[sS](\d{1,2})[ ._-]*[eE](\d{1,2})""")
        val match = seasonEpRegex.find(data)

        val url = if (match != null) {
            val title = match.groupValues[1].trim()
            val season = match.groupValues[2].toIntOrNull()
            val episode = match.groupValues[3].toIntOrNull()
            "$subdlApiBase?api_key=$apiKey&film_name=$title&type=tv&season_number=$season&episode_number=$episode&languages=EN"
        } else {
            "$subdlApiBase?api_key=$apiKey&film_name=$data&type=movie&languages=EN"
        }

        val response = app.get(url)
        if (!response.isSuccessful) return false

        val json = response.parsedSafe<SubdlResponse>() ?: return false
        val streamTag = data.lowercase()

        val sortedSubtitles = json.subtitles.map { sub ->
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

            Triple(sub, "$prefix${sub.release_info} (Puan: $score)", isForced)
        }.sortedByDescending { it.third }

        sortedSubtitles.forEach { (sub, displayName, _) ->
            subtitleCallback.invoke(
                SubtitleFile(
                    lang = displayName,
                    url = "https://dl.subdl.com${sub.url}"
                )
            )
        }

        return true
    }
}

data class SubdlResponse(
    val status: Boolean = false,
    val subtitles: List<SubdlItem> = emptyList()
)

data class SubdlItem(
    @JsonProperty("release_info") val release_info: String = "",
    val url: String = "",
    val hi: Boolean? = false,
    val comment: String? = null
)
