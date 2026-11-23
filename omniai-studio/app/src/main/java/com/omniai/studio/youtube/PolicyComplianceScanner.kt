package com.omniai.studio.youtube

enum class ComplianceSeverity(val label: String) {
    BLOCKER("BLOCKER"),
    WARNING("WARNING"),
    INFO("INFO")
}

data class ComplianceIssue(
    val code: String,
    val severity: ComplianceSeverity,
    val title: String,
    val recommendation: String
)

data class ComplianceScanReport(
    val score: Int = 100,
    val passed: Boolean = true,
    val issues: List<ComplianceIssue> = emptyList(),
    val summary: String = "Clean — ready to publish."
)

/**
 * v5.0 Pre-Upload YouTube & Meta Policy Compliance Scanner:
 * Performs instant, offline metadata & script inspection before calling YouTube Data API v3 or
 * Meta Graph API to catch title truncation, tag stuffing (>500 chars), spam/engagement-bait
 * phrasing, potential copyright music cues, and missing AI-altered content disclosure reminders.
 */
object PolicyComplianceScanner {

    private val spamBaitPatterns = listOf(
        "sub4sub",
        "sub 4 sub",
        "free subscribers",
        "guaranteed viral",
        "100% real hack",
        "click link in bio for free money",
        "dm for money"
    )

    private val copyrightAudioHints = listOf(
        "official audio",
        "full song",
        "copyrighted track",
        "spotify rip",
        "billboard top 100"
    )

    fun scan(
        title: String,
        description: String,
        tags: List<String>,
        narrationPreview: String = "",
        includesAiSyntheticVisuals: Boolean = true
    ): ComplianceScanReport {
        val issues = mutableListOf<ComplianceIssue>()
        var penalty = 0

        val cleanTitle = title.trim()
        val cleanDesc = description.trim()
        val combinedLower = "$cleanTitle\n$cleanDesc\n$narrationPreview".lowercase()

        // 1. Title presence & YouTube 100-character hard limit
        if (cleanTitle.isEmpty()) {
            issues += ComplianceIssue(
                code = "YT_TITLE_EMPTY",
                severity = ComplianceSeverity.BLOCKER,
                title = "Video title is empty",
                recommendation = "Add a clear, descriptive title (ideally 40–70 characters)."
            )
            penalty += 35
        } else if (cleanTitle.length > 100) {
            issues += ComplianceIssue(
                code = "YT_TITLE_TOO_LONG",
                severity = ComplianceSeverity.BLOCKER,
                title = "Title exceeds YouTube 100-char limit (${cleanTitle.length} chars)",
                recommendation = "Shorten title to <= 95 characters so API upload will not reject or clip it."
            )
            penalty += 25
        }

        // 2. Excessive ALL-CAPS shouting ratio
        val letters = cleanTitle.filter { it.isLetter() }
        if (letters.length >= 12) {
            val upperRatio = letters.count { it.isUpperCase() }.toFloat() / letters.length.toFloat()
            if (upperRatio > 0.72f) {
                issues += ComplianceIssue(
                    code = "YT_ALL_CAPS_TITLE",
                    severity = ComplianceSeverity.WARNING,
                    title = "High ALL-CAPS ratio (${(upperRatio * 100).toInt()}%)",
                    recommendation = "Switch to Title Case and reserve ALL CAPS for at most 1–2 focal words."
                )
                penalty += 10
            }
        }

        // 3. YouTube 500-char total tag limit & hashtag stuffing
        val cleanTags = tags.map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }
        val totalTagChars = cleanTags.sumOf { it.length + 1 }
        if (totalTagChars > 500) {
            issues += ComplianceIssue(
                code = "YT_TAG_CHAR_LIMIT",
                severity = ComplianceSeverity.BLOCKER,
                title = "Tags exceed YouTube 500-char limit ($totalTagChars chars)",
                recommendation = "Trim tags to 8–15 focused keywords under 450 total characters."
            )
            penalty += 20
        }

        val hashtagCount = Regex("""#[A-Za-z0-9_]+""").findAll(cleanDesc).count()
        if (hashtagCount > 15) {
            issues += ComplianceIssue(
                code = "YT_HASHTAG_STUFFING",
                severity = ComplianceSeverity.WARNING,
                title = "Over-hashtagging detected ($hashtagCount hashtags in description)",
                recommendation = "YouTube ignores all hashtags if a video has more than 15; keep 3–6 relevant tags."
            )
            penalty += 15
        }

        // 4. Spam / Engagement-bait policy phrases
        val matchedSpam = spamBaitPatterns.filter { combinedLower.contains(it) }
        if (matchedSpam.isNotEmpty()) {
            issues += ComplianceIssue(
                code = "POLICY_SPAM_BAIT",
                severity = ComplianceSeverity.BLOCKER,
                title = "Spam / engagement-bait phrase detected (${matchedSpam.first()})",
                recommendation = "Remove prohibited spam wording to avoid YouTube/Meta takedown or reach suppression."
            )
            penalty += 30
        }

        // 5. Copyrighted audio risk hints
        val matchedAudio = copyrightAudioHints.filter { combinedLower.contains(it) }
        if (matchedAudio.isNotEmpty()) {
            issues += ComplianceIssue(
                code = "COPYRIGHT_AUDIO_RISK",
                severity = ComplianceSeverity.WARNING,
                title = "Potential copyrighted music reference (${matchedAudio.first()})",
                recommendation = "Verify you own commercial rights or swap to royalty-free / YouTube Audio Library BGM."
            )
            penalty += 15
        }

        // 6. YouTube Synthetic / Altered Media disclosure reminder
        if (includesAiSyntheticVisuals &&
            !combinedLower.contains("ai-generated") &&
            !combinedLower.contains("altered") &&
            !combinedLower.contains("synthetic")
        ) {
            issues += ComplianceIssue(
                code = "AI_DISCLOSURE_REMINDER",
                severity = ComplianceSeverity.INFO,
                title = "AI synthetic visuals disclosure reminder",
                recommendation = "Enable 'Altered or synthetic content' in YouTube Studio if realistic scenes/voices are AI-generated."
            )
            penalty += 5
        }

        val finalScore = (100 - penalty).coerceIn(0, 100)
        val hasBlocker = issues.any { it.severity == ComplianceSeverity.BLOCKER }
        val summary = when {
            hasBlocker -> "Action required: ${issues.count { it.severity == ComplianceSeverity.BLOCKER }} blocker(s) found (Score $finalScore/100)."
            issues.isNotEmpty() -> "Ready with ${issues.size} advisory note(s) (Score $finalScore/100)."
            else -> "Clean — passed all offline policy & metadata checks (100/100)."
        }

        return ComplianceScanReport(
            score = finalScore,
            passed = !hasBlocker,
            issues = issues,
            summary = summary
        )
    }
}
