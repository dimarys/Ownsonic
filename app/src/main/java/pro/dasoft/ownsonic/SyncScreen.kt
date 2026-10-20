package pro.dasoft.ownsonic

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import pro.dasoft.ownsonic.data.AppMessage
import pro.dasoft.ownsonic.data.Library
import pro.dasoft.ownsonic.data.LibraryStore
import pro.dasoft.ownsonic.data.MessageCode
import pro.dasoft.ownsonic.data.SubsonicClient

@Composable
internal fun SyncScreen(store: LibraryStore, library: Library, workManager: WorkManager, job: WorkInfo?,
                        syncing: Boolean, waitingAutomaticSync: Boolean) {
    val actions = LocalLibraryActions.current
    val resources = LocalResources.current
    var uiError by remember { mutableStateOf<String?>(null) }
    Text(stringResource(R.string.road_ready), fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.sync_description))
    Text(stringResource(R.string.library_counts, library.songs.size, library.pending.size, library.scrobbles.size))
    Text(stringResource(R.string.podcast_count, library.episodes.size))
    val data = when (job?.state) {
        WorkInfo.State.RUNNING -> job.progress
        WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED -> job.outputData
        else -> null
    }
    val message = data?.appMessage() ?: AppMessage(when (job?.state) {
        WorkInfo.State.RUNNING -> MessageCode.SYNC_STARTING
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> MessageCode.SYNC_WAITING
        WorkInfo.State.CANCELLED -> MessageCode.SYNC_INTERRUPTED
        WorkInfo.State.FAILED -> MessageCode.SYNC_FAILED
        else -> MessageCode.SYNC_NONE
    })
    // Old WorkManager rows survive application upgrades; only those use the legacy decoder.
    val legacy = data?.takeIf { it.getString("messageCode") == null }?.getString("message")
    val downloading = message.code == MessageCode.DOWNLOADING || legacy?.startsWith("Downloading ") == true
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            legacy?.let { legacyLocalizedMessage(resources, it) } ?: localizedMessage(resources, message),
            maxLines = if (downloading) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
        val downloadTotal = job?.progress?.getInt("total", 0) ?: 0
        val downloaded = (job?.progress?.getInt("completed", 0) ?: 0).coerceIn(0, downloadTotal.coerceAtLeast(0))
        if (syncing && downloading && downloadTotal > 0) {
            LinearProgressIndicator(
                progress = { downloaded.toFloat() / downloadTotal },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
            )
            Text(stringResource(R.string.download_count, downloaded, downloadTotal), style = MaterialTheme.typography.bodySmall)
        } else if (syncing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    } }
    Button(enabled = !syncing && !actions.busy, onClick = { actions.run {
        uiError = null
        try {
            store.onIo { requireReadableLibrary(); SubsonicClient(settings()) }
            workManager.enqueueUniqueWork(SyncWorker.NAME,
                if (waitingAutomaticSync) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>().build())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { uiError = localizedMessage(resources, error) }
    } }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text(stringResource(R.string.sync_button), fontSize = 18.sp) }
    if (job != null && !job.state.isFinished) {
        OutlinedButton(onClick = { workManager.cancelWorkById(job.id) },
            modifier = Modifier.fillMaxWidth().height(60.dp)) {
            Text("✖️ ${stringResource(R.string.cancel_sync)}", fontSize = 18.sp)
        }
    }
    uiError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Text(stringResource(R.string.sync_reset_hint), style = MaterialTheme.typography.bodySmall)
}
