package com.bihstudio.madafim

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.bihstudio.madafim.data.remote.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BookShelfApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.ENABLE_FIREBASE_APP_CHECK) {
            AppCheckInitializer.install()
        }
        SyncWorker.enqueuePeriodicSync(this)
        SyncWorker.enqueueImmediateSync(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
