package com.omniai.studio.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omniai.studio.OmniAiApplication

/**
 * WorkManager CoroutineWorker triggered automatically when `project.autoPushOnSave == true`
 * after saving a file in the Code Editor or completing the AI Multi-File Splitter.
 * Runs with `NetworkType.CONNECTED` so pushes complete reliably even if the user switches apps.
 */
class GitAutoPushWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val projectId = inputData.getLong(KEY_PROJECT_ID, 0L)
        if (projectId <= 0L) return Result.failure()

        val commitMsg = inputData.getString(KEY_COMMIT_MESSAGE)
            ?.takeIf { it.isNotBlank() }
            ?: "chore: auto-push saved workspace changes"

        val app = applicationContext as? OmniAiApplication ?: return Result.failure()
        val project = app.database.projectDao().getProjectById(projectId) ?: return Result.failure()
        if (project.githubOwner.isBlank() || project.githubRepo.isBlank() || app.vaultManager.githubToken.isBlank()) {
            return Result.failure()
        }

        val diff = app.gitHubGitEngine.scanLocalChanges(project)
        if (!diff.hasChanges) {
            return Result.success()
        }

        return runCatching {
            app.gitHubGitEngine.pushProjectAtomically(
                project = project,
                commitMessage = commitMsg
            )
            Result.success()
        }.getOrElse {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val KEY_PROJECT_ID = "project_id"
        private const val KEY_COMMIT_MESSAGE = "commit_message"

        fun enqueueAutoPush(
            context: Context,
            projectId: Long,
            commitMessage: String = "chore: auto-push on save"
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val data = Data.Builder()
                .putLong(KEY_PROJECT_ID, projectId)
                .putString(KEY_COMMIT_MESSAGE, commitMessage)
                .build()

            val request = OneTimeWorkRequestBuilder<GitAutoPushWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    "omniai_git_autopush_$projectId",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }
}
