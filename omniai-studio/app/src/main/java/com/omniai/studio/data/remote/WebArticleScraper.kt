package com.omniai.studio.data.remote

import com.omniai.studio.security.NetworkSecurityValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

data class ScrapedArticle(
    val url: String,
    val title: String,
    val cleanText: String
)

/**
 * Fetches a web URL via Jsoup, enforces HTTPS (or localhost HTTP), and strips
 * navigation, scripts, ads, and boilerplate HTML down to clean readable article text.
 */
object WebArticleScraper {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    private const val MAX_ARTICLE_CHARS = 6800

    suspend fun fetchCleanArticle(rawUrl: String): ScrapedArticle = withContext(Dispatchers.IO) {
        val candidate = if (!rawUrl.trim().startsWith("http://") && !rawUrl.trim().startsWith("https://")) {
            "https://${rawUrl.trim()}"
        } else {
            rawUrl.trim()
        }

        val validation = NetworkSecurityValidator.validateEndpointUrl(candidate)
        if (!validation.isValid) {
            throw IllegalArgumentException(validation.errorMessage ?: "Invalid URL")
        }

        val doc = Jsoup.connect(validation.normalizedUrl)
            .userAgent(USER_AGENT)
            .timeout(12_000)
            .followRedirects(true)
            .maxBodySize(2 * 1024 * 1024)
            .get()

        val title = doc.title().trim().ifBlank { validation.normalizedUrl }

        // Remove non-content boilerplate elements before text extraction
        doc.select(
            "script, style, noscript, iframe, svg, nav, footer, header, aside, form, button, " +
                "[role=navigation], [role=banner], [role=complementary], .ads, .advertisement, .cookie-banner"
        ).remove()

        val mainContainer = doc.selectFirst("article")
            ?: doc.selectFirst("main")
            ?: doc.selectFirst("[role=main]")
            ?: doc.body()

        val paragraphs = mainContainer?.select("h1, h2, h3, p, li")
            ?.map { it.text().trim() }
            ?.filter { it.length >= 25 }
            ?.distinct()
            .orEmpty()

        val combinedText = if (paragraphs.size >= 2) {
            paragraphs.joinToString("\n\n")
        } else {
            mainContainer?.text().orEmpty()
        }

        val normalized = combinedText
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .take(MAX_ARTICLE_CHARS)

        if (normalized.isBlank()) {
            throw IllegalStateException("No readable article text found at ${validation.normalizedUrl}")
        }

        ScrapedArticle(
            url = validation.normalizedUrl,
            title = title,
            cleanText = normalized
        )
    }
}
