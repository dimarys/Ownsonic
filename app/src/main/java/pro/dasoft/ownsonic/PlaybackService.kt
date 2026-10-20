package pro.dasoft.ownsonic

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import pro.dasoft.ownsonic.data.LibraryStore
import pro.dasoft.ownsonic.data.ListeningTracker
import pro.dasoft.ownsonic.data.PlaybackSource

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var store: LibraryStore
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var revision = -1L
    private var replacingQueue = false
    private lateinit var listening: ListeningTracker
    private var songDuration = 0L
    private val reminderAudio = RatingReminderAudioProcessor()

    override fun onCreate() {
        super.onCreate()
        store = LibraryStore.get(this)
        listening = ListeningTracker(store.listeningSession())
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context).setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(reminderAudio)).build()
        }
        player = ExoPlayer.Builder(this, renderers).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            repeatMode = Player.REPEAT_MODE_OFF
            shuffleModeEnabled = false
        }
        val controlledPlayer = object : ForwardingPlayer(player) {
            override fun stop() {
                reminderAudio.cancel()
                listening.reset()
                persist { saveListeningSession(null) }
                persist { setFinished(false) }
                player.pause()
                player.seekTo(0)
                savePosition()
            }
            override fun seekToPrevious() = previous()
            override fun seekToPreviousMediaItem() = previous()
            private fun previous() {
                persist { setFinished(false) }
                if (player.currentMediaItemIndex > 0) player.seekTo(player.currentMediaItemIndex - 1, 0)
                else player.seekTo(0)
            }
        }
        session = MediaSession.Builder(this, controlledPlayer)
            .setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (replacingQueue || revision != store.library.value.revision) return
                reminderAudio.cancel()
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    completeListen(oldPosition.mediaItem?.mediaId)
                } else if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    if (oldPosition.mediaItem?.mediaId != newPosition.mediaItem?.mediaId) listening.reset()
                    else listening.seek(newPosition.positionMs, SystemClock.elapsedRealtime())
                }
                sampleListening()
            }

            override fun onEvents(player: Player, events: Player.Events) {
                isPlaybackActive = player.isPlaying
                if (!replacingQueue && revision == store.library.value.revision) {
                    if (player.isPlaying) persist { setFinished(false) }
                    else if (player.playbackState == Player.STATE_ENDED) {
                        if (player.playWhenReady) completeListen(player.currentMediaItem?.mediaId)
                        persist { setFinished(true) }
                    }
                    sampleListening()
                }
                savePosition()
            }
        })
        scope.launch {
            store.library.distinctUntilChangedBy { it.revision }.collect { library ->
                replacingQueue = true
                reminderAudio.cancel()
                if (revision >= 0 || library.source == PlaybackSource.AEROSTAT) {
                    listening.reset()
                    store.enqueuePlaybackWrite(library.revision) { saveListeningSession(null) }
                }
                val playOnLoad = store.onIo { consumePlaybackRequest(library.revision) }
                if (store.library.value.revision != library.revision) return@collect
                player.pause()
                revision = library.revision
                val songs = if (library.source == PlaybackSource.AEROSTAT) library.episodes.map { it.asSong() } else library.songs
                val currentId = if (library.source == PlaybackSource.AEROSTAT) "podcast:${library.podcastCurrentId}" else library.currentId
                val position = if (library.source == PlaybackSource.AEROSTAT) library.podcastPosition else library.position
                val items = songs.map { song ->
                    MediaItem.Builder().setMediaId(song.id).setUri(Uri.fromFile(store.audioFile(song.id)))
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title)
                            .setArtist(song.artist).setAlbumTitle(song.album).build()).build()
                }
                if (items.isEmpty()) player.clearMediaItems()
                else {
                    val index = songs.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
                    player.setMediaItems(items, index, position)
                    player.prepare()
                }
                replacingQueue = false
                if (playOnLoad && items.isNotEmpty()) player.play()
            }
        }
        scope.launch {
            while (true) { delay(1_000); sampleListening(); savePosition() }
        }
    }

    private fun sampleListening() {
        if (replacingQueue || revision != store.library.value.revision) return
        if (store.library.value.source == PlaybackSource.AEROSTAT) return
        player.currentMediaItem?.let { item ->
            songDuration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
                ?: store.library.value.songs.find { it.id == item.mediaId }?.duration ?: 0
            listening.sample(item.mediaId, revision, player.isPlaying, System.currentTimeMillis(), SystemClock.elapsedRealtime())
            val rating = store.library.value.songs.find { it.id == item.mediaId }?.rating ?: 0
            if (listening.reminderDue(store.ratingReminderEnabled(), player.isPlaying, rating, songDuration, player.currentPosition)) {
                val eventId = listening.session?.eventId
                reminderAudio.request {
                    scope.launch {
                        if (listening.session?.eventId == eventId && store.library.value.source == PlaybackSource.MUSIC) {
                            listening.markReminderPlayed()
                            saveListeningSession()
                        }
                    }
                }
            }
            saveListeningSession()
        }
    }

    private fun completeListen(mediaId: String?) {
        if (store.library.value.source == PlaybackSource.AEROSTAT) {
            store.library.value.episodes.find { it.audioId == mediaId }?.let { store.enqueuePlaybackWrite(null) { markEpisodeHeard(it.id, true) } }
            return
        }
        listening.complete(songDuration, SystemClock.elapsedRealtime())?.let { store.enqueuePlaybackWrite(null) { enqueueScrobble(it) } }
        persist { saveListeningSession(null) }
    }

    private fun persist(operation: LibraryStore.() -> Unit) {
        store.enqueuePlaybackWrite(revision, operation)
    }

    private fun saveListeningSession() {
        val snapshot = listening.session
        persist { saveListeningSession(snapshot) }
    }

    private fun savePosition() {
        if (!replacingQueue) player.currentMediaItem?.let {
            val mediaId = it.mediaId
            val position = player.currentPosition
            val expectedRevision = revision
            store.enqueuePlaybackWrite(expectedRevision) { savePlayback(mediaId, position, expectedRevision) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        sampleListening()
        savePosition()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isPlaybackActive = false
        sampleListening()
        savePosition()
        scope.cancel()
        reminderAudio.cancel()
        session?.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        @Volatile var isPlaybackActive = false
            private set
    }
}
