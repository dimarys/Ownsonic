package pro.dasoft.ownsonic.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LibraryStore internal constructor(context: Context) {
    private val prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE)
    private val playback = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
    private val preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val disk = AtomicFile(File(context.filesDir, "library.json"))
    val audioDirectory = File(context.filesDir, "audio").apply { mkdirs() }
    val coverDirectory = File(context.filesDir, "covers").apply { mkdirs() }

    fun coverFile(id: String): File {
        val name = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(coverDirectory, "$name.img")
    }
    private val playbackWrites = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "ownsonic-playback-storage").apply { isDaemon = true }
    }
    private val mutableWriteFailure = MutableStateFlow(false)
    val writeFailure = mutableWriteFailure.asStateFlow()
    private val mutableLoadFailure = MutableStateFlow(false)
    val loadFailure = mutableLoadFailure.asStateFlow()
    private val mutableLibrary = MutableStateFlow(loadLibrary())
    val library = mutableLibrary.asStateFlow()
    private val mutableRatingActions = MutableStateFlow(RatingActions(prefs.getInt("likeRating", 2), prefs.getInt("dislikeRating", 1)))
    val ratingActions = mutableRatingActions.asStateFlow()

    /** Await durability without blocking Compose or Media3's application thread. */
    suspend fun <T> onIo(operation: LibraryStore.() -> T): T = withContext(Dispatchers.IO) { operation() }

    /** Media3 callbacks cannot suspend; keep their writes ordered across service restarts. */
    fun enqueuePlaybackWrite(expectedRevision: Long?, operation: LibraryStore.() -> Unit) {
        playbackWrites.execute {
            try {
                synchronized(this) {
                    if (expectedRevision == null || library.value.revision == expectedRevision) operation()
                }
            } catch (_: Exception) {
                mutableWriteFailure.value = true
            }
        }
    }

    fun requireReadableLibrary() {
        if (loadFailure.value) throw AppFailure(MessageCode.LIBRARY_UNAVAILABLE)
    }

    private fun loadLibrary(): Library = try {
        readLibrary()
    } catch (_: JSONException) {
        mutableLoadFailure.value = true
        Library()
    } catch (_: IOException) {
        mutableLoadFailure.value = true
        Library()
    }

    @Synchronized
    fun retryLoadingLibrary() {
        val restored = readLibrary()
        mutableLibrary.value = restored
        mutableLoadFailure.value = false
    }

    /** Reset unreadable local state only after confirmation in the recovery screen. */
    @Synchronized
    fun resetDamagedLibrary() {
        check(loadFailure.value) { "The library does not require recovery." }
        persist(Library())
        check(playback.edit().clear().commit()) { "Could not reset playback state." }
        mutableLoadFailure.value = false
    }

    fun ratingReminderEnabled(): Boolean = preferences.getBoolean("ratingReminder", false)

    fun automaticSyncEnabled(): Boolean = preferences.getBoolean("automaticSync", false)

    fun setAutomaticSyncEnabled(enabled: Boolean) {
        check(preferences.edit().putBoolean("automaticSync", enabled).commit()) { throw AppFailure(MessageCode.AUTOMATIC_ERROR) }
    }

    fun hasPendingChanges(): Boolean = library.value.pending.isNotEmpty() || library.value.scrobbles.isNotEmpty()

    fun setRatingReminderEnabled(enabled: Boolean) {
        check(preferences.edit().putBoolean("ratingReminder", enabled).commit()) { throw AppFailure(MessageCode.REMINDER_ERROR) }
    }

    fun settings(includePassword: Boolean = true) = ServerSettings(
        prefs.getString("url", "").orEmpty(),
        prefs.getString("username", "").orEmpty(),
        if (!includePassword) "" else prefs.getString("password", null)?.let {
            try { decrypt(it) } catch (error: Exception) { throw AppFailure(MessageCode.CREDENTIALS_UNAVAILABLE, error) }
        }.orEmpty(),
        playlistId = prefs.getString("playlistId", "").orEmpty(),
        playlistName = prefs.getString("playlistName", "laterlist").orEmpty(),
        podcastId = if (prefs.getBoolean("podcastDisabled", !prefs.contains("url"))) null else prefs.getString("podcastId", "7"),
        podcastName = prefs.getString("podcastName", "Aerostat").orEmpty(),
        episodeLimit = prefs.getInt("episodeLimit", 5),
        likeRating = prefs.getInt("likeRating", 2), dislikeRating = prefs.getInt("dislikeRating", 1),
        newestEpisodes = prefs.getBoolean("newestEpisodes", false),
    )

    @Synchronized
    fun saveSettings(settings: ServerSettings) {
        requireReadableLibrary()
        require(settings.likeRating in 1..5 && settings.dislikeRating in 1..5) { throw AppFailure(MessageCode.RATING_RANGE_ERROR) }
        require(settings.episodeLimit in 1..50) { throw AppFailure(MessageCode.EPISODE_LIMIT_ERROR) }
        val current = library.value
        require(current.account.isEmpty() || current.account == settings.account) {
            throw AppFailure(MessageCode.ACCOUNT_ERROR)
        }
        check(prefs.edit().putString("url", settings.url).putString("username", settings.username)
            .putString("password", encrypt(settings.password))
            .putString("playlistId", settings.playlistId).putString("playlistName", settings.playlistName)
            .putBoolean("podcastDisabled", settings.podcastId == null).putString("podcastId", settings.podcastId)
            .putString("podcastName", settings.podcastName).putInt("episodeLimit", settings.episodeLimit)
            .putInt("likeRating", settings.likeRating).putInt("dislikeRating", settings.dislikeRating)
            .putBoolean("newestEpisodes", settings.newestEpisodes).commit()) { throw AppFailure(MessageCode.SAVE_SETTINGS_ERROR) }
        mutableRatingActions.value = RatingActions(settings.likeRating, settings.dislikeRating)
    }

    fun audioFile(id: String): File {
        val name = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(audioDirectory, "$name.audio")
    }

    @Synchronized
    fun rate(id: String, rating: Int) {
        require(rating in 1..5)
        val current = library.value
        if (current.songs.none { it.id == id }) return
        write(current.copy(
            songs = current.songs.map { if (it.id == id) it.copy(rating = rating) else it },
            pending = current.pending + (id to rating),
        ))
    }

    @Synchronized
    fun confirmRating(id: String, uploadedRating: Int) {
        val current = library.value
        if (current.pending[id] == uploadedRating) write(current.copy(pending = current.pending - id))
    }

    @Synchronized
    fun savePlayback(id: String, position: Long, revision: Long) {
        val current = library.value
        if (current.revision != revision) return
        if (current.source == PlaybackSource.AEROSTAT) {
            val episode = current.episodes.find { it.audioId == id } ?: return
            val savedPosition = position.coerceAtLeast(0)
            if (current.podcastCurrentId == episode.id && current.podcastPosition == savedPosition) return
            playback.edit().putString("podcastId", episode.id).putLong("podcastPosition", savedPosition)
                .putLong("revision", revision).apply()
            mutableLibrary.value = current.copy(podcastCurrentId = episode.id, podcastPosition = savedPosition,
                podcastFinished = current.podcastFinished && current.podcastCurrentId == episode.id)
            return
        }
        if (current.songs.none { it.id == id }) return
        if (current.currentId == id && current.position == position) return
        val savedPosition = position.coerceAtLeast(0)
        playback.edit().putString("id", id).putLong("position", savedPosition)
            .putLong("revision", revision).apply()
        mutableLibrary.value = current.copy(currentId = id, position = savedPosition,
            finished = current.finished && current.currentId == id)
    }

    @Synchronized
    fun setFinished(finished: Boolean) {
        val current = library.value
        if (current.source == PlaybackSource.AEROSTAT) {
            if (current.podcastFinished != finished) write(current.copy(podcastFinished = finished))
        } else if (current.finished != finished) write(current.copy(finished = finished))
    }

    @Synchronized
    fun enqueueScrobble(event: Scrobble) {
        val current = library.value
        if (event.eventId in current.recordedEvents || current.scrobbles.any { it.eventId == event.eventId }) return
        write(current.copy(scrobbles = current.scrobbles + event,
            recordedEvents = (current.recordedEvents + event.eventId).takeLast(512)))
    }

    @Synchronized
    fun confirmScrobble(eventId: String) {
        write(library.value.copy(scrobbles = library.value.scrobbles.filterNot { it.eventId == eventId }))
    }

    fun listeningSession(): ListeningSession? = playback.getString("listening", null)?.let {
        try {
        val json = JSONObject(it)
        ListeningSession(json.getString("eventId"), json.getString("songId"), json.getLong("revision"),
            json.getLong("startedAt"), json.getLong("playedMillis"), json.getBoolean("seeked"), json.optBoolean("reminderPlayed"))
        } catch (_: JSONException) { null }
    }

    fun saveListeningSession(session: ListeningSession?) {
        val json = session?.let {
            JSONObject().put("eventId", it.eventId).put("songId", it.songId).put("revision", it.revision)
                .put("startedAt", it.startedAt).put("playedMillis", it.playedMillis).put("seeked", it.seeked)
                .put("reminderPlayed", it.reminderPlayed).toString()
        }
        playback.edit().putString("listening", json).apply()
    }

    @Synchronized
    fun replaceQueue(account: String, songs: List<Song>) {
        val current = library.value
        val queue = songs.distinctBy { it.id }.map {
            it.copy(rating = current.pending[it.id] ?: it.rating)
        }.shuffled()
        write(current.copy(account = account, songs = queue, currentId = queue.firstOrNull()?.id.orEmpty(),
            position = 0, revision = current.revision + 1, finished = false, source = PlaybackSource.MUSIC, playOnLoad = false))
    }

    @Synchronized
    fun markEpisodeHeard(id: String, heard: Boolean) {
        val current = library.value
        if (current.episodes.none { it.id == id }) return
        val marks = if (heard) current.heardEpisodes + id else current.heardEpisodes - id
        if (marks != current.heardEpisodes) write(current.copy(heardEpisodes = marks))
    }

    @Synchronized
    fun consumePlaybackRequest(revision: Long): Boolean {
        val current = library.value
        if (current.revision != revision || !current.playOnLoad) return false
        mutableLibrary.value = current.copy(playOnLoad = false)
        return true
    }

    @Synchronized
    fun activateMusic(play: Boolean = true) {
        val current = library.value
        if (current.source == PlaybackSource.MUSIC) return
        write(current.copy(source = PlaybackSource.MUSIC, revision = current.revision + 1, playOnLoad = play,
            position = if (play && current.finished) 0 else current.position,
            finished = current.finished && !play))
    }

    @Synchronized
    fun activatePodcast(id: String) {
        val current = library.value
        if (current.episodes.none { it.id == id }) return
        val sameEpisode = current.podcastCurrentId == id
        write(current.copy(source = PlaybackSource.AEROSTAT, podcastCurrentId = id,
            podcastPosition = if (sameEpisode && !current.podcastFinished) current.podcastPosition else 0,
            podcastFinished = false, revision = current.revision + 1, playOnLoad = true))
    }

    @Synchronized
    fun replaceSyncedLibrary(account: String, songs: List<Song>, episodes: List<PodcastEpisode>) {
        val current = library.value
        val queue = songs.distinctBy { it.id }.map { it.copy(rating = current.pending[it.id] ?: it.rating) }.shuffled()
        val retainedEpisode = episodes.find { it.id == current.podcastCurrentId }
        write(current.copy(account = account, songs = queue, currentId = queue.firstOrNull()?.id.orEmpty(),
            position = 0, revision = current.revision + 1, finished = false, playOnLoad = false,
            episodes = episodes, podcastCurrentId = retainedEpisode?.id ?: episodes.firstOrNull()?.id.orEmpty(),
            podcastPosition = if (retainedEpisode != null) current.podcastPosition else 0,
            podcastFinished = retainedEpisode != null && current.podcastFinished))
    }

    private fun write(value: Library) {
        requireReadableLibrary()
        persist(value)
    }

    private fun persist(value: Library) {
        val json = JSONObject().put("account", value.account).put("currentId", value.currentId)
            .put("position", value.position).put("revision", value.revision).put("finished", value.finished)
            .put("pending", JSONObject(value.pending))
            .put("source", value.source.name).put("podcastCurrentId", value.podcastCurrentId)
            .put("podcastPosition", value.podcastPosition).put("podcastFinished", value.podcastFinished)
            .put("heardEpisodes", JSONArray(value.heardEpisodes.toList()))
            .put("episodes", JSONArray(value.episodes.map {
                JSONObject().put("id", it.id).put("streamId", it.streamId).put("title", it.title)
                    .put("publishedAt", it.publishedAt).put("duration", it.duration).put("podcastTitle", it.podcastTitle)
                    .put("coverArtId", it.coverArtId)
            }))
            .put("recordedEvents", JSONArray(value.recordedEvents))
            .put("scrobbles", JSONArray(value.scrobbles.map {
                JSONObject().put("eventId", it.eventId).put("songId", it.songId).put("startedAt", it.startedAt)
            }))
            .put("songs", JSONArray(value.songs.map {
                JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist)
                    .put("album", it.album).put("duration", it.duration).put("rating", it.rating)
            }))
        val stream = disk.startWrite()
        try {
            stream.write(json.toString().toByteArray())
            disk.finishWrite(stream)
        } catch (error: Exception) {
            disk.failWrite(stream)
            throw error
        }
        playback.edit().putString("id", value.currentId).putLong("position", value.position)
            .putString("podcastId", value.podcastCurrentId).putLong("podcastPosition", value.podcastPosition)
            .putLong("revision", value.revision).apply()
        mutableLibrary.value = value
    }

    private fun readLibrary(): Library {
        if (!disk.baseFile.exists() && !File(disk.baseFile.path + ".bak").exists()) return Library()
        val json = JSONObject(disk.openRead().bufferedReader().use { it.readText() })
        val songs = json.getJSONArray("songs")
        val pending = json.getJSONObject("pending")
        val restoredRevision = json.optLong("revision")
        val restorePlayback = playback.getLong("revision", -1) == restoredRevision
        val scrobbles = json.optJSONArray("scrobbles") ?: JSONArray()
        val recordedEvents = json.optJSONArray("recordedEvents") ?: JSONArray()
        return Library(json.optString("account"), (0 until songs.length()).map {
            val song = songs.getJSONObject(it)
            Song(song.getString("id"), song.getString("title"), song.getString("artist"),
                song.getString("album"), song.getLong("duration"), song.optInt("rating"))
        }, pending.keys().asSequence().associateWith { pending.getInt(it) },
            if (restorePlayback) playback.getString("id", "").orEmpty() else json.optString("currentId"),
            if (restorePlayback) playback.getLong("position", 0) else json.optLong("position"), restoredRevision,
            json.optBoolean("finished"), (0 until scrobbles.length()).map {
                val event = scrobbles.getJSONObject(it)
                Scrobble(event.getString("eventId"), event.getString("songId"), event.getLong("startedAt"))
            }, (0 until recordedEvents.length()).map { recordedEvents.getString(it) },
            episodes = (json.optJSONArray("episodes") ?: JSONArray()).let { episodes ->
                (0 until episodes.length()).map {
                    val episode = episodes.getJSONObject(it)
                    PodcastEpisode(episode.getString("id"), episode.getString("streamId"), episode.getString("title"),
                        episode.getLong("publishedAt"), episode.getLong("duration"), episode.optString("podcastTitle", "Aerostat"),
                        episode.optString("coverArtId"))
                }
            },
            heardEpisodes = (json.optJSONArray("heardEpisodes") ?: JSONArray()).let { marks ->
                (0 until marks.length()).map { marks.getString(it) }.toSet()
            },
            podcastCurrentId = if (restorePlayback) playback.getString("podcastId", json.optString("podcastCurrentId")).orEmpty()
                else json.optString("podcastCurrentId"),
            podcastPosition = if (restorePlayback) playback.getLong("podcastPosition", json.optLong("podcastPosition"))
                else json.optLong("podcastPosition"),
            podcastFinished = json.optBoolean("podcastFinished"),
            source = runCatching { PlaybackSource.valueOf(json.optString("source", "MUSIC")) }.getOrDefault(PlaybackSource.MUSIC))
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("ownsonic.connection", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("ownsonic.connection",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':')
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        }
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
    }

    companion object {
        @Volatile private var instance: LibraryStore? = null
        fun get(context: Context): LibraryStore = instance ?: synchronized(this) {
            instance ?: LibraryStore(context.applicationContext).also { instance = it }
        }
    }
}
