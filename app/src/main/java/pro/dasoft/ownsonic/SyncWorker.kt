package pro.dasoft.ownsonic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pro.dasoft.ownsonic.data.AppFailure
import pro.dasoft.ownsonic.data.AppMessage
import pro.dasoft.ownsonic.data.AutoSyncSlot
import pro.dasoft.ownsonic.data.LibraryStore
import pro.dasoft.ownsonic.data.MessageCode
import pro.dasoft.ownsonic.data.SubsonicClient
import pro.dasoft.ownsonic.data.SyncEngine

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val store = LibraryStore.get(applicationContext)
            val automaticSlot = inputData.getString(AutoSyncScheduler.SLOT)?.let { AutoSyncSlot.valueOf(it) }
            val network = if (automaticSlot != null) {
                if (!store.automaticSyncEnabled()) return@withLock Result.success(AppMessage(MessageCode.AUTOMATIC_DISABLED).toWorkData())
                if (runAttemptCount == 0 && !automaticSlot.shouldSync(store.hasPendingChanges())) {
                    return@withLock Result.success(AppMessage(MessageCode.NO_CHANGES).toWorkData())
                }
                if (PlaybackService.isPlaybackActive) return@withLock Result.retry()
                AutoSyncScheduler.wifiNetwork(applicationContext) ?: return@withLock Result.retry()
            } else null
            try {
                setForeground(foreground())
                val settings = store.settings()
                val client = SubsonicClient(settings, network)
                val count = coroutineScope {
                    val cancellationWatcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                        try { awaitCancellation() } finally { client.cancel() }
                    }
                    try {
                        SyncEngine(store, client).run(settings.account, ::status)
                    } finally {
                        cancellationWatcher.cancel()
                    }
                }
                Result.success(AppMessage(MessageCode.SYNC_COMPLETE_ALL, count, store.library.value.episodes.size).toWorkData())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                coroutineContext.ensureActive()
                val message = when (error) {
                    is java.net.UnknownHostException -> AppMessage(MessageCode.UNREACHABLE)
                    is java.net.SocketTimeoutException -> AppMessage(MessageCode.TIMEOUT)
                    is javax.net.ssl.SSLException -> AppMessage(MessageCode.SSL_ERROR)
                    is java.net.ConnectException -> AppMessage(MessageCode.CONNECT_ERROR)
                    else -> (error as? AppFailure)?.userMessage ?: AppMessage(MessageCode.SYNC_FAILED)
                }
                if (automaticSlot != null && runAttemptCount < 3) Result.retry()
                else Result.failure(message.toWorkData())
            }
        }
    }

    private suspend fun status(message: AppMessage, completed: Int = 0, total: Int = 0) {
        setProgress(message.toWorkData(completed, total))
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground()

    private fun foreground(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("sync", applicationContext.getString(R.string.sync_channel), NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "sync")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Ownsonic").setContentText(applicationContext.getString(R.string.sync_notification))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, applicationContext.getString(R.string.cancel_sync),
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .setOngoing(true).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(2, notification)
    }

    companion object {
        const val NAME = "laterlist-sync"
        private val syncMutex = Mutex()
    }
}
