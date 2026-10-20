package pro.dasoft.ownsonic

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pro.dasoft.ownsonic.data.AutoSyncSlot
import pro.dasoft.ownsonic.data.LibraryStore

class AutoSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val slot = inputData.getString(AutoSyncScheduler.SLOT)?.let { AutoSyncSlot.valueOf(it) } ?: return@withContext Result.failure()
        val store = LibraryStore.get(applicationContext)
        if (!store.automaticSyncEnabled()) return@withContext Result.success()
        if (slot.shouldSync(store.hasPendingChanges())) {
            if (AutoSyncScheduler.wifiNetwork(applicationContext) == null || PlaybackService.isPlaybackActive) {
                return@withContext Result.retry()
            }
            val manager = WorkManager.getInstance(applicationContext)
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(AutoSyncScheduler.SLOT to slot.name))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(AutoSyncScheduler.REQUEST_TAG).build()
            manager.enqueueUniqueWork(SyncWorker.NAME, ExistingWorkPolicy.KEEP, request).result.get()
            if (manager.getWorkInfoById(request.id).get() == null) return@withContext Result.retry()
        }
        WorkManager.getInstance(applicationContext).updateWork(AutoSyncScheduler.request(slot, id)).get()
        Result.success()
    }
}
