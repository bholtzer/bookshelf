package com.bihstudio.bookshelf.data.remote

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.bihstudio.bookshelf.domain.repository.BookRepository
import com.bihstudio.bookshelf.domain.repository.PageRepository
import com.bihstudio.bookshelf.domain.usecase.auth.GetCurrentUserUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val getCurrentUser: GetCurrentUserUseCase,
    private val bookRepository: BookRepository,
    private val pageRepository: PageRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val user = getCurrentUser() ?: return Result.success()
        return try {
            bookRepository.syncToRemote(user.uid)
            pageRepository.uploadPendingPages(user.uid)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "bookshelf_sync"

        fun enqueuePeriodicSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun enqueueImmediateSync(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("${WORK_NAME}_immediate", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
