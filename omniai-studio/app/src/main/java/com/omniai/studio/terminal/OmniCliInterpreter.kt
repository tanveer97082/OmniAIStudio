package com.omniai.studio.terminal

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import com.omniai.studio.BuildConfig
import com.omniai.studio.backup.BackupSelectionOptions
import com.omniai.studio.backup.GitHubBackupSync
import com.omniai.studio.backup.VaultBackupManager
import com.omniai.studio.cloud.CloudSyncRepository
import com.omniai.studio.cloud.SupabaseAuthManager
import com.omniai.studio.cloud.CloudUserRole
import com.omniai.studio.cloud.SupabaseSchemaScript
import com.omniai.studio.data.local.AppSettingsRepository
import com.omniai.studio.data.local.ContentDbExporter
import com.omniai.studio.data.local.OmniAiDatabase
import com.omniai.studio.data.remote.AiStreamingClient
import com.omniai.studio.data.remote.ApiKeyPoolManager
import com.omniai.studio.data.remote.HfModelExplorerClient
import com.omniai.studio.data.remote.KaggleApiClient
import com.omniai.studio.data.remote.OpenAiChatMessage
import com.omniai.studio.git.AiBuildDoctor
import com.omniai.studio.git.GitHubGitEngine
import com.omniai.studio.git.SecretLeakScanner
import com.omniai.studio.lan.LocalLanWebServer
import com.omniai.studio.niche.ContinuityEngine
import com.omniai.studio.security.DeviceHealthGuard
import com.omniai.studio.security.VaultManager
import com.omniai.studio.data.remote.YouTubeUploadRequest
import com.omniai.studio.worker.OutboxFlushWorker
import com.omniai.studio.youtube.PolicyComplianceScanner
import com.omniai.studio.youtube.YouTubeAnalyticsRepository
import com.omniai.studio.youtube.YouTubeApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class OmniCliOutcome {
    HANDLED,
    CLEAR_SCREEN
}

/**
 * Built-in OmniCLI Command Dispatcher intercepting high-value developer, studio, DB, Niche,
 * Resilience, Cloud & Git commands. Any unrecognized command falls through to LocalShellExecutor or TermuxBridge.
 */
