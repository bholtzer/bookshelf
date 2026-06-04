package com.bookshelf.data.remote

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.bookshelf.domain.repository.BookRepository
import com.bookshelf.domain.repository.PageRepository
import com.bookshelf.domain.usecase.auth.GetCurrentUserUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val getCurrentUser: GetCurrentUserUseCase,
    private val bookRepository: BookRepository,
    private val pageRepository: PageRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val user = getCurrentUser() ?: return Result.success() // not signed in — skip

        return try {
            // 1. Push unsynced book metadata
            bookRepository.syncToRemote(user.uid)

            // 2. Upload pending page files to Firebase Storage
            pageRepository.uploadPendingPages(user.uid)

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "bookshelf_sync"

        /**
         * Enqueue periodic sync every 30 minutes, requiring network.
         * Also enqueue an immediate one-shot sync whenever new content arrives.
         */
        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest,
            )
        }

        fun enqueueImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("${WORK_NAME}_immediate", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
