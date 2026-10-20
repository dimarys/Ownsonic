package pro.dasoft.ownsonic.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class SyncEngine(private val store: LibraryStore, private val client: SubsonicClient) {
    suspend fun run(account: String, status: suspend (AppMessage, Int, Int) -> Unit): Int {
        store.requireReadableLibrary()
        val pending = store.library.value.pending.toMap()
        for ((index, entry) in pending.entries.withIndex()) {
            coroutineContext.ensureActive()
            status(AppMessage(MessageCode.UPLOAD_RATINGS, index + 1, pending.size), index, pending.size)
            client.setRating(entry.key, entry.value)
            store.confirmRating(entry.key, entry.value)
        }
        val scrobbles = store.library.value.scrobbles.sortedBy { it.startedAt }
        for ((index, event) in scrobbles.withIndex()) {
            coroutineContext.ensureActive()
            status(AppMessage(MessageCode.UPLOAD_LISTENS, index + 1, scrobbles.size), index, scrobbles.size)
            client.scrobble(event)
            store.confirmScrobble(event.eventId)
        }
        status(AppMessage(MessageCode.READING_PLAYLIST), 0, 0)
        val songs = client.playlist().filter { (pending[it.id] ?: it.rating) == 0 }
        status(AppMessage(MessageCode.READING_PODCAST), 0, 0)
        val episodes = client.podcastEpisodes()
        val downloads = (songs.map { it.id to it } + episodes.map { it.streamId to it.asSong() }).filter { (_, song) ->
            val file = store.audioFile(song.id)
            !file.exists() || file.length() == 0L
        }
        if (downloads.isEmpty()) status(AppMessage(MessageCode.NO_DOWNLOADS), 0, 0)
        for ((index, download) in downloads.withIndex()) {
            coroutineContext.ensureActive()
            val (streamId, song) = download
            val message = AppMessage(MessageCode.DOWNLOADING, index + 1, downloads.size, song.title)
            status(message, index, downloads.size)
            client.download(streamId, store.audioFile(song.id))
            status(message, index + 1, downloads.size)
        }
        val coverIds = episodes.map { it.coverArtId }.filter { it.isNotBlank() }.toSet()
        for (id in coverIds) {
            coroutineContext.ensureActive()
            try {
                client.downloadCover(id, store.coverFile(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                // Artwork is optional; keep any existing valid image and finish audio sync.
                coroutineContext.ensureActive()
            }
        }
        coroutineContext.ensureActive()
        store.replaceSyncedLibrary(account, songs, episodes)
        val retained = (songs.map { it.id } + episodes.map { it.audioId }).map { store.audioFile(it).name }.toSet()
        store.audioDirectory.listFiles()?.filter { it.name !in retained }?.forEach { it.delete() }
        val retainedCovers = coverIds.map { store.coverFile(it).name }.toSet()
        store.coverDirectory.listFiles()?.filter { it.name !in retainedCovers }?.forEach { it.delete() }
        return songs.size
    }
}
