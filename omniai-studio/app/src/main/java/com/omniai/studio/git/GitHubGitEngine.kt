package com.omniai.studio.git

import android.content.Context
import android.util.Base64
import com.omniai.studio.data.local.GitCommitLogEntity
import com.omniai.studio.data.local.OmniAiDatabase
import com.omniai.studio.data.local.ProjectEntity
import com.omniai.studio.data.local.ProjectFileIndexEntity
import com.omniai.studio.data.remote.ActionsMonitorState
import com.omniai.studio.data.remote.GitDiffResult
import com.omniai.studio.data.remote.GitHubActionJobsResponse
import com.omniai.studio.data.remote.GitHubActionsRunDto
import com.omniai.studio.data.remote.GitHubBlobContentResponse
import com.omniai.studio.data.remote.GitHubBlobCreateResponse
import com.omniai.studio.data.remote.GitHubCommitResponse
import com.omniai.studio.data.remote.GitHubCreateRepoResponse
import com.omniai.studio.data.remote.GitHubLatestRelease
import com.omniai.studio.data.remote.GitHubRefResponse
import com.omniai.studio.data.remote.GitHubTreeResponse
import com.omniai.studio.data.remote.GitHubWorkflowRunsResponse
import com.omniai.studio.projects.AiMultiFileSplitter
import com.omniai.studio.security.VaultManager
import com.omniai.studio.terminal.NetworkLogInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AtomicPushResult(
    val commitSha: String,
    val filesChanged: Int,
    val branch: String,
    val summary: String
)

data class GitPullResult(
    val filesWritten: Int,
    val filesSkippedLarge: Int,
    val branch: String,
    val treeSha: String
)

/**
 * Zero-dependency Atomic GitHub Git Data API engine built exclusively on OkHttp3 4.12.0,
 * Kotlinx Serialization 1.6.3, Android Base64 (NO_WRAP), and MessageDigest (SHA-256).
 *
 * Pushes any number of added, modified, and deleted files in ONE atomic Git commit so
 * GitHub Actions CI/CD triggers exactly once per push.
 */
