package com.omniai.studio.git

import com.omniai.studio.data.remote.AiStreamingClient
import com.omniai.studio.data.remote.GitHubActionJobsResponse
import com.omniai.studio.security.VaultManager
import com.omniai.studio.terminal.NetworkLogInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class BuildDiagnosisResult(
    val owner: String,
    val repo: String,
    val runId: Long,
    val failedJobName: String,
    val extractedErrors: List<String>,
    val aiDiagnosisMarkdown: String,
    val suggestedFixFiles: List<String>,
    val providerTag: String
)

/**
 * v5.0 AI Build Doctor:
 * Downloads failed GitHub Actions job logs via pure OkHttp3, isolates Kotlin/Gradle/AAPT compiler
 * diagnostics, and requests a targeted root-cause + patch plan from the active AI provider chain.
 */
class AiBuildDoctor(
    private val vault: VaultManager,
    private val aiClient: AiStreamingClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor(NetworkLogInterceptor)
        .build()

    suspend fun diagnoseFailedRun(
        owner: String,
        repo: String,
        runId: Long
    ): Result<BuildDiagnosisResult> = withContext(Dispatchers.IO) {
        runCatching {
            val token = vault.githubToken.trim()
            require(token.isNotBlank()) { "Configure your GitHub Personal Access Token in API Vault first." }
            val cleanOwner = owner.trim()
            val cleanRepo = repo.trim()
            require(cleanOwner.isNotBlank() && cleanRepo.isNotBlank()) { "GitHub owner and repo are required." }

            val jobsReq = Request.Builder()
                .url("$API_BASE/repos/$cleanOwner/$cleanRepo/actions/runs/$runId/jobs")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .get()
                .build()

            val jobsResp = client.newCall(jobsReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException("Failed to fetch Actions jobs (HTTP ${resp.code}): ${raw.take(200)}")
                }
                json.decodeFromString<GitHubActionJobsResponse>(raw)
            }

            val failedJob = jobsResp.jobs.firstOrNull {
                it.conclusion.equals("failure", ignoreCase = true) ||
                    it.conclusion.equals("cancelled", ignoreCase = true)
            } ?: jobsResp.jobs.firstOrNull()
                ?: throw IOException("No jobs found for workflow run #$runId.")

            val failedStepsText = failedJob.steps
                .filter { it.conclusion.equals("failure", ignoreCase = true) }
                .joinToString("\n") { "Step #${it.number} failed: ${it.name}" }

            val rawLogText = fetchJobLogTail(cleanOwner, cleanRepo, failedJob.id, token)
            val extractedErrors = extractCompilerErrors(rawLogText, failedStepsText)
            val suggestedFiles = extractMentionedFilePaths(extractedErrors)

            val systemPrompt = """
                You are OmniAI Build Doctor, a senior Android/Gradle/Kotlin CI engineer.
                Analyze the GitHub Actions failure snippet and respond concisely with:
                1. Root Cause (1-2 sentences)
                2. Affected Files
                3. Exact Fix (short Kotlin/XML/Gradle patch instructions)
                Keep the entire response under 240 words.
            """.trimIndent()

            val userPrompt = buildString {
                appendLine("Repository: $cleanOwner/$cleanRepo | Workflow Run #$runId | Job: ${failedJob.name}")
                if (failedStepsText.isNotBlank()) {
                    appendLine("Failed Steps:")
                    appendLine(failedStepsText)
                }
                appendLine("Extracted Build Error Diagnostics:")
                extractedErrors.forEach { appendLine(it) }
            }

            val (diagnosis, provider) = runCatching {
                aiClient.completeSinglePrompt(systemPrompt, userPrompt)
            }.getOrElse {
                val offlineAdvice = buildString {
                    appendLine("AI provider unavailable — offline diagnostic summary:")
                    appendLine("Job '${failedJob.name}' failed.")
                    if (extractedErrors.isNotEmpty()) {
                        appendLine("Top error lines:")
                        extractedErrors.take(8).forEach { line -> appendLine("- $line") }
                    }
                    if (suggestedFiles.isNotEmpty()) {
                        appendLine("Inspect files: ${suggestedFiles.joinToString(", ")}")
                    }
                }.trim()
                offlineAdvice to "Offline Heuristic"
            }

            BuildDiagnosisResult(
                owner = cleanOwner,
                repo = cleanRepo,
                runId = runId,
                failedJobName = failedJob.name,
                extractedErrors = extractedErrors,
                aiDiagnosisMarkdown = diagnosis.ifBlank { "Unable to generate diagnosis for run #$runId." },
                suggestedFixFiles = suggestedFiles,
                providerTag = provider.ifBlank { "OmniAI" }
            )
        }
    }

    private fun fetchJobLogTail(owner: String, repo: String, jobId: Long, token: String): String {
        val logReq = Request.Builder()
            .url("$API_BASE/repos/$owner/$repo/actions/jobs/$jobId/logs")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .get()
            .build()

        return runCatching {
            client.newCall(logReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use ""
                val text = resp.body?.string().orEmpty()
                if (text.length > MAX_LOG_TAIL_CHARS) text.takeLast(MAX_LOG_TAIL_CHARS) else text
            }
        }.getOrDefault("")
    }

    fun extractCompilerErrors(rawLog: String, fallbackSteps: String = ""): List<String> {
        val matches = mutableListOf<String>()
        val errorMatchers = listOf(
            "e: file://",
            "e: ",
            "FAILURE:",
            "Execution failed for task",
            "Unresolved reference",
            "Type mismatch",
            "AAPT:",
            "ERROR:",
            "error:",
            "Could not resolve",
            "Manifest merger failed",
            "Compilation error"
        )

        val lines = rawLog.lineSequence().toList()
        lines.forEachIndexed { idx, rawLine ->
            val cleaned = rawLine.replace(TIMESTAMP_PREFIX_REGEX, "").trim()
            if (cleaned.isBlank()) return@forEachIndexed
            if (errorMatchers.any { cleaned.contains(it) }) {
                matches += cleaned.take(260)
                val nextLine = lines.getOrNull(idx + 1)?.replace(TIMESTAMP_PREFIX_REGEX, "")?.trim().orEmpty()
                if (nextLine.isNotBlank() && !nextLine.startsWith("> Task") && matches.size < MAX_ERROR_LINES) {
                    matches += "  $nextLine".take(260)
                }
            }
        }

        val unique = matches.distinct().take(MAX_ERROR_LINES)
        if (unique.isNotEmpty()) return unique

        val tailLines = lines.takeLast(12)
            .map { it.replace(TIMESTAMP_PREFIX_REGEX, "").trim() }
            .filter { it.isNotBlank() }
        if (tailLines.isNotEmpty()) return tailLines

        return if (fallbackSteps.isNotBlank()) fallbackSteps.lines() else listOf("No raw job log lines available.")
    }

    private fun extractMentionedFilePaths(errorLines: List<String>): List<String> {
        val found = linkedSetOf<String>()
        val fileRegex = Regex("""([A-Za-z0-9_./\-]+\.(?:kt|java|xml|kts|gradle|yml|yaml|json))""")
        errorLines.forEach { line ->
            fileRegex.findAll(line).forEach { match ->
                val path = match.groupValues[1]
                    .substringAfter("/app/src/", missingDelimiterValue = match.groupValues[1])
                    .trimStart('/')
                if (path.isNotBlank()) {
                    found += path
                }
            }
        }
        return found.take(10).toList()
    }

    companion object {
        private const val API_BASE = "https://api.github.com"
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val MAX_LOG_TAIL_CHARS = 65_536
        private const val MAX_ERROR_LINES = 28
        private val TIMESTAMP_PREFIX_REGEX = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z\s*""")
    }
}
