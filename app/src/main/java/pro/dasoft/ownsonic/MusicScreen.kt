package pro.dasoft.ownsonic

import kotlinx.coroutines.Dispatchers

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import pro.dasoft.ownsonic.data.Library
import pro.dasoft.ownsonic.data.LibraryStore
import pro.dasoft.ownsonic.data.PlaybackSource

@Composable
internal fun MusicScreen(store: LibraryStore, library: Library, player: MediaController?, syncing: Boolean,
                         tick: Int, musicCoverHeight: Dp, connectionError: String?, onSettings: () -> Unit) {
    val resources = LocalResources.current
    val ratingActions by store.ratingActions.collectAsState(context = Dispatchers.Main.immediate)
    var uiError by remember { mutableStateOf<String?>(null) }
    val musicActive = library.source == PlaybackSource.MUSIC
    val song = library.songs.find { musicActive && it.id == player?.currentMediaItem?.mediaId } ?: library.songs.find { it.id == library.currentId }
    val musicReady = musicActive && song != null && player?.currentMediaItem?.mediaId == song.id
    val position = if (tick >= 0 && musicReady) player?.currentPosition ?: library.position else library.position
    val duration = player?.duration?.takeIf { musicReady }?.takeIf { it != C.TIME_UNSET && it > 0 } ?: song?.duration ?: 0
    val playing = musicReady && player?.isPlaying == true
    val playerError = player?.playerError?.takeIf { musicReady }?.let { resources.getString(R.string.playback_error, it.errorCodeName) }
    key(song?.id, library.revision) {
        PlayerCover(song?.let { store.audioFile(it.id) }, height = musicCoverHeight)
    }
    Text(song?.title ?: stringResource(R.string.empty_title), fontSize = 26.sp, fontWeight = FontWeight.Bold,
        maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE,
            initialDelayMillis = MetadataMarqueePauseMillis, repeatDelayMillis = MetadataMarqueePauseMillis))
    Text(song?.artist ?: stringResource(R.string.empty_hint), fontSize = 20.sp, color = Color(0xFFAAB7AC),
        maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE,
            initialDelayMillis = MetadataMarqueePauseMillis, repeatDelayMillis = MetadataMarqueePauseMillis))
    Text(song?.album.orEmpty(), fontSize = 18.sp, minLines = 1, maxLines = 1, softWrap = false,
        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE,
            initialDelayMillis = MetadataMarqueePauseMillis, repeatDelayMillis = MetadataMarqueePauseMillis))
    if (song != null) {
        val index = library.songs.indexOfFirst { it.id == song.id }
        PlaybackBar(position, duration, song.id, musicReady) { fraction ->
            store.onIo { setFinished(false) }
            player?.seekTo((duration * fraction).toLong())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${time(position)} / ${time(duration)}")
            Text("${index + 1} / ${library.songs.size}")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaybackButton(R.drawable.ic_previous, stringResource(R.string.previous_song), Modifier.weight(1f), musicReady) {
                store.onIo { setFinished(false) }
                if (index > 0) player?.seekTo(index - 1, 0) else player?.seekTo(0)
            }
            PlaybackButton(if (playing) R.drawable.ic_pause else R.drawable.ic_play, if (playing) stringResource(R.string.pause) else stringResource(R.string.play), Modifier.weight(1f), player != null && !syncing && (!musicActive || musicReady)) {
                if (!musicActive) store.onIo { activateMusic() }
                else if (playing) player?.pause() else {
                    if (library.finished || player?.playbackState == Player.STATE_ENDED || (duration > 0 && position >= duration)) player?.seekTo(0)
                    store.onIo { setFinished(false) }
                    player?.play()
                }
            }
            PlaybackButton(R.drawable.ic_stop, stringResource(R.string.stop), Modifier.weight(1f), musicReady) { player?.stop() }
            PlaybackButton(R.drawable.ic_next, stringResource(R.string.next_song), Modifier.weight(1f), musicReady) { next(player, duration, store) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlaybackButton(R.drawable.ic_like, stringResource(R.string.like, ratingActions.like), Modifier.weight(1f), !syncing && musicReady, song.rating == ratingActions.like) {
                runCatching { store.onIo { rate(song.id, ratingActions.like) } }.onFailure { uiError = resources.getString(R.string.save_rating_error) }
            }
            PlaybackButton(R.drawable.ic_dislike, stringResource(R.string.dislike, ratingActions.dislike), Modifier.weight(1f), !syncing && musicReady, song.rating == ratingActions.dislike) {
                runCatching { store.onIo { rate(song.id, ratingActions.dislike) }; if (player?.currentMediaItem?.mediaId == song.id) next(player, duration, store) }.onFailure { uiError = resources.getString(R.string.save_rating_error) }
            }
        }
        if (library.finished) {
            Text(stringResource(R.string.queue_finished), style = MaterialTheme.typography.bodySmall, color = Accent)
        }
    } else Button(onClick = { onSettings() }) { Text(stringResource(R.string.setup)) }
    (uiError ?: connectionError ?: playerError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
