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
import com.omniai.studio.data.local.AppSettingsRepository
import com.omniai.studio.data.local.ContentDbExporter
import com.omniai.studio.data.local.OmniAiDatabase
import com.omniai.studio.data.remote.AiStreamingClient
import com.omniai.studio.data.remote.HfModelExplorerClient
import com.omniai.studio.data.remote.KaggleApiClient
import com.omniai.studio.data.remote.OpenAiChatMessage
import com.omniai.studio.git.GitHubGitEngine
import com.omniai.studio.niche.ContinuityEngine
import com.omniai.studio.security.VaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class OmniCliOutcome {
    HANDLED,
    CLEAR_SCREEN
}

/**
 * Built-in OmniCLI Command Dispatcher intercepting high-value developer, studio, DB, Niche & Git commands:
 * `help`, `proj list`, `proj switch <slug>`, `git status`, `git push "<msg>"`, `git pull`, `git actions`,
 * `niche list`, `niche switch <name>`, `next video`, `ai <prompt>`, `kaggle status`,
 * `hf search <query>`, `curl <url>`, `backup now`, `sysinfo`, `netlog`,
 * `db stats`, `db search <query>`, `db export csv`, and `clear`.
 * Any unrecognized command falls through to LocalShellExecutor or TermuxBridge.
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
    private val githubBackupSync: GitHubBackupSync
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
        onLine("  db stats             Live row counts for Room DB v9 tables", TerminalLineType.STDOUT)
        onLine("  db search <query>    Full-text search across Chats, Video Scripts/Scenes & Social SEO", TerminalLineType.STDOUT)
        onLine("  db export csv        Export Scripts + YT/IG/FB SEO to RFC-4180 CSV file", TerminalLineType.STDOUT)
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

            onLine("=== OmniAI-Studio Relational Room DB v9 Stats ===", TerminalLineType.SYSTEM)
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
}