class OmniCliInterpreter(
    private val context: Context,
    private val db: OmniAiDatabase,
    private val settingsRepo: AppSettingsRepository,
    private val contentDbExporter: ContentDbExporter,
    private val continuityEngine: ContinuityEngine,
    private val gitHubGitEngine: GitHubGitEngine,
    private val localShellExecutor: LocalShellExecutor,
    private val termuxBridge: TermuxBridge,
    private val aiClient: AiStreamingClient,
    private val kaggleClient: KaggleApiClient,
    private val hfClient: HfModelExplorerClient,
    private val vault: VaultManager,
    private val vaultBackupManager: VaultBackupManager,
    private val githubBackupSync: GitHubBackupSync,
    private val youTubeApiClient: YouTubeApiClient,
    private val youTubeAnalyticsRepository: YouTubeAnalyticsRepository,
    private val supabaseAuthManager: SupabaseAuthManager,
    private val cloudSyncRepository: CloudSyncRepository,
    private val deviceHealthGuard: DeviceHealthGuard,
    private val localLanWebServer: LocalLanWebServer,
    private val aiBuildDoctor: AiBuildDoctor,
    private val apiKeyPoolManager: ApiKeyPoolManager
) {

    private val curlClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(NetworkLogInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun dispatch(
        rawCommand: String,
        useTermuxBridge: Boolean,
        onLine: (String, TerminalLineType) -> Unit
    ): OmniCliOutcome {
        val trimmed = rawCommand.trim()
        if (trimmed.isBlank()) return OmniCliOutcome.HANDLED

        val lower = trimmed.lowercase(Locale.US)

        when {
            lower == "clear" || lower == "cls" -> {
                return OmniCliOutcome.CLEAR_SCREEN
            }
            lower == "help" || lower == "omnicli" -> {
                printHelp(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "sysinfo" -> {
                printSysInfo(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "netlog" -> {
                val enabled = NetworkLogInterceptor.toggle()
                if (enabled) {
                    onLine("NETLOG ENABLED — Streaming live OkHttp3 API traffic (Authorization, API keys & cookies masked).", TerminalLineType.SUCCESS)
                    val recent = NetworkLogInterceptor.recentLogs.value.takeLast(5)
                    if (recent.isNotEmpty()) {
                        onLine("--- Recent buffered requests (${recent.size}) ---", TerminalLineType.SYSTEM)
                        recent.forEach { entry ->
                            onLine(entry.toTerminalLine(), TerminalLineType.NETLOG)
                        }
                    }
                } else {
                    onLine("NETLOG DISABLED — Live API inspector paused.", TerminalLineType.SYSTEM)
                }
                return OmniCliOutcome.HANDLED
            }
            lower == "proj list" -> {
                runProjList(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower.startsWith("proj switch ") -> {
                val slugQuery = trimmed.substring(12).trim()
                runProjSwitch(slugQuery, onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "git status" -> {
                runGitStatus(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "git push" || lower.startsWith("git push ") -> {
                val rawMsg = if (trimmed.length > 8) trimmed.substring(8).trim().trim('"', '\'') else ""
                runGitPush(rawMsg, onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "git pull" -> {
                runGitPull(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "git actions" -> {
                runGitActions(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "niche list" -> {
                runNicheList(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower.startsWith("niche switch ") -> {
                val query = trimmed.substring(13).trim()
                runNicheSwitch(query, onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "next video" -> {
                runNextVideo(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "db stats" -> {
                runDbStats(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower.startsWith("db search ") -> {
                val query = trimmed.substring(10).trim()
                runDbSearch(query, onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "db export csv" -> {
                runDbExportCsv(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "kaggle status" -> {
                runKaggleStatus(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "backup now" -> {
                runBackupNow(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower.startsWith("ai ") -> {
                val prompt = trimmed.substring(3).trim()
                runAiPrompt(prompt, onLine)
                return OmniCliOutcome.HANDLED
            }
            lower.startsWith("hf search ") -> {
                val query = trimmed.substring(10).trim()
                runHfSearch(query, onLine)
                return OmniCliOutcome.HANDLED
            }
            lower.startsWith("curl ") -> {
                val target = trimmed.substring(5).trim()
                runCurl(target, onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "yt stats" -> {
                runYtStats(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower.startsWith("yt upload ") -> {
                val arg = trimmed.substring(10).trim()
                runYtUpload(arg, onLine)
                return OmniCliOutcome.HANDLED
            }
            lower.startsWith("yt comments ") -> {
                val arg = trimmed.substring(12).trim()
                runYtComments(arg, onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "cloud status" -> {
                runCloudStatus(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "cloud sync" -> {
                runCloudSync(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "cloud users" -> {
                runCloudUsers(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "supabase schema" -> {
                if (!BuildConfig.DEBUG && supabaseAuthManager.sessionState.value.role != CloudUserRole.OWNER) {
                    onLine("Owner Only — schema setup commands are available only to OWNER accounts.", TerminalLineType.STDERR)
                    return OmniCliOutcome.HANDLED
                }
                onLine("Run this once in Supabase Dashboard > SQL Editor. DDL cannot be run through the app's public API.", TerminalLineType.SYSTEM)
                try {
                    SupabaseSchemaScript.load(context).lines().forEach { onLine(it, TerminalLineType.STDOUT) }
                } catch (ex: Exception) {
                    onLine("Unable to load bundled Supabase schema: ${ex.message}", TerminalLineType.STDERR)
                }
                return OmniCliOutcome.HANDLED
            }
            lower == "supabase keys push" -> {
                runUserApiCredentialsPush(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "supabase keys pull" -> {
                runUserApiCredentialsPull(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "supabase status" -> {
                runCloudStatus(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "health" -> {
                runDeviceHealth(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "lan start" -> {
                val st = localLanWebServer.start()
                if (st.isRunning) {
                    onLine("LAN Web Studio LIVE -> ${st.url} | 6-Digit PIN: ${st.accessPin}", TerminalLineType.SUCCESS)
                } else {
                    onLine("LAN Web Studio failed: ${st.lastError}", TerminalLineType.STDERR)
                }
                return OmniCliOutcome.HANDLED
            }
            lower == "lan stop" -> {
                localLanWebServer.stop()
                onLine("LAN Web Studio stopped.", TerminalLineType.SYSTEM)
                return OmniCliOutcome.HANDLED
            }
            lower == "lan status" -> {
                val st = localLanWebServer.state.value
                if (st.isRunning) {
                    onLine("LAN Web Studio: RUNNING at ${st.url} | PIN=${st.accessPin} | Requests=${st.servedRequests}", TerminalLineType.SUCCESS)
                } else {
                    onLine("LAN Web Studio: STOPPED (run 'lan start' to launch)", TerminalLineType.SYSTEM)
                }
                return OmniCliOutcome.HANDLED
            }
            lower == "scan secrets" -> {
                runScanSecrets(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "doctor" -> {
                runBuildDoctor(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower == "keys pool" -> {
                runKeysPoolStatus(onLine)
                return OmniCliOutcome.HANDLED
            }
            lower.startsWith("yt compliance ") -> {
                val sampleTitle = trimmed.substring(14).trim()
                val report = PolicyComplianceScanner.scan(
                    title = sampleTitle,
                    description = "",
                    tags = emptyList()
                )
                onLine("Compliance Score: ${report.score}/100 (${if (report.passed) "PASS" else "BLOCKED"}) — ${report.summary}", if (report.passed) TerminalLineType.SUCCESS else TerminalLineType.STDERR)
                report.issues.forEach { issue ->
                    onLine("  [${issue.severity.label}] ${issue.title} -> ${issue.recommendation}", TerminalLineType.STDOUT)
                }
                return OmniCliOutcome.HANDLED
            }
            lower == "outbox flush" -> {
                OutboxFlushWorker.scheduleOnReconnect(context)
                onLine("Queued OutboxFlushWorker for immediate/on-reconnect flush.", TerminalLineType.SUCCESS)
                return OmniCliOutcome.HANDLED
            }
        }

        // Fall through to Termux Bridge or Native Local Sandbox Shell
        if (useTermuxBridge) {
            val result = termuxBridge.dispatchCommand(trimmed)
            onLine(
                result.message,
                if (result.dispatched) TerminalLineType.SUCCESS else TerminalLineType.STDERR
            )
            return OmniCliOutcome.HANDLED
        }

        val exitCode = localShellExecutor.execute(trimmed, onLine)
        if (exitCode != 0) {
            onLine("[exit $exitCode]", TerminalLineType.STDERR)
        }
        return OmniCliOutcome.HANDLED
    }

    private fun printHelp(onLine: (String, TerminalLineType) -> Unit) {
        onLine("=== OmniCLI v${BuildConfig.VERSION_NAME} Built-in Commands ===", TerminalLineType.SYSTEM)
        onLine("  help                 Show this OmniCLI command guide", TerminalLineType.STDOUT)
        onLine("  proj list            List all Projects with task progress & GitHub repo", TerminalLineType.STDOUT)
        onLine("  proj switch <slug>   Set active Project context for git commands", TerminalLineType.STDOUT)
        onLine("  git status           Scan local SHA-256 diff (NEW / MODIFIED / DELETED)", TerminalLineType.STDOUT)
        onLine("  git push \"<msg>\"     Run atomic multi-file GitHub push with live progress", TerminalLineType.STDOUT)
        onLine("  git pull             Pull latest remote branch files into active project", TerminalLineType.STDOUT)
        onLine("  git actions          Inspect latest GitHub Actions workflow run status", TerminalLineType.STDOUT)
        onLine("  niche list           List all configured Niches & active selection", TerminalLineType.STDOUT)
        onLine("  niche switch <name>  Switch active Niche context by name match", TerminalLineType.STDOUT)
        onLine("  next video           Generate/fetch top Auto-Next video command & copy to clipboard", TerminalLineType.STDOUT)
        onLine("  db stats             Live row counts for Room DB v10 tables & YT quota", TerminalLineType.STDOUT)
        onLine("  db search <query>    Full-text search across Chats, Video Scripts/Scenes & Social SEO", TerminalLineType.STDOUT)
        onLine("  db export csv        Export Scripts + YT/IG/FB SEO to RFC-4180 CSV file", TerminalLineType.STDOUT)
        onLine("  yt stats             Live YouTube subscribers, recent video views & daily API quota", TerminalLineType.STDOUT)
        onLine("  yt upload <scriptId> Resumable YouTube upload of linked MP4 + SEO metadata", TerminalLineType.STDOUT)
        onLine("  yt comments <vidId>  List latest viewer comments on a YouTube video", TerminalLineType.STDOUT)
        onLine("  cloud status         Show Google OAuth email, active RBAC role, last sync & GZIP savings", TerminalLineType.STDOUT)
        onLine("  cloud sync           Run two-way GZIP-compressed text delta sync with Supabase", TerminalLineType.STDOUT)
        onLine("  cloud users          (OWNER only) List registered accounts, devices & RBAC roles", TerminalLineType.STDOUT)
        onLine("  supabase schema      Print complete profiles, signup trigger, sync tables & strict RLS setup SQL", TerminalLineType.STDOUT)
        onLine("  supabase keys push   Save configured API credentials to your signed-in account", TerminalLineType.STDOUT)
        onLine("  supabase keys pull   Restore API credentials from your signed-in account", TerminalLineType.STDOUT)
        onLine("  supabase status      Show Supabase configuration and signed-in status", TerminalLineType.STDOUT)
        onLine("  health               Check device thermal status, battery, RAM & free storage guard", TerminalLineType.STDOUT)
        onLine("  lan start|stop|status Control zero-dep Local Wi-Fi Web Studio (6-digit PIN)", TerminalLineType.STDOUT)
        onLine("  scan secrets         Run SecretLeakScanner across active project workspace", TerminalLineType.STDOUT)
        onLine("  doctor               Run AI Build Doctor on latest failed GitHub Actions run", TerminalLineType.STDOUT)
        onLine("  keys pool            Inspect Multi-Key API Pool healthy vs cooling-down slots", TerminalLineType.STDOUT)
        onLine("  yt compliance <title> Offline pre-upload YouTube/Meta policy compliance check", TerminalLineType.STDOUT)
        onLine("  outbox flush         Schedule WorkManager outbox flush on network reconnect", TerminalLineType.STDOUT)
        onLine("  ai <prompt>          Stream an instant AI response directly inside terminal", TerminalLineType.STDOUT)
        onLine("  kaggle status        Poll active Dual-GPU Wan 2.1 Kaggle kernel status", TerminalLineType.STDOUT)
        onLine("  hf search <query>    Query Hugging Face Hub models sorted by downloads", TerminalLineType.STDOUT)
        onLine("  curl <url>           Perform OkHttp3 GET inspect (status, headers, body preview)", TerminalLineType.STDOUT)
        onLine("  backup now           Trigger encrypted local snapshot (+ GitHub push if configured)", TerminalLineType.STDOUT)
        onLine("  sysinfo              Display device CPU/ABI, RAM, storage & studio status", TerminalLineType.STDOUT)
        onLine("  netlog               Toggle live masked OkHttp3 API Network Inspector", TerminalLineType.STDOUT)
        onLine("  clear                Clear terminal output buffer", TerminalLineType.STDOUT)
        onLine("  <any shell cmd>      Runs in local sandbox (/system/bin/sh) or Termux Bridge", TerminalLineType.SYSTEM)
    }

    private suspend fun resolveActiveProject(): com.omniai.studio.data.local.ProjectEntity? {
        val activeId = settingsRepo.current.activeProjectId
        if (activeId > 0L) {
            db.projectDao().getProjectById(activeId)?.let { return it }
        }
        return db.projectDao().getAllProjectsOnce().firstOrNull()?.also { first ->
            settingsRepo.update { it.copy(activeProjectId = first.id) }
        }
    }

    private suspend fun runProjList(onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val projects = db.projectDao().getAllProjectsOnce()
            val allTasks = db.projectDao().getAllTasksOnce().groupBy { it.projectId }
            val activeId = settingsRepo.current.activeProjectId
            onLine("=== OmniAI Projects (${projects.size}) ===", TerminalLineType.SYSTEM)
            if (projects.isEmpty()) {
                onLine("  (no projects yet — create one in the Projects tab)", TerminalLineType.STDOUT)
            } else {
                projects.forEach { p ->
                    val tasks = allTasks[p.id].orEmpty()
                    val done = tasks.count { it.columnStatus == "DONE" }
                    val repoTag = if (p.githubOwner.isNotBlank() && p.githubRepo.isNotBlank()) {
                        "${p.githubOwner}/${p.githubRepo}@${p.githubBranch}"
                    } else "local-only"
                    val marker = if (p.id == activeId) " [ACTIVE]" else ""
                    onLine(
                        "  #${p.id} ${p.slug}$marker — ${p.name} [${p.status}] | Tasks $done/${tasks.size} | $repoTag",
                        if (p.id == activeId) TerminalLineType.SUCCESS else TerminalLineType.STDOUT
                    )
                }
            }
        } catch (ex: Exception) {
            onLine("proj list error: ${ex.message}", TerminalLineType.STDERR)
        }
    }

    private suspend fun runProjSwitch(query: String, onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            onLine("Usage: proj switch <slug>", TerminalLineType.STDERR)
            return@withContext
        }
        try {
            val matched = db.projectDao().findProjectBySlugOrName(query)
            if (matched == null) {
                onLine("No project matched '$query'. Run 'proj list' to see projects.", TerminalLineType.STDERR)
                return@withContext
            }
            settingsRepo.update { it.copy(activeProjectId = matched.id) }
            onLine(
                "Active Project -> ${matched.name} (${matched.slug}) | Repo: ${matched.githubOwner}/${matched.githubRepo}@${matched.githubBranch}",
                TerminalLineType.SUCCESS
            )
        } catch (ex: Exception) {
            onLine("proj switch error: ${ex.message}", TerminalLineType.STDERR)
        }
    }

    private suspend fun runGitStatus(onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val project = resolveActiveProject()
            if (project == null) {
                onLine("No active project found. Create a project first in the Projects tab.", TerminalLineType.STDERR)
                return@withContext
            }
            val diff = gitHubGitEngine.scanLocalChanges(project)
            onLine("=== git status (${project.slug} · ${project.githubOwner}/${project.githubRepo}@${project.githubBranch}) ===", TerminalLineType.SYSTEM)
            if (!diff.hasChanges) {
                onLine("  Working tree clean (${diff.synced.size} synced file(s)).", TerminalLineType.SUCCESS)
            } else {
                diff.added.forEach { onLine("  [NEW]      $it", TerminalLineType.SUCCESS) }
                diff.modified.forEach { onLine("  [MODIFIED] $it", TerminalLineType.AI_STREAM) }
                diff.deleted.forEach { onLine("  [DELETED]  $it", TerminalLineType.STDERR) }
                onLine("Summary: ${diff.added.size} new, ${diff.modified.size} modified, ${diff.deleted.size} deleted, ${diff.synced.size} synced.", TerminalLineType.SYSTEM)
            }
        } catch (ex: Exception) {
            onLine("git status error: ${ex.message}", TerminalLineType.STDERR)
        }
    }

    private suspend fun runGitPush(rawMessage: String, onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val project = resolveActiveProject()
            if (project == null) {
                onLine("No active project selected. Run 'proj switch <slug>' first.", TerminalLineType.STDERR)
                return@withContext
            }
            val diff = gitHubGitEngine.scanLocalChanges(project)
            if (!diff.hasChanges) {
                onLine("Nothing to push — working tree clean for '${project.slug}'.", TerminalLineType.SYSTEM)
                return@withContext
            }
            val commitMsg = if (rawMessage.isNotBlank()) {
                rawMessage
            } else if (settingsRepo.current.aiAutoCommitMessageEnabled) {
                onLine("Generating AI Conventional Commit message...", TerminalLineType.SYSTEM)
                aiClient.generateGitCommitMessage(project.name, diff)
            } else {
                "chore(${project.slug}): sync ${diff.totalPending} file(s)"
            }
            onLine("Commit Message: $commitMsg", TerminalLineType.AI_STREAM)
            val result = gitHubGitEngine.pushProjectAtomically(project, commitMsg) { progressLine ->
                onLine("  $progressLine", TerminalLineType.STDOUT)
            }
            onLine("ATOMIC PUSH SUCCESS: ${result.summary}", TerminalLineType.SUCCESS)
        } catch (ex: Exception) {
            onLine("git push failed: ${ex.message}", TerminalLineType.STDERR)
        }
    }

    private suspend fun runGitPull(onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val project = resolveActiveProject()
            if (project == null) {
                onLine("No active project selected. Run 'proj switch <slug>' first.", TerminalLineType.STDERR)
                return@withContext
            }
            val result = gitHubGitEngine.pullProjectFromGitHub(project) { line ->
                onLine("  $line", TerminalLineType.STDOUT)
            }
            onLine(
                "GIT PULL COMPLETE: ${result.filesWritten} file(s) synced from ${result.branch} (tree ${result.treeSha.take(7)})",
                TerminalLineType.SUCCESS
            )
        } catch (ex: Exception) {
            onLine("git pull failed: ${ex.message}", TerminalLineType.STDERR)
        }
    }

    private suspend fun runGitActions(onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val project = resolveActiveProject()
            if (project == null) {
                onLine("No active project selected. Run 'proj switch <slug>' first.", TerminalLineType.STDERR)
                return@withContext
            }
            if (project.githubOwner.isBlank() || project.githubRepo.isBlank()) {
                onLine("Project '${project.slug}' has no GitHub owner/repo configured.", TerminalLineType.STDERR)
                return@withContext
            }
            onLine("Querying latest GitHub Actions run for ${project.githubOwner}/${project.githubRepo}...", TerminalLineType.SYSTEM)
            val run = gitHubGitEngine.fetchLatestWorkflowRun(project.githubOwner, project.githubRepo)
            if (run == null) {
                onLine("No GitHub Actions workflow runs found yet.", TerminalLineType.SYSTEM)
                return@withContext
            }
            val statusColor = when {
                run.conclusion.equals("success", ignoreCase = true) -> TerminalLineType.SUCCESS
                run.conclusion.equals("failure", ignoreCase = true) -> TerminalLineType.STDERR
                else -> TerminalLineType.AI_STREAM
            }
            onLine("Workflow   : ${run.name} (#${run.runNumber})", TerminalLineType.STDOUT)
            onLine("Status     : ${run.status.uppercase()} | Conclusion: ${run.conclusion?.uppercase() ?: "PENDING"}", statusColor)
            onLine("Branch/SHA : ${run.headBranch} @ ${run.headSha.take(7)}", TerminalLineType.STDOUT)
            onLine("Run URL    : ${run.htmlUrl}", TerminalLineType.SYSTEM)
            if (run.conclusion.equals("failure", ignoreCase = true)) {
                val failSummary = gitHubGitEngine.fetchFailedJobsSummary(project.githubOwner, project.githubRepo, run.id)
                onLine("Failure    : $failSummary", TerminalLineType.STDERR)
            }
        } catch (ex: Exception) {
            onLine("git actions error: ${ex.message}", TerminalLineType.STDERR)
        }
    }

    private suspend fun runNicheList(onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val niches = db.nicheDao().getAllNichesOnce()
            val activeId = settingsRepo.current.activeNicheId
            onLine("=== Configured Creator Niches (${niches.size}) ===", TerminalLineType.SYSTEM)
            if (niches.isEmpty()) {
                onLine("  (no niches seeded yet)", TerminalLineType.STDOUT)
            } else {
                niches.forEach { n ->
                    val marker = if (n.id == activeId) " [ACTIVE]" else ""
                    val autoTag = if (n.autoNextEnabled) " [AUTO-NEXT]" else ""
                    onLine(
                        "  #${n.id} ${n.emoji} ${n.name}$marker$autoTag — ${n.visualStylePreset} · ${n.defaultAspectRatio} · Ep:${n.episodeCount}",
                        if (n.id == activeId) TerminalLineType.SUCCESS else TerminalLineType.STDOUT
                    )
                }
            }
        } catch (ex: Exception) {
            onLine("niche list error: ${ex.message}", TerminalLineType.STDERR)
        }
    }

    private suspend fun runNicheSwitch(query: String, onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            onLine("Usage: niche switch <name>", TerminalLineType.STDERR)
            return@withContext
        }
        try {
            val matched = db.nicheDao().findNicheByName(query)
            if (matched == null) {
                onLine("No niche matched '$query'. Run 'niche list' to see available niches.", TerminalLineType.STDERR)
                return@withContext
            }
            settingsRepo.update { it.copy(activeNicheId = matched.id, filterAllNiches = false) }
            onLine(
                "Switched active Niche -> ${matched.emoji} ${matched.name} (#${matched.id}) | Style=${matched.visualStylePreset} | Voice=${matched.defaultVoiceId} | Ratio=${matched.defaultAspectRatio}",
                TerminalLineType.SUCCESS
            )
        } catch (ex: Exception) {
            onLine("niche switch error: ${ex.message}", TerminalLineType.STDERR)
        }
    }

    private suspend fun runNextVideo(onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val activeNicheId = settingsRepo.current.activeNicheId
            val niche = db.nicheDao().getNicheById(activeNicheId)
            val nicheLabel = if (niche != null) "${niche.emoji} ${niche.name}" else "Niche #$activeNicheId"
            var cmds = db.nextCommandDao().getUnusedCommandsForNicheOnce(activeNicheId)
            if (cmds.isEmpty()) {
                onLine("No cached Next Commands for $nicheLabel — generating fresh Auto-Next suggestions...", TerminalLineType.SYSTEM)
                val latestScript = db.videoScriptDao().getRecentScriptsForNicheOnce(activeNicheId, 1).firstOrNull()
                cmds = continuityEngine.generatePostVideoNextCommands(
                    nicheId = activeNicheId,
                    sourceScriptId = latestScript?.script?.id,
                    lastVideoTitle = latestScript?.script?.title ?: niche?.name.orEmpty(),
                    finalSceneNarration = latestScript?.scenes?.maxByOrNull { it.sceneOrder }?.narration.orEmpty()
                )
            }
            val top = cmds.firstOrNull()
            if (top == null) {
                onLine("Could not produce a next video command.", TerminalLineType.STDERR)
                return@withContext
            }
            withContext(Dispatchers.Main) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("OmniAI Next Video Command", top.fullPromptCommand))
            }
            onLine("=== Auto-Next Video Command ($nicheLabel) ===", TerminalLineType.SYSTEM)
            onLine("  [${top.sourceType} · ${top.shortLabel}]", TerminalLineType.SUCCESS)
            onLine("  ${top.fullPromptCommand}", TerminalLineType.AI_STREAM)
            onLine("[Copied top command to Android clipboard]", TerminalLineType.SUCCESS)
        } catch (ex: Exception) {
            onLine("next video error: ${ex.message}", TerminalLineType.STDERR)
        }
    }

    private suspend fun runDbStats(onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val threads = db.chatDao().countThreads()
            val messages = db.chatDao().countMessages()
            val scripts = db.videoScriptDao().countScripts()
            val scenes = db.videoScriptDao().countScenes()
            val revisions = db.videoScriptDao().countRevisions()
            val socialPosts = db.socialSeoDao().countSocialPosts()
            val hashtagPacks = db.socialSeoDao().countHashtagPacks()
            val characters = db.characterProfileDao().countProfiles()
            val niches = db.nicheDao().countNiches()
            val nextCommands = db.nextCommandDao().countCommands()
            val seriesEpisodes = db.seriesEpisodeDao().countEpisodes()
            val projects = db.projectDao().countProjects()
            val projectTasks = db.projectDao().countTasks()
            val commitLogs = db.projectDao().countCommitLogs()
            val ytQuotaToday = youTubeAnalyticsRepository.getTodayQuotaUnitsOnce()

            onLine("=== OmniAI-Studio Relational Room DB v10 Stats ===", TerminalLineType.SYSTEM)
            onLine("  Projects           : $projects ($projectTasks tasks, $commitLogs git commits)", TerminalLineType.STDOUT)
            onLine("  Creator Niches     : $niches", TerminalLineType.STDOUT)
            onLine("  Next Commands      : $nextCommands", TerminalLineType.STDOUT)
            onLine("  Series Episodes    : $seriesEpisodes", TerminalLineType.STDOUT)
            onLine("  Chat Threads       : $threads", TerminalLineType.STDOUT)
            onLine("  Chat Messages      : $messages", TerminalLineType.STDOUT)
            onLine("  Video Scripts      : $scripts", TerminalLineType.STDOUT)
            onLine("  Normalized Scenes  : $scenes", TerminalLineType.STDOUT)
            onLine("  Script Revisions   : $revisions", TerminalLineType.STDOUT)
            onLine("  YT / IG / FB Posts : $socialPosts", TerminalLineType.STDOUT)
            onLine("  Hashtag Packs      : $hashtagPacks", TerminalLineType.STDOUT)
            onLine("  Character Profiles : $characters", TerminalLineType.STDOUT)
            onLine("  YT Daily Quota     : $ytQuotaToday / ${YouTubeApiClient.DAILY_FREE_QUOTA_LIMIT} units", TerminalLineType.STDOUT)
            onLine("[DB Inspector OK]", TerminalLineType.SUCCESS)
        } catch (ex: Exception) {
            onLine("db stats error: ${ex.message}", TerminalLineType.STDERR)
        }
    }

    private suspend fun runDbSearch(query: String, onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            onLine("Usage: db search <query>", TerminalLineType.STDERR)
            return@withContext
        }
        try {
            val matchedThreads = db.chatDao().searchThreadsAndMessages(query).take(6)
            val matchedMessages = db.chatDao().searchMessagesOnce(query, 6)
            val matchedScripts = db.videoScriptDao().searchScriptsAndScenes(query, 8)
            val matchedSocial = db.socialSeoDao().searchSocialPosts(query, 8)

            onLine("=== DB Search Results for '$query' ===", TerminalLineType.SYSTEM)

            onLine("Chats (${matchedThreads.size} thread(s), ${matchedMessages.size} message match(es)):", TerminalLineType.SUCCESS)
            if (matchedThreads.isEmpty() && matchedMessages.isEmpty()) {
                onLine("  (no chat matches)", TerminalLineType.STDOUT)
            } else {
                matchedThreads.forEach { t ->
                    val flags = buildString {
                        if (t.isPinned) append(" [PIN]")
                        if (t.isArchived) append(" [ARCHIVED]")
                    }
                    onLine("  • #${t.id} [${t.folderName}] ${t.title}$flags", TerminalLineType.STDOUT)
                }
                matchedMessages.forEach { m ->
                    val snippet = m.content.replace('\n', ' ').take(90)
                    onLine("    ↳ (${m.role}) $snippet", TerminalLineType.STDOUT)
                }
            }

            onLine("Video Scripts & Scenes (${matchedScripts.size}):", TerminalLineType.SUCCESS)
            if (matchedScripts.isEmpty()) {
                onLine("  (no script/scene matches)", TerminalLineType.STDOUT)
            } else {
                matchedScripts.forEach { s ->
                    onLine("  • #${s.id} [${s.status}] ${s.title} (${s.aspectRatio} · ${s.styleTag})", TerminalLineType.STDOUT)
                }
            }

            onLine("YT / IG / FB Social SEO Posts (${matchedSocial.size}):", TerminalLineType.SUCCESS)
            if (matchedSocial.isEmpty()) {
                onLine("  (no social post matches)", TerminalLineType.STDOUT)
            } else {
                matchedSocial.forEach { p ->
                    onLine("  • [${p.platform} · ${p.publishStatus}] ${p.title.ifBlank { "(Untitled)" }} ${p.hashtags.take(40)}", TerminalLineType.STDOUT)
                }
            }
        } catch (ex: Exception) {
            onLine("db search error: ${ex.message}", TerminalLineType.STDERR)
        }
    }

    private suspend fun runDbExportCsv(onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        onLine("Exporting Video Scripts, Scenes & YT/IG/FB SEO metadata to RFC-4180 CSV...", TerminalLineType.SYSTEM)
        try {
            val result = contentDbExporter.exportAllToLocalCsvFile()
            onLine("CSV Export Saved : ${result.filePath}", TerminalLineType.SUCCESS)
            onLine("Exported Rows    : ${result.rowCount} (${result.byteSize} bytes)", TerminalLineType.STDOUT)
        } catch (ex: Exception) {
            onLine("db export csv failed: ${ex.message}", TerminalLineType.STDERR)
        }
    }

    private fun printSysInfo(onLine: (String, TerminalLineType) -> Unit) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024L * 1024L)
        val totalMb = memInfo.totalMem / (1024L * 1024L)

        val statFs = StatFs(context.filesDir.absolutePath)
        val freeGb = (statFs.availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
        val totalGb = (statFs.totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))

        onLine("=== OmniAI-Studio System Info ===", TerminalLineType.SYSTEM)
        onLine("App Version : v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})", TerminalLineType.STDOUT)
        onLine("Device      : ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT})", TerminalLineType.STDOUT)
        onLine("ABIs        : ${Build.SUPPORTED_ABIS.joinToString(", ")}", TerminalLineType.STDOUT)
        onLine("Memory      : ${availMb} MB free / ${totalMb} MB total", TerminalLineType.STDOUT)
        onLine("Storage     : ${"%.2f".format(freeGb)} GB free / ${"%.2f".format(totalGb)} GB total", TerminalLineType.STDOUT)
        onLine("Workspace   : ${localShellExecutor.currentWorkingDir().absolutePath}", TerminalLineType.STDOUT)
        onLine("Termux      : ${if (termuxBridge.isTermuxInstalled()) "Installed (Bridge Ready)" else "Not installed"}", TerminalLineType.STDOUT)
        onLine(
            "AI Vault    : Tunnel=${if (vault.primaryTunnelUrl.isNotBlank()) "SET" else "EMPTY"} | HF=${if (vault.huggingFaceToken.isNotBlank()) "SET" else "EMPTY"} | Gemini=${if (vault.geminiApiKey.isNotBlank()) "SET" else "EMPTY"}",
            TerminalLineType.SUCCESS
        )
    }

    private suspend fun runAiPrompt(prompt: String, onLine: (String, TerminalLineType) -> Unit) {
        if (prompt.isBlank()) {
            onLine("Usage: ai <prompt>", TerminalLineType.STDERR)
            return
        }
        onLine("[OmniCLI AI] Streaming response...", TerminalLineType.SYSTEM)
        val lineBuffer = StringBuilder()
        var providerUsed = ""
        try {
            aiClient.streamChat(
                systemPrompt = "You are OmniCLI running inside an Android developer terminal. Respond concisely in plain text with runnable commands or crisp technical answers.",
                history = listOf(OpenAiChatMessage(role = "user", content = prompt))
            ).collect { event ->
                if (!event.failoverNotice.isNullOrBlank()) {
                    onLine("[Failover] ${event.failoverNotice}", TerminalLineType.SYSTEM)
                }
                if (event.providerTag.isNotBlank()) {
                    providerUsed = event.providerTag
                }
                if (event.token.isNotEmpty()) {
                    lineBuffer.append(event.token)
                    while (true) {
                        val nlIndex = lineBuffer.indexOf("\n")
                        if (nlIndex < 0) break
                        val completeLine = lineBuffer.substring(0, nlIndex)
                        lineBuffer.delete(0, nlIndex + 1)
                        onLine(completeLine, TerminalLineType.AI_STREAM)
                    }
                }
            }
            if (lineBuffer.isNotEmpty()) {
                onLine(lineBuffer.toString(), TerminalLineType.AI_STREAM)
            }
            if (providerUsed.isNotBlank()) {
                onLine("[Completed via $providerUsed]", TerminalLineType.SUCCESS)
            }
        } catch (ex: Exception) {
            onLine("AI stream error: ${ex.message}", TerminalLineType.STDERR)
        }
    }

    private suspend fun runKaggleStatus(onLine: (String, TerminalLineType) -> Unit) {
        if (vault.kaggleUsername.isBlank() || vault.kaggleApiKey.isBlank()) {
            onLine("Kaggle credentials missing. Configure Kaggle username & API key in Tab 5 (Vault).", TerminalLineType.STDERR)
            return
        }
        onLine("Polling Kaggle kernel ${vault.kaggleUsername}/${vault.kaggleKernelSlug}...", TerminalLineType.SYSTEM)
        try {
            val status = kaggleClient.pollKernelStatus()
            onLine("Kernel Status : ${status.status}", TerminalLineType.SUCCESS)
            if (!status.failureMessage.isNullOrBlank()) {
                onLine("Failure Info  : ${status.failureMessage}", TerminalLineType.STDERR)
            }
        } catch (ex: Exception) {
            onLine("Kaggle status error: ${ex.message}", TerminalLineType.STDERR)
        }
    }

    private suspend fun runHfSearch(query: String, onLine: (String, TerminalLineType) -> Unit) {
        if (query.isBlank()) {
            onLine("Usage: hf search <query>", TerminalLineType.STDERR)
            return
        }
        onLine("Searching Hugging Face Hub for '$query'...", TerminalLineType.SYSTEM)
        hfClient.searchModels(query, vault.huggingFaceToken).fold(
            onSuccess = { models ->
                if (models.isEmpty()) {
                    onLine("No Hugging Face models matched '$query'.", TerminalLineType.SYSTEM)
                } else {
                    onLine("Top ${models.take(8).size} models for '$query':", TerminalLineType.SUCCESS)
                    models.take(8).forEachIndexed { idx, m ->
                        val ggufBadge = if (m.isGguf) " [GGUF]" else ""
                        onLine(
                            "${idx + 1}. ${m.id}$ggufBadge | dl=${m.downloads} | likes=${m.likes}",
                            TerminalLineType.STDOUT
                        )
                    }
                }
            },
            onFailure = { ex ->
                onLine("HF search failed: ${ex.message}", TerminalLineType.STDERR)
            }
        )
    }

    private suspend fun runCurl(rawUrl: String, onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        val url = rawUrl.trim().trim('"', '\'')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            onLine("Usage: curl https://example.com (must start with http:// or https://)", TerminalLineType.STDERR)
            return@withContext
        }
        try {
            val start = System.currentTimeMillis()
            val req = Request.Builder().url(url).get().build()
            curlClient.newCall(req).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - start
                val bodyPreview = resp.body?.string().orEmpty().take(1200)
                onLine("HTTP ${resp.code} ${resp.message} (${elapsed}ms) — $url", if (resp.isSuccessful) TerminalLineType.SUCCESS else TerminalLineType.STDERR)
                onLine("Content-Type: ${resp.header("Content-Type") ?: "unknown"}", TerminalLineType.SYSTEM)
                if (bodyPreview.isNotBlank()) {
                    bodyPreview.lines().take(24).forEach { line ->
                        onLine(line, TerminalLineType.STDOUT)
                    }
                }
            }
        } catch (ex: Exception) {
            onLine("curl error: ${ex.message}", TerminalLineType.STDERR)
        }
    }

    private suspend fun runBackupNow(onLine: (String, TerminalLineType) -> Unit) {
        val pass = vault.autoBackupPassphrase
        if (pass.length < 6) {
            onLine("Set an Auto-Backup Passphrase (6+ chars) in Backup & Sync Center first.", TerminalLineType.STDERR)
            return
        }
        onLine("Running AES-256-GCM encrypted backup snapshot...", TerminalLineType.SYSTEM)
        val selection = BackupSelectionOptions()
        try {
            val file = vaultBackupManager.writeRollingLocalSnapshot(pass, selection)
            onLine("Local Rolling Snapshot saved: ${file.name} (${file.length() / 1024} KB)", TerminalLineType.SUCCESS)
        } catch (ex: Exception) {
            onLine("Local snapshot failed: ${ex.message}", TerminalLineType.STDERR)
        }
        if (vault.githubRepo.isNotBlank() && vault.githubToken.isNotBlank()) {
            githubBackupSync.pushEncryptedBackup(pass, selection).fold(
                onSuccess = { msg -> onLine("GitHub Cloud Sync: $msg", TerminalLineType.SUCCESS) },
                onFailure = { ex -> onLine("GitHub Cloud Sync skipped/failed: ${ex.message}", TerminalLineType.STDERR) }
            )
        }
    }

    private suspend fun runYtStats(onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        onLine("Fetching live YouTube channel performance & daily quota...", TerminalLineType.SYSTEM)
        val activeNiche = db.nicheDao().getNicheById(settingsRepo.current.activeNicheId)
        val profileId = activeNiche?.linkedYoutubeProfileId?.ifBlank { null }
        youTubeAnalyticsRepository.refresh(profileId).fold(
            onSuccess = { (stats, videos) ->
                val usedQuota = youTubeAnalyticsRepository.getTodayQuotaUnitsOnce()
                val remaining = (YouTubeApiClient.DAILY_FREE_QUOTA_LIMIT - usedQuota).coerceAtLeast(0)
                onLine("=== YouTube Channel Performance ===", TerminalLineType.SYSTEM)
                onLine("  Channel       : ${stats.channelTitle} (${stats.channelId})", TerminalLineType.SUCCESS)
                onLine("  Subscribers   : ${stats.subscriberCount} (${YouTubeApiClient.formatCompact(stats.subscriberCount)})", TerminalLineType.STDOUT)
                onLine("  Total Views   : ${stats.totalViewCount} (${YouTubeApiClient.formatCompact(stats.totalViewCount)})", TerminalLineType.STDOUT)
                onLine("  Videos        : ${stats.videoCount}", TerminalLineType.STDOUT)
                onLine("  Daily Quota   : $usedQuota / ${YouTubeApiClient.DAILY_FREE_QUOTA_LIMIT} units used ($remaining remaining)", TerminalLineType.SUCCESS)
                if (videos.isNotEmpty()) {
                    onLine("--- Recent Uploads (${videos.size}) ---", TerminalLineType.SYSTEM)
                    videos.take(8).forEachIndexed { idx, v ->
                        onLine(
                            "  ${idx + 1}. [${v.videoId}] ${v.title.take(48)} | ${v.viewCount} views · ${v.likeCount} likes · ${v.commentCount} comments",
                            TerminalLineType.STDOUT
                        )
                    }
                }
            },
            onFailure = { err ->
                onLine("yt stats failed: ${err.message}", TerminalLineType.STDERR)
            }
        )
    }

    private suspend fun runYtUpload(scriptIdArg: String, onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        val scriptId = scriptIdArg.toLongOrNull()
        if (scriptId == null) {
            onLine("Usage: yt upload <scriptId> (find IDs via 'db search <topic>' or Content Hub)", TerminalLineType.STDERR)
            return@withContext
        }
        val bundle = db.videoScriptDao().getScriptWithRelationsById(scriptId)
        if (bundle == null) {
            onLine("No VideoScript found with id #$scriptId.", TerminalLineType.STDERR)
            return@withContext
        }
        val videoPath = bundle.script.linkedVideoPath.trim()
        if (videoPath.isBlank() || !File(videoPath).exists()) {
            onLine("Script #$scriptId has no rendered MP4 file on disk at '$videoPath'. Render or link a video first.", TerminalLineType.STDERR)
            return@withContext
        }
        val ytPost = bundle.socialPosts.firstOrNull { it.platform.equals("YOUTUBE", ignoreCase = true) }
        val niche = db.nicheDao().getNicheById(bundle.script.nicheId)
        val tags = ytPost?.hashtags.orEmpty()
            .split(Regex("[\\s,]+"))
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() }

        val activeThumb = settingsRepo.current.activeThumbnailPath.takeIf { it.isNotBlank() && File(it).exists() }
        val request = YouTubeUploadRequest(
            videoFilePath = videoPath,
            thumbnailFilePath = activeThumb,
            title = ytPost?.title?.ifBlank { bundle.script.title } ?: bundle.script.title,
            description = buildString {
                if (!ytPost?.description.isNullOrBlank()) appendLine(ytPost?.description)
                if (!ytPost?.hashtags.isNullOrBlank()) appendLine(ytPost?.hashtags)
            }.trim().ifBlank { bundle.script.topic },
            tags = tags,
            privacyStatus = settingsRepo.current.youtubeDefaultPrivacy,
            firstComment = ytPost?.pinnedComment.orEmpty(),
            profileId = niche?.linkedYoutubeProfileId.orEmpty(),
            scriptId = bundle.script.id,
            socialPostId = ytPost?.id
        )

        onLine("Starting 2 MB chunked YouTube resumable upload for script #$scriptId ('${request.title}')...", TerminalLineType.SYSTEM)
        var lastPctLogged = -10
        youTubeApiClient.uploadVideoResumable(request) { pct, msg ->
            if (pct - lastPctLogged >= 15 || pct >= 90) {
                lastPctLogged = pct
                onLine("  [$pct%] $msg", TerminalLineType.STDOUT)
            }
        }.fold(
            onSuccess = { res ->
                onLine("YOUTUBE UPLOAD COMPLETE: ${res.watchUrl}", TerminalLineType.SUCCESS)
                onLine(
                    "  Channel=${res.channelLabel} | Thumbnail=${if (res.thumbnailUploaded) "SET" else "DEFAULT"} | FirstComment=${if (res.firstCommentPosted) "POSTED" else "SKIPPED"} | Quota=+${res.quotaUnitsLogged}",
                    TerminalLineType.STDOUT
                )
            },
            onFailure = { err ->
                onLine("yt upload failed: ${err.message}", TerminalLineType.STDERR)
            }
        )
    }

    private suspend fun runYtComments(videoIdArg: String, onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        if (videoIdArg.isBlank()) {
            onLine("Usage: yt comments <videoId or youtu.be URL>", TerminalLineType.STDERR)
            return@withContext
        }
        onLine("Fetching latest YouTube comments for '$videoIdArg'...", TerminalLineType.SYSTEM)
        val activeNiche = db.nicheDao().getNicheById(settingsRepo.current.activeNicheId)
        val profileId = activeNiche?.linkedYoutubeProfileId?.ifBlank { null }
        youTubeApiClient.fetchComments(videoIdArg, onlyUnanswered = false, profileId = profileId).fold(
            onSuccess = { comments ->
                if (comments.isEmpty()) {
                    onLine("No comments found for $videoIdArg.", TerminalLineType.SYSTEM)
                } else {
                    onLine("=== Latest Comments (${comments.size}) ===", TerminalLineType.SYSTEM)
                    comments.take(12).forEachIndexed { idx, c ->
                        val replyBadge = if (c.replyCount == 0) "[UNANSWERED]" else "[${c.replyCount} replies]"
                        onLine(
                            "  ${idx + 1}. ${c.authorDisplayName} $replyBadge (♥${c.likeCount}): ${c.textDisplay.replace('\n', ' ').take(120)}",
                            if (c.replyCount == 0) TerminalLineType.AI_STREAM else TerminalLineType.STDOUT
                        )
                    }
                }
            },
            onFailure = { err ->
                onLine("yt comments failed: ${err.message}", TerminalLineType.STDERR)
            }
        )
    }

    private suspend fun runCloudStatus(onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        val session = supabaseAuthManager.refreshLocalSessionSnapshot()
        val sync = cloudSyncRepository.syncStatus.value
        val lastSyncText = if (sync.lastSyncedAtMs > 0L) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date(sync.lastSyncedAtMs))
        } else {
            "Never"
        }
        onLine("=== Supabase Cloud DB & RBAC Status ===", TerminalLineType.SYSTEM)
        onLine("  Configured   : ${if (session.isConfigured) "YES (${supabaseAuthManager.normalizedBaseUrl()})" else "NO"}", TerminalLineType.STDOUT)
        onLine("  Google Email : ${session.email.ifBlank { "(Not signed in / Offline Local Mode)" }}", TerminalLineType.STDOUT)
        onLine("  Active Role  : ${session.role.badgeText} (Approved=${session.isApproved})", TerminalLineType.SUCCESS)
        onLine("  Device       : ${session.deviceName}", TerminalLineType.STDOUT)
        onLine("  Last Sync    : $lastSyncText | Rows=${sync.lastSyncedRows} | Avg GZIP Saved=${sync.lastSavedPercent}%", TerminalLineType.STDOUT)
        onLine("  Status       : ${sync.lastMessage}", TerminalLineType.SYSTEM)
    }

    private suspend fun runCloudSync(onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        onLine("Starting two-way GZIP-compressed text delta sync with Supabase...", TerminalLineType.SYSTEM)
        cloudSyncRepository.syncNow { progressLine ->
            onLine("  $progressLine", TerminalLineType.STDOUT)
        }.fold(
            onSuccess = { summary ->
                onLine("CLOUD SYNC COMPLETE: ${summary.message}", TerminalLineType.SUCCESS)
            },
            onFailure = { err ->
                onLine("cloud sync failed: ${err.message}", TerminalLineType.STDERR)
            }
        )
    }

    private suspend fun runCloudUsers(onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        onLine("Fetching registered Supabase user_profiles (OWNER only)...", TerminalLineType.SYSTEM)
        supabaseAuthManager.listAllUserProfiles().fold(
            onSuccess = { profiles ->
                onLine("=== Registered Cloud Accounts (${profiles.size}) ===", TerminalLineType.SYSTEM)
                if (profiles.isEmpty()) {
                    onLine("  (no user_profiles rows returned)", TerminalLineType.STDOUT)
                } else {
                    profiles.forEachIndexed { idx, p ->
                        val approval = if (p.isApproved) "APPROVED" else "PENDING/REVOKED"
                        val device = p.lastDevice?.ifBlank { "Unknown" } ?: "Unknown"
                        onLine(
                            "  ${idx + 1}. ${p.email} [${p.role} · $approval] | Device=$device | Seen=${p.lastSeenAt?.take(16) ?: "—"}",
                            if (p.role == "OWNER") TerminalLineType.SUCCESS else TerminalLineType.STDOUT
                        )
                    }
                }
            },
            onFailure = { err ->
                onLine("cloud users denied/failed: ${err.message}", TerminalLineType.STDERR)
            }
        )
    }

    private suspend fun runUserApiCredentialsPush(onLine: (String, TerminalLineType) -> Unit) =
        withContext(Dispatchers.IO) {
            onLine("Saving configured API credentials to the signed-in Supabase account...", TerminalLineType.SYSTEM)
            cloudSyncRepository.pushUserApiCredentials().fold(
                onSuccess = {
                    onLine("Private API credentials saved. RLS blocks other app users; Supabase project administrators remain privileged.", TerminalLineType.SUCCESS)
                },
                onFailure = { error ->
                    onLine("Supabase API credentials save failed: ${error.message}", TerminalLineType.STDERR)
                }
            )
        }

    private suspend fun runUserApiCredentialsPull(onLine: (String, TerminalLineType) -> Unit) =
        withContext(Dispatchers.IO) {
            onLine("Restoring API credentials for the signed-in Supabase account...", TerminalLineType.SYSTEM)
            cloudSyncRepository.pullUserApiCredentials().fold(
                onSuccess = { credentials ->
                    if (credentials == null) {
                        onLine("No API credentials are saved for this account yet.", TerminalLineType.STDOUT)
                    } else if (vault.importUserApiCredentials(credentials)) {
                        onLine("API credentials restored to this device's encrypted vault.", TerminalLineType.SUCCESS)
                    } else {
                        onLine("Could not save restored API credentials to the encrypted vault.", TerminalLineType.STDERR)
                    }
                },
                onFailure = { error ->
                    onLine("Supabase API credentials restore failed: ${error.message}", TerminalLineType.STDERR)
                }
            )
        }

    private fun runDeviceHealth(onLine: (String, TerminalLineType) -> Unit) {
        val snap = deviceHealthGuard.inspect()
        onLine("=== Device Thermal & Resource Health Guard ===", TerminalLineType.SYSTEM)
        onLine("  Thermal      : ${snap.thermalLevel.label}", if (snap.isSafeForHeavyMediaJob) TerminalLineType.SUCCESS else TerminalLineType.STDERR)
        onLine("  Battery      : ${snap.batteryPercent}% (${if (snap.isCharging) "Charging" else "On Battery"})", TerminalLineType.STDOUT)
        onLine("  Free Storage : ${snap.freeStorageMb} MB", TerminalLineType.STDOUT)
        onLine("  Avail RAM    : ${snap.availableRamMb} MB (LowMemory=${snap.isLowMemory})", TerminalLineType.STDOUT)
        onLine("  Media3 Gate  : ${if (snap.isSafeForHeavyMediaJob) "SAFE FOR RENDER" else "PAUSED"}", if (snap.isSafeForHeavyMediaJob) TerminalLineType.SUCCESS else TerminalLineType.STDERR)
        snap.warnings.forEach { w -> onLine("  [WARN] $w", TerminalLineType.STDERR) }
    }

    private suspend fun runScanSecrets(onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        val project = resolveActiveProject()
        if (project == null) {
            onLine("No active project found. Run 'proj list' first.", TerminalLineType.STDERR)
            return@withContext
        }
        val report = SecretLeakScanner.scanDirectory(gitHubGitEngine.getProjectDir(project.slug))
        onLine("=== SecretLeakScanner (${project.slug} · ${report.scannedFileCount} file(s)) ===", TerminalLineType.SYSTEM)
        if (report.isClean) {
            onLine("  CLEAN — 0 leaked tokens, keys, or private keys detected.", TerminalLineType.SUCCESS)
        } else {
            report.findings.forEach { f ->
                onLine("  [${f.severity.label}] ${f.filePath}:${f.lineNumber} — ${f.ruleName} (${f.maskedSnippet})", TerminalLineType.STDERR)
            }
        }
    }

    private suspend fun runBuildDoctor(onLine: (String, TerminalLineType) -> Unit) = withContext(Dispatchers.IO) {
        val project = resolveActiveProject()
        if (project == null || project.githubOwner.isBlank() || project.githubRepo.isBlank()) {
            onLine("Select a project with GitHub owner/repo configured first.", TerminalLineType.STDERR)
            return@withContext
        }
        onLine("Running AI Build Doctor on latest run of ${project.githubOwner}/${project.githubRepo}...", TerminalLineType.SYSTEM)
        val latest = runCatching { gitHubGitEngine.fetchLatestWorkflowRun(project.githubOwner, project.githubRepo) }.getOrNull()
        if (latest == null) {
            onLine("No GitHub Actions runs found for ${project.githubOwner}/${project.githubRepo}.", TerminalLineType.STDERR)
            return@withContext
        }
        aiBuildDoctor.diagnoseFailedRun(project.githubOwner, project.githubRepo, latest.id).fold(
            onSuccess = { diag ->
                onLine("=== AI Build Doctor (Run #${latest.runNumber} · ${diag.providerTag}) ===", TerminalLineType.SYSTEM)
                onLine("  Job: ${diag.failedJobName}", TerminalLineType.STDOUT)
                diag.aiDiagnosisMarkdown.lines().forEach { line ->
                    onLine("  $line", TerminalLineType.AI_STREAM)
                }
            },
            onFailure = { ex ->
                onLine("AI Build Doctor failed: ${ex.message}", TerminalLineType.STDERR)
            }
        )
    }

    private fun runKeysPoolStatus(onLine: (String, TerminalLineType) -> Unit) {
        onLine("=== Multi-Key API Pool Status ===", TerminalLineType.SYSTEM)
        apiKeyPoolManager.inspectPoolStatus().forEach { st ->
            onLine(
                "  ${st.provider.label.padEnd(16)} : ${st.healthyKeys}/${st.totalKeys} healthy (${st.coolingDownKeys} cooling down)",
                if (st.healthyKeys > 0) TerminalLineType.SUCCESS else TerminalLineType.STDOUT
            )
        }
    }
}