class GitHubGitEngine(
    private val context: Context,
    private val db: OmniAiDatabase,
    private val vault: VaultManager,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(NetworkLogInterceptor)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun getProjectsRootDir(): File = File(context.filesDir, "projects").apply { mkdirs() }

    fun getProjectDir(slug: String): File {
        val safeSlug = AiMultiFileSplitter.slugifyProjectName(slug)
        return File(getProjectsRootDir(), safeSlug).apply { mkdirs() }
    }

    private fun requireToken(): String {
        val token = vault.githubToken.trim()
        require(token.isNotBlank()) { "Configure your GitHub Personal Access Token (repo + workflow scope) in Vault Settings." }
        return token
    }

    private fun githubRequestBuilder(url: String, token: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .header("User-Agent", "OmniAI-Studio-Android/4.5")

    private fun computeSha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(16 * 1024)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 1. Create Private Repo Helper:
     * Calls `POST https://api.github.com/user/repos` with `private=true` and `auto_init=true`
     * so the default branch is initialized immediately (avoiding empty-repo 409 conflicts).
     */
    suspend fun createPrivateRepo(
        repoName: String,
        description: String = "Created with OmniAI-Studio v4.5"
    ): GitHubCreateRepoResponse = withContext(Dispatchers.IO) {
        val token = requireToken()
        val cleanName = repoName.trim().replace(Regex("[^a-zA-Z0-9._-]"), "-")
        require(cleanName.isNotBlank()) { "Repository name cannot be blank." }

        val bodyObj = buildJsonObject {
            put("name", JsonPrimitive(cleanName))
            put("description", JsonPrimitive(description))
            put("private", JsonPrimitive(true))
            put("auto_init", JsonPrimitive(true))
        }
        val req = githubRequestBuilder("$API_BASE/user/repos", token)
            .post(bodyObj.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Create private repo failed (HTTP ${resp.code}): ${raw.take(220)}")
            }
            json.decodeFromString<GitHubCreateRepoResponse>(raw)
        }
    }

    /**
     * 2. Local Diff Scanner:
     * Walks `filesDir/projects/{slug}/`, computes SHA-256 for every file, compares against
     * `ProjectFileIndexEntity`, and returns `GitDiffResult(added, modified, deleted, synced)`.
     */
    suspend fun scanLocalChanges(project: ProjectEntity): GitDiffResult = withContext(Dispatchers.IO) {
        val projectRoot = getProjectDir(project.slug)
        val indexed = db.projectDao().getFileIndexesForProjectOnce(project.id)
            .associateBy { it.relativePath }

        val diskFiles = linkedMapOf<String, String>()
        projectRoot.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val rel = file.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                val safeRel = AiMultiFileSplitter.sanitizeRelativePath(rel)
                if (safeRel != null) {
                    diskFiles[safeRel] = computeSha256Hex(file)
                }
            }

        val added = mutableListOf<String>()
        val modified = mutableListOf<String>()
        val synced = mutableListOf<String>()

        for ((path, sha) in diskFiles) {
            val prev = indexed[path]
            when {
                prev == null -> added += path
                prev.syncedSha256 != sha -> modified += path
                else -> synced += path
            }
        }

        val deleted = indexed.keys.filter { it !in diskFiles }.sorted()
        GitDiffResult(
            added = added.sorted(),
            modified = modified.sorted(),
            deleted = deleted,
            synced = synced.sorted()
        )
    }

    /**
     * Lists every relative file path currently on disk inside `filesDir/projects/{slug}/`.
     */
    suspend fun listLocalFiles(project: ProjectEntity): List<String> = withContext(Dispatchers.IO) {
        val projectRoot = getProjectDir(project.slug)
        projectRoot.walkTopDown()
            .filter { it.isFile }
            .mapNotNull { file ->
                AiMultiFileSplitter.sanitizeRelativePath(
                    file.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                )
            }
            .sorted()
            .toList()
    }

    /**
     * 3. Atomic Multi-File Push:
     * Executes the full 6-step GitHub Git Data API transaction in ONE commit so 30 changed files
     * trigger ONLY ONE GitHub Actions workflow run.
     */
    suspend fun pushProjectAtomically(
        project: ProjectEntity,
        commitMessage: String,
        onProgress: (String) -> Unit = {}
    ): AtomicPushResult = withContext(Dispatchers.IO) {
        val token = requireToken()
        val owner = project.githubOwner.trim()
        val repo = project.githubRepo.trim()
        val branch = project.githubBranch.trim().ifBlank { "main" }
        require(owner.isNotBlank() && repo.isNotBlank()) {
            "Configure GitHub owner and repository name on project '${project.name}' first."
        }

        val finalMessage = commitMessage.trim().ifBlank { "chore(${project.slug}): sync project updates" }
        val diff = scanLocalChanges(project)
        val projectRoot = getProjectDir(project.slug)

        if (!diff.hasChanges) {
            throw IllegalStateException("Working tree clean — no added, modified, or deleted files to push.")
        }

        try {
            // Step 1: GET /repos/{owner}/{repo}/git/ref/heads/{branch} (bootstrap empty repo on 404/409)
            onProgress("Step 1/6: Resolving remote branch '$branch'...")
            val baseCommitSha = resolveOrBootstrapBranchRef(owner, repo, branch, token, project.name, onProgress)

            // Step 2: GET /repos/{owner}/{repo}/git/commits/{baseCommitSha} -> baseTreeSha
            onProgress("Step 2/6: Reading base tree from ${baseCommitSha.take(7)}...")
            val baseTreeSha = fetchCommitTreeSha(owner, repo, baseCommitSha, token)

            // Step 3: Base64-encode (NO_WRAP) and POST /git/blobs for every added & modified file
            val uploadPaths = (diff.added + diff.modified).distinct()
            val blobMap = linkedMapOf<String, String>()
            val totalUploads = uploadPaths.size

            uploadPaths.forEachIndexed { idx, relPath ->
                onProgress("Uploading file ${idx + 1}/$totalUploads: $relPath")
                val localFile = AiMultiFileSplitter.resolveConfinedFile(projectRoot, relPath)
                    ?: throw IOException("Blocked unsafe file path: $relPath")
                if (!localFile.exists()) {
                    throw IOException("File disappeared before upload: $relPath")
                }
                val bytes = localFile.readBytes()
                val base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val blobSha = createGitBlob(owner, repo, base64Content, token)
                blobMap[relPath] = blobSha
            }

            // Step 4: POST /repos/{owner}/{repo}/git/trees (added/modified blobs + sha=null deletions)
            onProgress("Step 4/6: Building atomic Git tree (${uploadPaths.size} updated, ${diff.deleted.size} deleted)...")
            val newTreeSha = createGitTree(
                owner = owner,
                repo = repo,
                baseTreeSha = baseTreeSha,
                uploadedBlobs = blobMap,
                deletedPaths = diff.deleted,
                token = token
            )

            // Step 5: POST /repos/{owner}/{repo}/git/commits
            onProgress("Step 5/6: Creating atomic commit...")
            val newCommitSha = createGitCommit(
                owner = owner,
                repo = repo,
                message = finalMessage,
                treeSha = newTreeSha,
                parentCommitSha = baseCommitSha,
                token = token
            )

            // Step 6: PATCH /repos/{owner}/{repo}/git/refs/heads/{branch} with force=false
            onProgress("Step 6/6: Updating refs/heads/$branch -> ${newCommitSha.take(7)}...")
            updateBranchRef(owner, repo, branch, newCommitSha, token)

            // Persist refreshed SHA-256 index, project metadata, and commit log
            refreshFileIndexFromDisk(project.id, projectRoot)
            val now = System.currentTimeMillis()
            db.projectDao().updateLastCommit(project.id, newCommitSha, now)
            db.projectDao().insertCommitLog(
                GitCommitLogEntity(
                    projectId = project.id,
                    commitSha = newCommitSha,
                    commitMessage = finalMessage,
                    branch = branch,
                    filesChanged = diff.totalPending,
                    status = "SUCCESS",
                    errorLog = "",
                    timestamp = now
                )
            )

            val summary = "Pushed ${diff.totalPending} file(s) atomically to $owner/$repo@$branch (${newCommitSha.take(7)})"
            onProgress(summary)
            AtomicPushResult(
                commitSha = newCommitSha,
                filesChanged = diff.totalPending,
                branch = branch,
                summary = summary
            )
        } catch (ex: Exception) {
            db.projectDao().insertCommitLog(
                GitCommitLogEntity(
                    projectId = project.id,
                    commitSha = "",
                    commitMessage = finalMessage,
                    branch = branch,
                    filesChanged = diff.totalPending,
                    status = "FAILED",
                    errorLog = ex.message ?: "Unknown Git push error",
                    timestamp = System.currentTimeMillis()
                )
            )
            throw ex
        }
    }

    private fun resolveOrBootstrapBranchRef(
        owner: String,
        repo: String,
        branch: String,
        token: String,
        projectName: String,
        onProgress: (String) -> Unit
    ): String {
        val refUrl = "$API_BASE/repos/$owner/$repo/git/ref/heads/$branch"
        val req = githubRequestBuilder(refUrl, token).get().build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                return json.decodeFromString<GitHubRefResponse>(raw).obj.sha
            }
            if (resp.code == 404 || resp.code == 409) {
                onProgress("Empty repo or missing branch '$branch' — bootstrapping README.md...")
                val readmeText = "# $projectName\n\nInitialized automatically by OmniAI-Studio v4.5.\n"
                val encodedReadme = Base64.encodeToString(readmeText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val bootstrapBody = buildJsonObject {
                    put("message", JsonPrimitive("chore: bootstrap initial repository branch"))
                    put("content", JsonPrimitive(encodedReadme))
                    put("branch", JsonPrimitive(branch))
                }
                val putReq = githubRequestBuilder("$API_BASE/repos/$owner/$repo/contents/README.md", token)
                    .put(bootstrapBody.toString().toRequestBody(jsonMediaType))
                    .build()
                client.newCall(putReq).execute().use { putResp ->
                    val putRaw = putResp.body?.string().orEmpty()
                    if (!putResp.isSuccessful) {
                        throw IOException("Empty-repo bootstrap failed (HTTP ${putResp.code}): ${putRaw.take(200)}")
                    }
                }
                // Re-fetch head ref after bootstrap
                val retryReq = githubRequestBuilder(refUrl, token).get().build()
                client.newCall(retryReq).execute().use { retryResp ->
                    val retryRaw = retryResp.body?.string().orEmpty()
                    if (!retryResp.isSuccessful) {
                        throw IOException("Branch ref still unavailable after bootstrap (HTTP ${retryResp.code}): ${retryRaw.take(180)}")
                    }
                    return json.decodeFromString<GitHubRefResponse>(retryRaw).obj.sha
                }
            }
            throw IOException("Failed to read ref/heads/$branch (HTTP ${resp.code}): ${raw.take(200)}")
        }
    }

    private fun fetchCommitTreeSha(owner: String, repo: String, commitSha: String, token: String): String {
        val req = githubRequestBuilder("$API_BASE/repos/$owner/$repo/git/commits/$commitSha", token)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Failed to fetch base commit $commitSha (HTTP ${resp.code}): ${raw.take(200)}")
            }
            return json.decodeFromString<GitHubCommitResponse>(raw).tree.sha
        }
    }

    private fun createGitBlob(owner: String, repo: String, base64Content: String, token: String): String {
        val bodyObj = buildJsonObject {
            put("content", JsonPrimitive(base64Content))
            put("encoding", JsonPrimitive("base64"))
        }
        val req = githubRequestBuilder("$API_BASE/repos/$owner/$repo/git/blobs", token)
            .post(bodyObj.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Failed to upload Git blob (HTTP ${resp.code}): ${raw.take(200)}")
            }
            return json.decodeFromString<GitHubBlobCreateResponse>(raw).sha
        }
    }

    private fun createGitTree(
        owner: String,
        repo: String,
        baseTreeSha: String,
        uploadedBlobs: Map<String, String>,
        deletedPaths: List<String>,
        token: String
    ): String {
        val treeEntries: JsonArray = buildJsonArray {
            uploadedBlobs.forEach { (path, blobSha) ->
                add(
                    buildJsonObject {
                        put("path", JsonPrimitive(path))
                        put("mode", JsonPrimitive("100644"))
                        put("type", JsonPrimitive("blob"))
                        put("sha", JsonPrimitive(blobSha))
                    }
                )
            }
            deletedPaths.forEach { delPath ->
                add(
                    buildJsonObject {
                        put("path", JsonPrimitive(delPath))
                        put("mode", JsonPrimitive("100644"))
                        put("type", JsonPrimitive("blob"))
                        put("sha", JsonNull)
                    }
                )
            }
        }
        val bodyObj = buildJsonObject {
            put("base_tree", JsonPrimitive(baseTreeSha))
            put("tree", treeEntries)
        }
        val req = githubRequestBuilder("$API_BASE/repos/$owner/$repo/git/trees", token)
            .post(bodyObj.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Failed to create Git tree (HTTP ${resp.code}): ${raw.take(220)}")
            }
            return json.decodeFromString<GitHubTreeResponse>(raw).sha
        }
    }

    private fun createGitCommit(
        owner: String,
        repo: String,
        message: String,
        treeSha: String,
        parentCommitSha: String,
        token: String
    ): String {
        val bodyObj = buildJsonObject {
            put("message", JsonPrimitive(message))
            put("tree", JsonPrimitive(treeSha))
            put("parents", buildJsonArray { add(JsonPrimitive(parentCommitSha)) })
        }
        val req = githubRequestBuilder("$API_BASE/repos/$owner/$repo/git/commits", token)
            .post(bodyObj.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Failed to create Git commit (HTTP ${resp.code}): ${raw.take(220)}")
            }
            return json.decodeFromString<GitHubCommitResponse>(raw).sha
        }
    }

    private fun updateBranchRef(
        owner: String,
        repo: String,
        branch: String,
        newCommitSha: String,
        token: String
    ) {
        val bodyObj = buildJsonObject {
            put("sha", JsonPrimitive(newCommitSha))
            put("force", JsonPrimitive(false))
        }
        val req = githubRequestBuilder("$API_BASE/repos/$owner/$repo/git/refs/heads/$branch", token)
            .patch(bodyObj.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Failed to update branch ref '$branch' (HTTP ${resp.code}): ${raw.take(220)}")
            }
        }
    }

    /**
     * 4. Git Pull / Clone:
     * Calls `GET /repos/{owner}/{repo}/git/trees/{branch}?recursive=1`, downloads blobs,
     * writes files into `filesDir/projects/{slug}/` (skipping files > 5 MB safely), and refreshes
     * `ProjectFileIndexEntity` SHA-256 hashes.
     */
    suspend fun pullProjectFromGitHub(
        project: ProjectEntity,
        onProgress: (String) -> Unit = {}
    ): GitPullResult = withContext(Dispatchers.IO) {
        val token = requireToken()
        val owner = project.githubOwner.trim()
        val repo = project.githubRepo.trim()
        val branch = project.githubBranch.trim().ifBlank { "main" }
        require(owner.isNotBlank() && repo.isNotBlank()) {
            "Configure GitHub owner and repo name before pulling."
        }

        onProgress("Fetching remote tree for $owner/$repo@$branch...")
        val treeReq = githubRequestBuilder(
            "$API_BASE/repos/$owner/$repo/git/trees/$branch?recursive=1",
            token
        ).get().build()

        val treeResp = client.newCall(treeReq).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Failed to fetch remote tree (HTTP ${resp.code}): ${raw.take(220)}")
            }
            json.decodeFromString<GitHubTreeResponse>(raw)
        }

        val blobs = treeResp.tree.filter { it.type == "blob" && !it.sha.isNullOrBlank() }
        val projectRoot = getProjectDir(project.slug)
        var writtenCount = 0
        var skippedLarge = 0

        blobs.forEachIndexed { idx, entry ->
            if (entry.size > MAX_PULL_BLOB_BYTES) {
                skippedLarge++
                return@forEachIndexed
            }
            val safeTarget = AiMultiFileSplitter.resolveConfinedFile(projectRoot, entry.path)
                ?: return@forEachIndexed

            onProgress("Pulling file ${idx + 1}/${blobs.size}: ${entry.path}")
            val blobReq = githubRequestBuilder(
                "$API_BASE/repos/$owner/$repo/git/blobs/${entry.sha}",
                token
            ).get().build()

            client.newCall(blobReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException("Failed to pull blob ${entry.path} (HTTP ${resp.code})")
                }
                val blobDto = json.decodeFromString<GitHubBlobContentResponse>(raw)
                val cleanBase64 = blobDto.content.replace("\n", "").replace("\r", "")
                val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                safeTarget.parentFile?.mkdirs()
                safeTarget.writeBytes(decodedBytes)
                writtenCount++
            }
        }

        refreshFileIndexFromDisk(project.id, projectRoot)
        onProgress("Pulled $writtenCount file(s) from $owner/$repo@$branch" +
            if (skippedLarge > 0) " ($skippedLarge >5MB skipped)" else "")

        GitPullResult(
            filesWritten = writtenCount,
            filesSkippedLarge = skippedLarge,
            branch = branch,
            treeSha = treeResp.sha
        )
    }

    /**
     * Recomputes SHA-256 hashes for every file currently inside [projectRoot] and stores them
     * in `project_file_indexes`.
     */
    suspend fun refreshFileIndexFromDisk(projectId: Long, projectRoot: File) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entries = mutableListOf<ProjectFileIndexEntity>()
        projectRoot.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val rel = file.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                val safeRel = AiMultiFileSplitter.sanitizeRelativePath(rel)
                if (safeRel != null) {
                    entries += ProjectFileIndexEntity(
                        projectId = projectId,
                        relativePath = safeRel,
                        syncedSha256 = computeSha256Hex(file),
                        lastModifiedAt = now
                    )
                }
            }
        db.projectDao().clearFileIndexesForProject(projectId)
        if (entries.isNotEmpty()) {
            db.projectDao().insertFileIndexes(entries)
        }
    }

    /**
     * 5. Fetch Latest GitHub Actions Workflow Run (`GET /repos/{owner}/{repo}/actions/runs?per_page=1`).
     */
    suspend fun fetchLatestWorkflowRun(owner: String, repo: String): GitHubActionsRunDto? = withContext(Dispatchers.IO) {
        val token = requireToken()
        val req = githubRequestBuilder(
            "$API_BASE/repos/${owner.trim()}/${repo.trim()}/actions/runs?per_page=1",
            token
        ).get().build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Actions runs check failed (HTTP ${resp.code}): ${raw.take(200)}")
            }
            json.decodeFromString<GitHubWorkflowRunsResponse>(raw).workflowRuns.firstOrNull()
        }
    }

    /**
     * Fetches job & step failure summary for a failed GitHub Actions workflow run.
     */
    suspend fun fetchFailedJobsSummary(owner: String, repo: String, runId: Long): String = withContext(Dispatchers.IO) {
        val token = requireToken()
        val req = githubRequestBuilder(
            "$API_BASE/repos/${owner.trim()}/${repo.trim()}/actions/runs/$runId/jobs",
            token
        ).get().build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@withContext "Unable to load failed job logs (HTTP ${resp.code})."
            }
            val jobsResp = json.decodeFromString<GitHubActionJobsResponse>(raw)
            val failedJobs = jobsResp.jobs.filter { it.conclusion == "failure" || it.conclusion == "cancelled" }
            if (failedJobs.isEmpty()) {
                return@withContext "Workflow run #$runId reported failure; inspect the run URL for step details."
            }
            buildString {
                failedJobs.forEach { job ->
                    append("Job '${job.name}' (${job.conclusion}): ")
                    val failedSteps = job.steps.filter { it.conclusion == "failure" }
                    if (failedSteps.isEmpty()) {
                        appendLine("failed during runner setup.")
                    } else {
                        appendLine(failedSteps.joinToString(", ") { "#${it.number} ${it.name}" })
                    }
                }
            }.trim()
        }
    }

    suspend fun fetchLatestReleaseForRepo(owner: String, repo: String): GitHubLatestRelease? = withContext(Dispatchers.IO) {
        val token = vault.githubToken.trim()
        val reqBuilder = Request.Builder()
            .url("$API_BASE/repos/${owner.trim()}/${repo.trim()}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .get()
        if (token.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $token")
        }
        runCatching {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                json.decodeFromString<GitHubLatestRelease>(resp.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    /**
     * Live GitHub Actions Build Watcher:
     * Polls `GET /repos/{owner}/{repo}/actions/runs?per_page=1` every 15 seconds until
     * `status == "completed"`, emitting live elapsed time, status (`QUEUED` -> `IN_PROGRESS` ->
     * `SUCCESS` / `FAILURE`), failure summary, and matching GitHub Release APK asset when available.
     */
    suspend fun watchLatestWorkflowRun(
        owner: String,
        repo: String,
        onUpdate: (ActionsMonitorState) -> Unit
    ) = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val maxPolls = 60 // Up to 15 minutes at 15s intervals

        onUpdate(
            ActionsMonitorState(
                isWatching = true,
                status = "QUEUED",
                workflowName = "Checking GitHub Actions..."
            )
        )

        for (poll in 0 until maxPolls) {
            if (!currentCoroutineContext().isActive) break
            val elapsedSec = (System.currentTimeMillis() - startMs) / 1000L
            val run = runCatching { fetchLatestWorkflowRun(owner, repo) }.getOrNull()

            if (run != null) {
                val normalizedStatus = when {
                    run.status.equals("completed", ignoreCase = true) -> {
                        when (run.conclusion?.lowercase()) {
                            "success" -> "SUCCESS"
                            "cancelled" -> "CANCELLED"
                            else -> "FAILURE"
                        }
                    }
                    run.status.equals("in_progress", ignoreCase = true) -> "IN_PROGRESS"
                    else -> "QUEUED"
                }

                if (run.status.equals("completed", ignoreCase = true)) {
                    val failureSummary = if (normalizedStatus == "FAILURE") {
                        runCatching { fetchFailedJobsSummary(owner, repo, run.id) }.getOrDefault("")
                    } else ""

                    val release = if (normalizedStatus == "SUCCESS") {
                        fetchLatestReleaseForRepo(owner, repo)
                    } else null
                    val apkAsset = release?.assets?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

                    onUpdate(
                        ActionsMonitorState(
                            isWatching = false,
                            runId = run.id,
                            runNumber = run.runNumber,
                            workflowName = run.name.ifBlank { run.displayTitle.ifBlank { "GitHub Actions" } },
                            status = normalizedStatus,
                            conclusion = run.conclusion.orEmpty(),
                            htmlUrl = run.htmlUrl,
                            elapsedSec = elapsedSec,
                            failureSummary = failureSummary,
                            releaseApkAsset = apkAsset,
                            releaseTagName = release?.tagName.orEmpty()
                        )
                    )
                    return@withContext
                } else {
                    onUpdate(
                        ActionsMonitorState(
                            isWatching = true,
                            runId = run.id,
                            runNumber = run.runNumber,
                            workflowName = run.name.ifBlank { run.displayTitle.ifBlank { "GitHub Actions" } },
                            status = normalizedStatus,
                            conclusion = run.conclusion.orEmpty(),
                            htmlUrl = run.htmlUrl,
                            elapsedSec = elapsedSec
                        )
                    )
                }
            } else {
                onUpdate(
                    ActionsMonitorState(
                        isWatching = true,
                        status = "QUEUED",
                        workflowName = "Waiting for workflow run...",
                        elapsedSec = elapsedSec
                    )
                )
            }

            delay(POLL_INTERVAL_MS)
        }

        onUpdate(
            ActionsMonitorState(
                isWatching = false,
                status = "IDLE",
                workflowName = "Actions watcher timed out after 15m"
            )
        )
    }

    companion object {
        private const val API_BASE = "https://api.github.com"
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val MAX_PULL_BLOB_BYTES = 5L * 1024L * 1024L // 5 MB safety cap
        private const val POLL_INTERVAL_MS = 15_000L
    }
}
