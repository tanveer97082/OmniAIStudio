package com.omniai.studio.data.remote

import com.omniai.studio.data.local.RssFeedEntity
import com.omniai.studio.security.NetworkSecurityValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.util.concurrent.TimeUnit

data class TrendFeedItem(
    val title: String,
    val summary: String,
    val source: String,
    val url: String
)

data class TrendFetchResult(
    val items: List<TrendFeedItem>,
    val sourceErrors: List<String>
)

object TrendDiscoveryClient {

    private val supportedRegions = setOf("IN", "US", "GB", "CA", "AU")
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(
        nicheName: String,
        focus: String,
        region: String,
        savedFeeds: List<RssFeedEntity>,
        braveSearchApiKey: String
    ): TrendFetchResult = withContext(Dispatchers.IO) {
        val cleanRegion = region.trim().uppercase()
        require(cleanRegion in supportedRegions) { "Choose a supported trend region." }

        val sources = buildList {
            add("Google Trends" to "https://trends.google.com/trending/rss?geo=$cleanRegion")
            savedFeeds.asSequence()
                .filter { it.enabled }
                .take(4)
                .forEach { add(it.name to it.feedUrl) }
        }
        val collected = mutableListOf<TrendFeedItem>()
        val errors = mutableListOf<String>()

        sources.forEach { (sourceName, rawUrl) ->
            currentCoroutineContext().ensureActive()
            val validation = NetworkSecurityValidator.validateEndpointUrl(rawUrl)
            if (!validation.isValid) {
                errors += "$sourceName: ${validation.errorMessage ?: "Invalid feed URL."}"
                return@forEach
            }

            try {
                val document = Jsoup.connect(validation.normalizedUrl)
                    .parser(Parser.xmlParser())
                    .userAgent("OmniAI-Studio Android trend discovery")
                    .timeout(7_000)
                    .maxBodySize(1_000_000)
                    .get()
                val entries = document.select("item, entry").take(12)
                entries.forEach { entry ->
                    val title = entry.selectFirst("title")?.text()?.trim().orEmpty()
                    if (title.isNotBlank()) {
                        val linkElement = entry.selectFirst("link")
                        val link = linkElement?.attr("href")
                            ?.takeIf { it.isNotBlank() }
                            ?: linkElement?.text()?.trim().orEmpty()
                        val summary = (
                            entry.selectFirst("description")?.text()
                                ?: entry.selectFirst("summary")?.text()
                                ?: entry.selectFirst("content")?.text()
                                ?: ""
                            )
                            .replace(Regex("\\s+"), " ")
                            .trim()
                            .take(320)
                        collected += TrendFeedItem(title, summary, sourceName, link)
                    }
                }
                if (entries.isEmpty()) errors += "$sourceName: no RSS/Atom entries were found."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ex: Exception) {
                errors += "$sourceName: ${ex.message ?: "Feed request failed."}"
            }
        }

        if (braveSearchApiKey.isNotBlank()) {
            try {
                currentCoroutineContext().ensureActive()
                val query = listOf(nicheName, focus, "trending")
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .joinToString(" ")
                    .take(240)
                val searchUrl = "https://api.search.brave.com/res/v1/web/search"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("count", "10")
                    .addQueryParameter("country", cleanRegion)
                    .build()
                val request = Request.Builder()
                    .url(searchUrl)
                    .header("Accept", "application/json")
                    .header("X-Subscription-Token", braveSearchApiKey.trim())
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        errors += "Brave Search API: HTTP ${response.code}."
                    } else {
                        val payload = response.body?.string().orEmpty()
                        val results = json.parseToJsonElement(payload)
                            .jsonObject["web"]?.jsonObject?.get("results")?.jsonArray
                            .orEmpty()
                        results.forEach { element ->
                            val item = element.jsonObject
                            val title = item["title"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                            if (title.isNotBlank()) {
                                collected += TrendFeedItem(
                                    title = title,
                                    summary = item["description"]?.jsonPrimitive?.contentOrNull.orEmpty().take(320),
                                    source = "Brave Search",
                                    url = item["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                )
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ex: Exception) {
                errors += "Brave Search API: ${ex.message ?: "Search request failed."}"
            }
        }

        currentCoroutineContext().ensureActive()
        val uniqueItems = collected
            .distinctBy { it.title.lowercase() }
            .take(40)
        if (uniqueItems.isEmpty()) {
            throw IllegalStateException(
                errors.joinToString(separator = "\n").ifBlank { "No trend or RSS items were available." }
            )
        }
        TrendFetchResult(uniqueItems, errors)
    }
}
