package com.bihstudio.bookshelf.data.remote

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.bihstudio.bookshelf.domain.model.AppResult
import com.bihstudio.bookshelf.domain.repository.BookRepository
import com.bihstudio.bookshelf.domain.repository.PageRepository
import com.bihstudio.bookshelf.domain.usecase.auth.GetCurrentUserUseCase
import com.google.firebase.storage.StorageException
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
        val results = listOf(
            bookRepository.syncToRemote(user.uid),
            pageRepository.uploadPendingPages(user.uid),
            bookRepository.syncFromRemote(user.uid),
        )
        val errors = results.filterIsInstance<AppResult.Error>()
        if (errors.isEmpty()) return Result.success()

        val output = workDataOf(
            KEY_SYNC_ERROR to errors.joinToString(separator = "\n") { it.message },
        )
        return when {
            errors.any { it.cause.isPermanentStorageFailure() } -> Result.failure(output)
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> Result.failure(output)
        }
    }

    companion object {
        const val WORK_NAME = "bookshelf_sync"
        const val KEY_SYNC_ERROR = "sync_error"
        private const val MAX_RETRIES = 3

        fun enqueuePeriodicSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
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

private fun Throwable?.isPermanentStorageFailure(): Boolean {
    var current = this
    while (current != null) {
        if (current is StorageException) {
            return current.httpResultCode == 404 ||
                current.errorCode == StorageException.ERROR_NOT_AUTHENTICATED ||
                current.errorCode == StorageException.ERROR_NOT_AUTHORIZED
        }
        current = current.cause
    }
    return false
}
