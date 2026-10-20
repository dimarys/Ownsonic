package pro.dasoft.ownsonic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.*
import pro.dasoft.ownsonic.data.AutoSyncSlot
import pro.dasoft.ownsonic.data.LibraryStore
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

object AutoSyncScheduler {
    const val SLOT = "automaticSyncSlot"
    const val REQUEST_TAG = "automatic-sync-request"
    const val SCHEDULE_TAG = "automatic-sync-schedule"

    fun schedule(context: Context, refresh: Boolean = false) {
        val manager = WorkManager.getInstance(context)
        if (LibraryStore.get(context).automaticSyncEnabled()) {
            AutoSyncSlot.entries.forEach { slot ->
                manager.enqueueUniquePeriodicWork(name(slot),
                    if (refresh) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                    request(slot))
            }
        } else {
            AutoSyncSlot.entries.forEach { manager.cancelUniqueWork(name(it)) }
            manager.cancelAllWorkByTag(REQUEST_TAG)
        }
    }

    fun name(slot: AutoSyncSlot) = "automatic-sync-${slot.name.lowercase()}"

    fun request(slot: AutoSyncSlot, id: UUID? = null): PeriodicWorkRequest {
        val builder = PeriodicWorkRequestBuilder<AutoSyncWorker>(1, TimeUnit.DAYS)
            .setInputData(workDataOf(SLOT to slot.name))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setNextScheduleTimeOverride(slot.nextRun(ZonedDateTime.now()).toInstant().toEpochMilli())
            .addTag(SCHEDULE_TAG)
        if (id != null) builder.setId(id)
        return builder.build()
    }

    fun wifiNetwork(context: Context): Network? {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return null
        val capabilities = manager.getNetworkCapabilities(network) ?: return null
        return network.takeIf { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
    }
}

class AutoSyncTimeChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED || intent.action == Intent.ACTION_TIME_CHANGED) {
            AutoSyncScheduler.schedule(context, refresh = true)
        }
    }
}
